package com.meada.profiles.restaurant.reservations;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de reserva (camada 7.3): a reserva existente (pendente/confirmada) cuja janela temporal
 * sobrepõe a nova reserva na MESMA mesa. Devolvido por
 * {@link ReservationRepository#findConflict} e exposto no 409 conflict_slot (o tenant vê quem/quando
 * ocupa o horário).
 */
public record ReservationConflict(
    UUID existingReservationId,
    String existingGuestName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
