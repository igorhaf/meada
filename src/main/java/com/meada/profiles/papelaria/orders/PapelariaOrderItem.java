package com.meada.profiles.papelaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de um pedido papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.PadariaOrderItem} (camada 8.8) — {@code qtd} é a
 * TIRAGEM (escala o line total = unit × qtd), {@code cakeMessage} vira {@code customText} (snapshot do
 * texto personalizado, nullable) + o {@code madeToOrder} snapshot. itemName + unitPriceCents são
 * SNAPSHOTS do momento do pedido ({@code unitPriceCents} JÁ inclui a soma dos deltas das opções).
 */
public record PapelariaOrderItem(
    UUID id,
    UUID catalogItemId,
    String itemName,
    int qtd,
    int unitPriceCents,
    boolean madeToOrder,
    String customText,
    List<PapelariaOrderItemOption> options) {
}
