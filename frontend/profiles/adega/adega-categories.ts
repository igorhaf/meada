/**
 * Categorias de cardápio do perfil adega (delivery de bebidas alcoólicas, camada 8.9) —
 * espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/adega/AdegaCategory.java.
 *
 * Clone do comida-categories.ts adaptado às categorias de adega. O AdegaCategoryParityTest
 * (backend) garante que os ids aqui e no enum Java nunca divergem (o teste casa textualmente
 * cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de adega_menu_items.category
 * trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const ADEGA_CATEGORIES = [
  { id: 'vinhos', label: 'Vinhos' },
  { id: 'espumantes', label: 'Espumantes' },
  { id: 'cervejas', label: 'Cervejas' },
  { id: 'destilados', label: 'Destilados' },
  { id: 'sem_alcool', label: 'Sem Álcool' },
  { id: 'acessorios', label: 'Acessórios' },
] as const

export type AdegaCategory = (typeof ADEGA_CATEGORIES)[number]
export type AdegaCategoryId = AdegaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return ADEGA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
