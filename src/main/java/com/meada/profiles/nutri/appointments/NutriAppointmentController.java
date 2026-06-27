package com.meada.profiles.nutri.appointments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.nutri.NutriProfileGuard;
import com.meada.profiles.nutri.NutriProfileGuard.WrongProfileException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.AppointmentNotFoundException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.ConflictException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InactivePatientException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InactiveProfessionalException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InvalidStatusException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InvalidTypeException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.OutsideHoursException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.PatientNotFoundException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.ProfessionalNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

/** Consultas do tenant nutri (camada 8.0). TENANT + perfil 'nutri' only. */
@RestController
public class NutriAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final NutriAppointmentService service;
    private final NutriProfileGuard profileGuard;

    public NutriAppointmentController(NutriAppointmentService service, NutriProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual. startAt em ISO-8601 instant. */
    public record CreateRequest(
        @NotNull UUID professionalId,
        @NotNull UUID patientId,
        @NotBlank String appointmentType,
        @NotBlank String startAt,
        Integer durationMinutes,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/nutri/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
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
        long total = service.count(companyId, status, from, to, professionalId, patientId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, professionalId, patientId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/nutri/appointments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "appointment_not_found"));
    }

    @PostMapping("/api/nutri/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
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
            NutriAppointment created = service.create(companyId, req.professionalId(), req.patientId(),
                null, req.appointmentType(), startAt, req.durationMinutes(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        } catch (InactiveProfessionalException e) {
            return error(400, "Bad Request", "inactive_professional");
        } catch (InactivePatientException e) {
            return error(400, "Bad Request", "inactive_patient");
        } catch (InvalidTypeException e) {
            return error(400, "Bad Request", "invalid_type");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            NutriAppointmentConflict c = e.conflict();
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

    @PatchMapping("/api/nutri/appointments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
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
