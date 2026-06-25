package com.meada.whatsapp.profiles.adega.menu;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.adega.AdegaCategory;
import com.meada.whatsapp.profiles.adega.AdegaMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do cardápio do adega (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.menu.SushiMenuService} + a gestão das OPÇÕES (ESCAPADA 2).
 * Valida categoria contra {@link AdegaCategory}, audita as mutações (audit_log do tenant) e invalida
 * o {@link AdegaMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class AdegaMenuService {

    private final AdegaMenuItemRepository repository;
    private final AdegaMenuOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final AdegaMenuCache menuCache;

    public AdegaMenuService(AdegaMenuItemRepository repository,
                             AdegaMenuOptionRepository optionRepository,
                             AuditLogger auditLogger,
                             AdegaMenuCache menuCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class MenuItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class MenuItemInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro item-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Itens --------------------------------------------------------------

    @Transactional
    public AdegaMenuItem create(UUID companyId, UUID userId, String name, String description,
                                 int priceCents, String category) {
        requireValidCategory(category);
        AdegaMenuItem created = repository.insert(companyId, name, description, priceCents, category);
        auditLogger.log(companyId, userId, "adega_menu_item_created", "adega_menu_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AdegaMenuItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                 Integer priceCents, String category, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        AdegaMenuItem updated = repository.update(companyId, id, name, description, priceCents, category, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "adega_menu_item_updated", "adega_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AdegaMenuItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        AdegaMenuItem item = repository.toggle(companyId, id, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "adega_menu_item_updated", "adega_menu_item", id,
            Map.of("available", available));
        menuCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new MenuItemNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe adega_order_item apontando para este item.
            throw new MenuItemInUseException();
        }
        auditLogger.log(companyId, userId, "adega_menu_item_deleted", "adega_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<AdegaMenuItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<AdegaMenuItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    public List<AdegaMenuOption> listOptions(UUID companyId, UUID menuItemId) {
        requireItem(companyId, menuItemId);
        return optionRepository.listByItem(companyId, menuItemId);
    }

    @Transactional
    public AdegaMenuOption addOption(UUID companyId, UUID userId, UUID menuItemId, String groupLabel,
                                      String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, menuItemId);
        AdegaMenuOption created = optionRepository.insert(
            companyId, menuItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "adega_menu_option_created", "adega_menu_item_option",
            created.id(), Map.of("menu_item_id", menuItemId, "group_label", created.groupLabel()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AdegaMenuOption updateOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                         String groupLabel, String optionLabel, Integer priceDeltaCents,
                                         Integer sortOrder, Boolean available) {
        requireItem(companyId, menuItemId);
        AdegaMenuOption updated = optionRepository.update(companyId, menuItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "adega_menu_option_updated", "adega_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AdegaMenuOption toggleOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                         boolean available) {
        requireItem(companyId, menuItemId);
        AdegaMenuOption option = optionRepository.toggle(companyId, menuItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "adega_menu_option_updated", "adega_menu_item_option",
            optionId, Map.of("available", available));
        menuCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId) {
        requireItem(companyId, menuItemId);
        boolean deleted = optionRepository.delete(companyId, menuItemId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "adega_menu_option_deleted", "adega_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID menuItemId) {
        if (repository.findById(companyId, menuItemId).isEmpty()) {
            throw new MenuItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (AdegaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
