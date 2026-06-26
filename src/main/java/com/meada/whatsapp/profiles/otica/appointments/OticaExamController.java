package com.meada.whatsapp.profiles.otica.appointments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.otica.OticaProfileGuard;
import com.meada.whatsapp.profiles.otica.OticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.otica.appointments.OticaExamService.ExamNotFoundException;
import com.meada.whatsapp.profiles.otica.appointments.OticaExamService.InvalidStatusException;
import com.meada.whatsapp.profiles.otica.appointments.OticaExamService.InvalidStatusTransitionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Exames de vista do tenant otica (camada 8.12, FLUXO A). TENANT + perfil 'otica' only. READ
 * (lista/detalhe com filtros de agenda) + transição de status. NÃO há POST — exames vêm da IA
 * (ExameOticaConfirmHandler); "remover" é status=cancelado. Espelho do DentalAppointmentController
 * (sem o POST manual).
 */
@RestController
public class OticaExamController {

    private static final int MAX_PAGE_SIZE = 200;

    private final OticaExamService service;
    private final OticaProfileGuard profileGuard;

    public OticaExamController(OticaExamService service, OticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record StatusRequest(String newStatus) {}

    // ---- GET lista (filtros: status, dateFrom, dateTo, professionalId, paginação) -----------
    @GetMapping("/api/otica/exams")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
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
        long total = service.count(companyId, status, from, to, professionalId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, professionalId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/otica/exams/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "exam_not_found"));
    }

    // ---- PATCH status -------------------------------------------------------
    @PatchMapping("/api/otica/exams/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (ExamNotFoundException e) {
            return error(404, "Not Found", "exam_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
