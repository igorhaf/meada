package com.meada.whatsapp.profiles.nutri.plans;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.nutri.NutriProfileGuard;
import com.meada.whatsapp.profiles.nutri.NutriProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.nutri.plans.NutriPlanService.PatientNotFoundException;
import com.meada.whatsapp.profiles.nutri.plans.NutriPlanService.PlanNotFoundException;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Planos alimentares do tenant nutri (camada 8.0) — sub-entidade do paciente. TENANT + perfil 'nutri'
 * only. É o EDITOR do plano: o body é escrito SÓ pelo painel (a IA nunca escreve, só LÊ na entrega).
 */
@RestController
public class NutriPlanController {

    private final NutriPlanService service;
    private final NutriProfileGuard profileGuard;

    public NutriPlanController(NutriPlanService service, NutriProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotNull UUID patientId,
        UUID professionalId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String body,
        String startsOn,
        String endsOn,
        Boolean active,
        String notes) {}

    public record UpdateRequest(
        String title,
        String body,
        UUID professionalId,
        Boolean clearProfessional,
        String startsOn,
        Boolean clearStarts,
        String endsOn,
        Boolean clearEnds,
        String notes) {}

    @GetMapping("/api/nutri/plans")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam UUID patientId,
            @RequestParam(required = false) String status) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.listByPatient(companyId, patientId, status)));
    }

    @GetMapping("/api/nutri/plans/active")
    public ResponseEntity<Object> active(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam UUID patientId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.getActiveByPatient(companyId, patientId)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "no_active_plan"));
    }

    @GetMapping("/api/nutri/plans/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "plan_not_found"));
    }

    @PostMapping("/api/nutri/plans")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean active = req.active() == null ? true : req.active();
        LocalDate startsOn;
        LocalDate endsOn;
        try {
            startsOn = req.startsOn() == null || req.startsOn().isBlank() ? null : LocalDate.parse(req.startsOn());
            endsOn = req.endsOn() == null || req.endsOn().isBlank() ? null : LocalDate.parse(req.endsOn());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            NutriPlan created = service.create(companyId, user.userId(), req.patientId(), req.professionalId(),
                req.title(), req.body(), startsOn, endsOn, active, req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        }
    }

    @PatchMapping("/api/nutri/plans/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean professionalProvided = req.professionalId() != null || Boolean.TRUE.equals(req.clearProfessional());
        UUID professionalId = Boolean.TRUE.equals(req.clearProfessional()) ? null : req.professionalId();
        boolean startsProvided = req.startsOn() != null || Boolean.TRUE.equals(req.clearStarts());
        boolean endsProvided = req.endsOn() != null || Boolean.TRUE.equals(req.clearEnds());
        LocalDate startsOn;
        LocalDate endsOn;
        try {
            startsOn = Boolean.TRUE.equals(req.clearStarts()) || req.startsOn() == null
                ? null : LocalDate.parse(req.startsOn());
            endsOn = Boolean.TRUE.equals(req.clearEnds()) || req.endsOn() == null
                ? null : LocalDate.parse(req.endsOn());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.title(), req.body(),
                professionalId, professionalProvided, startsOn, startsProvided, endsOn, endsProvided, req.notes()));
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        }
    }

    @PatchMapping("/api/nutri/plans/{id}/archive")
    public ResponseEntity<Object> archive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.archive(companyId, user.userId(), id));
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        }
    }

    @PatchMapping("/api/nutri/plans/{id}/activate")
    public ResponseEntity<Object> activate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireNutri(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.activate(companyId, user.userId(), id));
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        }
    }
}
