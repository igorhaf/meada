/**
 * Status de um pedido de óculos do perfil otica (camada 8.12) — espelho 1:1 de
 * src/main/java/com/meada/profiles/otica/orders/OticaOrderStatus.java.
 *
 * O OticaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de otica_orders.status trava os mesmos ids no banco.
 *
 * FLUXO B (pedido sob receita, clone do chassi floricultura/comida). GATE DE ACEITE HUMANO — ação do
 * lojista no painel, nunca da IA. Transições:
 *   aguardando  → em_montagem, recusado
 *   em_montagem → pronto, cancelado
 *   pronto      → retirado, cancelado
 *   retirado/recusado/cancelado → (terminal)
 */
export const OTICA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'em_montagem', label: 'Em montagem' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'retirado', label: 'Retirado' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type OticaOrderStatus = (typeof OTICA_ORDER_STATUSES)[number]
export type OticaOrderStatusId = OticaOrderStatus['id']

export const ALLOWED_NEXT: Record<OticaOrderStatusId, OticaOrderStatusId[]> = {
  aguardando: ['em_montagem', 'recusado'],
  em_montagem: ['pronto', 'cancelado'],
  pronto: ['retirado', 'cancelado'],
  retirado: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return OTICA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
