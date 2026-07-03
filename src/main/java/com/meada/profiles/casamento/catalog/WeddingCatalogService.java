package com.meada.profiles.casamento.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.casamento.CasamentoContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras do catálogo de pacotes/adicionais do casamento (onda 1, backlog #3). CRUD + auditoria +
 * invalidação do contexto da IA (que apresenta os itens COM o preço do catálogo e faz o upsell
 * controlado). kind pacote|adicional; nome vazio/preço negativo/kind inválido → invalid_item.
 */
@Service
public class WeddingCatalogService {

    private final WeddingCatalogRepository repository;
    private final AuditLogger auditLogger;
    private final CasamentoContextCache contextCache;

    public WeddingCatalogService(WeddingCatalogRepository repository, AuditLogger auditLogger,
                                 CasamentoContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class CatalogItemNotFoundException extends RuntimeException {}
    public static class InvalidCatalogItemException extends RuntimeException {}

    private static void requireValidKind(String kind) {
        if (!"pacote".equals(kind) && !"adicional".equals(kind)) {
            throw new InvalidCatalogItemException();
        }
    }

    public List<WeddingCatalogItem> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    @Transactional
    public WeddingCatalogItem create(UUID companyId, UUID userId, String name, String kind,
                                     String description, Integer priceCents, Boolean active) {
        if (name == null || name.isBlank() || name.trim().length() > 200
            || priceCents == null || priceCents < 0) {
            throw new InvalidCatalogItemException();
        }
        String effKind = kind == null || kind.isBlank() ? "adicional" : kind;
        requireValidKind(effKind);
        WeddingCatalogItem created = repository.insert(companyId, name, effKind, description, priceCents,
            active == null || active);
        auditLogger.log(companyId, userId, "wedding_catalog_item_created", "wedding_catalog_item",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public WeddingCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String kind,
                                     String description, boolean descProvided, Integer priceCents,
                                     Boolean active) {
        if ((name != null && (name.isBlank() || name.trim().length() > 200))
            || (priceCents != null && priceCents < 0)) {
            throw new InvalidCatalogItemException();
        }
        if (kind != null && !kind.isBlank()) {
            requireValidKind(kind);
        }
        WeddingCatalogItem updated = repository.update(companyId, id, name, kind, description, descProvided,
            priceCents, active).orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "wedding_catalog_item_updated", "wedding_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new CatalogItemNotFoundException();
        }
        auditLogger.log(companyId, userId, "wedding_catalog_item_deleted", "wedding_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
    }
}
