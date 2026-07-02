package com.meada.profiles.atelie.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.atelie.AtelieContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo de materiais/técnicas do atelie (onda 2, backlog #15). CRUD + auditoria +
 * invalidação do contexto da IA (o upsell do backlog #10 injeta os NOMES ativos do catálogo).
 * Preço negativo/nome vazio → invalid_item. Delete é livre (o item de orçamento é snapshot texto).
 */
@Service
public class AtelieCatalogService {

    private final AtelieCatalogRepository repository;
    private final AuditLogger auditLogger;
    private final AtelieContextCache contextCache;

    public AtelieCatalogService(AtelieCatalogRepository repository, AuditLogger auditLogger,
                                AtelieContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class CatalogItemNotFoundException extends RuntimeException {}
    public static class InvalidCatalogItemException extends RuntimeException {}

    public List<AtelieCatalogItem> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<AtelieCatalogItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public AtelieCatalogItem create(UUID companyId, UUID userId, String name, String category,
                                    Integer unitPriceCents, Boolean active, String notes) {
        if (name == null || name.isBlank() || name.trim().length() > 200
            || unitPriceCents == null || unitPriceCents < 0) {
            throw new InvalidCatalogItemException();
        }
        AtelieCatalogItem created = repository.insert(companyId, name, category, unitPriceCents,
            active == null || active, notes);
        auditLogger.log(companyId, userId, "atelie_catalog_item_created", "atelie_catalog_item",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AtelieCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String category,
                                    boolean categoryProvided, Integer unitPriceCents, Boolean active,
                                    String notes, boolean notesProvided) {
        if ((name != null && (name.isBlank() || name.trim().length() > 200))
            || (unitPriceCents != null && unitPriceCents < 0)) {
            throw new InvalidCatalogItemException();
        }
        AtelieCatalogItem updated = repository.update(companyId, id, name, category, categoryProvided,
            unitPriceCents, active, notes, notesProvided).orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "atelie_catalog_item_updated", "atelie_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new CatalogItemNotFoundException();
        }
        auditLogger.log(companyId, userId, "atelie_catalog_item_deleted", "atelie_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
    }
}
