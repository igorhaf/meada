package com.meada.profiles.lingerie.orders;

import java.util.UUID;

/**
 * Item de um pedido lingerie (camada 8.21). Análogo ao
 * {@link com.meada.profiles.adega.orders.AdegaOrderItem}, mas referencia a VARIANTE e
 * carrega o SNAPSHOT de produto+variante+preço do momento do pedido: {@code productName} + {@code size}
 * + {@code color} + {@code unitPriceCents}. Alterar/excluir produto/variante depois NÃO altera
 * pedidos passados.
 */
public record LingerieOrderItem(
    UUID id,
    UUID variantId,
    String productName,
    String size,
    String color,
    int qtd,
    int unitPriceCents) {
}
