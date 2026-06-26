package com.meada.whatsapp.profiles.concessionaria.testdrives;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.whatsapp.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.ConflictException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.InactiveSalespersonException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.InvalidStatusException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.OutsideHoursException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.SalespersonNotFoundException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.TestDriveNotFoundException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.VehicleNotAvailableException;
import com.meada.whatsapp.profiles.concessionaria.testdrives.ConcessionariaTestDriveService.VehicleNotFoundException;
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

/**
 * Test-drives do tenant concessionaria (camada 8.17). TENANT + perfil 'concessionaria' only. READ
 * (lista/detalhe com filtros de agenda) + POST manual (sem WhatsApp) + transição de status. NÃO há
 * DELETE — test-drive mantém histórico; "remover" é status=cancelado.
 */
@RestController
public class ConcessionariaTestDriveController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ConcessionariaTestDriveService service;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaTestDriveController(ConcessionariaTestDriveService service,
                                             ConcessionariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). startAt em ISO-8601 instant. */
    public record CreateTestDriveRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID salespersonId,
        @NotBlank String startAt,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/concessionaria/testdrives")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID salespersonId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
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
        long total = service.count(companyId, status, from, to, salespersonId, vehicleId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, salespersonId, vehicleId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/concessionaria/testdrives/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "testdrive_not_found"));
    }

    @PostMapping("/api/concessionaria/testdrives")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateTestDriveRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
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
            ConcessionariaTestDrive created = service.createTestDrive(companyId,
                new TestDriveInput(req.vehicleId(), req.salespersonId(), null, null, startAt, req.notes()));
            return ResponseEntity.status(201).body(created);
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (SalespersonNotFoundException e) {
            return error(404, "Not Found", "salesperson_not_found");
        } catch (VehicleNotAvailableException e) {
            return error(422, "Unprocessable Entity", "vehicle_not_available");
        } catch (InactiveSalespersonException e) {
            return error(422, "Unprocessable Entity", "inactive_salesperson");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            TestDriveConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "testDriveId", c.existingId().toString(),
                    "customerName", c.existingCustomerName() == null ? "" : c.existingCustomerName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/concessionaria/testdrives/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (TestDriveNotFoundException e) {
            return error(404, "Not Found", "testdrive_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
