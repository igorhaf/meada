/**
 * Status de uma reserva do perfil restaurant (camada 7.3) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/restaurant/ReservationStatus.java.
 *
 * O ReservationStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de table_reservations.status (migration 32) trava os mesmos ids no banco.
 * id = string estável (persistida); label = rótulo pt-BR exibido.
 *
 * Transições (decisão 2):
 *   pendente   → confirmada, cancelada
 *   confirmada → realizada, cancelada, no_show
 *   realizada/cancelada/no_show → (terminal)
 */
export const RESERVATION_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'confirmada', label: 'Confirmada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
  { id: 'no_show', label: 'Não compareceu' },
] as const

export type ReservationStatus = (typeof RESERVATION_STATUSES)[number]
export type ReservationStatusId = ReservationStatus['id']

/** Transições permitidas a partir de cada status (espelha ReservationStatus.allowedNext). */
export const ALLOWED_NEXT: Record<ReservationStatusId, ReservationStatusId[]> = {
  pendente: ['confirmada', 'cancelada'],
  confirmada: ['realizada', 'cancelada', 'no_show'],
  realizada: [],
  cancelada: [],
  no_show: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return RESERVATION_STATUSES.find((s) => s.id === id)?.label ?? id
}
