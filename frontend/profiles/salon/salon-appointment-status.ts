/**
 * Status de um agendamento do perfil salon (camada 7.5) — espelho 1:1 de
 * src/main/java/com/meada/profiles/salon/SalonAppointmentStatus.java.
 *
 * O SalonAppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de salon_appointments.status (migration 34) trava os mesmos ids.
 *
 * Transições (decisão 2):
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const SALON_APPOINTMENT_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type SalonAppointmentStatus = (typeof SALON_APPOINTMENT_STATUSES)[number]
export type SalonAppointmentStatusId = SalonAppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha SalonAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<SalonAppointmentStatusId, SalonAppointmentStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return SALON_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
