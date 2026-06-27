package com.meada.profiles.oficina.mechanics;

import java.time.Instant;
import java.util.UUID;

/**
 * Mecânico/responsável da oficina (camada 7.9) — espelha os_mechanics. {@code specialty} texto livre
 * ("motor/suspensão", "elétrica/ar"). Catálogo SIMPLES, sem agenda — atribuição opcional na OS.
 */
public record OsMechanic(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
