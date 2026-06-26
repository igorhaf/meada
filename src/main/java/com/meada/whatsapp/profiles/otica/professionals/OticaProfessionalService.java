package com.meada.whatsapp.profiles.otica.professionals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.otica.OticaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos optometristas do otica (camada 8.12, FLUXO A). Audita mutações e invalida o
 * {@link OticaContextCache} — a IA vê a mudança (quem está ativo) na hora. Clone de
 * {@link com.meada.whatsapp.profiles.salon.professionals.SalonProfessionalService}.
 */
@Service
public class OticaProfessionalService {

    private final OticaProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final OticaContextCache contextCache;

    public OticaProfessionalService(OticaProfessionalRepository repository, AuditLogger auditLogger,
                                    OticaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Profissional não encontrado / de outro tenant (→ 404). */
    public static class ProfessionalNotFoundException extends RuntimeException {}

    /** Profissional referenciado por exame (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public OticaProfessional create(UUID companyId, UUID userId, String name, String notes) {
        OticaProfessional created = repository.insert(companyId, name, notes);
        auditLogger.log(companyId, userId, "otica_professional_created", "otica_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OticaProfessional update(UUID companyId, UUID userId, UUID id, String name, String notes,
                                    Boolean active) {
        OticaProfessional updated = repository.update(companyId, id, name, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_professional_updated", "otica_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public OticaProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        OticaProfessional p = repository.toggle(companyId, id, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_professional_updated", "otica_professional", id,
            Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new ProfessionalNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ProfessionalInUseException();
        }
        auditLogger.log(companyId, userId, "otica_professional_deleted", "otica_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<OticaProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<OticaProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
