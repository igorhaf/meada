import type { AtelieProposalStatusId } from './atelie-proposal-status'
import type { AtelieProjectTypeId } from './atelie-project-type'

/** Artesão/responsável do tenant atelie (espelha AtelieArtisan). */
export type AtelieArtisan = {
  id: string
  companyId: string
  name: string
  specialty: string | null
  active: boolean
  notes: string | null
  createdAt: string
  updatedAt: string
}

/** Config do tenant atelie (espelha AtelieConfig). Nome do ateliê + notas + lembrete de prova (SEM horário). */
export type AtelieConfig = {
  businessName: string | null
  notes: string | null
  fittingReminderEnabled: boolean
}

/** Item de ORÇAMENTO de uma proposta (espelha AtelieProposalItem). lineTotalCents materializado. */
export type AtelieProposalItem = {
  id: string
  proposalId: string
  description: string
  quantity: number
  unitPriceCents: number
  lineTotalCents: number
}

/** Status possíveis de uma prova/ajuste (sub-entidade — sem paridade, lista de 2 itens). */
export const FITTING_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'realizada', label: 'Realizada' },
] as const

export type FittingStatusId = (typeof FITTING_STATUSES)[number]['id']

/** Etapa de PROVA/AJUSTE (espelha AtelieFitting). A ESCAPADA da SM: ordenada por position. */
export type AtelieFitting = {
  id: string
  proposalId: string
  title: string
  description: string | null
  dueDate: string | null
  status: FittingStatusId
  position: number
  completedAt: string | null
}

/** Proposta de ateliê (espelha AtelieProposal). totalCents materializado. items + fittings no detalhe. */
export type AtelieProposal = {
  id: string
  companyId: string
  contactId: string | null
  artisanId: string | null
  conversationId: string | null
  customerName: string
  customerPhone: string | null
  projectType: AtelieProjectTypeId
  occasion: string | null
  briefing: string | null
  estimatedDate: string | null
  totalCents: number
  status: AtelieProposalStatusId
  notes: string | null
  depositCents: number | null
  depositPaid: boolean
  depositPaidAt: string | null
  openedAt: string
  closedAt: string | null
  statusUpdatedAt: string
  items: AtelieProposalItem[]
  fittings: AtelieFitting[]
}

/** Formata centavos em R$ pt-BR. */
export function formatBrl(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('pt-BR')
}

/**
 * Alerta de atraso da entrega (onda backlog #12): prazo prometido (estimatedDate) já passou e a
 * proposta ainda está viva (não-terminal). Comparação por string yyyy-MM-dd no fuso LOCAL do
 * navegador (toLocaleDateString en-CA) — evita o off-by-one do toISOString/UTC à noite.
 */
export function isDeliveryOverdue(
  estimatedDate: string | null,
  status: AtelieProposalStatusId,
): boolean {
  if (!estimatedDate) return false
  if (status === 'realizada' || status === 'recusada' || status === 'cancelada') return false
  const today = new Date().toLocaleDateString('en-CA')
  return estimatedDate < today
}
