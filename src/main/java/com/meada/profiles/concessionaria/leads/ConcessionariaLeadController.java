package com.meada.profiles.concessionaria.leads;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.InvalidPaymentConditionException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.InvalidStatusException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.InvalidStatusTransitionException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.LeadNotFoundException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.SalespersonNotFoundException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.VehicleNotAvailableException;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService.VehicleNotFoundException;
import jakarta.validation.Valid;
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

import java.util.Map;
import java.util.UUID;

/**
 * Leads de compra do tenant concessionaria (camada 8.17). TENANT + perfil 'concessionaria' only. READ
 * (lista/detalhe com filtros) + POST manual (sem WhatsApp) + transição de status + atribuição de
 * vendedor. NÃO há DELETE — lead mantém histórico; "perder" é status=perdido.
 */
@RestController
public class ConcessionariaLeadController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ConcessionariaLeadService service;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaLeadController(ConcessionariaLeadService service,
                                        ConcessionariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). paymentCondition avista|financiado (default avista). */
    public record CreateLeadRequest(
        @NotNull UUID vehicleId,
        String paymentCondition,
        String notes) {}

    public record StatusRequest(String newStatus, String lostReason) {}

    public record AssignRequest(UUID salespersonId) {}

    @GetMapping("/api/concessionaria/leads")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID salespersonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, vehicleId, contactId, salespersonId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, vehicleId, contactId, salespersonId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/concessionaria/leads/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "lead_not_found"));
    }

    @PostMapping("/api/concessionaria/leads")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateLeadRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            ConcessionariaLead created = service.createLead(companyId,
                new LeadInput(req.vehicleId(), null, null, req.paymentCondition(), req.notes()));
            return ResponseEntity.status(201).body(created);
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (VehicleNotAvailableException e) {
            return error(422, "Unprocessable Entity", "vehicle_not_available");
        } catch (InvalidPaymentConditionException e) {
            return error(400, "Bad Request", "invalid_payment_condition");
        }
    }

    @PatchMapping("/api/concessionaria/leads/{id}/status")
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
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus(), req.lostReason()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (LeadNotFoundException e) {
            return error(404, "Not Found", "lead_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    @PatchMapping("/api/concessionaria/leads/{id}/assign")
    public ResponseEntity<Object> assign(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody AssignRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.assignSalesperson(companyId, id, req.salespersonId()));
        } catch (LeadNotFoundException e) {
            return error(404, "Not Found", "lead_not_found");
        } catch (SalespersonNotFoundException e) {
            return error(404, "Not Found", "salesperson_not_found");
        }
    }
}
