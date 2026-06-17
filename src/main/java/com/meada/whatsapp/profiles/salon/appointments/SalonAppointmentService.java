package com.meada.whatsapp.profiles.salon.appointments;

import com.meada.whatsapp.profiles.salon.SalonAppointmentStatus;
import com.meada.whatsapp.profiles.salon.SalonContextCache;
import com.meada.whatsapp.profiles.salon.config.SalonConfig;
import com.meada.whatsapp.profiles.salon.config.SalonConfigRepository;
import com.meada.whatsapp.profiles.salon.offerings.SalonOffering;
import com.meada.whatsapp.profiles.salon.offerings.SalonOfferingRepository;
import com.meada.whatsapp.profiles.salon.professionals.SalonProfessional;
import com.meada.whatsapp.profiles.salon.professionals.SalonProfessionalRepository;
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
 * Regras dos agendamentos de salão (camada 7.5).
 *
 * <p>{@link #create} valida profissional (existe + ativo), serviço (existe + ativo; dá a duração e o
 * preço para snapshot), e a janela de funcionamento. Delega ao repositório, que re-verifica o
 * conflito POR PROFISSIONAL dentro da transação (decisão 5). Status inicial = agendado.
 *
 * <p>{@link #updateStatus} valida a transição (decisão 2) e dispara a notificação (decisão 3) com o
 * nome do profissional. Fuso HARDCODED America/Sao_Paulo (pendência).
 */
@Service
public class SalonAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SalonAppointmentRepository appointmentRepository;
    private final SalonProfessionalRepository professionalRepository;
    private final SalonOfferingRepository offeringRepository;
    private final SalonConfigRepository configRepository;
    private final SalonAppointmentNotifier notifier;
    private final SalonContextCache contextCache;

    public SalonAppointmentService(SalonAppointmentRepository appointmentRepository,
                                   SalonProfessionalRepository professionalRepository,
                                   SalonOfferingRepository offeringRepository,
                                   SalonConfigRepository configRepository,
                                   SalonAppointmentNotifier notifier,
                                   SalonContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.offeringRepository = offeringRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ServiceNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactiveServiceException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient SalonAppointmentConflict conflict;

        public ConflictException(SalonAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public SalonAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um agendamento (status inicial agendado). Valida profissional/serviço (existem + ativos),
     * janela de funcionamento (no fuso do tenant), e delega ao repo — que re-verifica conflito por
     * profissional na transação. Snapshots de nome/preço/duração vêm de professional+offering.
     */
    public SalonAppointment create(UUID companyId, UUID professionalId, UUID serviceId, UUID contactId,
                                   UUID conversationId, Instant startAt, String guestName,
                                   String guestPhone, String notes) {
        SalonProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        SalonOffering offering = offeringRepository.findById(companyId, serviceId)
            .orElseThrow(ServiceNotFoundException::new);
        if (!offering.active()) {
            throw new InactiveServiceException();
        }
        SalonConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, offering.durationMinutes(), config);

        SalonAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(),
                serviceId, offering.name(), offering.priceCents(), offering.durationMinutes(),
                conversationId, contactId, guestName, guestPhone, startAt, notes);
        } catch (SalonAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<SalonAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                       UUID professionalId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, contactId);
    }

    public Optional<SalonAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public SalonAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        SalonAppointmentStatus newStatus = SalonAppointmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        SalonAppointment current = appointmentRepository.findById(companyId, id)
            .orElseThrow(AppointmentNotFoundException::new);
        SalonAppointmentStatus from = SalonAppointmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(
            DATE_FMT.format(z), TIME_FMT.format(z), current.professionalName());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que o agendamento inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, SalonConfig config) {
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
