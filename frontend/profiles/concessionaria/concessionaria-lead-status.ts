/**
 * Status de um lead do perfil concessionaria (camada 8.17) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/concessionaria/ConcessionariaLeadStatus.java.
 *
 * O ConcessionariaLeadStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de concessionaria_leads.status (migration) trava os mesmos ids.
 *
 * Funil de venda:
 *   novo          → em_negociacao, perdido
 *   em_negociacao → fechado, perdido
 *   fechado/perdido → (terminal)
 */
export const LEAD_STATUSES = [
  { id: 'novo', label: 'Novo' },
  { id: 'em_negociacao', label: 'Em negociação' },
  { id: 'fechado', label: 'Fechado' },
  { id: 'perdido', label: 'Perdido' },
] as const

export type LeadStatus = (typeof LEAD_STATUSES)[number]
export type LeadStatusId = LeadStatus['id']

/** Transições permitidas a partir de cada status (espelha ConcessionariaLeadStatus.allowedNext). */
export const ALLOWED_NEXT: Record<LeadStatusId, LeadStatusId[]> = {
  novo: ['em_negociacao', 'perdido'],
  em_negociacao: ['fechado', 'perdido'],
  fechado: [],
  perdido: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return LEAD_STATUSES.find((s) => s.id === id)?.label ?? id
}
