package com.meada.profiles.concessionaria.leads;

import java.time.Instant;
import java.util.UUID;

/**
 * Lead de compra (camada 8.17) — espelha concessionaria_leads. É INTERESSE em UM veículo, NÃO pedido
 * (sem itens/total). Funil novo→em_negociacao→fechado/perdido. {@code vehiclePriceCents} é SNAPSHOT do
 * preço do catálogo no momento do lead (a IA NUNCA fecha preço). {@code salespersonId} é atribuído no
 * painel (nullable). {@code lostReason} preenchido ao mover p/ 'perdido'.
 */
public record ConcessionariaLead(
    UUID id,
    UUID companyId,
    UUID vehicleId,
    UUID conversationId,
    UUID contactId,
    String customerName,
    String customerPhone,
    String vehicleBrand,
    String vehicleModel,
    Integer vehicleYear,
    int vehiclePriceCents,
    String paymentCondition,
    String status,
    UUID salespersonId,
    String lostReason,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
