package com.meada.profiles.barbearia.barbers;

import java.time.Instant;
import java.util.UUID;

/**
 * Barbeiro (camada 8.1) — espelha barber_barbers. {@code specialty} é texto livre, opcional
 * ("corte/barba", "degradê"). {@code active=false} retira da disponibilidade que a IA enxerga. O
 * conflito de agenda é por barbeiro.
 */
public record BarberBarber(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
