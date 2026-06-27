package com.meada.profiles.nutri.appointments;

import com.meada.profiles.nutri.NutriAppointmentStatus;
import com.meada.profiles.nutri.NutriContextCache;
import com.meada.profiles.nutri.config.NutriConfig;
import com.meada.profiles.nutri.config.NutriConfigRepository;
import com.meada.profiles.nutri.patients.NutriPatient;
import com.meada.profiles.nutri.patients.NutriPatientRepository;
import com.meada.profiles.nutri.professionals.NutriProfessional;
import com.meada.profiles.nutri.professionals.NutriProfessionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Regras das consultas de nutrição (camada 8.0).
 *
 * <p>{@link #create} valida professional (ativo), patient (ativo, do tenant), tipo de consulta e a
 * janela do config. Delega ao repo (conflito por profissional na transação). Snapshots de paciente
 * (do contact do paciente) + professional. Status inicial agendado.
 *
 * <p>{@link #updateStatus} valida a transição e notifica (confirmado/cancelado). Fuso HARDCODED
 * America/Sao_Paulo.
 */
@Service
public class NutriAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> VALID_TYPES = Set.of("primeira", "retorno", "avaliacao");
    private static final int DEFAULT_DURATION_MIN = 60;

    private final NutriAppointmentRepository appointmentRepository;
    private final NutriProfessionalRepository professionalRepository;
    private final NutriPatientRepository patientRepository;
    private final NutriConfigRepository configRepository;
    private final NutriAppointmentNotifier notifier;
    private final NutriContextCache contextCache;

    public NutriAppointmentService(NutriAppointmentRepository appointmentRepository,
                                   NutriProfessionalRepository professionalRepository,
                                   NutriPatientRepository patientRepository,
                                   NutriConfigRepository configRepository,
                                   NutriAppointmentNotifier notifier,
                                   NutriContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class AppointmentNotFoundException extends RuntimeException {}
    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class PatientNotFoundException extends RuntimeException {}
    public static class InactiveProfessionalException extends RuntimeException {}
    public static class InactivePatientException extends RuntimeException {}
    public static class InvalidTypeException extends RuntimeException {}
    public static class OutsideHoursException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller. */
    public static class ConflictException extends RuntimeException {
        private final transient NutriAppointmentConflict conflict;

        public ConflictException(NutriAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public NutriAppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma consulta (status agendado). Valida professional/patient (ativos, do tenant), tipo e
     * janela. O snapshot de paciente (name/phone do contact) vem do contact do paciente. O repo
     * re-verifica o conflito por profissional na transação. {@code durationMinutes} default 60.
     */
    public NutriAppointment create(UUID companyId, UUID professionalId, UUID patientId, UUID conversationId,
                                   String appointmentType, Instant startAt, Integer durationMinutes, String notes) {
        if (appointmentType == null || !VALID_TYPES.contains(appointmentType)) {
            throw new InvalidTypeException();
        }
        NutriProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        if (!prof.active()) {
            throw new InactiveProfessionalException();
        }
        NutriPatient patient = patientRepository.findById(companyId, patientId).orElseThrow(PatientNotFoundException::new);
        if (!patient.active()) {
            throw new InactivePatientException();
        }
        int duration = durationMinutes == null || durationMinutes <= 0 ? DEFAULT_DURATION_MIN : durationMinutes;
        NutriConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, duration, config);

        String patientName = patient.name();
        String patientPhone = patientRepository.contactPhone(companyId, patient.contactId()).orElse(null);

        NutriAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, professionalId, prof.name(), patientId,
                patientName, patientPhone, patient.contactId(), conversationId, appointmentType, duration, startAt, notes);
        } catch (NutriAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<NutriAppointment> list(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                       UUID professionalId, UUID patientId, UUID contactId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, patientId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                      UUID professionalId, UUID patientId, UUID contactId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId, patientId, contactId);
    }

    public Optional<NutriAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    @Transactional
    public NutriAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        NutriAppointmentStatus newStatus = NutriAppointmentStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);

        NutriAppointment current = appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
        NutriAppointmentStatus from = NutriAppointmentStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(typeLabel(current.appointmentType()), current.professionalName(),
            DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "primeira" -> "primeira consulta";
            case "retorno" -> "retorno";
            case "avaliacao" -> "avaliação";
            default -> "consulta";
        };
    }

    /** Valida que a consulta inteira (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, NutriConfig config) {
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
