package com.meada.whatsapp.profiles.oficina.orders;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de uma ordem de serviço (camada 7.9) — espelha os_items. {@code kind} 'peca'|'mao_de_obra'.
 * {@code lineTotalCents} é materializado (= quantity * unitPriceCents); o {@code totalCents} da OS é
 * recalculado na mesma transação a cada mutação de item.
 */
public record OsItem(
    UUID id,
    UUID serviceOrderId,
    String kind,
    String description,
    int quantity,
    int unitPriceCents,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt) {
}
