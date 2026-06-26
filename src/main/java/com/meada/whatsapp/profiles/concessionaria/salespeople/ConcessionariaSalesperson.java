package com.meada.whatsapp.profiles.concessionaria.salespeople;

import java.time.Instant;
import java.util.UUID;

/**
 * Vendedor da concessionaria (camada 8.17) — espelha concessionaria_salespeople. Catálogo SIMPLES
 * (~salon_professionals/os_mechanics). O conflito de agenda do test-drive é POR salesperson_id
 * (paralelismo entre vendedores). {@code phone} é texto livre opcional.
 */
public record ConcessionariaSalesperson(
    UUID id,
    String name,
    String phone,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
