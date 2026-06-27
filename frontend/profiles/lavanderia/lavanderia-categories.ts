/**
 * Categorias de serviço do perfil lavanderia — espelho 1:1 de
 * src/main/java/com/meada/profiles/lavanderia/LavanderiaServiceCategory.java.
 *
 * O LavanderiaServiceCategoryParityTest (backend) garante que os ids aqui e no enum Java nunca
 * divergem (o teste casa textualmente cada objeto `{ id: '...' }` deste arquivo). A CHECK constraint de
 * lavanderia_services.category trava os mesmos ids no banco.
 * id = string estável ASCII sem acento (persistida); label = rótulo pt-BR exibido.
 */
export const LAVANDERIA_CATEGORIES = [
  { id: 'lavar', label: 'Lavar' },
  { id: 'lavar_passar', label: 'Lavar e passar' },
  { id: 'lavagem_seco', label: 'Lavagem a seco' },
  { id: 'passar', label: 'Passar' },
  { id: 'edredom_pesados', label: 'Edredom e pesados' },
] as const

export type LavanderiaCategory = (typeof LAVANDERIA_CATEGORIES)[number]
export type LavanderiaCategoryId = LavanderiaCategory['id']

/** Rótulo pt-BR de uma categoria (fallback: o próprio id se desconhecido). */
export function categoryLabel(id: string): string {
  return LAVANDERIA_CATEGORIES.find((c) => c.id === id)?.label ?? id
}
