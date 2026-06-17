package com.meada.whatsapp.profiles.salon.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento de salão (camada 7.5) — espelha salon_appointments. {@code professionalName}/
 * {@code serviceName}/{@code priceCents}/{@code durationMinutes} são SNAPSHOTS do momento.
 * {@code conversationId}/{@code contactId} nullable (agendamento manual sem WhatsApp). {@code notes}
 * é administrativo (LGPD).
 */
public record SalonAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID serviceId,
    String serviceName,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    Integer priceCents,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
