/**
 * Status de uma proposta de evento do perfil eventos (camada 8.2) — espelho 1:1 de
 * src/main/java/com/meada/profiles/eventos/EventProposalStatus.java.
 *
 * O EventProposalStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de event_proposals.status (migration 45) trava os mesmos ids.
 *
 * Transições (funil de casa de festas):
 *   rascunho   → orcada, cancelada
 *   orcada     → aprovada, recusada, cancelada
 *   aprovada   → fechada, cancelada
 *   fechada    → realizada, cancelada
 *   realizada/recusada/cancelada → (terminal)
 */
export const EVENT_PROPOSAL_STATUSES = [
  { id: 'rascunho', label: 'Rascunho' },
  { id: 'orcada', label: 'Orçada' },
  { id: 'aprovada', label: 'Aprovada' },
  { id: 'recusada', label: 'Recusada' },
  { id: 'fechada', label: 'Fechada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type EventProposalStatus = (typeof EVENT_PROPOSAL_STATUSES)[number]
export type EventProposalStatusId = EventProposalStatus['id']

/** Transições permitidas a partir de cada status (espelha EventProposalStatus.allowedNext). */
export const ALLOWED_NEXT: Record<EventProposalStatusId, EventProposalStatusId[]> = {
  rascunho: ['orcada', 'cancelada'],
  orcada: ['aprovada', 'recusada', 'cancelada'],
  aprovada: ['fechada', 'cancelada'],
  fechada: ['realizada', 'cancelada'],
  realizada: [],
  recusada: [],
  cancelada: [],
}

/**
 * Estados em que os ITENS da proposta (orçamento E cronograma) não podem mais ser mutados
 * (espelha EventProposalStatus.itemsLocked — trava a partir de 'fechada'). O painel desabilita os
 * editores nesses estados.
 */
export const ITEMS_LOCKED: Record<EventProposalStatusId, boolean> = {
  rascunho: false,
  orcada: false,
  aprovada: false,
  fechada: true,
  realizada: true,
  recusada: true,
  cancelada: true,
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return EVENT_PROPOSAL_STATUSES.find((s) => s.id === id)?.label ?? id
}
