package com.meada.whatsapp.profiles.cursos.courses;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.cursos.CursosProfileGuard;
import com.meada.whatsapp.profiles.cursos.CursosProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.cursos.courses.CursosCourseService.CourseInUseException;
import com.meada.whatsapp.profiles.cursos.courses.CursosCourseService.CourseNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Cursos do tenant cursos (camada 8.20 / perfil cursos). TENANT + perfil 'cursos' only. Clone do
 * AcademiaPlanController (camada 7.7).
 */
@RestController
public class CursosCourseController {

    private final CursosCourseService service;
    private final CursosProfileGuard profileGuard;

    public CursosCourseController(CursosCourseService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateCourseRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 100) String category,
        @PositiveOrZero int monthlyCents,
        String description) {}

    public record UpdateCourseRequest(
        @Size(max = 200) String title,
        @Size(max = 100) String category,
        @PositiveOrZero Integer monthlyCents,
        String description,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/cursos/courses")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/cursos/courses/{id}")
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
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "course_not_found"));
    }

    @PostMapping("/api/cursos/courses")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateCourseRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        CursosCourse created = service.create(companyId, user.userId(), req.title(), req.category(),
            req.monthlyCents(), req.description());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/cursos/courses/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.title(),
                req.category(), req.monthlyCents(), req.description(), req.active()));
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        }
    }

    @PatchMapping("/api/cursos/courses/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        }
    }

    @DeleteMapping("/api/cursos/courses/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        } catch (CourseInUseException e) {
            return error(409, "Conflict", "course_in_use");
        }
    }
}
