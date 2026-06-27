package com.meada.profiles.pizzaria.orders;

import java.util.UUID;

/**
 * Opção escolhida de um item de pedido pizzaria (camada 8.4, ESCAPADA 2). DTO de saída.
 * groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido (a opção do cardápio
 * pode ter mudado/sumido depois — {@code menuOptionId} vira null no on-delete-set-null). Sem
 * paralelo no sushi.
 */
public record PizzariaOrderItemOption(
    UUID id,
    UUID menuOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
