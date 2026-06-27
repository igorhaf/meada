package com.meada.profiles.barbearia.services;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.barbearia.BarberContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos serviços da barbearia (camada 8.1). Audita mutações e invalida o
 * {@link BarberContextCache}. Espelho de SalonOfferingService.
 */
@Service
public class BarberServiceService {

    private final BarberServiceRepository repository;
    private final AuditLogger auditLogger;
    private final BarberContextCache contextCache;

    public BarberServiceService(BarberServiceRepository repository, AuditLogger auditLogger,
                                BarberContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Serviço não encontrado / de outro tenant (→ 404). */
    public static class ServiceNotFoundException extends RuntimeException {}

    /** Serviço referenciado por agendamento/ticket (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ServiceInUseException extends RuntimeException {}

    @Transactional
    public BarberService create(UUID companyId, UUID userId, String name, String category,
                                int durationMinutes, Integer priceCents, String description) {
        BarberService created = repository.insert(companyId, name, category, durationMinutes, priceCents, description);
        auditLogger.log(companyId, userId, "barber_service_created", "barber_service",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public BarberService update(UUID companyId, UUID userId, UUID id, String name, String category,
                                Integer durationMinutes, Integer priceCents, String description, Boolean active) {
        BarberService updated = repository.update(companyId, id, name, category, durationMinutes, priceCents, description, active)
            .orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "barber_service_updated", "barber_service", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public BarberService toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        BarberService o = repository.toggle(companyId, id, active)
            .orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "barber_service_updated", "barber_service", id,
            Map.of("active", active));
        contextCache.invalidate(companyId);
        return o;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new ServiceNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ServiceInUseException();
        }
        auditLogger.log(companyId, userId, "barber_service_deleted", "barber_service", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<BarberService> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<BarberService> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
