package com.meada.whatsapp.profiles.otica.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de uma encomenda otica (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderItem} + {@code madeToOrder}
 * (snapshot — define se o item exige prazo de montagem). itemName + unitPriceCents são SNAPSHOTS do
 * momento do pedido ({@code unitPriceCents} JÁ inclui a soma dos deltas das opções).
 */
public record OticaOrderItem(
    UUID id,
    UUID catalogItemId,
    String itemName,
    int qtd,
    int unitPriceCents,
    boolean madeToOrder,
    List<OticaOrderItemOption> options) {
}
