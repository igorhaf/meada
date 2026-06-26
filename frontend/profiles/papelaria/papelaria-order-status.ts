/**
 * Status de um pedido papelaria (convites/impressos sob encomenda, camada 8.15) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/papelaria/orders/PapelariaOrderStatus.java.
 *
 * O PapelariaOrderStatusParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual aos demais perfis).
 * A CHECK constraint de papelaria_orders.status trava os mesmos ids no banco.
 *
 * Gate de aceite humano (nunca da IA) + PROVA DE ARTE (ESCAPADA 8.15: a etapa `arte_aprovacao` em
 * que o lojista sobe a arte e o cliente aprova antes de produzir) + FUNIL QUE DIVERGE pela forma de
 * entrega. Transições:
 *   aguardando     → aceito, recusado, cancelado
 *   aceito         → arte_aprovacao, em_producao, cancelado
 *   arte_aprovacao → em_producao, cancelado
 *   em_producao    → pronto, cancelado
 *   pronto         → retirado (retirada), saiu_entrega (entrega), cancelado
 *   saiu_entrega   → entregue, cancelado
 *   retirado/entregue/recusado/cancelado → (terminal)
 */
export const PAPELARIA_ORDER_STATUSES = [
  { id: 'aguardando', label: 'Aguardando aceite' },
  { id: 'aceito', label: 'Aceito' },
  { id: 'arte_aprovacao', label: 'Aprovação de arte' },
  { id: 'em_producao', label: 'Em produção' },
  { id: 'pronto', label: 'Pronto' },
  { id: 'retirado', label: 'Retirado' },
  { id: 'saiu_entrega', label: 'Saiu pra entrega' },
  { id: 'entregue', label: 'Entregue' },
  { id: 'recusado', label: 'Recusado' },
  { id: 'cancelado', label: 'Cancelado' },
] as const

export type PapelariaOrderStatus = (typeof PAPELARIA_ORDER_STATUSES)[number]
export type PapelariaOrderStatusId = PapelariaOrderStatus['id']

export const ALLOWED_NEXT: Record<PapelariaOrderStatusId, PapelariaOrderStatusId[]> = {
  aguardando: ['aceito', 'recusado', 'cancelado'],
  aceito: ['arte_aprovacao', 'em_producao', 'cancelado'],
  arte_aprovacao: ['em_producao', 'cancelado'],
  em_producao: ['pronto', 'cancelado'],
  pronto: ['retirado', 'saiu_entrega', 'cancelado'],
  saiu_entrega: ['entregue', 'cancelado'],
  retirado: [],
  entregue: [],
  recusado: [],
  cancelado: [],
}

export function statusLabel(id: string): string {
  return PAPELARIA_ORDER_STATUSES.find((s) => s.id === id)?.label ?? id
}
