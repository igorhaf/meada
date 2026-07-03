package com.meada.profiles.adega;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.adega.AdegaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant adega (camada 8.9): taxa de entrega + pedido mínimo. TENANT + perfil
 * 'adega' only. GET (fallback ZERO) + PATCH (upsert). Sob {@code /api/adega/config}.
 */
@RestController
public class AdegaConfigController {

    private final AdegaConfigService service;
    private final AdegaProfileGuard profileGuard;

    public AdegaConfigController(AdegaConfigService service, AdegaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents) {}

    @GetMapping("/api/adega/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAdega(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PatchMapping("/api/adega/config")
    public ResponseEntity<Object> patch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAdega(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.update(companyId, user.userId(),
            req.deliveryFeeCents(), req.minOrderCents()));
    }
}
