import type { CmsBlock } from './cms-block-type'

/**
 * Fetch SERVER-SIDE da página pública do CMS (SM-N). Roda no servidor Next; usa a base INTERNA do
 * backend (CMS_BACKEND_URL → http://backend:8095 no compose; cai p/ NEXT_PUBLIC_API_URL e localhost).
 * Sem auth — /public/cms/**. A view inclui tema + nav (páginas publicadas) pro render montar o menu.
 */

export type CmsNavItem = { pageSlug: string; title: string; isHome: boolean }
export type CmsThemePreset = 'meada-dark'
export type CmsTheme = { primaryColor?: string; dark?: boolean; preset?: CmsThemePreset }
export type PublicCmsView = { title: string; blocks: CmsBlock[]; theme: CmsTheme | null; nav: CmsNavItem[] }

function backendBase(): string {
  return process.env.CMS_BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8095'
}

async function fetchPublic(path: string): Promise<PublicCmsView | null> {
  try {
    const res = await fetch(`${backendBase()}${path}`, { cache: 'no-store' })
    if (!res.ok) return null
    return (await res.json()) as PublicCmsView
  } catch {
    return null
  }
}

export function fetchHomeBySlug(slug: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-slug/${encodeURIComponent(slug)}`)
}

export function fetchPageBySlug(slug: string, pageSlug: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-slug/${encodeURIComponent(slug)}/${encodeURIComponent(pageSlug)}`)
}

export function fetchHomeByDomain(host: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-domain?host=${encodeURIComponent(host)}`)
}

export function fetchPageByDomain(host: string, pageSlug: string): Promise<PublicCmsView | null> {
  return fetchPublic(`/public/cms/by-domain/${encodeURIComponent(pageSlug)}?host=${encodeURIComponent(host)}`)
}
