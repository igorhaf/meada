package com.meada.profiles.eventos.planners;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.eventos.EventosContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos cerimonialistas do tenant eventos (camada 8.2). Audita + invalida {@link EventosContextCache}. */
@Service
public class EventPlannerService {

    private final EventPlannerRepository repository;
    private final AuditLogger auditLogger;
    private final EventosContextCache contextCache;

    public EventPlannerService(EventPlannerRepository repository, AuditLogger auditLogger,
                               EventosContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PlannerNotFoundException extends RuntimeException {}
    public static class PlannerInUseException extends RuntimeException {}

    @Transactional
    public EventPlanner create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        EventPlanner created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "event_planner_created", "event_planner",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public EventPlanner update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                               String notes, Boolean active) {
        EventPlanner updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(PlannerNotFoundException::new);
        auditLogger.log(companyId, userId, "event_planner_updated", "event_planner", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public EventPlanner toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        EventPlanner p = repository.toggle(companyId, id, active).orElseThrow(PlannerNotFoundException::new);
        auditLogger.log(companyId, userId, "event_planner_updated", "event_planner", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // planner_id é ON DELETE SET NULL na proposta — checamos uso explicitamente (a FK não barra).
        if (repository.hasProposals(companyId, id)) {
            throw new PlannerInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new PlannerNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PlannerInUseException();
        }
        auditLogger.log(companyId, userId, "event_planner_deleted", "event_planner", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<EventPlanner> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<EventPlanner> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
