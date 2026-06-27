package com.meada.profiles.barbearia.appointments;

import com.meada.profiles.barbearia.BarberAppointmentStatus;
import com.meada.profiles.barbearia.BarberContextCache;
import com.meada.profiles.barbearia.barbers.BarberBarber;
import com.meada.profiles.barbearia.barbers.BarberBarberRepository;
import com.meada.profiles.barbearia.config.BarberConfig;
import com.meada.profiles.barbearia.config.BarberConfigRepository;
import com.meada.profiles.barbearia.services.BarberService;
import com.meada.profiles.barbearia.services.BarberServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos agendamentos de barbearia (camada 8.1). Clone de SalonAppointmentService — conflito POR
 * BARBEIRO. {@link #create} valida barbeiro (existe + ativo), serviço (existe + ativo; dá a duração e
 * o preço para snapshot), e a janela de funcionamento. Delega ao repositório, que re-verifica o
 * conflito DENTRO da transação. Status inicial = agendado.
 *
 * <p>{@link #updateStatus} valida a transição e dispara a notificação (confirmado/cancelado) com o
 * nome do barbeiro. Fuso HARDCODED America/Sao_Paulo (pendência, igual salon).
 */
@Service
public class BarberAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final BarberAppointmentRepository appointmentRepository;
    private final BarberBarberRepository barberRepository;
    private final BarberServiceRepository serviceRepository;
    private final BarberConfigRepository configRepository;
    private final BarberAppointmentNotifier notifier;
    private final BarberContextCache contextCache;

    public BarberAppointmentService(BarberAppointmentRepository appointmentRepository,
                                    BarberBarberRepository barberRepository,
                                    BarberServiceRepository serviceRepository,
                                    BarberConfigRepository configRepository,
                                    BarberAppointmentNotifier notifier,
                                    BarberContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.barberRepository = barberRepository;
        this.serviceRepository = serviceRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class BarberNotFoundException extends RuntimeException {}
    public static class ServiceNotFoundException extends RuntimeException {}
    public static class InactiveBarberException extends RuntimeException {}
    public static class InactiveServiceException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient BarberAppointmentConflict conflict;

        public ConflictException(BarberAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public BarberAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um agendamento (status inicial agendado). Valida barbeiro/serviço (existem + ativos),
     * janela de funcionamento (no fuso do tenant), e delega ao repo — que re-verifica conflito por
     * barbeiro na transação. Snapshots de nome/preço/duração vêm de barber+service.
     */
    public BarberAppointment create(UUID companyId, UUID barberId, UUID serviceId, UUID contactId,
                                    UUID conversationId, Instant startAt, String guestName,
                                    String guestPhone, String notes) {
        BarberBarber barber = barberRepository.findById(companyId, barberId)
            .orElseThrow(BarberNotFoundException::new);
        if (!barber.active()) {
            throw new InactiveBarberException();
        }
        BarberService service = serviceRepository.findById(companyId, serviceId)
            .orElseThrow(ServiceNotFoundException::new);
        if (!service.active()) {
            throw new InactiveServiceException();
        }
        BarberConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, service.durationMinutes(), config);

        BarberAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, barberId, barber.name(),
                serviceId, service.name(), service.priceCents(), service.durationMinutes(),
                conversationId, contactId, guestName, guestPhone, startAt, notes);
        } catch (BarberAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<BarberAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                        UUID barberId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, barberId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID barberId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, barberId, contactId);
    }

    public Optional<BarberAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public BarberAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        BarberAppointmentStatus newStatus = BarberAppointmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        BarberAppointment current = appointmentRepository.findById(companyId, id)
            .orElseThrow(AppointmentNotFoundException::new);
        BarberAppointmentStatus from = BarberAppointmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(
            DATE_FMT.format(z), TIME_FMT.format(z), current.barberName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que o agendamento inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, BarberConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(durationMinutes).toLocalTime();
        boolean startsOk = !startTime.isBefore(config.opensAt());
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
