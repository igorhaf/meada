package com.meada.profiles.salon.offerings;

import java.time.Instant;
import java.util.UUID;

/**
 * Serviço oferecido pelo salão (camada 7.5) — espelha salon_offerings. Nomeado "Offering" (não
 * "Service") para não colidir com o Spring {@code SalonOfferingService}; a UI/rota chama "serviços".
 * {@code durationMinutes} varia por serviço (entra como snapshot no agendamento). {@code priceCents}
 * é nullable (salão pode não expor preço).
 */
public record SalonOffering(
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
