import { headers } from 'next/headers'
import { notFound } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { institutionalPageFallback } from '@/lib/cms/institutional-fallback'
import { MEADA_INSTITUTIONAL_SLUG } from '@/lib/cms/meada-institutional'
import { fetchPageBySlug } from '@/lib/cms/public-fetch'
import { isUniversalSubdomain, SUBDOMAIN_HEADER } from '@/lib/profiles/subdomain'

/**
 * Subpágina institucional do Meada na RAIZ do domínio-base: meadadigital.com/{pageSlug}
 * (ex.: /servicos, /sobre, /contato, /portfolio) → serve a página do CMS da company-âncora.
 *
 * Catch-all de 1 segmento: o Next prioriza rotas estáticas (/login, /dashboard, /admin,
 * /auth/...) sobre esta dinâmica, então só cai aqui um slug que NÃO é rota conhecida.
 * Em subdomínio de nicho/empresa este path não institucional → 404 (o middleware já tratou
 * empresa antes; nicho não tem subpágina institucional). 404 se a página não existe/publicada.
 */
export default async function InstitutionalPage({
  params,
}: {
  params: Promise<{ pageSlug: string }>
}) {
  const sub = (await headers()).get(SUBDOMAIN_HEADER) ?? 'meada'
  if (!isUniversalSubdomain(sub)) {
    notFound()
  }
  const { pageSlug } = await params
  // Backend primeiro; se não responder (ex.: produção sem banco), cai no fallback ESTÁTICO
  // exportado do CMS local. Slug que não existe em nenhum dos dois → 404.
  const view =
    (await fetchPageBySlug(MEADA_INSTITUTIONAL_SLUG, pageSlug)) ??
    institutionalPageFallback(pageSlug)
  if (!view) {
    notFound()
  }
  return (
    <CmsRender
      title={view.title}
      blocks={view.blocks}
      theme={view.theme}
      nav={view.nav}
      navBase=""
    />
  )
}
