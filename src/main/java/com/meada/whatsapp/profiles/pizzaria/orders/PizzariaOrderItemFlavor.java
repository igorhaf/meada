package com.meada.whatsapp.profiles.pizzaria.orders;

import java.util.UUID;

/**
 * Sabor de uma fração de uma pizza (camada 8.6, ESCAPADA meio-a-meio). DTO de saída.
 *
 * <p>Um item-pizza do pedido tem N frações (1 = inteira, 2 = meio-a-meio); cada fração é um sabor.
 * {@code fractionIndex} é a posição (1..N). {@code flavorName}/{@code flavorPriceCents} são
 * SNAPSHOTS do momento do pedido — alterar o preço do sabor no cardápio NÃO altera o histórico
 * ({@code menuItemId} vira null no on-delete-set-null). O preço da pizza = MAX(flavorPriceCents das
 * frações) + Σ deltas de modifiers, recalculado no backend.
 */
public record PizzariaOrderItemFlavor(
    UUID id,
    UUID menuItemId,
    int fractionIndex,
    String flavorName,
    int flavorPriceCents) {
}
