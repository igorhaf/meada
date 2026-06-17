package com.meada.whatsapp.profiles.oficina.vehicles;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.oficina.OficinaProfileGuard;
import com.meada.whatsapp.profiles.oficina.OficinaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.oficina.vehicles.OsVehicleService.ContactNotFoundException;
import com.meada.whatsapp.profiles.oficina.vehicles.OsVehicleService.PlateTakenException;
import com.meada.whatsapp.profiles.oficina.vehicles.OsVehicleService.VehicleInUseException;
import com.meada.whatsapp.profiles.oficina.vehicles.OsVehicleService.VehicleNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Veículos do tenant oficina (camada 7.9) — sub-entidade do cliente (contact). TENANT + perfil
 * 'oficina' only. CRUD + archive (preferido a delete) + delete (409 se houver OS). plate UNIQUE.
 */
@RestController
public class OsVehicleController {

    private final OsVehicleService service;
    private final OficinaProfileGuard profileGuard;

    public OsVehicleController(OsVehicleService service, OficinaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateVehicleRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 10) String plate,
        String brand,
        String model,
        Integer year,
        String color,
        Integer mileageKm,
        String notes) {}

    public record UpdateVehicleRequest(
        @Size(max = 10) String plate,
        String brand,
        String model,
        Integer year,
        String color,
        Integer mileageKm,
        String notes,
        Boolean active) {}

    @GetMapping("/api/oficina/vehicles")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, contactId, active, search)));
    }

    @GetMapping("/api/oficina/vehicles/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "vehicle_not_found"));
    }

    @PostMapping("/api/oficina/vehicles")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateVehicleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            OsVehicle created = service.create(companyId, user.userId(), req.contactId(), req.plate(),
                req.brand(), req.model(), req.year(), req.color(), req.mileageKm(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        } catch (PlateTakenException e) {
            return error(409, "Conflict", "plate_taken");
        }
    }

    @PatchMapping("/api/oficina/vehicles/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.plate(), req.brand(),
                req.model(), req.year(), req.color(), req.mileageKm(), req.notes(), req.active()));
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (PlateTakenException e) {
            return error(409, "Conflict", "plate_taken");
        }
    }

    @PatchMapping("/api/oficina/vehicles/{id}/archive")
    public ResponseEntity<Object> archive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.archive(companyId, user.userId(), id));
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        }
    }

    @DeleteMapping("/api/oficina/vehicles/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (VehicleInUseException e) {
            return error(409, "Conflict", "vehicle_in_use");
        }
    }
}
