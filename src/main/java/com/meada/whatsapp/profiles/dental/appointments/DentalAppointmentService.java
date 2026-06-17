package com.meada.whatsapp.profiles.dental.appointments;

import com.meada.whatsapp.profiles.dental.AppointmentStatus;
import com.meada.whatsapp.profiles.dental.DentalContextCache;
import com.meada.whatsapp.profiles.dental.config.DentalClinicConfig;
import com.meada.whatsapp.profiles.dental.config.DentalClinicConfigRepository;
import com.meada.whatsapp.profiles.dental.patients.DentalPatientRepository;
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
 * Regras das consultas (camada 7.4).
 *
 * <p>{@link #create} valida que o paciente existe e que a consulta CABE na janela de funcionamento
 * do consultório (no fuso do tenant), lê a duração do config (snapshot), e delega ao repositório —
 * que re-verifica o conflito DENTRO da transação (decisão 4).
 *
 * <p>{@link #updateStatus} valida a transição (decisão 1 → 409 se inválida) e dispara a notificação
 * outbound do novo status (decisão 2) via {@link DentalAppointmentNotifier}, best-effort.
 *
 * <p>Fuso HARDCODED America/Sao_Paulo (pendência conhecida, igual ao resto dos perfis).
 */
@Service
public class DentalAppointmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DentalAppointmentRepository appointmentRepository;
    private final DentalPatientRepository patientRepository;
    private final DentalClinicConfigRepository configRepository;
    private final DentalAppointmentNotifier notifier;
    private final DentalContextCache contextCache;

    public DentalAppointmentService(DentalAppointmentRepository appointmentRepository,
                                    DentalPatientRepository patientRepository,
                                    DentalClinicConfigRepository configRepository,
                                    DentalAppointmentNotifier notifier,
                                    DentalContextCache contextCache) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    /** Consulta não encontrada / de outro tenant (→ 404). */
    public static class AppointmentNotFoundException extends RuntimeException {}

    /** Paciente inexistente / de outro tenant (→ 404 patient_not_found). */
    public static class PatientNotFoundException extends RuntimeException {}

    /** Consulta fora do horário de funcionamento (→ 400 outside_hours). */
    public static class OutsideHoursException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient AppointmentConflict conflict;

        public ConflictException(AppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public AppointmentConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria uma consulta (status inicial agendada). Valida paciente + janela de funcionamento; a
     * duração vem do config (snapshot). O repositório re-verifica o conflito na transação. Invalida
     * o cache de contexto da IA.
     */
    public DentalAppointment create(UUID companyId, UUID patientId, UUID conversationId,
                                    Instant startAt, String type, String notes) {
        if (patientRepository.findById(companyId, patientId).isEmpty()) {
            throw new PatientNotFoundException();
        }
        DentalClinicConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, config);

        DentalAppointment created;
        try {
            created = appointmentRepository.insertAppointment(companyId, patientId, conversationId,
                startAt, config.durationMinutes(), type, notes);
        } catch (DentalAppointmentRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<DentalAppointment> list(UUID companyId, String status, Instant dateFrom,
                                        Instant dateTo, UUID patientId, int limit, int offset) {
        return appointmentRepository.listByCompany(companyId, status, dateFrom, dateTo, patientId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo, UUID patientId) {
        return appointmentRepository.countByCompany(companyId, status, dateFrom, dateTo, patientId);
    }

    public Optional<DentalAppointment> get(UUID companyId, UUID id) {
        return appointmentRepository.findById(companyId, id);
    }

    public List<DentalAppointment> listByPatient(UUID companyId, UUID patientId, boolean futureOnly) {
        return appointmentRepository.listByPatient(companyId, patientId, futureOnly);
    }

    /**
     * Transiciona o status. Valida o alvo (enum) e a transição (decisão 1). Persiste e notifica o
     * paciente com o texto do novo status (decisão 2). A notificação é best-effort (não reverte).
     */
    @Transactional
    public DentalAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        AppointmentStatus newStatus = AppointmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        DentalAppointment current = appointmentRepository.findById(companyId, id)
            .orElseThrow(AppointmentNotFoundException::new);
        AppointmentStatus from = AppointmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        appointmentRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return appointmentRepository.findById(companyId, id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** Valida que a consulta inteira (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, DentalClinicConfig config) {
        ZonedDateTime start = startAt.atZone(TENANT_ZONE);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = start.plusMinutes(config.durationMinutes()).toLocalTime();
        boolean startsOk = !startTime.isBefore(config.opensAt());
        boolean endsOk = !endTime.isAfter(config.closesAt()) && !endTime.isBefore(startTime);
        if (!startsOk || !endsOk) {
            throw new OutsideHoursException();
        }
    }
}
