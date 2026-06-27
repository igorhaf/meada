package com.meada.profiles.modainfantil.orders;

import java.util.UUID;

/**
 * Item de um pedido moda_infantil (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrderItem}: referencia a VARIANTE e
 * carrega o SNAPSHOT de produto+variante+preço do momento do pedido: {@code productName} + {@code size}
 * (faixa etária) + {@code color} + {@code unitPriceCents}. Alterar/excluir produto/variante depois NÃO
 * altera pedidos passados. O restock no cancelamento usa {@code variantId} + {@code qtd} destes itens.
 */
public record ModaInfantilOrderItem(
    UUID id,
    UUID variantId,
    String productName,
    String size,
    String color,
    int qtd,
    int unitPriceCents) {
}
