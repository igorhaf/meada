package com.meada.profiles.floricultura.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil floricultura (camada 8.4): item do cardápio +
 * quantidade + ids das opções escolhidas (ESCAPADA 2). Clone de
 * {@link com.meada.profiles.sushi.orders.OrderLineInput} + {@code optionIds}. Item sem
 * opção → lista vazia.
 */
public record OrderLineInput(UUID catalogItemId, int qtd, List<UUID> optionIds) {
}
