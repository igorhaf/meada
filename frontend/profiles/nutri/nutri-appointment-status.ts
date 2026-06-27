/**
 * Status de uma consulta de nutrição (camada 8.0) — espelho 1:1 de
 * src/main/java/com/meada/profiles/nutri/NutriAppointmentStatus.java.
 *
 * O NutriAppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de nutri_appointments.status (migration 39) trava os mesmos ids.
 *
 * Transições:
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const NUTRI_APPOINTMENT_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type NutriAppointmentStatus = (typeof NUTRI_APPOINTMENT_STATUSES)[number]
export type NutriAppointmentStatusId = NutriAppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha NutriAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<NutriAppointmentStatusId, NutriAppointmentStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return NUTRI_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
