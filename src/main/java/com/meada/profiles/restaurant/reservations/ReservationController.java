package com.meada.profiles.restaurant.reservations;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.restaurant.RestaurantProfileGuard;
import com.meada.profiles.restaurant.RestaurantProfileGuard.WrongProfileException;
import com.meada.profiles.restaurant.reservations.ReservationService.ConflictException;
import com.meada.profiles.restaurant.reservations.ReservationService.InvalidStatusException;
import com.meada.profiles.restaurant.reservations.ReservationService.InvalidStatusTransitionException;
import com.meada.profiles.restaurant.reservations.ReservationService.OutsideHoursException;
import com.meada.profiles.restaurant.reservations.ReservationService.ReservationNotFoundException;
import com.meada.profiles.restaurant.reservations.ReservationService.TableNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reservas do tenant restaurant (camada 7.3). TENANT + perfil 'restaurant' only. READ (lista/
 * detalhe com filtros de agenda) + POST manual (sem WhatsApp) + transição de status (Kanban/agenda).
 * NÃO há DELETE — reserva mantém histórico; "remover" é status=cancelada.
 */
@RestController
public class ReservationController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ReservationService service;
    private final RestaurantProfileGuard profileGuard;

    public ReservationController(ReservationService service, RestaurantProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). startAt em ISO-8601 instant ("2026-06-20T20:00:00-03:00"). */
    public record CreateReservationRequest(
        @NotNull UUID tableId,
        @NotBlank @Size(max = 120) String guestName,
        @Size(max = 40) String guestPhone,
        @NotBlank String startAt,
        @Min(1) @Max(50) int numPeople,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    // ---- GET lista (filtros: status, dateFrom, dateTo, paginação) -----------
    @GetMapping("/api/restaurant/reservations")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
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
        long total = service.count(companyId, status, from, to);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/restaurant/reservations/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "reservation_not_found"));
    }

    // ---- POST cria (manual, sem WhatsApp) -----------------------------------
    @PostMapping("/api/restaurant/reservations")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateReservationRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        Instant startAt;
        try {
            startAt = Instant.parse(req.startAt());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            Reservation created = service.create(companyId, req.tableId(), null, null,
                req.guestName(), req.guestPhone(), startAt, req.numPeople(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (TableNotFoundException e) {
            return error(404, "Not Found", "table_not_found");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            // Expõe os detalhes do conflito (mesa/horário ocupados por quem) pro alerta do frontend.
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            ReservationConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "reservationId", c.existingReservationId().toString(),
                    "guestName", c.existingGuestName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    // ---- PATCH status -------------------------------------------------------
    @PatchMapping("/api/restaurant/reservations/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (ReservationNotFoundException e) {
            return error(404, "Not Found", "reservation_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
