package com.meada.profiles.cursos.payments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.cursos.CursosProfileGuard;
import com.meada.profiles.cursos.CursosProfileGuard.WrongProfileException;
import com.meada.profiles.cursos.enrollments.CursosEnrollment;
import com.meada.profiles.cursos.payments.CursosPaymentService.DuplicatePaymentException;
import com.meada.profiles.cursos.payments.CursosPaymentService.EnrollmentNotActiveException;
import com.meada.profiles.cursos.payments.CursosPaymentService.EnrollmentNotFoundException;
import com.meada.profiles.cursos.payments.CursosPaymentService.PaymentNotFoundException;
import com.meada.profiles.cursos.payments.CursosPaymentService.PaymentSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pagamentos de uma matrícula (camada 8.20 / perfil cursos). TENANT + perfil 'cursos' only. Rotas sob
 * /api/cursos/enrollments/{id}/payments. Clone do AcademiaPaymentController (camada 7.7).
 */
@RestController
public class CursosPaymentController {

    private final CursosPaymentService service;
    private final CursosProfileGuard profileGuard;

    public CursosPaymentController(CursosPaymentService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do registro. referenceMonth em "YYYY-MM-DD" (normalizado pro dia 01). */
    public record RecordPaymentRequest(
        @NotBlank String referenceMonth,
        @PositiveOrZero int amountCents,
        String method,
        String notes) {}

    @GetMapping("/api/cursos/enrollments/{id}/payments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        CursosEnrollment e = service.getEnrollment(companyId, id).orElse(null);
        if (e == null) {
            return error(404, "Not Found", "enrollment_not_found");
        }
        PaymentSummary summary = service.summary(companyId, id, e.startDate());
        Map<String, Object> summaryMap = new HashMap<>();
        summaryMap.put("lastPaidMonth", summary.lastPaidMonth() == null ? null : summary.lastPaidMonth().toString());
        summaryMap.put("monthsOpen", summary.monthsOpen());
        summaryMap.put("totalPayments", summary.totalPayments());
        Map<String, Object> body = new HashMap<>();
        body.put("items", service.listByEnrollment(companyId, id));
        body.put("summary", summaryMap);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/cursos/enrollments/{id}/payments")
    public ResponseEntity<Object> record(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate refMonth;
        try {
            refMonth = LocalDate.parse(req.referenceMonth());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            CursosPayment created = service.record(companyId, user.userId(), id, refMonth,
                req.amountCents(), req.method(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (EnrollmentNotFoundException e) {
            return error(404, "Not Found", "enrollment_not_found");
        } catch (EnrollmentNotActiveException e) {
            return error(400, "Bad Request", "enrollment_not_active");
        } catch (DuplicatePaymentException e) {
            return error(409, "Conflict", "duplicate_payment");
        }
    }

    @DeleteMapping("/api/cursos/enrollments/{id}/payments/{paymentId}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), paymentId);
            return ResponseEntity.noContent().build();
        } catch (PaymentNotFoundException e) {
            return error(404, "Not Found", "payment_not_found");
        }
    }
}
