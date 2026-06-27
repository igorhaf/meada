package com.meada.profiles.papelaria.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.papelaria.PapelariaCatalogCache;
import com.meada.profiles.papelaria.PapelariaCategory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo da papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@code com.meada.profiles.padaria.menu.PadariaMenuService} (camada 8.8) — menu→catalog +
 * specs (no lugar de allergens). Valida categoria contra {@link PapelariaCategory}, audita as mutações
 * (audit_log do tenant) e invalida o {@link PapelariaCatalogCache} a cada gravação — para a IA ver a
 * mudança na hora.
 */
@Service
public class PapelariaCatalogService {

    private final PapelariaCatalogItemRepository repository;
    private final PapelariaCatalogOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final PapelariaCatalogCache catalogCache;

    public PapelariaCatalogService(PapelariaCatalogItemRepository repository,
                                   PapelariaCatalogOptionRepository optionRepository,
                                   AuditLogger auditLogger,
                                   PapelariaCatalogCache catalogCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class CatalogItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409 catalog_item_in_use). */
    public static class CatalogItemInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro item-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Itens --------------------------------------------------------------

    @Transactional
    public PapelariaCatalogItem create(UUID companyId, UUID userId, String name, String description,
                                       int priceCents, String category, boolean madeToOrder,
                                       Integer leadTimeDays, String specs) {
        requireValidCategory(category);
        PapelariaCatalogItem created = repository.insert(companyId, name, description, priceCents, category,
            madeToOrder, leadTimeDays, specs);
        auditLogger.log(companyId, userId, "papelaria_catalog_item_created", "papelaria_catalog_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PapelariaCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                       Integer priceCents, String category, Boolean madeToOrder,
                                       Integer leadTimeDays, boolean clearLeadTime, String specs,
                                       Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        PapelariaCatalogItem updated = repository.update(companyId, id, name, description, priceCents,
                category, madeToOrder, leadTimeDays, clearLeadTime, specs, available)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "papelaria_catalog_item_updated", "papelaria_catalog_item", id, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PapelariaCatalogItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        PapelariaCatalogItem item = repository.toggle(companyId, id, available)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "papelaria_catalog_item_updated", "papelaria_catalog_item", id,
            Map.of("available", available));
        catalogCache.invalidate(companyId);
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
            // FK restrict: existe papelaria_order_item apontando para este item.
            throw new CatalogItemInUseException();
        }
        auditLogger.log(companyId, userId, "papelaria_catalog_item_deleted", "papelaria_catalog_item", id, Map.of());
        catalogCache.invalidate(companyId);
    }

    public List<PapelariaCatalogItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<PapelariaCatalogItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções -------------------------------------------------------------

    public List<PapelariaCatalogOption> listOptions(UUID companyId, UUID catalogItemId) {
        requireItem(companyId, catalogItemId);
        return optionRepository.listByItem(companyId, catalogItemId);
    }

    @Transactional
    public PapelariaCatalogOption addOption(UUID companyId, UUID userId, UUID catalogItemId, String groupLabel,
                                            String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, catalogItemId);
        PapelariaCatalogOption created = optionRepository.insert(
            companyId, catalogItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "papelaria_catalog_option_created", "papelaria_catalog_item_option",
            created.id(), Map.of("catalog_item_id", catalogItemId, "group_label", created.groupLabel()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PapelariaCatalogOption updateOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                               String groupLabel, String optionLabel, Integer priceDeltaCents,
                                               Integer sortOrder, Boolean available) {
        requireItem(companyId, catalogItemId);
        PapelariaCatalogOption updated = optionRepository.update(companyId, catalogItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "papelaria_catalog_option_updated", "papelaria_catalog_item_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PapelariaCatalogOption toggleOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                               boolean available) {
        requireItem(companyId, catalogItemId);
        PapelariaCatalogOption option = optionRepository.toggle(companyId, catalogItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "papelaria_catalog_option_updated", "papelaria_catalog_item_option",
            optionId, Map.of("available", available));
        catalogCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId) {
        requireItem(companyId, catalogItemId);
        boolean deleted = optionRepository.delete(companyId, catalogItemId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "papelaria_catalog_option_deleted", "papelaria_catalog_item_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID catalogItemId) {
        if (repository.findById(companyId, catalogItemId).isEmpty()) {
            throw new CatalogItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (PapelariaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
