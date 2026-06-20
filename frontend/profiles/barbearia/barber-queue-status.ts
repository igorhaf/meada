/**
 * Status de um ticket da FILA DE WALK-IN do perfil barbearia (camada 8.1) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/barbearia/BarberQueueStatus.java.
 *
 * O BarberQueueStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de barber_queue_tickets.status (migration 43) trava os mesmos ids.
 *
 * Transições:
 *   aguardando → chamado, desistiu, expirado
 *   chamado    → atendido, desistiu
 *   atendido/desistiu/expirado → (terminal)
 *
 * aguardando→chamado é AÇÃO HUMANA no painel (a IA não move ticket) e notifica "chegou sua vez".
 */
export const BARBER_QUEUE_STATUSES = [
  { id: 'aguardando', label: 'Aguardando' },
  { id: 'chamado', label: 'Chamado' },
  { id: 'atendido', label: 'Atendido' },
  { id: 'desistiu', label: 'Desistiu' },
  { id: 'expirado', label: 'Expirado' },
] as const

export type BarberQueueStatus = (typeof BARBER_QUEUE_STATUSES)[number]
export type BarberQueueStatusId = BarberQueueStatus['id']

/** Transições permitidas a partir de cada status (espelha BarberQueueStatus.allowedNext). */
export const ALLOWED_NEXT: Record<BarberQueueStatusId, BarberQueueStatusId[]> = {
  aguardando: ['chamado', 'desistiu', 'expirado'],
  chamado: ['atendido', 'desistiu'],
  atendido: [],
  desistiu: [],
  expirado: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return BARBER_QUEUE_STATUSES.find((s) => s.id === id)?.label ?? id
}
