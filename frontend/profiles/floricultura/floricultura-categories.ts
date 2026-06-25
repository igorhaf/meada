/**
 * Categorias de catálogo do perfil floricultura (loja de flores) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/profiles/floricultura/FloriculturaCategory.java.
 *
 * O FloriculturaCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * A CHECK constraint de floricultura_catalog_items.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const FLORICULTURA_CATEGORIES = [
  { id: 'buques', label: 'Buquês' },
  { id: 'arranjos', label: 'Arranjos' },
  { id: 'cestas', label: 'Cestas' },
  { id: 'plantas', label: 'Plantas' },
  { id: 'coroas', label: 'Coroas' },
  { id: 'acessorios', label: 'Acessórios' },
] as const

export type FloriculturaCategory = (typeof FLORICULTURA_CATEGORIES)[number]
export type FloriculturaCategoryId = FloriculturaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return FLORICULTURA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
