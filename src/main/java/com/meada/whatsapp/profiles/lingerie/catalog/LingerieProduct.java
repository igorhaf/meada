package com.meada.whatsapp.profiles.lingerie.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produto do catálogo lingerie (camada 8.21). DTO de saída. Análogo ao
 * {@link com.meada.whatsapp.profiles.adega.menu.AdegaMenuItem}, mas com {@code category} (id estável
 * de {@link com.meada.whatsapp.profiles.lingerie.LingerieCategory}) + {@code basePriceCents} (preço
 * base) e a lista de {@link LingerieVariant variants} (⭐ a grade tamanho×cor — substitui as opções
 * do adega). A variante pode SOBREPOR o {@code basePriceCents} via seu próprio {@code priceCents}.
 */
public record LingerieProduct(
    UUID id,
    String name,
    String description,
    String category,
    int basePriceCents,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<LingerieVariant> variants) {
}
