package com.meada.profiles.otica.appointments;

import com.meada.profiles.otica.OticaContextCache;
import com.meada.profiles.otica.OticaExamStatus;
import com.meada.profiles.otica.config.OticaConfig;
import com.meada.profiles.otica.config.OticaConfigRepository;
import com.meada.profiles.otica.professionals.OticaProfessional;
import com.meada.profiles.otica.professionals.OticaProfessionalRepository;
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
 * Regras dos exames de vista (camada 8.12, perfil otica FLUXO A).
 *
 * <p>{@link #create} valida o profissional (existe, do tenant) e que o exame CABE na janela de
 * funcionamento (no fuso do tenant), lê a duração do config (snapshot), resolve os snapshots de nome
 * (profissional + cliente) e delega ao repositório — que re-verifica o conflito POR PROFISSIONAL
 * DENTRO da transação. O CLIENTE é o contact (snapshot customer_name passado pelo chamador —
 * espelho fotografia/salon, SEM sub-entidade de paciente).
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e dispara a notificação outbound
 * (confirmado/cancelado) via {@link OticaExamNotifier}, best-effort. Espelho do
 * DentalAppointmentService + conflito por profissional (fotografia).
 *
 * <p>Fuso HARDCODED America/Sao_Paulo (pendência conhecida, igual ao resto dos perfis).
 */
@Service
public class OticaExamService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final OticaExamRepository examRepository;
    private final OticaProfessionalRepository professionalRepository;
    private final OticaConfigRepository configRepository;
    private final OticaExamNotifier notifier;
    private final OticaContextCache contextCache;

    public OticaExamService(OticaExamRepository examRepository,
                            OticaProfessionalRepository professionalRepository,
                            OticaConfigRepository configRepository,
                            OticaExamNotifier notifier,
                            OticaContextCache contextCache) {
        this.examRepository = examRepository;
        this.professionalRepository = professionalRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    /** Exame não encontrado / de outro tenant (→ 404). */
    public static class ExamNotFoundException extends RuntimeException {}

    /** Profissional inexistente / de outro tenant (→ 404 professional_not_found). */
    public static class ProfessionalNotFoundException extends RuntimeException {}

    /** Exame fora do horário de funcionamento (→ 400 outside_hours). */
    public static class OutsideHoursException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Conflito de slot (→ 409 conflict_slot). Carrega o conflito p/ o controller expor detalhes. */
    public static class ConflictException extends RuntimeException {
        private final transient OticaExamConflict conflict;

        public ConflictException(OticaExamConflict conflict) {
            this.conflict = conflict;
        }

        public OticaExamConflict conflict() {
            return conflict;
        }
    }

    /**
     * Cria um exame (status inicial agendado). Valida profissional + janela de funcionamento; a
     * duração vem do config (snapshot). O repositório re-verifica o conflito por profissional na
     * transação. Invalida o cache de contexto da IA.
     */
    @Transactional
    public OticaExamAppointment create(UUID companyId, UUID professionalId, UUID conversationId,
                                       UUID contactId, String customerName, Instant startAt, String notes) {
        OticaProfessional prof = professionalRepository.findById(companyId, professionalId)
            .orElseThrow(ProfessionalNotFoundException::new);
        OticaConfig config = configRepository.findByCompany(companyId);
        requireInsideHours(startAt, config.examDurationMinutes(), config);

        OticaExamAppointment created;
        try {
            created = examRepository.insertAppointment(companyId, professionalId, prof.name(),
                conversationId, contactId, customerName, startAt, config.examDurationMinutes(), notes);
        } catch (OticaExamRepository.SlotConflictException e) {
            throw new ConflictException(e.conflict());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    public List<OticaExamAppointment> list(UUID companyId, String status, Instant dateFrom,
                                           Instant dateTo, UUID professionalId, int limit, int offset) {
        return examRepository.listByCompany(companyId, status, dateFrom, dateTo, professionalId, limit, offset);
    }

    public long count(UUID companyId, String status, Instant dateFrom, Instant dateTo, UUID professionalId) {
        return examRepository.countByCompany(companyId, status, dateFrom, dateTo, professionalId);
    }

    public Optional<OticaExamAppointment> get(UUID companyId, UUID id) {
        return examRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status. Valida o alvo (enum) e a transição. Persiste e notifica o cliente com o
     * texto do novo status (confirmado/cancelado). A notificação é best-effort (não reverte).
     */
    @Transactional
    public OticaExamAppointment updateStatus(UUID companyId, UUID id, String newStatusId) {
        OticaExamStatus newStatus = OticaExamStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        OticaExamAppointment current = examRepository.findById(companyId, id)
            .orElseThrow(ExamNotFoundException::new);
        OticaExamStatus from = OticaExamStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        examRepository.updateStatus(companyId, id, newStatus.id());

        ZonedDateTime z = current.startAt().atZone(TENANT_ZONE);
        String text = newStatus.notificationText(DATE_FMT.format(z), TIME_FMT.format(z));
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return examRepository.findById(companyId, id).orElseThrow(ExamNotFoundException::new);
    }

    /** Valida que o exame inteiro (início → início+duração) cabe na janela, no fuso do tenant. */
    private void requireInsideHours(Instant startAt, int durationMinutes, OticaConfig config) {
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
