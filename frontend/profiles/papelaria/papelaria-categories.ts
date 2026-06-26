/**
 * Categorias de catálogo do perfil papelaria (papelaria & convites personalizados, camada 8.15) —
 * espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/papelaria/PapelariaCategory.java.
 *
 * O PapelariaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de papelaria_catalog_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const PAPELARIA_CATEGORIES = [
  { id: 'convites', label: 'Convites' },
  { id: 'save_the_date', label: 'Save the Date' },
  { id: 'cartoes', label: 'Cartões' },
  { id: 'papelaria', label: 'Papelaria' },
  { id: 'adesivos', label: 'Adesivos' },
  { id: 'embalagens', label: 'Embalagens' },
] as const

export type PapelariaCategory = (typeof PAPELARIA_CATEGORIES)[number]
export type PapelariaCategoryId = PapelariaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return PAPELARIA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
