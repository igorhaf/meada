package com.meada.profiles.oficina.orders;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.oficina.OficinaProfileGuard;
import com.meada.profiles.oficina.OficinaProfileGuard.WrongProfileException;
import com.meada.profiles.oficina.orders.ServiceOrderService.EmptyBudgetException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InactiveMechanicException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InactiveVehicleException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InvalidKindException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InvalidStatusException;
import com.meada.profiles.oficina.orders.ServiceOrderService.InvalidStatusTransitionException;
import com.meada.profiles.oficina.orders.ServiceOrderService.ItemNotFoundException;
import com.meada.profiles.oficina.orders.ServiceOrderService.MechanicNotFoundException;
import com.meada.profiles.oficina.orders.ServiceOrderService.OrderLockedException;
import com.meada.profiles.oficina.orders.ServiceOrderService.OrderNotFoundException;
import com.meada.profiles.oficina.orders.ServiceOrderService.VehicleNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Ordens de serviço do tenant oficina (camada 7.9). TENANT + perfil 'oficina' only. */
@RestController
public class ServiceOrderController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ServiceOrderService service;
    private final OficinaProfileGuard profileGuard;

    public ServiceOrderController(ServiceOrderService service, OficinaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record OpenRequest(
        @NotNull UUID vehicleId,
        UUID mechanicId,
        @NotBlank String complaint,
        String diagnosis,
        String expectedDelivery,   // ISO date yyyy-MM-dd
        String notes) {}

    public record UpdateFieldsRequest(
        String diagnosis,
        UUID mechanicId,
        Boolean clearMechanic,
        String expectedDelivery,
        Boolean clearExpected,
        String notes) {}

    public record ItemRequest(
        @NotBlank String kind,
        @NotBlank @Size(max = 200) String description,
        @Min(1) int quantity,
        @Min(0) int unitPriceCents) {}

    public record ItemUpdateRequest(String kind, String description, Integer quantity, Integer unitPriceCents) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/oficina/orders")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID mechanicId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
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
        long total = service.count(companyId, status, mechanicId, vehicleId, contactId, from, to);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, mechanicId, vehicleId, contactId, from, to, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/oficina/orders/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "order_not_found"));
    }

    @PostMapping("/api/oficina/orders")
    public ResponseEntity<Object> open(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody OpenRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate expected;
        try {
            expected = req.expectedDelivery() == null || req.expectedDelivery().isBlank()
                ? null : LocalDate.parse(req.expectedDelivery());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            ServiceOrder created = service.open(companyId, req.vehicleId(), req.mechanicId(), null,
                req.complaint(), req.diagnosis(), expected, req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (VehicleNotFoundException e) {
            return error(404, "Not Found", "vehicle_not_found");
        } catch (InactiveVehicleException e) {
            return error(400, "Bad Request", "inactive_vehicle");
        } catch (MechanicNotFoundException e) {
            return error(404, "Not Found", "mechanic_not_found");
        } catch (InactiveMechanicException e) {
            return error(400, "Bad Request", "inactive_mechanic");
        }
    }

    @PatchMapping("/api/oficina/orders/{id}")
    public ResponseEntity<Object> updateFields(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateFieldsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean mechanicProvided = req.mechanicId() != null || Boolean.TRUE.equals(req.clearMechanic());
        UUID mechanicId = Boolean.TRUE.equals(req.clearMechanic()) ? null : req.mechanicId();
        boolean expectedProvided = req.expectedDelivery() != null || Boolean.TRUE.equals(req.clearExpected());
        LocalDate expected;
        try {
            expected = Boolean.TRUE.equals(req.clearExpected()) || req.expectedDelivery() == null || req.expectedDelivery().isBlank()
                ? null : LocalDate.parse(req.expectedDelivery());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.updateFields(companyId, id, req.diagnosis(), mechanicId,
                mechanicProvided, expected, expectedProvided, req.notes()));
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (MechanicNotFoundException e) {
            return error(404, "Not Found", "mechanic_not_found");
        } catch (InactiveMechanicException e) {
            return error(400, "Bad Request", "inactive_mechanic");
        }
    }

    @PostMapping("/api/oficina/orders/{id}/items")
    public ResponseEntity<Object> addItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            OsItem item = service.addItem(companyId, id, req.kind(), req.description(), req.quantity(), req.unitPriceCents());
            return ResponseEntity.status(201).body(item);
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (OrderLockedException e) {
            return error(409, "Conflict", "order_locked");
        } catch (InvalidKindException e) {
            return error(400, "Bad Request", "invalid_kind");
        }
    }

    @PatchMapping("/api/oficina/orders/{id}/items/{itemId}")
    public ResponseEntity<Object> updateItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateItem(companyId, id, itemId, req.kind(), req.description(),
                req.quantity(), req.unitPriceCents()));
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (ItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        } catch (OrderLockedException e) {
            return error(409, "Conflict", "order_locked");
        } catch (InvalidKindException e) {
            return error(400, "Bad Request", "invalid_kind");
        }
    }

    @DeleteMapping("/api/oficina/orders/{id}/items/{itemId}")
    public ResponseEntity<Object> deleteItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteItem(companyId, id, itemId);
            return ResponseEntity.noContent().build();
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (ItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        } catch (OrderLockedException e) {
            return error(409, "Conflict", "order_locked");
        }
    }

    @PatchMapping("/api/oficina/orders/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (EmptyBudgetException e) {
            return error(400, "Bad Request", "empty_budget");
        } catch (OrderNotFoundException e) {
            return error(404, "Not Found", "order_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
