package com.meada.profiles.papelaria.orders;

import java.util.UUID;

/**
 * Opção/personalização escolhida de um item de pedido papelaria (camada 8.15 / perfil papelaria). DTO
 * de saída. groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido (a opção do
 * catálogo pode ter mudado/sumido depois — {@code catalogOptionId} vira null no on-delete-set-null).
 * Clone de {@link com.meada.profiles.padaria.orders.PadariaOrderItemOption} (camada 8.8).
 */
public record PapelariaOrderItemOption(
    UUID id,
    UUID catalogOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
