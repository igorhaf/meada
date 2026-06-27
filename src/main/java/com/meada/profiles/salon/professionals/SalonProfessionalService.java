package com.meada.profiles.salon.professionals;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.salon.SalonContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos profissionais do salão (camada 7.5). Audita mutações e invalida o
 * {@link SalonContextCache} — a IA vê a mudança (quem está ativo) na hora.
 */
@Service
public class SalonProfessionalService {

    private final SalonProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final SalonContextCache contextCache;

    public SalonProfessionalService(SalonProfessionalRepository repository, AuditLogger auditLogger,
                                    SalonContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Profissional não encontrado / de outro tenant (→ 404). */
    public static class ProfessionalNotFoundException extends RuntimeException {}

    /** Profissional referenciado por agendamento (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public SalonProfessional create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        SalonProfessional created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "salon_professional_created", "salon_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SalonProfessional update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                    String notes, Boolean active) {
        SalonProfessional updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "salon_professional_updated", "salon_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SalonProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        SalonProfessional p = repository.toggle(companyId, id, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "salon_professional_updated", "salon_professional", id,
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
        auditLogger.log(companyId, userId, "salon_professional_deleted", "salon_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<SalonProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<SalonProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
