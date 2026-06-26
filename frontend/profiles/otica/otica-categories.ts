/**
 * Categorias de catálogo do perfil otica (loja de ótica) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/otica/OticaCategory.java.
 *
 * O OticaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de otica_catalog_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const OTICA_CATEGORIES = [
  { id: 'armacoes', label: 'Armações' },
  { id: 'lentes', label: 'Lentes' },
  { id: 'acessorios', label: 'Acessórios' },
] as const

export type OticaCategory = (typeof OTICA_CATEGORIES)[number]
export type OticaCategoryId = OticaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return OTICA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
