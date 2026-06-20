package com.meada.whatsapp.profiles.barbearia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento de barbearia (camada 8.1) — espelha barber_appointments. {@code barberName}/
 * {@code serviceName}/{@code priceCents}/{@code durationMinutes} são SNAPSHOTS do momento.
 * {@code conversationId}/{@code contactId} nullable (agendamento manual sem WhatsApp). {@code notes}
 * é administrativo (LGPD). Clone 1:1 de SalonAppointment.
 */
public record BarberAppointment(
    UUID id,
    UUID barberId,
    String barberName,
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
