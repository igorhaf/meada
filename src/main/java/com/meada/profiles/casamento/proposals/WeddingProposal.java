package com.meada.profiles.casamento.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Proposta de casamento (camada 8.7) — espelha wedding_proposals. Order-based com {@code totalCents}
 * materializado (recalculado a cada mutação de item de ORÇAMENTO). Snapshots de cliente (noivos).
 * {@code items} (orçamento), {@code timeline} (cronograma do dia) e {@code checklist} (preparação
 * pré-casamento) hidratados no findById/detalhe (listas podem vir vazias em listagens leves). TRÊS
 * tipos de sub-item no mesmo artefato — não confundir: orçamento entra no total; cronograma e
 * checklist NÃO. CUPOM (onda 1, backlog #10): {@code discountCents} MATERIALIZADO e re-derivado a
 * cada mutação de item (clampado ao total); total final = totalCents − discountCents; aplicado pelo
 * painel — a IA nunca negocia preço. {@code dateBusy} (onda 1, backlog #15) é DERIVADO na leitura:
 * true quando OUTRA proposta aprovada/fechada/realizada tem a MESMA wedding_date (alerta de
 * overbooking no painel — informativo, sem 409). Espelho do EventProposal + a 3ª sub-entidade
 * (checklist).
 */
public record WeddingProposal(
    UUID id,
    UUID contactId,
    UUID plannerId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String plannerName,
    String weddingStyle,
    LocalDate weddingDate,
    Integer guestCount,
    String briefing,
    int totalCents,
    int discountCents,
    UUID couponId,
    String couponCodeSnapshot,
    boolean dateBusy,
    String status,
    String notes,
    Instant openedAt,
    Instant closedAt,
    Instant statusUpdatedAt,
    List<WeddingProposalItem> items,
    List<WeddingTimelineItem> timeline,
    List<WeddingChecklistTask> checklist) {
}
