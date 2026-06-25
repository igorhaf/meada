package com.meada.whatsapp.profiles.pizzaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de um pedido pizzaria (camada 8.6). Clone do ComidaOrderItem + a lista de {@code options}
 * (modifiers) e a lista de {@code flavors} (ESCAPADA meio-a-meio). itemName + unitPriceCents são
 * SNAPSHOTS do momento do pedido. {@code unitPriceCents} JÁ inclui a regra de preço da pizza
 * (MAX dos sabores das frações) + a soma dos deltas dos modifiers. {@code flavors} vazio = item
 * não-pizza (bebida/sobremesa).
 */
public record PizzariaOrderItem(
    UUID id,
    UUID menuItemId,
    String itemName,
    int qtd,
    int unitPriceCents,
    List<PizzariaOrderItemOption> options,
    List<PizzariaOrderItemFlavor> flavors) {
}
