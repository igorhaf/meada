package com.meada.profiles.barbearia.services;

import java.time.Instant;
import java.util.UUID;

/**
 * Serviço oferecido pela barbearia (camada 8.1) — espelha barber_services. {@code durationMinutes}
 * varia por serviço (entra como snapshot no agendamento/ticket). {@code priceCents} é nullable
 * (pode não expor preço). Espelho de SalonOffering.
 */
public record BarberService(
    UUID id,
    String name,
    String category,
    int durationMinutes,
    Integer priceCents,
    boolean active,
    String description,
    Instant createdAt,
    Instant updatedAt) {
}
