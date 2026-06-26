package com.meada.whatsapp.profiles.concessionaria.leads;

import java.util.UUID;

/**
 * Entrada para criar um lead (camada 8.17) — usada pelo POST manual e pelo
 * {@link LeadCarroConfirmHandler}. {@code conversationId} nullable (POST manual sem WhatsApp). O preço
 * NÃO entra aqui — é sempre SNAPSHOT do catálogo no momento do lead.
 */
public record LeadInput(
    UUID vehicleId,
    UUID conversationId,
    UUID contactId,
    String paymentCondition,
    String notes) {
}
