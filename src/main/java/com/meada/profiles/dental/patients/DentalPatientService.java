package com.meada.profiles.dental.patients;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.dental.DentalContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pacientes da clínica (camada 7.4). Audita as mutações (audit_log do tenant) e invalida
 * o {@link DentalContextCache} a cada gravação — para a IA ver a mudança (nome/vínculo) na hora.
 */
@Service
public class DentalPatientService {

    private final DentalPatientRepository repository;
    private final AuditLogger auditLogger;
    private final DentalContextCache contextCache;

    public DentalPatientService(DentalPatientRepository repository, AuditLogger auditLogger,
                                DentalContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Paciente não encontrado / de outro tenant (→ 404). */
    public static class PatientNotFoundException extends RuntimeException {}

    /** Paciente referenciado por consulta (FK restrict) — não pode hard-deletar (→ 409). */
    public static class PatientInUseException extends RuntimeException {}

    @Transactional
    public DentalPatient create(UUID companyId, UUID userId, String name, String email, String phone,
                                String document, LocalDate birthDate, UUID contactId, String notes) {
        DentalPatient created = repository.insert(
            companyId, name, email, phone, document, birthDate, contactId, notes);
        auditLogger.log(companyId, userId, "dental_patient_created", "dental_patient",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public DentalPatient update(UUID companyId, UUID userId, UUID id, String name, String email,
                                String phone, String document, LocalDate birthDate, UUID contactId,
                                String notes) {
        DentalPatient updated = repository.update(
                companyId, id, name, email, phone, document, birthDate, contactId, notes)
            .orElseThrow(PatientNotFoundException::new);
        auditLogger.log(companyId, userId, "dental_patient_updated", "dental_patient", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new PatientNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe dental_appointment apontando para este paciente.
            throw new PatientInUseException();
        }
        auditLogger.log(companyId, userId, "dental_patient_deleted", "dental_patient", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<DentalPatient> list(UUID companyId, String search) {
        return repository.listByCompany(companyId, search);
    }

    public Optional<DentalPatient> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
