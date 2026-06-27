package com.meada.profiles.lavanderia.services;

import java.time.Instant;
import java.util.UUID;

/**
 * Opção/modifier (Acabamento, Cuidado) de um serviço da lavanderia (camada 8.10). Cada linha é UMA
 * opção de UM grupo ({@code groupLabel}). DTO de saída. {@code priceDeltaCents} soma ao preço base no
 * pedido (recálculo no backend). Clone de
 * {@link com.meada.profiles.floricultura.catalog.FloriculturaCatalogOption}.
 */
public record LavanderiaServiceOption(
    UUID id,
    UUID serviceId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents,
    boolean available,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {
}
