package com.meada.whatsapp.profiles.barbearia.barbers;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.barbearia.BarberProfileGuard;
import com.meada.whatsapp.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.barbearia.barbers.BarberBarberService.BarberInUseException;
import com.meada.whatsapp.profiles.barbearia.barbers.BarberBarberService.BarberNotFoundException;
import jakarta.validation.Valid;
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
 * Barbeiros do tenant barbearia (camada 8.1). TENANT + perfil 'barbearia' only. Espelho de
 * SalonProfessionalController. Rota /api/barbearia/barbers (slug "barbers" pra não colidir com a
 * tela genérica /dashboard/professionals do salon).
 */
@RestController
public class BarberBarberController {

    private final BarberBarberService service;
    private final BarberProfileGuard profileGuard;

    public BarberBarberController(BarberBarberService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateBarberRequest(
        @NotBlank @Size(max = 200) String name,
        String specialty,
        String notes) {}

    public record UpdateBarberRequest(
        @Size(max = 200) String name,
        String specialty,
        String notes,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/barbearia/barbers")
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

    @GetMapping("/api/barbearia/barbers/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "barber_not_found"));
    }

    @PostMapping("/api/barbearia/barbers")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateBarberRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        BarberBarber created = service.create(companyId, user.userId(), req.name(), req.specialty(), req.notes());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/barbearia/barbers/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBarberRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.specialty(), req.notes(), req.active()));
        } catch (BarberNotFoundException e) {
            return error(404, "Not Found", "barber_not_found");
        }
    }

    @PatchMapping("/api/barbearia/barbers/{id}/toggle")
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
        } catch (BarberNotFoundException e) {
            return error(404, "Not Found", "barber_not_found");
        }
    }

    @DeleteMapping("/api/barbearia/barbers/{id}")
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
        } catch (BarberNotFoundException e) {
            return error(404, "Not Found", "barber_not_found");
        } catch (BarberInUseException e) {
            return error(409, "Conflict", "barber_in_use");
        }
    }
}
