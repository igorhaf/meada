/**
 * Categorias de produtos do perfil lingerie (moda íntima / varejo, camada 8.21) —
 * espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/lingerie/LingerieCategory.java.
 *
 * Clone do adega-categories.ts adaptado às categorias de lingerie. O LingerieCategoryParityTest
 * (backend) garante que os ids aqui e no enum Java nunca divergem (o teste casa textualmente
 * cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de lingerie_products.category
 * trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const LINGERIE_CATEGORIES = [
  { id: 'sutias', label: 'Sutiãs' },
  { id: 'calcinhas', label: 'Calcinhas' },
  { id: 'conjuntos', label: 'Conjuntos' },
  { id: 'pijamas', label: 'Pijamas' },
  { id: 'modeladores', label: 'Modeladores' },
  { id: 'meias', label: 'Meias' },
  { id: 'acessorios', label: 'Acessórios' },
] as const

export type LingerieCategory = (typeof LINGERIE_CATEGORIES)[number]
export type LingerieCategoryId = LingerieCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return LINGERIE_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
