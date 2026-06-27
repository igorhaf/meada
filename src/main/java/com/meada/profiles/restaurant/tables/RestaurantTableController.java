package com.meada.profiles.restaurant.tables;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.restaurant.RestaurantProfileGuard;
import com.meada.profiles.restaurant.RestaurantProfileGuard.WrongProfileException;
import com.meada.profiles.restaurant.tables.RestaurantTableService.LabelInUseException;
import com.meada.profiles.restaurant.tables.RestaurantTableService.TableInUseException;
import com.meada.profiles.restaurant.tables.RestaurantTableService.TableNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
 * Mesas do tenant restaurant (camada 7.3). TENANT + perfil 'restaurant' only —
 * {@link RestaurantProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob
 * {@code /api/restaurant/**} (rota de tenant fora do prefixo /admin, com CORS já liberado).
 */
@RestController
public class RestaurantTableController {

    private final RestaurantTableService service;
    private final RestaurantProfileGuard profileGuard;

    public RestaurantTableController(RestaurantTableService service, RestaurantProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação. */
    public record CreateTableRequest(
        @NotBlank @Size(max = 60) String label,
        @Min(1) @Max(50) int capacity,
        String notes) {}

    /** Body de edição (PATCH parcial; todos opcionais). */
    public record UpdateTableRequest(
        @Size(max = 60) String label,
        @Min(1) @Max(50) Integer capacity,
        String notes,
        Boolean available) {}

    /** Body do toggle de disponibilidade. */
    public record ToggleRequest(boolean available) {}

    // ---- GET lista ----------------------------------------------------------
    @GetMapping("/api/restaurant/tables")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyAvailable) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyAvailable)));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/restaurant/tables/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "table_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/restaurant/tables")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateTableRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            RestaurantTable created = service.create(
                companyId, user.userId(), req.label(), req.capacity(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (LabelInUseException e) {
            return error(409, "Conflict", "label_in_use");
        }
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/restaurant/tables/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTableRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(
                companyId, user.userId(), id, req.label(), req.capacity(), req.notes(), req.available()));
        } catch (TableNotFoundException e) {
            return error(404, "Not Found", "table_not_found");
        } catch (LabelInUseException e) {
            return error(409, "Conflict", "label_in_use");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/restaurant/tables/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (TableNotFoundException e) {
            return error(404, "Not Found", "table_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/restaurant/tables/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (TableNotFoundException e) {
            return error(404, "Not Found", "table_not_found");
        } catch (TableInUseException e) {
            return error(409, "Conflict", "table_in_use");
        }
    }
}
