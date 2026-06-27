package com.meada.profiles.oficina.mechanics;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.oficina.OficinaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos mecânicos da oficina (camada 7.9). Audita + invalida {@link OficinaContextCache}. */
@Service
public class OsMechanicService {

    private final OsMechanicRepository repository;
    private final AuditLogger auditLogger;
    private final OficinaContextCache contextCache;

    public OsMechanicService(OsMechanicRepository repository, AuditLogger auditLogger,
                             OficinaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class MechanicNotFoundException extends RuntimeException {}
    public static class MechanicInUseException extends RuntimeException {}

    @Transactional
    public OsMechanic create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        OsMechanic created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "os_mechanic_created", "os_mechanic",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OsMechanic update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                             String notes, Boolean active) {
        OsMechanic updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(MechanicNotFoundException::new);
        auditLogger.log(companyId, userId, "os_mechanic_updated", "os_mechanic", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public OsMechanic toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        OsMechanic m = repository.toggle(companyId, id, active).orElseThrow(MechanicNotFoundException::new);
        auditLogger.log(companyId, userId, "os_mechanic_updated", "os_mechanic", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return m;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // mechanic_id é ON DELETE SET NULL na OS — checamos uso explicitamente (a FK não barra).
        if (repository.hasOrders(companyId, id)) {
            throw new MechanicInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new MechanicNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new MechanicInUseException();
        }
        auditLogger.log(companyId, userId, "os_mechanic_deleted", "os_mechanic", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<OsMechanic> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<OsMechanic> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
