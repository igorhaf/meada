package com.meada.profiles.salon.offerings;

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
 * Regras dos serviços do salão (camada 7.5). Audita mutações e invalida o {@link SalonContextCache}.
 */
@Service
public class SalonOfferingService {

    private final SalonOfferingRepository repository;
    private final AuditLogger auditLogger;
    private final SalonContextCache contextCache;

    public SalonOfferingService(SalonOfferingRepository repository, AuditLogger auditLogger,
                                SalonContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Serviço não encontrado / de outro tenant (→ 404). */
    public static class OfferingNotFoundException extends RuntimeException {}

    /** Serviço referenciado por agendamento (FK restrict) — não pode hard-deletar (→ 409). */
    public static class OfferingInUseException extends RuntimeException {}

    @Transactional
    public SalonOffering create(UUID companyId, UUID userId, String name, String category,
                                int durationMinutes, Integer priceCents, String description) {
        SalonOffering created = repository.insert(companyId, name, category, durationMinutes, priceCents, description);
        auditLogger.log(companyId, userId, "salon_offering_created", "salon_offering",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SalonOffering update(UUID companyId, UUID userId, UUID id, String name, String category,
                                Integer durationMinutes, Integer priceCents, String description, Boolean active) {
        SalonOffering updated = repository.update(companyId, id, name, category, durationMinutes, priceCents, description, active)
            .orElseThrow(OfferingNotFoundException::new);
        auditLogger.log(companyId, userId, "salon_offering_updated", "salon_offering", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SalonOffering toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        SalonOffering o = repository.toggle(companyId, id, active)
            .orElseThrow(OfferingNotFoundException::new);
        auditLogger.log(companyId, userId, "salon_offering_updated", "salon_offering", id,
            Map.of("active", active));
        contextCache.invalidate(companyId);
        return o;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new OfferingNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new OfferingInUseException();
        }
        auditLogger.log(companyId, userId, "salon_offering_deleted", "salon_offering", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<SalonOffering> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<SalonOffering> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
