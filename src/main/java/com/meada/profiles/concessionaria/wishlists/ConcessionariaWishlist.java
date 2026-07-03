package com.meada.profiles.concessionaria.wishlists;

import java.time.Instant;
import java.util.UUID;

/**
 * Desejo de carro do tenant concessionaria (onda 1, backlog #1) — espelha concessionaria_wishlists.
 * Critérios LIVRES (brand/model por ILIKE; teto de preço; ano mínimo — pelo menos brand OU model).
 * ONE-SHOT: ao casar com um veículo disponível, o contato é avisado e o desejo desativa
 * ({@code notifiedAt} + {@code notifiedVehicleId}).
 */
public record ConcessionariaWishlist(
    UUID id,
    UUID companyId,
    UUID contactId,
    UUID conversationId,
    String contactName,
    String brand,
    String model,
    Integer maxPriceCents,
    Integer minYear,
    String notes,
    boolean active,
    Instant notifiedAt,
    UUID notifiedVehicleId,
    Instant createdAt,
    Instant updatedAt) {
}
