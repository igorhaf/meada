/**
 * Forma de entrega de um pedido papelaria (camada 8.15) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/papelaria/PapelariaFulfillment.java.
 *
 * O pedido é RETIRADA (o cliente busca na loja) ou ENTREGA (delivery no endereço). O fluxo do
 * Kanban diverge no fim por causa disso: pronto → retirado (retirada) ou pronto → saiu_entrega →
 * entregue (entrega).
 * O PapelariaFulfillmentParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK de papelaria_orders.fulfillment trava os mesmos ids no banco.
 */
export const PAPELARIA_FULFILLMENTS = [
  { id: 'retirada', label: 'Retirada' },
  { id: 'entrega', label: 'Entrega' },
] as const

export type PapelariaFulfillment = (typeof PAPELARIA_FULFILLMENTS)[number]
export type PapelariaFulfillmentId = PapelariaFulfillment['id']

/** Rótulo pt-BR de uma forma de entrega (fallback: o próprio id se desconhecido). */
export function fulfillmentLabel(id: string): string {
  return PAPELARIA_FULFILLMENTS.find((f) => f.id === id)?.label ?? id
}
