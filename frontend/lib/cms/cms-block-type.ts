/**
 * Catálogo HARDCODED de tipos de bloco do CMS (SM-M, page builder) — espelho 1:1 de
 * src/main/java/com/meada/whatsapp/cms/CmsBlockType.java.
 *
 * O CmsBlockTypeParityTest (backend) garante que os ids aqui e no enum Java nunca divergem.
 * Cada bloco de uma página é { id, type, props }; type é um destes. As props variam por tipo
 * (ver CmsBlock abaixo). Adicionar um tipo = editar os 2 arquivos + o editor/render + a paridade.
 */
export const CMS_BLOCK_TYPES = [
  { id: 'hero', label: 'Destaque (Hero)' },
  { id: 'text', label: 'Texto' },
  { id: 'services', label: 'Serviços' },
  { id: 'contact', label: 'Contato' },
  { id: 'gallery', label: 'Galeria' },
  { id: 'faq', label: 'Perguntas (FAQ)' },
  { id: 'testimonials', label: 'Depoimentos' },
  { id: 'map', label: 'Mapa' },
] as const

export type CmsBlockTypeId = (typeof CMS_BLOCK_TYPES)[number]['id']

/** Rótulo pt-BR de um tipo de bloco (fallback: o próprio id). */
export function blockTypeLabel(id: string): string {
  return CMS_BLOCK_TYPES.find((t) => t.id === id)?.label ?? id
}

// ---- Props por tipo (shape do que vai em block.props) -----------------------

export type HeroProps = { title: string; subtitle: string; buttonLabel: string; buttonHref: string }
export type TextProps = { body: string } // markdown livre
export type ServiceItem = { name: string; description: string; price: string }
export type ServicesProps = { title: string; items: ServiceItem[] }
export type ContactProps = { phone: string; whatsapp: string; address: string; hours: string }
export type GalleryImage = { url: string; caption: string }
export type GalleryProps = { title: string; images: GalleryImage[] }
export type FaqItem = { question: string; answer: string }
export type FaqProps = { title: string; items: FaqItem[] }
export type Testimonial = { name: string; text: string; rating: string }
export type TestimonialsProps = { title: string; items: Testimonial[] }
export type MapProps = { title: string; address: string; embedUrl: string }

export type CmsBlock =
  | { id: string; type: 'hero'; props: HeroProps }
  | { id: string; type: 'text'; props: TextProps }
  | { id: string; type: 'services'; props: ServicesProps }
  | { id: string; type: 'contact'; props: ContactProps }
  | { id: string; type: 'gallery'; props: GalleryProps }
  | { id: string; type: 'faq'; props: FaqProps }
  | { id: string; type: 'testimonials'; props: TestimonialsProps }
  | { id: string; type: 'map'; props: MapProps }

/** Props default ao adicionar um bloco novo do tipo dado. */
export function defaultProps(type: CmsBlockTypeId): CmsBlock['props'] {
  switch (type) {
    case 'hero':
      return { title: '', subtitle: '', buttonLabel: '', buttonHref: '' }
    case 'text':
      return { body: '' }
    case 'services':
      return { title: 'Serviços', items: [] }
    case 'contact':
      return { phone: '', whatsapp: '', address: '', hours: '' }
    case 'gallery':
      return { title: 'Galeria', images: [] }
    case 'faq':
      return { title: 'Perguntas frequentes', items: [] }
    case 'testimonials':
      return { title: 'Depoimentos', items: [] }
    case 'map':
      return { title: 'Onde estamos', address: '', embedUrl: '' }
  }
}
