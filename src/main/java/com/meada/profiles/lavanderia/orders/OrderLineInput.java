package com.meada.profiles.lavanderia.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil lavanderia (camada 8.10): serviço do catálogo
 * + quantidade de peças + ids das opções escolhidas. Clone de
 * {@link com.meada.profiles.floricultura.orders.OrderLineInput} (catalogItemId → serviceId).
 * Item sem opção → lista vazia.
 */
public record OrderLineInput(UUID serviceId, int qty, List<UUID> optionIds) {
}
