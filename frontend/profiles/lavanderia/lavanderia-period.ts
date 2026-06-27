/**
 * Período de COLETA do perfil lavanderia (camada 8.10) — espelho 1:1 de
 * src/main/java/com/meada/profiles/lavanderia/LavanderiaPeriod.java.
 *
 * O pedido carrega o dia da coleta (collectDate) + a FAIXA do dia (este enum). O
 * LavanderiaPeriodParityTest (backend) garante que os ids aqui e no enum Java nunca divergem. A CHECK
 * de lavanderia_orders.period trava os mesmos ids no banco.
 */
export const LAVANDERIA_PERIODS = [
  { id: 'manha', label: 'Manhã (8h–12h)' },
  { id: 'tarde', label: 'Tarde (13h–18h)' },
] as const

export type LavanderiaPeriod = (typeof LAVANDERIA_PERIODS)[number]
export type LavanderiaPeriodId = LavanderiaPeriod['id']

/** Rótulo pt-BR de um período (fallback: o próprio id se desconhecido). */
export function periodLabel(id: string): string {
  return LAVANDERIA_PERIODS.find((p) => p.id === id)?.label ?? id
}
