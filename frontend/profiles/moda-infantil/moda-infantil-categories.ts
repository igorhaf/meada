/**
 * Categorias de produtos do perfil moda_infantil (roupa de criança / varejo, camada 8.22) —
 * espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/modainfantil/ModaInfantilCategory.java.
 *
 * Clone do lingerie-categories.ts adaptado às categorias de moda infantil. O
 * ModaInfantilCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de
 * moda_infantil_products.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const MODA_INFANTIL_CATEGORIES = [
  { id: 'bebe', label: 'Bebê' },
  { id: 'menino', label: 'Menino' },
  { id: 'menina', label: 'Menina' },
  { id: 'calcados', label: 'Calçados' },
  { id: 'acessorios', label: 'Acessórios' },
  { id: 'pijamas', label: 'Pijamas' },
  { id: 'kits', label: 'Kits' },
] as const

export type ModaInfantilCategory = (typeof MODA_INFANTIL_CATEGORIES)[number]
export type ModaInfantilCategoryId = ModaInfantilCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return MODA_INFANTIL_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
