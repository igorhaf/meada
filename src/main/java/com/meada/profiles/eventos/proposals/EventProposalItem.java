package com.meada.profiles.eventos.proposals;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de ORÇAMENTO de uma proposta de evento (camada 8.2) — espelha event_proposal_items.
 * {@code lineTotalCents} é materializado (= quantity * unitPriceCents); o {@code totalCents} da
 * proposta é recalculado na mesma transação a cada mutação de item. ENTRA no total (≠
 * {@link EventTimelineItem}, que é cronograma e NÃO entra).
 */
public record EventProposalItem(
    UUID id,
    UUID proposalId,
    String description,
    int quantity,
    int unitPriceCents,
    int lineTotalCents,
    Instant createdAt,
    Instant updatedAt) {
}
