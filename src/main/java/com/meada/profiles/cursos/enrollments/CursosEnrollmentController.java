package com.meada.profiles.cursos.enrollments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.cursos.CursosProfileGuard;
import com.meada.profiles.cursos.CursosProfileGuard.WrongProfileException;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.EnrollmentNotFoundException;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.InvalidStatusException;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.InvalidStatusTransitionException;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.ProgressSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Matrículas do tenant cursos (camada 8.20 / perfil cursos). TENANT + perfil 'cursos' only. READ +
 * PATCH status. NÃO há POST (a matrícula vem da IA, via {@code <matricula_curso>}) nem DELETE
 * (histórico; "remover" = cancelar). O detalhe inclui o progresso (módulos concluídos/total + próximo
 * módulo). Análogo ao AcademiaMembershipController (camada 7.7).
 */
@RestController
public class CursosEnrollmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CursosEnrollmentService service;
    private final CursosProfileGuard profileGuard;

    public CursosEnrollmentController(CursosEnrollmentService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/cursos/enrollments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, courseId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, courseId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/cursos/enrollments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(e -> {
                ProgressSummary p = service.progress(e.id());
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("enrollment", e);
                Map<String, Object> progress = new LinkedHashMap<>();
                progress.put("doneCount", p.doneCount());
                progress.put("totalModules", p.totalModules());
                progress.put("nextModuleTitle", p.nextModuleTitle());
                body.put("progress", progress);
                return ResponseEntity.ok(body);
            })
            .orElseGet(() -> error(404, "Not Found", "enrollment_not_found"));
    }

    @PatchMapping("/api/cursos/enrollments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (EnrollmentNotFoundException e) {
            return error(404, "Not Found", "enrollment_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
