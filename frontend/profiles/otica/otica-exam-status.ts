/**
 * Status de um exame de vista do perfil otica (camada 8.12) — espelho 1:1 de
 * src/main/java/com/meada/profiles/otica/OticaExamStatus.java.
 *
 * O OticaExamStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de otica_exam_appointments.status (migration 56) trava os mesmos ids no banco.
 * id = string estável (persistida); label = rótulo pt-BR exibido.
 *
 * FLUXO A (agenda de exames, clone do chassi clínico dental/dermatologia). Transições:
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, falta
 *   realizado/cancelado/falta → (terminal)
 */
export const OTICA_EXAM_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'falta', label: 'Falta' },
] as const

export type OticaExamStatus = (typeof OTICA_EXAM_STATUSES)[number]
export type OticaExamStatusId = OticaExamStatus['id']

/** Transições permitidas a partir de cada status (espelha OticaExamStatus.allowedNext). */
export const ALLOWED_NEXT: Record<OticaExamStatusId, OticaExamStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'falta'],
  realizado: [],
  cancelado: [],
  falta: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return OTICA_EXAM_STATUSES.find((s) => s.id === id)?.label ?? id
}
