package com.meada.whatsapp.profiles.cursos.modules;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.cursos.CursosProfileGuard;
import com.meada.whatsapp.profiles.cursos.CursosProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.cursos.modules.CursosModuleService.CourseNotFoundException;
import com.meada.whatsapp.profiles.cursos.modules.CursosModuleService.DuplicatePositionException;
import com.meada.whatsapp.profiles.cursos.modules.CursosModuleService.ModuleNotFoundException;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Módulos de um curso (camada 8.20 / perfil cursos, ESCAPADA 1). TENANT + perfil 'cursos' only.
 * Rotas aninhadas sob /api/cursos/courses/{courseId}/modules (a trilha pertence ao curso). O update e
 * o delete usam o id do módulo direto (já escopado por company_id). Análogo ao AcademiaClassController
 * (camada 7.7), mas nested.
 */
@RestController
public class CursosModuleController {

    private final CursosModuleService service;
    private final CursosProfileGuard profileGuard;

    public CursosModuleController(CursosModuleService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateModuleRequest(
        @PositiveOrZero int position,
        @NotBlank @Size(max = 200) String title,
        String content) {}

    public record UpdateModuleRequest(
        @PositiveOrZero Integer position,
        @Size(max = 200) String title,
        String content) {}

    public record ReorderModulesRequest(java.util.List<UUID> moduleIds) {}

    @GetMapping("/api/cursos/courses/{courseId}/modules")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID courseId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items", service.listByCourse(companyId, courseId)));
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        }
    }

    @PostMapping("/api/cursos/courses/{courseId}/modules")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateModuleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            CursosModule created = service.create(companyId, user.userId(), courseId,
                req.position(), req.title(), req.content());
            return ResponseEntity.status(201).body(created);
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        } catch (DuplicatePositionException e) {
            return error(409, "Conflict", "duplicate_position");
        }
    }

    @PatchMapping("/api/cursos/courses/{courseId}/modules/reorder")
    public ResponseEntity<Object> reorder(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID courseId,
            @RequestBody ReorderModulesRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items",
                service.reorder(companyId, user.userId(), courseId,
                    req.moduleIds() == null ? java.util.List.of() : req.moduleIds())));
        } catch (CourseNotFoundException e) {
            return error(404, "Not Found", "course_not_found");
        }
    }

    @PatchMapping("/api/cursos/courses/{courseId}/modules/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID courseId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModuleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.position(), req.title(), req.content()));
        } catch (ModuleNotFoundException e) {
            return error(404, "Not Found", "module_not_found");
        } catch (DuplicatePositionException e) {
            return error(409, "Conflict", "duplicate_position");
        }
    }

    @DeleteMapping("/api/cursos/courses/{courseId}/modules/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID courseId,
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
        } catch (ModuleNotFoundException e) {
            return error(404, "Not Found", "module_not_found");
        }
    }
}
