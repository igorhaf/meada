package com.meada.profiles.salon.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de agendamento (camada 7.5): o agendamento existente (agendado/confirmado) do MESMO
 * PROFISSIONAL cuja janela temporal sobrepõe o novo. Devolvido por
 * {@link SalonAppointmentRepository#findConflict} e exposto no 409 conflict_slot.
 */
public record SalonAppointmentConflict(
    UUID existingId,
    String existingGuestName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
