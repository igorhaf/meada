package com.meada.profiles.nutri.patients;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.nutri.NutriContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pacientes (camada 8.0). Sub-entidade do contact (nível 1). Valida o cliente ao criar,
 * audita e invalida o {@link NutriContextCache}. DELETE protegido por FK (consulta E plano via
 * restrict) → 409 patient_in_use; o caminho preferido pra "remover" é {@link #archive}
 * (active=false).
 */
@Service
public class NutriPatientService {

    private final NutriPatientRepository repository;
    private final AuditLogger auditLogger;
    private final NutriContextCache contextCache;

    public NutriPatientService(NutriPatientRepository repository, AuditLogger auditLogger, NutriContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PatientNotFoundException extends RuntimeException {}
    public static class ContactNotFoundException extends RuntimeException {}
    public static class PatientInUseException extends RuntimeException {}

    @Transactional
    public NutriPatient create(UUID companyId, UUID userId, UUID contactId, String name, String goal,
                               String dietaryRestrictions, LocalDate birthDate, String notes) {
        if (!repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        NutriPatient created = repository.insert(companyId, contactId, name, goal, dietaryRestrictions, birthDate, notes);
        auditLogger.log(companyId, userId, "nutri_patient_created", "nutri_patient",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public NutriPatient update(UUID companyId, UUID userId, UUID id, String name, String goal,
                               String dietaryRestrictions, LocalDate birthDate, boolean birthProvided,
                               String notes, Boolean active) {
        NutriPatient updated = repository.update(companyId, id, name, goal, dietaryRestrictions, birthDate,
                birthProvided, notes, active)
            .orElseThrow(PatientNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_patient_updated", "nutri_patient", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public NutriPatient archive(UUID companyId, UUID userId, UUID id) {
        NutriPatient p = repository.archive(companyId, id).orElseThrow(PatientNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_patient_archived", "nutri_patient", id, Map.of());
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new PatientNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PatientInUseException();
        }
        auditLogger.log(companyId, userId, "nutri_patient_deleted", "nutri_patient", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<NutriPatient> list(UUID companyId, UUID contactId, Boolean active, String search) {
        return repository.listByCompany(companyId, contactId, active, search);
    }

    public Optional<NutriPatient> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
