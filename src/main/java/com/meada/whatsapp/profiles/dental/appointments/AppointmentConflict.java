package com.meada.whatsapp.profiles.dental.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de consulta (camada 7.4): a consulta existente (agendada/confirmada) cuja janela
 * temporal sobrepõe a nova consulta no MESMO consultório (company — 1 dentista por tenant nesta SM).
 * Devolvido por {@link DentalAppointmentRepository#findConflict} e exposto no 409 conflict_slot.
 */
public record AppointmentConflict(
    UUID existingId,
    String existingPatientName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
