import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchPageBySlug } from '@/lib/cms/public-fetch'

/**
 * Página PÚBLICA do CMS por slug do tenant (SM-M): /p/{slug}. Server component, sem o shell do
 * painel, sem auth. 404 se a página não existe ou está em rascunho. É também o destino do middleware
 * quando um domínio custom resolve (via /p/by-domain/[host]).
 */
export default async function PublicCmsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const page = await fetchPageBySlug(slug)
  if (!page) {
    notFound()
  }
  return <CmsRender title={page.title} blocks={page.blocks} />
}
