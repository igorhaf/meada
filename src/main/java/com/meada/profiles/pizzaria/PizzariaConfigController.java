package com.meada.profiles.pizzaria;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.pizzaria.PizzariaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant pizzaria (camada 8.6): taxa de entrega + pedido mínimo. TENANT + perfil
 * 'pizzaria' only. GET (fallback ZERO) + PATCH (upsert). Sob {@code /api/pizzaria/config}.
 */
@RestController
public class PizzariaConfigController {

    private final PizzariaConfigService service;
    private final PizzariaProfileGuard profileGuard;

    public PizzariaConfigController(PizzariaConfigService service, PizzariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents) {}

    @GetMapping("/api/pizzaria/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePizzaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PatchMapping("/api/pizzaria/config")
    public ResponseEntity<Object> patch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePizzaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.update(companyId, user.userId(),
            req.deliveryFeeCents(), req.minOrderCents()));
    }
}
