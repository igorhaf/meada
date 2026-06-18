package com.meada.whatsapp.profiles.nutri.professionals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.nutri.NutriContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos nutricionistas (camada 8.0). Audita + invalida {@link NutriContextCache}. */
@Service
public class NutriProfessionalService {

    private final NutriProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final NutriContextCache contextCache;

    public NutriProfessionalService(NutriProfessionalRepository repository, AuditLogger auditLogger,
                                    NutriContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public NutriProfessional create(UUID companyId, UUID userId, String name, String specialty, String crn, String notes) {
        NutriProfessional created = repository.insert(companyId, name, specialty, crn, notes);
        auditLogger.log(companyId, userId, "nutri_professional_created", "nutri_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public NutriProfessional update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                    String crn, String notes, Boolean active) {
        NutriProfessional updated = repository.update(companyId, id, name, specialty, crn, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_professional_updated", "nutri_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public NutriProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        NutriProfessional p = repository.toggle(companyId, id, active).orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "nutri_professional_updated", "nutri_professional", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new ProfessionalNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ProfessionalInUseException();
        }
        auditLogger.log(companyId, userId, "nutri_professional_deleted", "nutri_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<NutriProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<NutriProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
