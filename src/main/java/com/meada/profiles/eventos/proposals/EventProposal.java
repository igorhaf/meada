package com.meada.profiles.eventos.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Proposta de evento (camada 8.2) — espelha event_proposals. Order-based com {@code totalCents}
 * materializado (recalculado a cada mutação de item de ORÇAMENTO). Snapshots de cliente. {@code
 * items} (orçamento) e {@code timeline} (cronograma) hidratados no findById/detalhe (listas podem
 * vir vazias em listagens leves). DOIS tipos de sub-item no mesmo artefato — não confundir:
 * orçamento entra no total; cronograma NÃO.
 */
public record EventProposal(
    UUID id,
    UUID contactId,
    UUID plannerId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String plannerName,
    String eventType,
    LocalDate eventDate,
    Integer guestCount,
    String briefing,
    int totalCents,
    String status,
    String notes,
    Instant openedAt,
    Instant closedAt,
    Instant statusUpdatedAt,
    List<EventProposalItem> items,
    List<EventTimelineItem> timeline) {
}
