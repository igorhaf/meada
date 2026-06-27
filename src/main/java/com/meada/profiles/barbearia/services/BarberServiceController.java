package com.meada.profiles.barbearia.services;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.profiles.barbearia.services.BarberServiceService.ServiceInUseException;
import com.meada.profiles.barbearia.services.BarberServiceService.ServiceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
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
 * Serviços do tenant barbearia (camada 8.1). TENANT + perfil 'barbearia' only. Rota
 * /api/barbearia/services. Espelho de SalonOfferingController (entidade BarberService no backend).
 */
@RestController
public class BarberServiceController {

    private final BarberServiceService service;
    private final BarberProfileGuard profileGuard;

    public BarberServiceController(BarberServiceService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateServiceRequest(
        @NotBlank @Size(max = 200) String name,
        String category,
        @Min(5) @Max(480) int durationMinutes,
        @PositiveOrZero Integer priceCents,
        String description) {}

    public record UpdateServiceRequest(
        @Size(max = 200) String name,
        String category,
        @Min(5) @Max(480) Integer durationMinutes,
        Integer priceCents,
        String description,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/barbearia/services")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/barbearia/services/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "service_not_found"));
    }

    @PostMapping("/api/barbearia/services")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateServiceRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        BarberService created = service.create(companyId, user.userId(), req.name(), req.category(),
            req.durationMinutes(), req.priceCents(), req.description());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/barbearia/services/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.category(), req.durationMinutes(), req.priceCents(), req.description(), req.active()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @PatchMapping("/api/barbearia/services/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @DeleteMapping("/api/barbearia/services/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (ServiceInUseException e) {
            return error(409, "Conflict", "service_in_use");
        }
    }
}
