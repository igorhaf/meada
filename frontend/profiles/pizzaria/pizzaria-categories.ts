/**
 * Categorias de cardápio do perfil pizzaria (delivery iFood-style + meio-a-meio) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/pizzaria/PizzariaCategory.java.
 *
 * O PizzariaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de pizzaria_menu_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const PIZZARIA_CATEGORIES = [
  { id: 'pizzas_salgadas', label: 'Pizzas Salgadas' },
  { id: 'pizzas_doces', label: 'Pizzas Doces' },
  { id: 'bordas', label: 'Bordas' },
  { id: 'bebidas', label: 'Bebidas' },
  { id: 'sobremesas', label: 'Sobremesas' },
  { id: 'combos', label: 'Combos' },
] as const

export type PizzariaCategory = (typeof PIZZARIA_CATEGORIES)[number]
export type PizzariaCategoryId = PizzariaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return PIZZARIA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
