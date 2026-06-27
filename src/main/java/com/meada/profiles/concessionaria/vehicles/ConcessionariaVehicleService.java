package com.meada.profiles.concessionaria.vehicles;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.concessionaria.ConcessionariaContextCache;
import com.meada.profiles.concessionaria.VehicleStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos veículos do ESTOQUE (camada 8.17). CRUD + transição de status de estoque. A mudança de
 * status é AÇÃO HUMANA do painel (a IA não toca) — valida a transição via {@link VehicleStatus} (→ 409
 * invalid_status_transition), materializa status_updated_at. delete-in-use: veículo com test-drive OU
 * lead → 409 vehicle_in_use. Invalida o {@link ConcessionariaContextCache} em toda mutação (a vitrine
 * entra no contexto da IA).
 */
@Service
public class ConcessionariaVehicleService {

    private final ConcessionariaVehicleRepository repository;
    private final AuditLogger auditLogger;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaVehicleService(ConcessionariaVehicleRepository repository, AuditLogger auditLogger,
                                        ConcessionariaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class VehicleNotFoundException extends RuntimeException {}
    public static class VehicleInUseException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    @Transactional
    public ConcessionariaVehicle create(UUID companyId, UUID userId, String brand, String model,
                                        Integer modelYear, Integer mileageKm, int priceCents, String color,
                                        String fuel, String transmission, String plate, String photoUrl,
                                        String description) {
        ConcessionariaVehicle created = repository.insert(companyId, brand, model, modelYear, mileageKm,
            priceCents, color, fuel, transmission, plate, photoUrl, description);
        auditLogger.log(companyId, userId, "concessionaria_vehicle_created", "concessionaria_vehicle",
            created.id(), Map.of("brand", created.brand(), "model", created.model()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ConcessionariaVehicle update(UUID companyId, UUID userId, UUID id, String brand, String model,
                                        Integer modelYear, boolean modelYearProvided, Integer mileageKm,
                                        boolean mileageProvided, Integer priceCents, String color, String fuel,
                                        String transmission, String plate, String photoUrl, String description,
                                        Boolean active) {
        ConcessionariaVehicle updated = repository.update(companyId, id, brand, model, modelYear,
                modelYearProvided, mileageKm, mileageProvided, priceCents, color, fuel, transmission,
                plate, photoUrl, description, active)
            .orElseThrow(VehicleNotFoundException::new);
        auditLogger.log(companyId, userId, "concessionaria_vehicle_updated", "concessionaria_vehicle", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    /** Transiciona o status de ESTOQUE (disponivel→reservado→vendido). Valida a transição (→ 409). */
    @Transactional
    public ConcessionariaVehicle updateStatus(UUID companyId, UUID userId, UUID id, String newStatusId) {
        VehicleStatus newStatus = VehicleStatus.fromId(newStatusId).orElseThrow(InvalidStatusException::new);
        ConcessionariaVehicle current = repository.findById(companyId, id).orElseThrow(VehicleNotFoundException::new);
        VehicleStatus from = VehicleStatus.fromId(current.status()).orElseThrow(InvalidStatusException::new);
        if (from != newStatus && !from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }
        ConcessionariaVehicle updated = repository.updateStatus(companyId, id, newStatus.id())
            .orElseThrow(VehicleNotFoundException::new);
        auditLogger.log(companyId, userId, "concessionaria_vehicle_status_updated", "concessionaria_vehicle",
            id, Map.of("status", newStatus.id()));
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (repository.hasReferences(companyId, id)) {
            throw new VehicleInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new VehicleNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new VehicleInUseException();
        }
        auditLogger.log(companyId, userId, "concessionaria_vehicle_deleted", "concessionaria_vehicle", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<ConcessionariaVehicle> list(UUID companyId, String status, Boolean active, String search) {
        return repository.listByCompany(companyId, status, active, search);
    }

    public List<ConcessionariaVehicle> listAvailable(UUID companyId) {
        return repository.listAvailable(companyId);
    }

    public Optional<ConcessionariaVehicle> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
