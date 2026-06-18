package com.meada.whatsapp.profiles.nutri.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de consulta (camada 8.0): a consulta existente (agendado/confirmado) do MESMO
 * PROFISSIONAL cuja janela temporal sobrepõe a nova. Exposto no 409 conflict_slot.
 */
public record NutriAppointmentConflict(
    UUID existingId,
    String existingPatientName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
