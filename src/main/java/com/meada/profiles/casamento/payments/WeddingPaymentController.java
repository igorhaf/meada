package com.meada.profiles.casamento.payments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.casamento.CasamentoProfileGuard;
import com.meada.profiles.casamento.CasamentoProfileGuard.WrongProfileException;
import com.meada.profiles.casamento.payments.WeddingPaymentService.InvalidPaymentException;
import com.meada.profiles.casamento.payments.WeddingPaymentService.PaymentNotFoundException;
import com.meada.profiles.casamento.payments.WeddingPaymentService.ProposalLockedException;
import com.meada.profiles.casamento.payments.WeddingPaymentService.ProposalNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Plano de pagamento do contrato (onda 1, backlog #1). TENANT + perfil 'casamento' only. A equipe
 * monta sinal + parcelas e marca pago à mão (até o #50); com 'sinal' não pago a proposta não fecha
 * (gate no updateStatus da proposta → 409 deposit_required).
 */
@RestController
public class WeddingPaymentController {

    private final WeddingPaymentService service;
    private final CasamentoProfileGuard profileGuard;

    public WeddingPaymentController(WeddingPaymentService service, CasamentoProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record PaymentRequest(String kind, String label, String dueDate, Integer amountCents) {}

    public record PaymentUpdateRequest(String kind, String label, Boolean clearLabel,
                                       String dueDate, Integer amountCents) {}

    public record PaidRequest(boolean paid) {}

    @GetMapping("/api/casamento/proposals/{id}/payments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items", service.list(companyId, id)));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        }
    }

    @PostMapping("/api/casamento/proposals/{id}/payments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody PaymentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate dueDate;
        try {
            dueDate = req.dueDate() == null || req.dueDate().isBlank() ? null : LocalDate.parse(req.dueDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), id,
                req.kind(), req.label(), dueDate, req.amountCents()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (InvalidPaymentException e) {
            return error(400, "Bad Request", "invalid_payment");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/payments/{paymentId}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID paymentId, @RequestBody PaymentUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean labelProvided = req.label() != null || Boolean.TRUE.equals(req.clearLabel());
        String label = Boolean.TRUE.equals(req.clearLabel()) ? null : req.label();
        LocalDate dueDate;
        try {
            dueDate = req.dueDate() == null || req.dueDate().isBlank() ? null : LocalDate.parse(req.dueDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, paymentId,
                req.kind(), label, labelProvided, dueDate, req.amountCents()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (PaymentNotFoundException e) {
            return error(404, "Not Found", "payment_not_found");
        } catch (InvalidPaymentException e) {
            return error(400, "Bad Request", "invalid_payment");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/payments/{paymentId}/paid")
    public ResponseEntity<Object> setPaid(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID paymentId, @RequestBody PaidRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.setPaid(companyId, user.userId(), id, paymentId, req.paid()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (PaymentNotFoundException e) {
            return error(404, "Not Found", "payment_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/casamento/proposals/{id}/payments/{paymentId}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID paymentId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id, paymentId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (PaymentNotFoundException e) {
            return error(404, "Not Found", "payment_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }
}
