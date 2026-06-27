package com.meada.profiles.otica.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil otica (camada 8.12, FLUXO B): item do
 * catálogo + quantidade + ids das opções escolhidas (tipo de lente/tratamento). Clone de
 * {@link com.meada.profiles.floricultura.orders.OrderLineInput}. Item sem opção → lista vazia.
 */
public record OticaOrderLineInput(UUID catalogItemId, int qtd, List<UUID> optionIds) {
}
