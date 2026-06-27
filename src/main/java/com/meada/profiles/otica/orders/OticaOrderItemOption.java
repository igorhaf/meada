package com.meada.profiles.otica.orders;

import java.util.UUID;

/**
 * Opção escolhida (tipo de lente/tratamento) de um item de encomenda otica (camada 8.12, FLUXO B).
 * DTO de saída. groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido (a opção do
 * catálogo pode ter mudado/sumido depois — {@code catalogOptionId} vira null no on-delete-set-null).
 * Clone de {@link com.meada.profiles.floricultura.orders.FloriculturaOrderItemOption}.
 */
public record OticaOrderItemOption(
    UUID id,
    UUID catalogOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
