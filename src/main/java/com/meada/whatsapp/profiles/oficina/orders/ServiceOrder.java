package com.meada.whatsapp.profiles.oficina.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ordem de serviço (camada 7.9) — espelha service_orders. Order-based com {@code totalCents}
 * materializado (recalculado a cada mutação de item). Snapshots de cliente/veículo. {@code items}
 * hidratado no findById/detalhe (lista pode vir vazia em listagens leves).
 */
public record ServiceOrder(
    UUID id,
    UUID contactId,
    UUID vehicleId,
    UUID mechanicId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String vehiclePlate,
    String vehicleModel,
    String mechanicName,
    String complaint,
    String diagnosis,
    int totalCents,
    String status,
    LocalDate expectedDelivery,
    String notes,
    Instant openedAt,
    Instant closedAt,
    Instant statusUpdatedAt,
    List<OsItem> items) {
}
