package com.meada.profiles.barbearia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de agendamento (camada 8.1): o agendamento existente (agendado/confirmado) do MESMO
 * BARBEIRO cuja janela temporal sobrepõe o novo. Devolvido por
 * {@link BarberAppointmentRepository#findConflict} e exposto no 409 conflict_slot.
 * Clone de SalonAppointmentConflict.
 */
public record BarberAppointmentConflict(
    UUID existingId,
    String existingGuestName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
