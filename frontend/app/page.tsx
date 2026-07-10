import { headers } from 'next/headers'
import { redirect } from 'next/navigation'

import { CmsRender } from '@/components/cms/cms-render'
import { institutionalHomeFallback } from '@/lib/cms/institutional-fallback'
import { MEADA_INSTITUTIONAL_SLUG } from '@/lib/cms/meada-institutional'
import { fetchHomeBySlug } from '@/lib/cms/public-fetch'
import { isUniversalSubdomain, SUBDOMAIN_HEADER } from '@/lib/profiles/subdomain'

/**
 * Raiz do host.
 * - {nicho}.meadadigital.com/ → login do nicho (spec #1).
 * - meadadigital.com/ (domínio-base) → CMS do tenant institucional do Meada (editado no painel,
 *   igual aos tenants). Se esse CMS ainda não estiver publicado, cai numa landing mínima de
 *   fallback (a raiz nunca fica quebrada).
 *
 * Subdomínio de empresa ({empresa}.meadadigital.com) é tratado ANTES, no middleware.
 */
export default async function Home() {
  const sub = (await headers()).get(SUBDOMAIN_HEADER) ?? 'meada'
  if (!isUniversalSubdomain(sub)) {
    redirect('/login')
  }

  // Domínio-base → serve o CMS institucional do Meada (mesma maquinaria dos tenants).
  // Se o backend não responder (ex.: produção sem banco vivo), cai no fallback ESTÁTICO
  // (exportado do CMS local) — a raiz nunca fica só com a casca de login. O dinâmico tem
  // precedência: o fallback só entra quando fetchHomeBySlug devolve null.
  const view = (await fetchHomeBySlug(MEADA_INSTITUTIONAL_SLUG)) ?? institutionalHomeFallback()
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
