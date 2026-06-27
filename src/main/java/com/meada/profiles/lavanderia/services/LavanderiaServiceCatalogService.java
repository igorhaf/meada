package com.meada.profiles.lavanderia.services;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.lavanderia.LavanderiaCatalogCache;
import com.meada.profiles.lavanderia.LavanderiaServiceCategory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo de SERVIÇOS do lavanderia (camada 8.10). Clone de
 * {@link com.meada.profiles.floricultura.catalog.FloriculturaCatalogService} + os campos
 * turnaround_days/care_instructions + a gestão das OPÇÕES. Valida categoria contra
 * {@link LavanderiaServiceCategory}, audita as mutações e invalida o {@link LavanderiaCatalogCache} a
 * cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class LavanderiaServiceCatalogService {

    private final LavanderiaServiceRepository repository;
    private final LavanderiaServiceOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final LavanderiaCatalogCache catalogCache;

    public LavanderiaServiceCatalogService(LavanderiaServiceRepository repository,
                                           LavanderiaServiceOptionRepository optionRepository,
                                           AuditLogger auditLogger,
                                           LavanderiaCatalogCache catalogCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Serviço não encontrado / de outro tenant (→ 404). */
    public static class ServiceNotFoundException extends RuntimeException {}

    /** Serviço referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409 service_in_use). */
    public static class ServiceInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro serviço-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Serviços -----------------------------------------------------------

    @Transactional
    public LavanderiaService create(UUID companyId, UUID userId, String name, String description,
                                    int priceCents, String category, int turnaroundDays,
                                    String careInstructions) {
        requireValidCategory(category);
        LavanderiaService created = repository.insert(companyId, name, description, priceCents, category,
            turnaroundDays, careInstructions);
        auditLogger.log(companyId, userId, "lavanderia_service_created", "lavanderia_service",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LavanderiaService update(UUID companyId, UUID userId, UUID id, String name, String description,
                                    Integer priceCents, String category, Integer turnaroundDays,
                                    String careInstructions, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        LavanderiaService updated = repository.update(companyId, id, name, description, priceCents,
                category, turnaroundDays, careInstructions, available)
            .orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "lavanderia_service_updated", "lavanderia_service", id, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public LavanderiaService toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        LavanderiaService item = repository.toggle(companyId, id, available)
            .orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "lavanderia_service_updated", "lavanderia_service", id,
            Map.of("available", available));
        catalogCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new ServiceNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe lavanderia_order_item apontando para este serviço.
            throw new ServiceInUseException();
        }
        auditLogger.log(companyId, userId, "lavanderia_service_deleted", "lavanderia_service", id, Map.of());
        catalogCache.invalidate(companyId);
    }

    public List<LavanderiaService> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<LavanderiaService> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções -------------------------------------------------------------

    public List<LavanderiaServiceOption> listOptions(UUID companyId, UUID serviceId) {
        requireService(companyId, serviceId);
        return optionRepository.listByService(companyId, serviceId);
    }

    @Transactional
    public LavanderiaServiceOption addOption(UUID companyId, UUID userId, UUID serviceId, String groupLabel,
                                             String optionLabel, int priceDeltaCents, int sortOrder) {
        requireService(companyId, serviceId);
        LavanderiaServiceOption created = optionRepository.insert(
            companyId, serviceId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "lavanderia_service_option_created", "lavanderia_service_option",
            created.id(), Map.of("service_id", serviceId, "group_label", created.groupLabel()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LavanderiaServiceOption updateOption(UUID companyId, UUID userId, UUID serviceId, UUID optionId,
                                                String groupLabel, String optionLabel, Integer priceDeltaCents,
                                                Integer sortOrder, Boolean available) {
        requireService(companyId, serviceId);
        LavanderiaServiceOption updated = optionRepository.update(companyId, serviceId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "lavanderia_service_option_updated", "lavanderia_service_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public LavanderiaServiceOption toggleOption(UUID companyId, UUID userId, UUID serviceId, UUID optionId,
                                                boolean available) {
        requireService(companyId, serviceId);
        LavanderiaServiceOption option = optionRepository.toggle(companyId, serviceId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "lavanderia_service_option_updated", "lavanderia_service_option",
            optionId, Map.of("available", available));
        catalogCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID serviceId, UUID optionId) {
        requireService(companyId, serviceId);
        boolean deleted = optionRepository.delete(companyId, serviceId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "lavanderia_service_option_deleted", "lavanderia_service_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
    }

    private void requireService(UUID companyId, UUID serviceId) {
        if (repository.findById(companyId, serviceId).isEmpty()) {
            throw new ServiceNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (LavanderiaServiceCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
