package com.meada.profiles.eventos.proposals;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Marco de CRONOGRAMA do dia do evento (camada 8.2) — espelha event_timeline_items. A ESCAPADA da
 * SM: roteiro ORDENADO por {@code startTime} (ex.: 19:00 recepção / 20:00 cerimônia / 23:00 festa).
 * NÃO entra no {@code totalCents} da proposta (≠ {@link EventProposalItem}, que é preço). É o "dia
 * do evento" organizacional.
 */
public record EventTimelineItem(
    UUID id,
    UUID proposalId,
    LocalTime startTime,
    String title,
    String description,
    Instant createdAt,
    Instant updatedAt) {
}
