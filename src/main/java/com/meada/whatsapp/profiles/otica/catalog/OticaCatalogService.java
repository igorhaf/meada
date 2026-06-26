package com.meada.whatsapp.profiles.otica.catalog;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.otica.OticaCategory;
import com.meada.whatsapp.profiles.otica.OticaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo do otica (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogService} + os campos
 * {@code madeToOrder}/{@code leadTimeDays} (a ESCAPADA). Valida categoria contra
 * {@link OticaCategory}, audita as mutações e invalida o {@link OticaContextCache} a cada gravação —
 * para a IA ver a mudança na hora.
 */
@Service
public class OticaCatalogService {

    private final OticaCatalogItemRepository repository;
    private final OticaCatalogOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final OticaContextCache contextCache;

    public OticaCatalogService(OticaCatalogItemRepository repository,
                               OticaCatalogOptionRepository optionRepository,
                               AuditLogger auditLogger,
                               OticaContextCache contextCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class CatalogItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class CatalogItemInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro item-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Itens --------------------------------------------------------------

    @Transactional
    public OticaCatalogItem create(UUID companyId, UUID userId, String name, String description,
                                   int priceCents, String category, boolean madeToOrder, Integer leadTimeDays) {
        requireValidCategory(category);
        OticaCatalogItem created = repository.insert(companyId, name, description, priceCents, category,
            madeToOrder, leadTimeDays);
        auditLogger.log(companyId, userId, "otica_catalog_item_created", "otica_catalog_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OticaCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                   Integer priceCents, String category, Boolean madeToOrder,
                                   Integer leadTimeDays, boolean leadTimeDaysProvided, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        OticaCatalogItem updated = repository.update(companyId, id, name, description, priceCents, category,
                madeToOrder, leadTimeDays, leadTimeDaysProvided, available)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_catalog_item_updated", "otica_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public OticaCatalogItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        OticaCatalogItem item = repository.toggle(companyId, id, available)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_catalog_item_updated", "otica_catalog_item", id,
            Map.of("available", available));
        contextCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new CatalogItemNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe otica_order_item apontando para este item.
            throw new CatalogItemInUseException();
        }
        auditLogger.log(companyId, userId, "otica_catalog_item_deleted", "otica_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<OticaCatalogItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<OticaCatalogItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções (modifiers: tipo de lente / tratamento) ---------------------

    public List<OticaCatalogOption> listOptions(UUID companyId, UUID catalogItemId) {
        requireItem(companyId, catalogItemId);
        return optionRepository.listByItem(companyId, catalogItemId);
    }

    @Transactional
    public OticaCatalogOption addOption(UUID companyId, UUID userId, UUID catalogItemId, String groupLabel,
                                        String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, catalogItemId);
        OticaCatalogOption created = optionRepository.insert(
            companyId, catalogItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "otica_catalog_option_created", "otica_catalog_item_option",
            created.id(), Map.of("catalog_item_id", catalogItemId, "group_label", created.groupLabel()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OticaCatalogOption updateOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                           String groupLabel, String optionLabel, Integer priceDeltaCents,
                                           Integer sortOrder, Boolean available) {
        requireItem(companyId, catalogItemId);
        OticaCatalogOption updated = optionRepository.update(companyId, catalogItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_catalog_option_updated", "otica_catalog_item_option",
            optionId, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public OticaCatalogOption toggleOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                           boolean available) {
        requireItem(companyId, catalogItemId);
        OticaCatalogOption option = optionRepository.toggle(companyId, catalogItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "otica_catalog_option_updated", "otica_catalog_item_option",
            optionId, Map.of("available", available));
        contextCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId) {
        requireItem(companyId, catalogItemId);
        boolean deleted = optionRepository.delete(companyId, catalogItemId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "otica_catalog_option_deleted", "otica_catalog_item_option",
            optionId, Map.of());
        contextCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID catalogItemId) {
        if (repository.findById(companyId, catalogItemId).isEmpty()) {
            throw new CatalogItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (OticaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
