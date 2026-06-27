/**
 * Status de um agendamento do perfil barbearia (camada 8.1) — espelho 1:1 de
 * src/main/java/com/meada/profiles/barbearia/BarberAppointmentStatus.java.
 *
 * O BarberAppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de barber_appointments.status (migration 43) trava os mesmos ids.
 *
 * Transições:
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const BARBER_APPOINTMENT_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type BarberAppointmentStatus = (typeof BARBER_APPOINTMENT_STATUSES)[number]
export type BarberAppointmentStatusId = BarberAppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha BarberAppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<BarberAppointmentStatusId, BarberAppointmentStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return BARBER_APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
