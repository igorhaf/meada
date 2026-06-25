package com.meada.whatsapp.profiles.floricultura.orders;

import java.util.UUID;

/**
 * Opção escolhida de um item de pedido floricultura (camada 8.4, ESCAPADA 2). DTO de saída.
 * groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido (a opção do cardápio
 * pode ter mudado/sumido depois — {@code catalogOptionId} vira null no on-delete-set-null). Sem
 * paralelo no sushi.
 */
public record FloriculturaOrderItemOption(
    UUID id,
    UUID catalogOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
