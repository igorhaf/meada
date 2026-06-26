package com.meada.whatsapp.profiles.otica.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item do catálogo otica (camada 8.12, FLUXO B). DTO de saída. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogItem} + a ESCAPADA da
 * ótica: {@code madeToOrder} (armação/lente sob encomenda → exige ready_date que respeite o lead) +
 * {@code leadTimeDays} (override nullable do default da config). {@code priceCents} é o preço BASE
 * (sem opções); {@code category} é o id estável de
 * {@link com.meada.whatsapp.profiles.otica.OticaCategory}.
 */
public record OticaCatalogItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean madeToOrder,
    Integer leadTimeDays,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<OticaCatalogOption> options) {
}
