package com.meada.profiles.otica.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Encomenda de óculos otica (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.profiles.floricultura.orders.FloriculturaOrder} (gate de aceite,
 * rejectionReason) + as ESCAPADAS da ótica: PRAZO DE MONTAGEM ({@code readyDate}, nullable — só
 * acessório dispensa) e DADOS DE RECEITA (rx_* + {@code prescriptionPending} — ADMINISTRATIVOS, a IA
 * registra mas NÃO interpreta o grau). Óculos pronto = RETIRADA na loja (sem taxa de entrega nesta
 * SM → total = subtotal). DTO de saída com os itens + dados do contato (comprador) para o Kanban.
 */
public record OticaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int totalCents,
    LocalDate readyDate,
    String notes,
    String rejectionReason,
    BigDecimal rxOdSpherical,
    BigDecimal rxOdCylindrical,
    Integer rxOdAxis,
    BigDecimal rxOeSpherical,
    BigDecimal rxOeCylindrical,
    Integer rxOeAxis,
    BigDecimal rxPd,
    boolean prescriptionPending,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<OticaOrderItem> items) {
}
