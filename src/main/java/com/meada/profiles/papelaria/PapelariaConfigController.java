package com.meada.profiles.papelaria;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.papelaria.PapelariaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant papelaria (camada 8.15): taxa de entrega + pedido mínimo + lead time padrão.
 * TENANT + perfil 'papelaria' only. GET (fallback DEFAULT) + PATCH (upsert). Sob
 * {@code /api/papelaria/config}. Clone do SuplementosConfigController + o 3º campo
 * {@code leadTimeDaysDefault}.
 */
@RestController
public class PapelariaConfigController {

    private final PapelariaConfigService service;
    private final PapelariaProfileGuard profileGuard;

    public PapelariaConfigController(PapelariaConfigService service, PapelariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents, int leadTimeDaysDefault) {}

    @GetMapping("/api/papelaria/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PatchMapping("/api/papelaria/config")
    public ResponseEntity<Object> patch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.update(companyId, user.userId(),
            req.deliveryFeeCents(), req.minOrderCents(), req.leadTimeDaysDefault()));
    }
}
