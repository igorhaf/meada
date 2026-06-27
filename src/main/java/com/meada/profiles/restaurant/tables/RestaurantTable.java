package com.meada.profiles.restaurant.tables;

import java.time.Instant;
import java.util.UUID;

/**
 * Mesa do restaurante (camada 7.3) — espelha restaurant_tables. {@code label} é o identificador
 * humano ("Mesa 1", "Varanda 3"), UNIQUE por company; {@code capacity} é quantas pessoas cabem;
 * {@code available=false} retira a mesa da disponibilidade que a IA enxerga.
 */
public record RestaurantTable(
    UUID id,
    String label,
    int capacity,
    boolean available,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
