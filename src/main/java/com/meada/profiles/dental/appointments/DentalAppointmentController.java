package com.meada.profiles.dental.appointments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.dental.DentalProfileGuard;
import com.meada.profiles.dental.DentalProfileGuard.WrongProfileException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.ConflictException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.InvalidStatusException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.OutsideHoursException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.AppointmentNotFoundException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.PatientNotFoundException;
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

/**
 * Consultas do tenant dental (camada 7.4). TENANT + perfil 'dental' only. READ (lista/detalhe com
 * filtros de agenda) + POST manual (sem WhatsApp) + transição de status. NÃO há DELETE — consulta
 * mantém histórico; "remover" é status=cancelada.
 */
@RestController
public class DentalAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final DentalAppointmentService service;
    private final DentalProfileGuard profileGuard;

    public DentalAppointmentController(DentalAppointmentService service, DentalProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). startAt em ISO-8601 instant. */
    public record CreateAppointmentRequest(
        @NotNull UUID patientId,
        @NotBlank String startAt,
        @NotBlank @Size(max = 100) String type,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    // ---- GET lista (filtros: status, dateFrom, dateTo, patientId, paginação) -----------
    @GetMapping("/api/dental/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
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
        long total = service.count(companyId, status, from, to, patientId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, patientId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/dental/appointments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "appointment_not_found"));
    }

    // ---- POST cria (manual, sem WhatsApp) -----------------------------------
    @PostMapping("/api/dental/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateAppointmentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
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
            DentalAppointment created = service.create(
                companyId, req.patientId(), null, startAt, req.type(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            AppointmentConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "appointmentId", c.existingId().toString(),
                    "patientName", c.existingPatientName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    // ---- PATCH status -------------------------------------------------------
    @PatchMapping("/api/dental/appointments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (AppointmentNotFoundException e) {
            return error(404, "Not Found", "appointment_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
