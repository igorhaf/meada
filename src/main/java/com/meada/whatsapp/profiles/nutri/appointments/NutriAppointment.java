package com.meada.whatsapp.profiles.nutri.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Consulta de nutrição (camada 8.0) — espelha nutri_appointments. Snapshots de paciente/profissional.
 * {@code appointmentType} primeira|retorno|avaliacao. {@code endAt} materializado no INSERT.
 */
public record NutriAppointment(
    UUID id,
    UUID professionalId,
    String professionalName,
    UUID patientId,
    String patientName,
    String patientPhone,
    UUID contactId,
    UUID conversationId,
    String appointmentType,
    int durationMinutes,
    Instant startAt,
    Instant endAt,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
