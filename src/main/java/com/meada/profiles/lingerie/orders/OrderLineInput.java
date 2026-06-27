package com.meada.profiles.lingerie.orders;

import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil lingerie (camada 8.21): uma VARIANTE
 * (SKU tamanho×cor) + quantidade. Análogo ao
 * {@link com.meada.profiles.adega.orders.OrderLineInput}, mas referencia a variante em vez
 * do item+opções. O preço e o snapshot são resolvidos no repositório a partir da variante/produto.
 */
public record OrderLineInput(UUID variantId, int qtd) {
}
