package com.meada.whatsapp.profiles.otica.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Opção/modifier de um item do catálogo otica (camada 8.12, FLUXO B). Cada linha é UMA opção de UM
 * grupo ({@code groupLabel}: "Tipo de lente", "Tratamento"). DTO de saída. {@code priceDeltaCents}
 * soma ao preço base no pedido (recálculo no backend). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogOption}.
 */
public record OticaCatalogOption(
    UUID id,
    UUID catalogItemId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents,
    boolean available,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {
}
