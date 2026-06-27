package com.meada.profiles.modainfantil.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produto do catálogo moda_infantil (camada 8.22). DTO de saída. Clone do
 * {@link com.meada.profiles.lingerie.catalog.LingerieProduct}: {@code category} (id estável
 * de {@link com.meada.profiles.modainfantil.ModaInfantilCategory}) + {@code basePriceCents}
 * (preço base) + a lista de {@link ModaInfantilVariant variants} (a grade faixa-etária×cor). A
 * variante pode SOBREPOR o {@code basePriceCents} via seu próprio {@code priceCents}.
 */
public record ModaInfantilProduct(
    UUID id,
    String name,
    String description,
    String category,
    int basePriceCents,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<ModaInfantilVariant> variants) {
}
