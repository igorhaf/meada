package com.meada.whatsapp.profiles.otica.professionals;

import java.time.Instant;
import java.util.UUID;

/**
 * Optometrista do tenant otica (camada 8.12, FLUXO A) — espelha {@code otica_professionals}. O
 * conflito de agenda do exame é POR profissional. {@code active=false} retira da disponibilidade que
 * a IA enxerga. {@code notes} é administrativo. Espelho de
 * {@link com.meada.whatsapp.profiles.salon.professionals.SalonProfessional} sem {@code specialty}.
 */
public record OticaProfessional(
    UUID id,
    String name,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
