package com.meada.whatsapp.profiles.lavanderia.services;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.lavanderia.LavanderiaProfileGuard;
import com.meada.whatsapp.profiles.lavanderia.LavanderiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.InvalidCategoryException;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.OptionNotFoundException;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.ServiceInUseException;
import com.meada.whatsapp.profiles.lavanderia.services.LavanderiaServiceCatalogService.ServiceNotFoundException;
import jakarta.validation.Valid;
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
 * Catálogo de serviços do tenant lavanderia (camada 8.10). Clone do FloriculturaCatalogController +
 * os campos turnaround_days/care_instructions + as rotas aninhadas de OPÇÃO. TENANT + perfil
 * 'lavanderia' only — {@link LavanderiaProfileGuard} rejeita 403 forbidden_wrong_profile. Sob
 * {@code /api/lavanderia/services/**}.
 */
@RestController
public class LavanderiaServiceCatalogController {

    private final LavanderiaServiceCatalogService service;
    private final LavanderiaProfileGuard profileGuard;

    public LavanderiaServiceCatalogController(LavanderiaServiceCatalogService service,
                                              LavanderiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de serviço. category validada contra o enum no service (400 invalid_category). */
    public record CreateServiceRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @PositiveOrZero int priceCents,
        @NotBlank String category,
        @PositiveOrZero int turnaroundDays,
        String careInstructions) {}

    /** Body de edição de serviço (PATCH parcial; todos opcionais). */
    public record UpdateServiceRequest(
        @Size(max = 120) String name,
        String description,
        @PositiveOrZero Integer priceCents,
        String category,
        @PositiveOrZero Integer turnaroundDays,
        String careInstructions,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    /** Body de criação de opção. */
    public record CreateOptionRequest(
        @NotBlank @Size(max = 60) String groupLabel,
        @NotBlank @Size(max = 80) String optionLabel,
        @PositiveOrZero int priceDeltaCents,
        int sortOrder) {}

    /** Body de edição de opção (PATCH parcial; todos opcionais). */
    public record UpdateOptionRequest(
        @Size(max = 60) String groupLabel,
        @Size(max = 80) String optionLabel,
        @PositiveOrZero Integer priceDeltaCents,
        Integer sortOrder,
        Boolean available) {}

    // ===== SERVIÇOS ==========================================================

    @GetMapping("/api/lavanderia/services")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    @GetMapping("/api/lavanderia/services/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "service_not_found"));
    }

    @PostMapping("/api/lavanderia/services")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateServiceRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(),
                req.name(), req.description(), req.priceCents(), req.category(), req.turnaroundDays(),
                req.careInstructions()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    @PatchMapping("/api/lavanderia/services/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.description(), req.priceCents(), req.category(), req.turnaroundDays(),
                req.careInstructions(), req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @PatchMapping("/api/lavanderia/services/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @DeleteMapping("/api/lavanderia/services/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
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

    // ===== OPÇÕES ============================================================

    @GetMapping("/api/lavanderia/services/{serviceId}/options")
    public ResponseEntity<Object> listOptions(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID serviceId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("options", service.listOptions(companyId, serviceId)));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @PostMapping("/api/lavanderia/services/{serviceId}/options")
    public ResponseEntity<Object> createOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID serviceId,
            @Valid @RequestBody CreateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addOption(companyId, user.userId(), serviceId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @PatchMapping("/api/lavanderia/services/{serviceId}/options/{optionId}")
    public ResponseEntity<Object> updateOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID serviceId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateOption(companyId, user.userId(), serviceId, optionId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder(), req.available()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    @PatchMapping("/api/lavanderia/services/{serviceId}/options/{optionId}/toggle")
    public ResponseEntity<Object> toggleOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID serviceId,
            @PathVariable UUID optionId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleOption(companyId, user.userId(), serviceId, optionId, req.available()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    @DeleteMapping("/api/lavanderia/services/{serviceId}/options/{optionId}")
    public ResponseEntity<Object> deleteOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID serviceId,
            @PathVariable UUID optionId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteOption(companyId, user.userId(), serviceId, optionId);
            return ResponseEntity.noContent().build();
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }
}
