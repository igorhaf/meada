package com.meada.whatsapp.profiles.fotografia.professionals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.fotografia.FotografiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos fotógrafos (camada 8.16). Audita + invalida {@link FotografiaContextCache}. Clone do DermatologiaProfessionalService. */
@Service
public class FotografiaProfessionalService {

    private final FotografiaProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final FotografiaContextCache contextCache;

    public FotografiaProfessionalService(FotografiaProfessionalRepository repository, AuditLogger auditLogger,
                                         FotografiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public FotografiaProfessional create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        FotografiaProfessional created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "fotografia_professional_created", "fotografia_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public FotografiaProfessional update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                         String notes, Boolean active) {
        FotografiaProfessional updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "fotografia_professional_updated", "fotografia_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public FotografiaProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        FotografiaProfessional p = repository.toggle(companyId, id, active).orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "fotografia_professional_updated", "fotografia_professional", id, Map.of("active", active));
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
        auditLogger.log(companyId, userId, "fotografia_professional_deleted", "fotografia_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<FotografiaProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<FotografiaProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
