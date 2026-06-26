package com.meada.whatsapp.profiles.otica.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de exame (camada 8.12, perfil otica FLUXO A): o exame existente (agendado/confirmado) do
 * MESMO PROFISSIONAL cuja janela temporal sobrepõe o novo exame. Devolvido por
 * {@link OticaExamRepository#findConflict} e exposto no 409 conflict_slot. Espelho do
 * {@code FotografiaAppointmentConflict} (conflito por professional_id).
 */
public record OticaExamConflict(
    UUID existingId,
    String existingCustomerName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
