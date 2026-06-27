package com.meada.profiles.salon.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Profissional do salão (camada 7.5) — espelha salon_professionals. {@code specialty} é texto livre
 * ("Cabeleireira", "Manicure"). {@code active=false} retira da disponibilidade que a IA enxerga. O
 * conflito de agenda é por profissional.
 */
public record SalonProfessional(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
