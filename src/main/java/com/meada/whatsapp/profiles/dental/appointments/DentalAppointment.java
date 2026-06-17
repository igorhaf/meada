package com.meada.whatsapp.profiles.dental.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Consulta (camada 7.4) — espelha dental_appointments + o nome do paciente (join).
 * {@code conversationId} é nullable (consulta criada manualmente pelo tenant não tem WhatsApp).
 * {@code durationMinutes} é snapshot do config. {@code type} é texto livre administrativo
 * ("Limpeza", "Avaliação"…), NUNCA recomendação clínica. {@code notes} é ADMINISTRATIVO (LGPD).
 */
public record DentalAppointment(
    UUID id,
    UUID patientId,
    String patientName,
    UUID conversationId,
    Instant startAt,
    Instant endAt,
    int durationMinutes,
    String type,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
