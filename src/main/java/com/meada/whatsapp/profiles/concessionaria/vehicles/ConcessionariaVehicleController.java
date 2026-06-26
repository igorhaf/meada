package com.meada.whatsapp.profiles.concessionaria.vehicles;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.whatsapp.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.InvalidStatusException;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.VehicleInUseException;
import com.meada.whatsapp.profiles.concessionaria.vehicles.ConcessionariaVehicleService.VehicleNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * Veículos do ESTOQUE do tenant concessionaria (camada 8.17). TENANT + perfil 'concessionaria' only.
 * CRUD + PATCH {id}/status (transição de estoque) + GET ?available (vitrine).
 */
@RestController
public class ConcessionariaVehicleController {

    private final ConcessionariaVehicleService service;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaVehicleController(ConcessionariaVehicleService service,
                                           ConcessionariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 80) String brand,
        @NotBlank @Size(max = 120) String model,
        Integer modelYear,
        Integer mileageKm,
        @Min(0) int priceCents,
        String color,
        String fuel,
        String transmission,
        String plate,
        String photoUrl,
        String description) {}

    public record UpdateRequest(
        @Size(max = 80) String brand,
        @Size(max = 120) String model,
        Integer modelYear,
        Integer mileageKm,
        Integer priceCents,
        String color,
        String fuel,
        String transmission,
        String plate,
        String photoUrl,
        String description,
        Boolean active) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/concessionaria/vehicles")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (available) {
            return ResponseEntity.ok(Map.of("items", service.listAvailable(companyId)));
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, status, active, search)));
    }

    @GetMapping("/api/concessionaria/vehicles/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "vehicle_not_found"));
    }

    @PostMapping("/api/concessionaria/vehicles")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.brand(),
            req.model(), req.modelYear(), req.mileageKm(), req.priceCents(), req.color(), req.fuel(),
            req.transmission(), req.plate(), req.photoUrl(), req.description()));
    }

    @PatchMapping("/api/concessionaria/vehicles/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.brand(), req.model(),
                req.modelYear(), req.modelYear() != null, req.mileageKm(), req.mileageKm() != null,
                req.priceCents(), req.color(), req.fuel(), req.transmission(), req.plate(), req.photoUrl(),
                req.description(), req.active()));
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        }
    }

    @PatchMapping("/api/concessionaria/vehicles/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, user.userId(), id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    @DeleteMapping("/api/concessionaria/vehicles/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
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
