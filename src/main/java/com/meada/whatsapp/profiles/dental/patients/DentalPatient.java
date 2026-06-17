package com.meada.whatsapp.profiles.dental.patients;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paciente da clínica (camada 7.4) — espelha dental_patients. {@code contactId} (nullable) liga ao
 * contato WhatsApp; a IA resolve contact → patient. {@code notes} é ADMINISTRATIVO (preferências de
 * horário/contato), NÃO clínico (LGPD — ver CLAUDE.md). {@code birthDate} opcional (idade calculada
 * no frontend).
 */
public record DentalPatient(
    UUID id,
    String name,
    String email,
    String phone,
    String document,
    LocalDate birthDate,
    UUID contactId,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
