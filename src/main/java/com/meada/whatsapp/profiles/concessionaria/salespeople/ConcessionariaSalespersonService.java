package com.meada.whatsapp.profiles.concessionaria.salespeople;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.concessionaria.ConcessionariaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos vendedores da concessionaria (camada 8.17). Audita + invalida o
 * {@link ConcessionariaContextCache}. delete-in-use: vendedor com test-drive OU lead → 409
 * salesperson_in_use (checagem explícita — lead.salesperson_id é SET NULL, não barra na FK).
 */
@Service
public class ConcessionariaSalespersonService {

    private final ConcessionariaSalespersonRepository repository;
    private final AuditLogger auditLogger;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaSalespersonService(ConcessionariaSalespersonRepository repository,
                                            AuditLogger auditLogger,
                                            ConcessionariaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class SalespersonNotFoundException extends RuntimeException {}
    public static class SalespersonInUseException extends RuntimeException {}

    @Transactional
    public ConcessionariaSalesperson create(UUID companyId, UUID userId, String name, String phone, String notes) {
        ConcessionariaSalesperson created = repository.insert(companyId, name, phone, notes);
        auditLogger.log(companyId, userId, "concessionaria_salesperson_created", "concessionaria_salesperson",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ConcessionariaSalesperson update(UUID companyId, UUID userId, UUID id, String name, String phone,
                                            String notes, Boolean active) {
        ConcessionariaSalesperson updated = repository.update(companyId, id, name, phone, notes, active)
            .orElseThrow(SalespersonNotFoundException::new);
        auditLogger.log(companyId, userId, "concessionaria_salesperson_updated", "concessionaria_salesperson",
            id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public ConcessionariaSalesperson toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        ConcessionariaSalesperson p = repository.toggle(companyId, id, active)
            .orElseThrow(SalespersonNotFoundException::new);
        auditLogger.log(companyId, userId, "concessionaria_salesperson_updated", "concessionaria_salesperson",
            id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (repository.hasReferences(companyId, id)) {
            throw new SalespersonInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new SalespersonNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new SalespersonInUseException();
        }
        auditLogger.log(companyId, userId, "concessionaria_salesperson_deleted", "concessionaria_salesperson",
            id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<ConcessionariaSalesperson> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<ConcessionariaSalesperson> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
