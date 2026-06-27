package com.meada.profiles.fotografia.appointments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.fotografia.FotografiaProfileGuard;
import com.meada.profiles.fotografia.FotografiaProfileGuard.WrongProfileException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.ConflictException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InactivePackageException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InactiveProfessionalException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InvalidStatusException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.OutsideHoursException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.PackageNotFoundException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.ProfessionalNotFoundException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.SessionNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sessões do tenant fotografia (camada 8.16). TENANT + perfil 'fotografia' only. Espelho do DermatologiaAppointmentController. */
@RestController
public class FotografiaAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final FotografiaAppointmentService service;
    private final FotografiaProfileGuard profileGuard;

    public FotografiaAppointmentController(FotografiaAppointmentService service, FotografiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual. startAt em ISO-8601 instant. customerName/phone são snapshots. */
    public record CreateRequest(
        @NotNull UUID professionalId,
        @NotNull UUID packageId,
        @NotBlank String startAt,
        @NotBlank @Size(max = 200) String customerName,
        String customerPhone,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    /** Body do PATCH da sessão: o estúdio grava o delivery_link (e/ou notes) DEPOIS da sessão. */
    public record SessionRequest(String deliveryLink, String notes) {}

    @GetMapping("/api/fotografia/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) UUID packageId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        Instant from;
        Instant to;
        try {
            from = dateFrom == null || dateFrom.isBlank() ? null : Instant.parse(dateFrom);
            to = dateTo == null || dateTo.isBlank() ? null : Instant.parse(dateTo);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, from, to, professionalId, packageId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, professionalId, packageId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/fotografia/appointments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "session_not_found"));
    }

    @PostMapping("/api/fotografia/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        Instant startAt;
        try {
            startAt = Instant.parse(req.startAt());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            // POST manual: sem conversation_id (sem WhatsApp) → não notifica. Sem contactId (snapshots
            // de cliente vêm do request).
            FotografiaSessionAppointment created = service.create(companyId, req.professionalId(), req.packageId(),
                null, null, startAt, req.customerName(), req.customerPhone(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        } catch (InactiveProfessionalException e) {
            return error(400, "Bad Request", "inactive_professional");
        } catch (InactivePackageException e) {
            return error(400, "Bad Request", "inactive_package");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            FotografiaAppointmentConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "appointmentId", c.existingId().toString(),
                    "customerName", c.existingCustomerName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/fotografia/appointments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (SessionNotFoundException e) {
            return error(404, "Not Found", "session_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    @PatchMapping("/api/fotografia/appointments/{id}")
    public ResponseEntity<Object> updateSession(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody SessionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean linkProvided = req.deliveryLink() != null;
        try {
            return ResponseEntity.ok(service.updateSession(companyId, id, req.deliveryLink(), linkProvided, req.notes()));
        } catch (SessionNotFoundException e) {
            return error(404, "Not Found", "session_not_found");
        }
    }
}
