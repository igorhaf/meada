package com.meada.whatsapp.profiles.eventos.planners;

import java.time.Instant;
import java.util.UUID;

/**
 * Cerimonialista/responsável do tenant eventos (camada 8.2) — espelha event_planners.
 * {@code specialty} texto livre ("casamentos", "corporativo"). Catálogo SIMPLES, sem agenda —
 * atribuição opcional na proposta. Espelho do OsMechanic.
 */
public record EventPlanner(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
