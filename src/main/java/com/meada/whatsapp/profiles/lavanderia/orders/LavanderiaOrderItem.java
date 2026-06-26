package com.meada.whatsapp.profiles.lavanderia.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de um pedido lavanderia (camada 8.10). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderItem} + {@code
 * turnaroundSnapshot} (prazo do serviço no momento do pedido — entra no MAX que define a entrega).
 * serviceName + unitPriceCents são SNAPSHOTS do momento ({@code unitPriceCents} JÁ inclui a soma dos
 * deltas das opções).
 */
public record LavanderiaOrderItem(
    UUID id,
    UUID serviceId,
    String serviceName,
    int qty,
    int unitPriceCents,
    int turnaroundSnapshot,
    List<LavanderiaOrderItemOption> options) {
}
