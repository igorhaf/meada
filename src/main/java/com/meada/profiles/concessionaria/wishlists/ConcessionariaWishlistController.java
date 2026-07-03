package com.meada.profiles.concessionaria.wishlists;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import com.meada.profiles.concessionaria.wishlists.ConcessionariaWishlistService.InvalidWishlistException;
import com.meada.profiles.concessionaria.wishlists.ConcessionariaWishlistService.WishlistNotFoundException;
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
 * Lista de desejos do tenant concessionaria (onda 1, backlog #1). TENANT + perfil 'concessionaria'
 * only. A IA registra pela tag <desejo_carro>; o painel cadastra/desativa/exclui aqui. O alerta
 * dispara automaticamente quando um veículo disponível casa com o desejo.
 */
@RestController
public class ConcessionariaWishlistController {

    private final ConcessionariaWishlistService service;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaWishlistController(ConcessionariaWishlistService service,
                                            ConcessionariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(UUID contactId, String brand, String model, Integer maxPriceCents,
                                Integer minYear, String notes) {}

    public record ActiveRequest(boolean active) {}

    @GetMapping("/api/concessionaria/wishlists")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @PostMapping("/api/concessionaria/wishlists")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.contactId(),
                null, req.brand(), req.model(), req.maxPriceCents(), req.minYear(), req.notes()));
        } catch (InvalidWishlistException e) {
            return error(400, "Bad Request", "invalid_wishlist");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return error(404, "Not Found", "contact_not_found");
        }
    }

    @PatchMapping("/api/concessionaria/wishlists/{id}/active")
    public ResponseEntity<Object> setActive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ActiveRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.setActive(companyId, user.userId(), id, req.active()));
        } catch (WishlistNotFoundException e) {
            return error(404, "Not Found", "wishlist_not_found");
        }
    }

    @DeleteMapping("/api/concessionaria/wishlists/{id}")
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
        } catch (WishlistNotFoundException e) {
            return error(404, "Not Found", "wishlist_not_found");
        }
    }
}
