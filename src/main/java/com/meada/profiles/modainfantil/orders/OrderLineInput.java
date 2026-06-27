package com.meada.profiles.modainfantil.orders;

import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil moda_infantil (camada 8.22): uma VARIANTE
 * (SKU faixa-etária×cor) + quantidade. Clone do
 * {@link com.meada.profiles.lingerie.orders.OrderLineInput}. O preço e o snapshot são
 * resolvidos no repositório a partir da variante/produto.
 */
public record OrderLineInput(UUID variantId, int qtd) {
}
