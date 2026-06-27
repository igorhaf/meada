/**
 * Status de um pedido adega (delivery de bebidas, camada 8.9) — espelho 1:1 de
 * src/main/java/com/meada/profiles/adega/orders/AdegaOrderStatus.java.
 *
 * O AdegaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de adega_orders.status trava os mesmos ids no banco.
 *
 * Máquina de status idêntica ao comida (gate de aceite — ação HUMANA no painel, nunca da IA):
 *   aguardando   → em_preparo, recusado
 *   em_preparo   → saiu_entrega, cancelado
 *   saiu_entrega → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const ADEGA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type AdegaOrderStatus = (typeof ADEGA_ORDER_STATUSES)[number]
export type AdegaOrderStatusId = AdegaOrderStatus['id']

export const ALLOWED_NEXT: Record<AdegaOrderStatusId, AdegaOrderStatusId[]> = {
  aguardando: ['em_preparo', 'recusado'],
  em_preparo: ['saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return ADEGA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
