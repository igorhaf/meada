/**
 * Status de uma ordem de serviço do perfil oficina (camada 7.9) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/oficina/OsStatus.java.
 *
 * O OsStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem. A CHECK
 * constraint de service_orders.status (migration 38) trava os mesmos ids.
 *
 * Transições:
 *   aberta       → orcada, cancelada
 *   orcada       → aprovada, recusada, cancelada
 *   aprovada     → em_execucao, cancelada
 *   em_execucao  → concluida, cancelada
 *   concluida    → entregue
 *   recusada/entregue/cancelada → (terminal)
 */
export const OS_STATUSES = [
  { id: 'aberta', label: 'Aberta' },
  { id: 'orcada', label: 'Orçada' },
  { id: 'aprovada', label: 'Aprovada' },
  { id: 'recusada', label: 'Recusada' },
  { id: 'em_execucao', label: 'Em execução' },
  { id: 'concluida', label: 'Concluída' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'cancelada', label: 'Cancelada' },
] as const

export type OsStatus = (typeof OS_STATUSES)[number]
export type OsStatusId = OsStatus['id']

/** Transições permitidas a partir de cada status (espelha OsStatus.allowedNext). */
export const ALLOWED_NEXT: Record<OsStatusId, OsStatusId[]> = {
  aberta: ['orcada', 'cancelada'],
  orcada: ['aprovada', 'recusada', 'cancelada'],
  aprovada: ['em_execucao', 'cancelada'],
  em_execucao: ['concluida', 'cancelada'],
  concluida: ['entregue'],
  recusada: [],
  entregue: [],
  cancelada: [],
}

/**
 * Estados em que os ITENS da OS não podem mais ser mutados (espelha OsStatus.itemsLocked).
 * O painel desabilita o editor de itens nesses estados.
 */
export const ITEMS_LOCKED: Record<OsStatusId, boolean> = {
  aberta: false,
  orcada: false,
  aprovada: false,
  em_execucao: true,
  concluida: true,
  entregue: true,
  recusada: true,
  cancelada: true,
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return OS_STATUSES.find((s) => s.id === id)?.label ?? id
}
