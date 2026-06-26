/**
 * Status de um pedido lavanderia (delivery com coleta+entrega, camada 8.10) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/lavanderia/orders/LavanderiaOrderStatus.java.
 *
 * O LavanderiaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de
 * lavanderia_orders.status trava os mesmos ids no banco.
 *
 * Gate de aceite humano (ação no painel, nunca da IA). Transições:
 *   aguardando   → coletado, recusado, cancelado
 *   coletado     → em_processo, cancelado
 *   em_processo  → pronto, cancelado
 *   pronto       → saiu_entrega, cancelado
 *   saiu_entrega → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const LAVANDERIA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'coletado', label: 'Coletado' },
  { id: 'em_processo', label: 'Em processo' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type LavanderiaOrderStatus = (typeof LAVANDERIA_ORDER_STATUSES)[number]
export type LavanderiaOrderStatusId = LavanderiaOrderStatus['id']

export const ALLOWED_NEXT: Record<LavanderiaOrderStatusId, LavanderiaOrderStatusId[]> = {
  aguardando: ['coletado', 'recusado', 'cancelado'],
  coletado: ['em_processo', 'cancelado'],
  em_processo: ['pronto', 'cancelado'],
  pronto: ['saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return LAVANDERIA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
