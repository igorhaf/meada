package com.meada.whatsapp.profiles.fotografia.appointments;

import com.meada.whatsapp.profiles.fotografia.FotografiaAppointmentStatus;
import com.meada.whatsapp.profiles.fotografia.FotografiaContextCache;
import com.meada.whatsapp.profiles.fotografia.config.FotografiaConfig;
import com.meada.whatsapp.profiles.fotografia.config.FotografiaConfigRepository;
import com.meada.whatsapp.profiles.fotografia.packages.FotografiaPackage;
import com.meada.whatsapp.profiles.fotografia.packages.FotografiaPackageRepository;
import com.meada.whatsapp.profiles.fotografia.professionals.FotografiaProfessional;
import com.meada.whatsapp.profiles.fotografia.professionals.FotografiaProfessionalRepository;
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
 * Regras das sessões de fotografia (camada 8.16).
 *
 * <p>{@link #create} valida professional (ativo) e package (ativo, do tenant) e a janela do config. A
 * duração + preço + delivery_days vêm do pacote (SNAPSHOT). O CLIENTE é o contact (snapshots
 * customer_name/phone passados pelo chamador — espelho salon/estetica, SEM sub-entidade de paciente).
 * Delega ao repo (conflito por profissional na transação; end_at + delivery_due_date materializados).
 * Status inicial agendada.
 *
 * <p>{@link #updateStatus} valida a transição (incl. realizada→entregue) e notifica
 * (confirmada/cancelada). {@link #updateSession} deixa o estúdio gravar o delivery_link DEPOIS da
 * sessão. Fuso HARDCODED America/Sao_Paulo. Espelho do DermatologiaAppointmentService.
 */
@Service
public class FotografiaAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final FotografiaAppointmentRepository appointmentRepository;
    private final FotografiaProfessionalRepository professionalRepository;
    private final FotografiaPackageRepository packageRepository;
    private final FotografiaConfigRepository configRepository;
    private final FotografiaAppointmentNotifier notifier;
    private final FotografiaContextCache contextCache;

    public FotografiaAppointmentService(FotografiaAppointmentRepository appointmentRepository,
                                        FotografiaProfessionalRepository professionalRepository,
                                        FotografiaPackageRepository packageRepository,
                                        FotografiaConfigRepository configRepository,
                                        FotografiaAppointmentNotifier notifier,
                                        FotografiaContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.packageRepository = packageRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class SessionNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class PackageNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactivePackageException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller. */
    public static class ConflictException extends RuntimeException {
        private final transient FotografiaAppointmentConflict conflict;

        public ConflictException(FotografiaAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public FotografiaAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma sessão (status agendada). Valida professional/package (ativos, do tenant) e janela. A
     * duração/preço/delivery_days vêm do pacote (snapshot). customer_name/phone são snapshots do
     * contact (passados pelo chamador). O repo re-verifica o conflito por profissional na transação e
     * materializa end_at + delivery_due_date.
     */
    @Transactional
    public FotografiaSessionAppointment create(UUID companyId, UUID professionalId, UUID packageId, UUID contactId,
                                               UUID conversationId, Instant startAt, String customerName,
                                               String customerPhone, String notes) {
        FotografiaProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        FotografiaPackage pkg = packageRepository.findById(companyId, packageId).orElseThrow(PackageNotFoundException::new);
        if (!pkg.active()) {
            throw new InactivePackageException();
        }
        int duration = pkg.durationMinutes();
        FotografiaConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, duration, config);

        FotografiaSessionAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(), packageId,
                pkg.name(), pkg.priceCents(), duration, pkg.deliveryDays(), contactId, conversationId,
                customerName, customerPhone, startAt, notes);
        } catch (FotografiaAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<FotografiaSessionAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                                   UUID professionalId, UUID packageId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, packageId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID packageId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, packageId, contactId);
    }

    public Optional<FotografiaSessionAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public FotografiaSessionAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        FotografiaAppointmentStatus newStatus = FotografiaAppointmentStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);

        FotografiaSessionAppointment current = appointmentRepository.findById(companyId, id).orElseThrow(SessionNotFoundException::new);
        FotografiaAppointmentStatus from = FotografiaAppointmentStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(current.packageName(), current.professionalName(),
            DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(SessionNotFoundException::new);
    }

    /**
     * Grava o delivery_link e/ou notes DEPOIS da sessão (ação do estúdio). Só atualiza os campos
     * fornecidos. Invalida o cache de contexto (a IA passa a poder entregar o material).
     */
    @Transactional
    public FotografiaSessionAppointment updateSession(UUID companyId, UUID id, String deliveryLink,
                                                      boolean linkProvided, String notes) {
        int n = appointmentRepository.updateSession(companyId, id, deliveryLink, linkProvided, notes);
        if (n == 0) {
            throw new SessionNotFoundException();
        }
        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(SessionNotFoundException::new);
    }

    /** Valida que a sessão inteira (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, FotografiaConfig config) {
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
