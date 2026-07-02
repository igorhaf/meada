package com.meada.profiles.adega.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido adega (camada 8.9). Clone do chassi comida (gate de aceite: {@code rejectionReason} na
 * recusa) + a ESCAPADA +18: {@code ageConfirmed} (maioridade declarada, persistido pra compliance —
 * sempre true em pedido existente, pois o backend recusa criar sem o flag). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban e o selo +18 no painel.
 * {@code discountCents}/{@code couponCode}/{@code loyaltyApplied} são o desconto materializado
 * (cupom + fidelidade, backlog #1/#2 — clone do chassi sushi).
 */
public record AdegaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCode,
    boolean loyaltyApplied,
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
