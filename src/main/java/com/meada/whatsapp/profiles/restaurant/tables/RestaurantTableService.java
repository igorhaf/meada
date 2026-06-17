package com.meada.whatsapp.profiles.restaurant.tables;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.restaurant.ReservationContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das mesas do restaurante (camada 7.3). Audita as mutações (audit_log do tenant) e
 * invalida o {@link ReservationContextCache} a cada gravação — para a IA ver a mudança (capacidade/
 * disponibilidade) na hora.
 */
@Service
public class RestaurantTableService {

    private final RestaurantTableRepository repository;
    private final AuditLogger auditLogger;
    private final ReservationContextCache contextCache;

    public RestaurantTableService(RestaurantTableRepository repository, AuditLogger auditLogger,
                                  ReservationContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Mesa não encontrada / de outro tenant (→ 404). */
    public static class TableNotFoundException extends RuntimeException {}

    /** Label de mesa já em uso no tenant (UNIQUE) (→ 409 label_in_use). */
    public static class LabelInUseException extends RuntimeException {}

    /** Mesa referenciada por reserva (FK restrict) — não pode hard-deletar (→ 409 table_in_use). */
    public static class TableInUseException extends RuntimeException {}

    @Transactional
    public RestaurantTable create(UUID companyId, UUID userId, String label, int capacity, String notes) {
        RestaurantTable created;
        try {
            created = repository.insert(companyId, label, capacity, notes);
        } catch (DataIntegrityViolationException e) {
            throw new LabelInUseException();   // UNIQUE (company_id, label).
        }
        auditLogger.log(companyId, userId, "restaurant_table_created", "restaurant_table",
            created.id(), Map.of("label", created.label(), "capacity", created.capacity()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public RestaurantTable update(UUID companyId, UUID userId, UUID id, String label, Integer capacity,
                                  String notes, Boolean available) {
        RestaurantTable updated;
        try {
            updated = repository.update(companyId, id, label, capacity, notes, available)
                .orElseThrow(TableNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new LabelInUseException();
        }
        auditLogger.log(companyId, userId, "restaurant_table_updated", "restaurant_table", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public RestaurantTable toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        RestaurantTable table = repository.toggle(companyId, id, available)
            .orElseThrow(TableNotFoundException::new);
        auditLogger.log(companyId, userId, "restaurant_table_updated", "restaurant_table", id,
            Map.of("available", available));
        contextCache.invalidate(companyId);
        return table;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new TableNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe table_reservation apontando para esta mesa.
            throw new TableInUseException();
        }
        auditLogger.log(companyId, userId, "restaurant_table_deleted", "restaurant_table", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<RestaurantTable> list(UUID companyId, boolean onlyAvailable) {
        return repository.listByCompany(companyId, onlyAvailable);
    }

    public Optional<RestaurantTable> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
