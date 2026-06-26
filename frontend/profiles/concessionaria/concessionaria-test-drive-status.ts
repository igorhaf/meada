/**
 * Status de um test-drive do perfil concessionaria (camada 8.17) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/concessionaria/ConcessionariaTestDriveStatus.java.
 *
 * O ConcessionariaTestDriveStatusParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem. A CHECK constraint de concessionaria_test_drives.status (migration) trava os mesmos ids.
 *
 * Transições (agenda):
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, no_show
 *   realizado/cancelado/no_show → (terminal)
 */
export const TEST_DRIVE_STATUSES = [
  { id: 'agendado', label: 'Agendado' },
  { id: 'confirmado', label: 'Confirmado' },
  { id: 'realizado', label: 'Realizado' },
  { id: 'cancelado', label: 'Cancelado' },
  { id: 'no_show', label: 'No-show' },
] as const

export type TestDriveStatus = (typeof TEST_DRIVE_STATUSES)[number]
export type TestDriveStatusId = TestDriveStatus['id']

/** Transições permitidas a partir de cada status (espelha ConcessionariaTestDriveStatus.allowedNext). */
export const ALLOWED_NEXT: Record<TestDriveStatusId, TestDriveStatusId[]> = {
  agendado: ['confirmado', 'cancelado'],
  confirmado: ['realizado', 'cancelado', 'no_show'],
  realizado: [],
  cancelado: [],
  no_show: [],
}

/** Rótulo pt-BR de um status (fallback: o próprio id se desconhecido). */
export function statusLabel(id: string): string {
  return TEST_DRIVE_STATUSES.find((s) => s.id === id)?.label ?? id
}
