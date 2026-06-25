/**
 * Período de entrega do perfil floricultura (camada 8.5, ESCAPADA) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/floricultura/FloriculturaPeriod.java.
 *
 * Flor é presente AGENDADO: o pedido carrega o dia (deliveryDate) + a FAIXA do dia (este enum).
 * O FloriculturaPeriodParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * A CHECK de floricultura_orders.delivery_period trava os mesmos ids no banco.
 */
export const FLORICULTURA_PERIODS = [
  { id: 'manha', label: 'Manhã (8h–12h)' },
  { id: 'tarde', label: 'Tarde (13h–18h)' },
] as const

export type FloriculturaPeriod = (typeof FLORICULTURA_PERIODS)[number]
export type FloriculturaPeriodId = FloriculturaPeriod['id']

/** Rótulo pt-BR de um período (fallback: o próprio id se desconhecido). */
export function periodLabel(id: string): string {
  return FLORICULTURA_PERIODS.find((p) => p.id === id)?.label ?? id
}
