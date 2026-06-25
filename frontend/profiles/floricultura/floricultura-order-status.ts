/**
 * Status de um pedido floricultura (delivery iFood-style, camada 8.4) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/floricultura/orders/FloriculturaOrderStatus.java.
 *
 * O FloriculturaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de floricultura_orders.status trava os mesmos ids no banco.
 *
 * ESCAPADA 1 (gate de aceite do restaurante — ação HUMANA no painel, nunca da IA). Transições:
 *   aguardando   → em_preparo, recusado
 *   em_preparo   → saiu_entrega, cancelado
 *   saiu_entrega → entregue, cancelado
 *   entregue/recusado/cancelado → (terminal)
 */
export const FLORICULTURA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_preparo', label: 'Em preparo' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type FloriculturaOrderStatus = (typeof FLORICULTURA_ORDER_STATUSES)[number]
export type FloriculturaOrderStatusId = FloriculturaOrderStatus['id']

export const ALLOWED_NEXT: Record<FloriculturaOrderStatusId, FloriculturaOrderStatusId[]> = {
  aguardando: ['em_preparo', 'recusado'],
  em_preparo: ['saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return FLORICULTURA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
