package com.meada.whatsapp.profiles.concessionaria.testdrives;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflito de test-drive (camada 8.17): o test-drive existente (agendado/confirmado) do MESMO
 * VENDEDOR cuja janela temporal sobrepõe o novo. Conflito é POR salesperson_id (paralelismo entre
 * vendedores). Devolvido por {@link ConcessionariaTestDriveRepository#findConflict} e exposto no 409
 * conflict_slot.
 */
public record TestDriveConflict(
    UUID existingId,
    String existingCustomerName,
    Instant existingStartAt,
    Instant existingEndAt) {
}
