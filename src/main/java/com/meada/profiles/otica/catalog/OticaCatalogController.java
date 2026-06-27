package com.meada.profiles.otica.catalog;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.otica.OticaProfileGuard;
import com.meada.profiles.otica.OticaProfileGuard.WrongProfileException;
import com.meada.profiles.otica.catalog.OticaCatalogService.CatalogItemInUseException;
import com.meada.profiles.otica.catalog.OticaCatalogService.CatalogItemNotFoundException;
import com.meada.profiles.otica.catalog.OticaCatalogService.InvalidCategoryException;
import com.meada.profiles.otica.catalog.OticaCatalogService.OptionNotFoundException;
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
 * Catálogo do tenant otica (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.profiles.floricultura.catalog.FloriculturaCatalogController} + os campos
 * {@code madeToOrder}/{@code leadTimeDays} (a ESCAPADA) + as rotas aninhadas de OPÇÃO (tipo de
 * lente/tratamento). TENANT + perfil 'otica' only. Sob {@code /api/otica/catalog/**}.
 */
@RestController
public class OticaCatalogController {

    private final OticaCatalogService service;
    private final OticaProfileGuard profileGuard;

    public OticaCatalogController(OticaCatalogService service, OticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de item. category validada contra o enum no service (400 invalid_category). */
    public record CreateCatalogItemRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @PositiveOrZero int priceCents,
        @NotBlank String category,
        boolean madeToOrder,
        @PositiveOrZero Integer leadTimeDays) {}

    /** Body de edição de item (PATCH parcial; todos opcionais). */
    public record UpdateCatalogItemRequest(
        @Size(max = 120) String name,
        String description,
        @PositiveOrZero Integer priceCents,
        String category,
        Boolean madeToOrder,
        @PositiveOrZero Integer leadTimeDays,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    /** Body de criação de opção (tipo de lente/tratamento). */
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

    // ===== ITENS =============================================================

    @GetMapping("/api/otica/catalog")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    @GetMapping("/api/otica/catalog/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "catalog_item_not_found"));
    }

    @PostMapping("/api/otica/catalog")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateCatalogItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.description(), req.priceCents(), req.category(), req.madeToOrder(), req.leadTimeDays()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    @PatchMapping("/api/otica/catalog/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCatalogItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            // leadTimeDays != null = "definir esse valor"; null = "não mexer" (semântica de PATCH parcial).
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.description(),
                req.priceCents(), req.category(), req.madeToOrder(), req.leadTimeDays(),
                req.leadTimeDays() != null, req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    @PatchMapping("/api/otica/catalog/{id}/toggle")
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
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    @DeleteMapping("/api/otica/catalog/{id}")
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
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (CatalogItemInUseException e) {
            return error(409, "Conflict", "catalog_item_in_use");
        }
    }

    // ===== OPÇÕES (tipo de lente / tratamento) ===============================

    @GetMapping("/api/otica/catalog/{itemId}/options")
    public ResponseEntity<Object> listOptions(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("options", service.listOptions(companyId, itemId)));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    @PostMapping("/api/otica/catalog/{itemId}/options")
    public ResponseEntity<Object> createOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addOption(companyId, user.userId(), itemId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    @PatchMapping("/api/otica/catalog/{itemId}/options/{optionId}")
    public ResponseEntity<Object> updateOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateOption(companyId, user.userId(), itemId, optionId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder(), req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    @PatchMapping("/api/otica/catalog/{itemId}/options/{optionId}/toggle")
    public ResponseEntity<Object> toggleOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleOption(companyId, user.userId(), itemId, optionId, req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    @DeleteMapping("/api/otica/catalog/{itemId}/options/{optionId}")
    public ResponseEntity<Object> deleteOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteOption(companyId, user.userId(), itemId, optionId);
            return ResponseEntity.noContent().build();
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }
}
