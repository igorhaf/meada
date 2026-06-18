import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { fetchPageByDomain } from '@/lib/cms/public-fetch'

/**
 * Página PÚBLICA do CMS por DOMÍNIO custom do tenant (SM-M). Destino interno do rewrite do middleware
 * quando o host NÃO é um domínio do Meada: /p/by-domain/{host}. Resolve o tenant pelo cms_pages.domain.
 * Server component, sem shell, sem auth. 404 se não há página publicada nesse domínio.
 */
export default async function PublicCmsByDomainPage({ params }: { params: Promise<{ host: string }> }) {
  const { host } = await params
  const page = await fetchPageByDomain(host)
  if (!page) {
    notFound()
  }
  return <CmsRender title={page.title} blocks={page.blocks} />
}
