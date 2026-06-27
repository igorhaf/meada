package com.meada.profiles.lingerie.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * ⭐ Variante (tamanho×cor) de um produto lingerie (camada 8.21, chassi de varejo) — o SKU real.
 * DTO de saída. É a camada NOVA que substitui o option/modifier do
 * {@link com.meada.profiles.adega.menu.AdegaMenuOption}: em vez de um delta de preço, cada
 * variante é uma combinação concreta com SEU PRÓPRIO preço e ESTOQUE.
 *
 * <p>{@code priceCents} é NULLABLE — quando null, a variante herda o {@code basePriceCents} do
 * produto. {@code stockQty} é o estoque disponível; o pedido o DECREMENTA transacionalmente na
 * criação. {@code size} é validado contra {@link com.meada.profiles.lingerie.LingerieSize};
 * {@code color} é texto livre.
 */
public record LingerieVariant(
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
