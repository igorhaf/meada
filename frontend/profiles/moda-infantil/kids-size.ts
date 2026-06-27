/**
 * Tamanhos da grade de variantes do perfil moda_infantil (roupa de criança / varejo, camada 8.22) —
 * espelho 1:1 de src/main/java/com/meada/profiles/modainfantil/KidsSize.java.
 *
 * ESCAPADA da camada (vs lingerie): o eixo de tamanho aqui são FAIXAS ETÁRIAS (age bands), não as
 * letras PP..XGG da lingerie. O KidsSizeParityTest (backend) garante que os ids aqui e no enum Java
 * nunca divergem (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo).
 * id = string estável ASCII (persistida em moda_infantil_variants.size); label = rótulo exibido.
 */
export const KIDS_SIZES = [
  { id: 'RN', label: 'Recém-nascido' },
  { id: '0-3m', label: '0 a 3 meses' },
  { id: '3-6m', label: '3 a 6 meses' },
  { id: '6-9m', label: '6 a 9 meses' },
  { id: '9-12m', label: '9 a 12 meses' },
  { id: '1a', label: '1 ano' },
  { id: '2a', label: '2 anos' },
  { id: '3a', label: '3 anos' },
  { id: '4a', label: '4 anos' },
  { id: '6a', label: '6 anos' },
  { id: '8a', label: '8 anos' },
  { id: '10a', label: '10 anos' },
  { id: '12a', label: '12 anos' },
] as const

export type KidsSize = (typeof KIDS_SIZES)[number]
export type KidsSizeId = KidsSize['id']

/** Rótulo de um tamanho/faixa etária (fallback: o próprio id se desconhecido). */
export function sizeLabel(id: string): string {
  return KIDS_SIZES.find((s) => s.id === id)?.label ?? id
}
