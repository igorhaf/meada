/**
 * Tamanhos da grade de variantes do perfil lingerie (moda íntima / varejo, camada 8.21) —
 * espelho 1:1 de src/main/java/com/meada/whatsapp/profiles/lingerie/LingerieSize.java.
 *
 * A grade de variantes (tamanho × cor) inaugurada nesta camada usa estes tamanhos. O
 * LingerieSizeParityTest (backend) garante que os ids aqui e no enum Java nunca divergem
 * (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo, igual às categorias).
 * id = string estável ASCII (persistida em lingerie_variants.size); label = rótulo exibido.
 */
export const LINGERIE_SIZES = [
  { id: 'PP', label: 'PP' },
  { id: 'P', label: 'P' },
  { id: 'M', label: 'M' },
  { id: 'G', label: 'G' },
  { id: 'GG', label: 'GG' },
  { id: 'XGG', label: 'XGG' },
] as const

export type LingerieSize = (typeof LINGERIE_SIZES)[number]
export type LingerieSizeId = LingerieSize['id']

/** Rótulo de um tamanho (fallback: o próprio id se desconhecido). */
export function sizeLabel(id: string): string {
  return LINGERIE_SIZES.find((s) => s.id === id)?.label ?? id
}
