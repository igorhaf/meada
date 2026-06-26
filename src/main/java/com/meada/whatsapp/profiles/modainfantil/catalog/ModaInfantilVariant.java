package com.meada.whatsapp.profiles.modainfantil.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Variante (faixa-etária×cor) de um produto moda_infantil (camada 8.22, chassi de varejo) — o SKU
 * real. DTO de saída. Clone do {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieVariant}:
 * cada variante é uma combinação concreta com SEU PRÓPRIO preço e ESTOQUE.
 *
 * <p>{@code priceCents} é NULLABLE — quando null, a variante herda o {@code basePriceCents} do
 * produto. {@code stockQty} é o estoque disponível; o pedido o DECREMENTA transacionalmente na
 * criação, e o cancelamento/recusa o DEVOLVE (⭐ restock — adaptação da camada 8.22). {@code size} é a
 * FAIXA ETÁRIA, validada contra {@link com.meada.whatsapp.profiles.modainfantil.KidsSize};
 * {@code color} é texto livre.
 */
public record ModaInfantilVariant(
    UUID id,
    UUID productId,
    String size,
    String color,
    String sku,
    Integer priceCents,
    int stockQty,
    boolean available,
    Instant createdAt,
    Instant updatedAt) {
}
