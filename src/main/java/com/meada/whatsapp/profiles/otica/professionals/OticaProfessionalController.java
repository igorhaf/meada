package com.meada.whatsapp.profiles.otica.professionals;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.otica.OticaProfileGuard;
import com.meada.whatsapp.profiles.otica.OticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.otica.professionals.OticaProfessionalService.ProfessionalInUseException;
import com.meada.whatsapp.profiles.otica.professionals.OticaProfessionalService.ProfessionalNotFoundException;
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
 * Optometristas do tenant otica (camada 8.12, FLUXO A). TENANT + perfil 'otica' only —
 * {@link OticaProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob
 * {@code /api/otica/professionals}. Clone do SalonProfessionalController.
 */
@RestController
public class OticaProfessionalController {

    private final OticaProfessionalService service;
    private final OticaProfileGuard profileGuard;

    public OticaProfessionalController(OticaProfessionalService service, OticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateProfessionalRequest(
        @NotBlank @Size(max = 200) String name,
        String notes) {}

    public record UpdateProfessionalRequest(
        @Size(max = 200) String name,
        String notes,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/otica/professionals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/otica/professionals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "professional_not_found"));
    }

    @PostMapping("/api/otica/professionals")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateProfessionalRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        OticaProfessional created = service.create(companyId, user.userId(), req.name(), req.notes());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/otica/professionals/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfessionalRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.notes(), req.active()));
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        }
    }

    @PatchMapping("/api/otica/professionals/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        }
    }

    @DeleteMapping("/api/otica/professionals/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        } catch (ProfessionalInUseException e) {
            return error(409, "Conflict", "professional_in_use");
        }
    }
}
