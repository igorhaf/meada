package com.meada.profiles.fotografia.appointments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sessão/cobertura de fotografia (camada 8.16) — espelha fotografia_session_appointments. O CLIENTE é
 * o contact direto (snapshots customer_name/customer_phone — espelho salon/estetica, NÃO há
 * sub-entidade de paciente). Snapshots de profissional + pacote (name+price+duration+delivery_days).
 * {@code endAt} materializado no INSERT (start_at + duration_minutes); {@code deliveryDueDate}
 * materializada (date(start_at) + delivery_days). {@code deliveryLink} é gravado DEPOIS pelo estúdio
 * (nullable); a IA o entrega VERBATIM (escapada read-only). Status feminino.
 */
public record FotografiaSessionAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID packageId,
    String packageName,
    int priceCents,
    int durationMinutes,
    int deliveryDays,
    UUID contactId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    Instant startAt,
    Instant endAt,
    LocalDate deliveryDueDate,
    String deliveryLink,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
