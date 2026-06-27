package com.meada.profiles.fotografia.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de sessão (camada 8.16): a sessão existente (agendada/confirmada) do MESMO PROFISSIONAL
 * cuja janela temporal sobrepõe a nova. Exposto no 409 conflict_slot. Espelho do
 * DermatologiaAppointmentConflict.
 */
public record FotografiaAppointmentConflict(
    UUID existingId,
    String existingCustomerName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
