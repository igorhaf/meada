package com.meada.whatsapp.profiles.lavanderia.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido lavanderia (camada 8.10). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrder} + a ESCAPADA da lavanderia:
 * DUAS DATAS acopladas — {@code collectDate} (coleta, obrigatória >= hoje) e {@code deliveryDate}
 * (MATERIALIZADA = collect + MAX(turnaround dos itens)). {@code period} é o período da coleta. DTO de
 * saída com os itens + dados do contato (cliente) para o card do Kanban.
 */
public record LavanderiaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int deliveryFeeCents,
    int totalCents,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    LocalDate collectDate,
    LocalDate deliveryDate,
    String period,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<LavanderiaOrderItem> items) {
}
