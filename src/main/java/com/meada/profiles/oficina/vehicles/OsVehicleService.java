package com.meada.profiles.oficina.vehicles;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.oficina.OficinaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos veículos (camada 7.9). Sub-entidade do cliente (contact). Valida o cliente ao criar,
 * audita e invalida o {@link OficinaContextCache}. DELETE protegido por FK (service_orders) → 409
 * vehicle_in_use; o caminho preferido pra "remover" é {@link #archive} (active=false). plate UNIQUE
 * por company → 409 plate_taken.
 */
@Service
public class OsVehicleService {

    private final OsVehicleRepository repository;
    private final AuditLogger auditLogger;
    private final OficinaContextCache contextCache;

    public OsVehicleService(OsVehicleRepository repository, AuditLogger auditLogger,
                            OficinaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class VehicleNotFoundException extends RuntimeException {}
    public static class ContactNotFoundException extends RuntimeException {}
    public static class VehicleInUseException extends RuntimeException {}
    public static class PlateTakenException extends RuntimeException {}

    @Transactional
    public OsVehicle create(UUID companyId, UUID userId, UUID contactId, String plate, String brand,
                            String model, Integer year, String color, Integer mileageKm, String notes) {
        if (!repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        OsVehicle created;
        try {
            created = repository.insert(companyId, contactId, plate, brand, model, year, color, mileageKm, notes);
        } catch (DataIntegrityViolationException e) {
            throw new PlateTakenException();
        }
        auditLogger.log(companyId, userId, "os_vehicle_created", "os_vehicle",
            created.id(), Map.of("plate", created.plate()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OsVehicle update(UUID companyId, UUID userId, UUID id, String plate, String brand, String model,
                            Integer year, String color, Integer mileageKm, String notes, Boolean active) {
        OsVehicle updated;
        try {
            updated = repository.update(companyId, id, plate, brand, model, year, color, mileageKm, notes, active)
                .orElseThrow(VehicleNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new PlateTakenException();
        }
        auditLogger.log(companyId, userId, "os_vehicle_updated", "os_vehicle", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public OsVehicle archive(UUID companyId, UUID userId, UUID id) {
        OsVehicle v = repository.archive(companyId, id).orElseThrow(VehicleNotFoundException::new);
        auditLogger.log(companyId, userId, "os_vehicle_archived", "os_vehicle", id, Map.of());
        contextCache.invalidate(companyId);
        return v;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new VehicleNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new VehicleInUseException();
        }
        auditLogger.log(companyId, userId, "os_vehicle_deleted", "os_vehicle", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<OsVehicle> list(UUID companyId, UUID contactId, Boolean active, String search) {
        return repository.listByCompany(companyId, contactId, active, search);
    }

    public Optional<OsVehicle> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
