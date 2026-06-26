package com.meada.whatsapp.profiles.papelaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil papelaria (camada 8.15 / perfil papelaria):
 * item do catálogo + TIRAGEM (quantity = quantas peças, ex.: 50/100/200) + ids das opções escolhidas +
 * o texto personalizado ({@code customText}, nullable, snapshot). Clone de
 * {@link com.meada.whatsapp.profiles.padaria.orders.OrderLineInput} (camada 8.8) — {@code cakeMessage}
 * vira {@code customText}; {@code qtd} aqui é a TIRAGEM (eixo de negócio, escala o line total). Item
 * sem opção → lista vazia; item sem personalização → customText null.
 */
public record OrderLineInput(UUID catalogItemId, int qtd, List<UUID> optionIds, String customText) {
}
