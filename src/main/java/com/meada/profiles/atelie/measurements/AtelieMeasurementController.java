package com.meada.profiles.atelie.measurements;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.atelie.AtelieProfileGuard;
import com.meada.profiles.atelie.AtelieProfileGuard.WrongProfileException;
import com.meada.profiles.atelie.measurements.AtelieMeasurementService.ContactNotFoundException;
import com.meada.profiles.atelie.measurements.AtelieMeasurementService.InvalidMeasurementException;
import com.meada.profiles.atelie.measurements.AtelieMeasurementService.MeasurementNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Tabela de medidas por CONTATO do tenant atelie (onda 2, backlog #9). TENANT + perfil 'atelie'
 * only. POST é upsert por (contato, lower(label)) — regravar a mesma medida atualiza o valor.
 */
@RestController
public class AtelieMeasurementController {

    private final AtelieMeasurementService service;
    private final AtelieProfileGuard profileGuard;

    public AtelieMeasurementController(AtelieMeasurementService service, AtelieProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record UpsertRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 100) String value) {}

    @GetMapping("/api/atelie/contacts/{contactId}/measurements")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID contactId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items", service.list(companyId, contactId)));
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        }
    }

    @PostMapping("/api/atelie/contacts/{contactId}/measurements")
    public ResponseEntity<Object> upsert(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID contactId, @Valid @RequestBody UpsertRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.upsert(companyId, user.userId(), contactId, req.label(), req.value()));
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        } catch (InvalidMeasurementException e) {
            return error(400, "Bad Request", "invalid_measurement");
        }
    }

    @DeleteMapping("/api/atelie/measurements/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (MeasurementNotFoundException e) {
            return error(404, "Not Found", "measurement_not_found");
        }
    }
}
