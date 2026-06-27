/**
 * Período de retirada/entrega do perfil papelaria (camada 8.15) — espelho 1:1 de
 * src/main/java/com/meada/profiles/papelaria/PapelariaPeriod.java.
 *
 * Pedido sob encomenda carrega o dia (pickup_or_delivery_date) + a FAIXA do dia (este enum).
 * O PapelariaPeriodParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK de papelaria_orders.delivery_period trava os mesmos ids no banco. Clone do
 * padaria-period.ts.
 */
export const PAPELARIA_PERIODS = [
  { id: 'manha', label: 'Manhã (8h–12h)' },
  { id: 'tarde', label: 'Tarde (13h–18h)' },
] as const

export type PapelariaPeriod = (typeof PAPELARIA_PERIODS)[number]
export type PapelariaPeriodId = PapelariaPeriod['id']

/** Rótulo pt-BR de um período (fallback: o próprio id se desconhecido). */
export function periodLabel(id: string): string {
  return PAPELARIA_PERIODS.find((p) => p.id === id)?.label ?? id
}
