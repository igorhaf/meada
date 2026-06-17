/**
 * Status de uma consulta do perfil dental (camada 7.4) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/dental/AppointmentStatus.java.
 *
 * O AppointmentStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK constraint de dental_appointments.status (migration 33) trava os mesmos ids no banco.
 * id = string estável (persistida); label = rótulo pt-BR exibido.
 *
 * Transições (decisão 1):
 *   agendada   → confirmada, cancelada
 *   confirmada → realizada, cancelada, falta
 *   realizada/cancelada/falta → (terminal)
 */
export const APPOINTMENT_STATUSES = [
  { id: 'agendada', label: 'Agendada' },
  { id: 'confirmada', label: 'Confirmada' },
  { id: 'realizada', label: 'Realizada' },
  { id: 'cancelada', label: 'Cancelada' },
  { id: 'falta', label: 'Falta' },
] as const

export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number]
export type AppointmentStatusId = AppointmentStatus['id']

/** Transições permitidas a partir de cada status (espelha AppointmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<AppointmentStatusId, AppointmentStatusId[]> = {
  agendada: ['confirmada', 'cancelada'],
  confirmada: ['realizada', 'cancelada', 'falta'],
  realizada: [],
  cancelada: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return APPOINTMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
