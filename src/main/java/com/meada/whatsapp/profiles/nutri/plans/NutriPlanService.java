package com.meada.whatsapp.profiles.nutri.plans;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.nutri.NutriContextCache;
import com.meada.whatsapp.profiles.nutri.patients.NutriPatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos planos alimentares (camada 8.0). SUB-ENTIDADE do paciente (nível 2). O body é escrito SÓ
 * por aqui (painel) — a IA não tem caminho de escrita; só LÊ via {@link #getActiveByPatient} na
 * entrega. Garante NO MÁXIMO 1 plano 'ativo' por paciente: ao criar um plano ativo (ou reativar),
 * arquiva o ativo anterior NA MESMA transação (antes de inserir, pra liberar o índice parcial).
 */
@Service
public class NutriPlanService {

    private final NutriPlanRepository repository;
    private final NutriPatientRepository patientRepository;
    private final AuditLogger auditLogger;
    private final NutriContextCache contextCache;

    public NutriPlanService(NutriPlanRepository repository, NutriPatientRepository patientRepository,
                            AuditLogger auditLogger, NutriContextCache contextCache) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PlanNotFoundException extends RuntimeException {}
    public static class PatientNotFoundException extends RuntimeException {}

    /**
     * Cria um plano. Se {@code active} (default), arquiva o plano ativo anterior do paciente ANTES de
     * inserir (garante 1 ativo via índice parcial). O body é o texto do profissional, gravado como veio.
     */
    @Transactional
    public NutriPlan create(UUID companyId, UUID userId, UUID patientId, UUID professionalId, String title,
                            String body, LocalDate startsOn, LocalDate endsOn, boolean active, String notes) {
        if (patientRepository.findById(companyId, patientId).isEmpty()) {
            throw new PatientNotFoundException();
        }
        String status = active ? "ativo" : "arquivado";
        if (active) {
            repository.archiveActive(companyId, patientId);
        }
        NutriPlan created = repository.insert(companyId, patientId, professionalId, title, body, startsOn, endsOn, status, notes);
        auditLogger.log(companyId, userId, "nutri_plan_created", "nutri_plan", created.id(),
            Map.of("patient_id", patientId.toString(), "status", status));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public NutriPlan update(UUID companyId, UUID userId, UUID id, String title, String body, UUID professionalId,
                            boolean professionalProvided, LocalDate startsOn, boolean startsProvided,
                            LocalDate endsOn, boolean endsProvided, String notes) {
        NutriPlan updated = repository.update(companyId, id, title, body, professionalId, professionalProvided,
                startsOn, startsProvided, endsOn, endsProvided, notes)
            .orElseThrow(PlanNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_plan_updated", "nutri_plan", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    /** Arquiva um plano (status arquivado). */
    @Transactional
    public NutriPlan archive(UUID companyId, UUID userId, UUID id) {
        NutriPlan p = repository.archive(companyId, id).orElseThrow(PlanNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_plan_archived", "nutri_plan", id, Map.of());
        contextCache.invalidate(companyId);
        return p;
    }

    /** Reativa um plano arquivado: arquiva o ativo atual do paciente antes (garante 1 ativo). */
    @Transactional
    public NutriPlan activate(UUID companyId, UUID userId, UUID id) {
        NutriPlan plan = repository.findById(companyId, id).orElseThrow(PlanNotFoundException::new);
        repository.archiveActive(companyId, plan.patientId());
        NutriPlan p = repository.setActive(companyId, id).orElseThrow(PlanNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_plan_activated", "nutri_plan", id, Map.of());
        contextCache.invalidate(companyId);
        return p;
    }

    public List<NutriPlan> listByPatient(UUID companyId, UUID patientId, String status) {
        return repository.listByPatient(companyId, patientId, status);
    }

    public Optional<NutriPlan> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    public Optional<NutriPlan> getActiveByPatient(UUID companyId, UUID patientId) {
        return repository.findActiveByPatient(companyId, patientId);
    }
}
