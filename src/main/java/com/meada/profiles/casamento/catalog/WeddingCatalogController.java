package com.meada.profiles.casamento.catalog;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.casamento.CasamentoProfileGuard;
import com.meada.profiles.casamento.CasamentoProfileGuard.WrongProfileException;
import com.meada.profiles.casamento.catalog.WeddingCatalogService.CatalogItemNotFoundException;
import com.meada.profiles.casamento.catalog.WeddingCatalogService.InvalidCatalogItemException;
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
 * Catálogo de pacotes/adicionais do tenant casamento (onda 1, backlog #3). TENANT + perfil
 * 'casamento' only. CRUD; o editor de orçamento usa a lista como autofill e a IA apresenta com o
 * preço do catálogo.
 */
@RestController
public class WeddingCatalogController {

    private final WeddingCatalogService service;
    private final CasamentoProfileGuard profileGuard;

    public WeddingCatalogController(WeddingCatalogService service, CasamentoProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        String kind,
        String description,
        @NotNull Integer priceCents,
        Boolean active) {}

    public record UpdateRequest(
        @Size(max = 200) String name,
        String kind,
        String description,
        Boolean clearDescription,
        Integer priceCents,
        Boolean active) {}

    @GetMapping("/api/casamento/catalog")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @PostMapping("/api/casamento/catalog")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            String description = req.description() == null || req.description().isBlank() ? null : req.description();
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.kind(), description, req.priceCents(), req.active()));
        } catch (InvalidCatalogItemException e) {
            return error(400, "Bad Request", "invalid_item");
        }
    }

    @PatchMapping("/api/casamento/catalog/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean descProvided = req.description() != null || Boolean.TRUE.equals(req.clearDescription());
        String description = Boolean.TRUE.equals(req.clearDescription()) ? null
            : (req.description() == null || req.description().isBlank() ? null : req.description());
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.kind(),
                description, descProvided, req.priceCents(), req.active()));
        } catch (InvalidCatalogItemException e) {
            return error(400, "Bad Request", "invalid_item");
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        }
    }

    @DeleteMapping("/api/casamento/catalog/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        }
    }
}
