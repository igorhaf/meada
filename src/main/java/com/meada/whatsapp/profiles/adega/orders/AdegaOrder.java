package com.meada.whatsapp.profiles.adega.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido adega (camada 8.9). Clone do chassi comida (gate de aceite: {@code rejectionReason} na
 * recusa) + a ESCAPADA +18: {@code ageConfirmed} (maioridade declarada, persistido pra compliance —
 * sempre true em pedido existente, pois o backend recusa criar sem o flag). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban e o selo +18 no painel.
 */
public record AdegaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int deliveryFeeCents,
    int totalCents,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    boolean ageConfirmed,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<AdegaOrderItem> items) {
}
