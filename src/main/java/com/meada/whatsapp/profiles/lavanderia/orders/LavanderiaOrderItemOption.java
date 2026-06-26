package com.meada.whatsapp.profiles.lavanderia.orders;

import java.util.UUID;

/**
 * Opção escolhida de um item de pedido lavanderia (camada 8.10). DTO de saída.
 * groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido (a opção do catálogo pode
 * ter mudado/sumido depois — {@code serviceOptionId} vira null no on-delete-set-null). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderItemOption}.
 */
public record LavanderiaOrderItemOption(
    UUID id,
    UUID serviceOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
