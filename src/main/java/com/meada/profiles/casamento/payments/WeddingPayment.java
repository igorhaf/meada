package com.meada.profiles.casamento.payments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha do plano de pagamento do contrato de casamento (onda 1, backlog #1) — espelha
 * wedding_payments. {@code kind} sinal|parcela; {@code paid} é marcação MANUAL da equipe (Pix
 * conferido) até o gateway #50. Com 'sinal' não pago, aprovada→fechada é bloqueada
 * (409 deposit_required). A IA INFORMA o plano, nunca inventa condição nem confirma pagamento.
 */
public record WeddingPayment(
    UUID id,
    UUID companyId,
    UUID proposalId,
    String kind,
    String label,
    LocalDate dueDate,
    int amountCents,
    boolean paid,
    Instant paidAt,
    Instant createdAt,
    Instant updatedAt) {
}
