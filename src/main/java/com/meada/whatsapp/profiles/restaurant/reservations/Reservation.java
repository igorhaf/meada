package com.meada.whatsapp.profiles.restaurant.reservations;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de mesa (camada 7.3) — espelha table_reservations + o label da mesa (join).
 * {@code conversationId}/{@code contactId} são nullable (reserva criada manualmente pelo tenant não
 * tem WhatsApp). {@code guestName}/{@code guestPhone}/{@code durationMinutes} são snapshots do
 * momento da reserva. {@code endAt} = startAt + durationMinutes (materializado no insert).
 */
public record Reservation(
    UUID id,
    UUID tableId,
    String tableLabel,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    int numPeople,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
