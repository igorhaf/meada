package com.meada.profiles.dental.patients;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.dental.DentalProfileGuard;
import com.meada.profiles.dental.DentalProfileGuard.WrongProfileException;
import com.meada.profiles.dental.patients.DentalPatientService.PatientInUseException;
import com.meada.profiles.dental.patients.DentalPatientService.PatientNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Pacientes do tenant dental (camada 7.4). TENANT + perfil 'dental' only —
 * {@link DentalProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob
 * {@code /api/dental/**} (rota de tenant fora do prefixo /admin, com CORS já liberado).
 */
@RestController
public class DentalPatientController {

    private final DentalPatientService service;
    private final DentalProfileGuard profileGuard;

    public DentalPatientController(DentalPatientService service, DentalProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação. birthDate em "YYYY-MM-DD" (opcional). */
    public record CreatePatientRequest(
        @NotBlank @Size(max = 200) String name,
        String email,
        String phone,
        String document,
        String birthDate,
        String notes) {}

    /** Body de edição (PATCH parcial; todos opcionais). */
    public record UpdatePatientRequest(
        @Size(max = 200) String name,
        String email,
        String phone,
        String document,
        String birthDate,
        String notes) {}

    private static LocalDate parseBirth(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s);   // DateTimeException tratada pelo caller.
    }

    // ---- GET lista (busca opcional) -----------------------------------------
    @GetMapping("/api/dental/patients")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, search)));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/dental/patients/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "patient_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/dental/patients")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreatePatientRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate birth;
        try {
            birth = parseBirth(req.birthDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        DentalPatient created = service.create(companyId, user.userId(), req.name(), req.email(),
            req.phone(), req.document(), birth, null, req.notes());
        return ResponseEntity.status(201).body(created);
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/dental/patients/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate birth;
        try {
            birth = parseBirth(req.birthDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.email(), req.phone(), req.document(), birth, null, req.notes()));
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/dental/patients/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDental(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        } catch (PatientInUseException e) {
            return error(409, "Conflict", "patient_in_use");
        }
    }
}
