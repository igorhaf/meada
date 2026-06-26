/**
 * Status de uma matrícula do perfil cursos (camada 8.20) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/cursos/CursoEnrollmentStatus.java.
 *
 * O CursoEnrollmentStatusParityTest (backend) garante que os ids nunca divergem. A CHECK
 * constraint de cursos_enrollments.status trava os mesmos ids.
 *
 * Transições:
 *   ativa     → trancada, concluida, cancelada
 *   trancada  → ativa, concluida, cancelada
 *   concluida → (terminal)
 *   cancelada → (terminal)
 */
export type CursoEnrollmentStatusId = 'ativa' | 'trancada' | 'concluida' | 'cancelada'

export const CURSO_ENROLLMENT_STATUSES = [
  { id: 'ativa', label: 'Ativa' },
  { id: 'trancada', label: 'Trancada' },
  { id: 'concluida', label: 'Concluída' },
  { id: 'cancelada', label: 'Cancelada' },
] as const satisfies ReadonlyArray<{ id: CursoEnrollmentStatusId; label: string }>

/** Transições permitidas a partir de cada status (espelha CursoEnrollmentStatus.allowedNext). */
export const ALLOWED_NEXT: Record<CursoEnrollmentStatusId, CursoEnrollmentStatusId[]> = {
  ativa: ['trancada', 'concluida', 'cancelada'],
  trancada: ['ativa', 'concluida', 'cancelada'],
  concluida: [],
  cancelada: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return CURSO_ENROLLMENT_STATUSES.find((s) => s.id === id)?.label ?? id
}
