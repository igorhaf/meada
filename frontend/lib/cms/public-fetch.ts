import type { CmsBlock } from './cms-block-type'

/**
 * Fetch SERVER-SIDE da página pública do CMS (SM-M). Roda no servidor Next (não no browser), então
 * usa a base INTERNA do backend: CMS_BACKEND_URL (ex.: http://backend:8095 dentro do compose). Cai
 * para NEXT_PUBLIC_API_URL e por fim http://localhost:8095 (dev sem Docker). Sem auth — bate em
 * /public/cms/**, que não passa pelo filtro de JWT.
 */

export type PublicCmsPage = { title: string; blocks: CmsBlock[] }

function backendBase(): string {
  return (
    process.env.CMS_BACKEND_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    'http://localhost:8095'
  )
}

async function fetchPublic(path: string): Promise<PublicCmsPage | null> {
  try {
    const res = await fetch(`${backendBase()}${path}`, { cache: 'no-store' })
    if (!res.ok) return null
    return (await res.json()) as PublicCmsPage
  } catch {
    return null
  }
}

export function fetchPageBySlug(slug: string): Promise<PublicCmsPage | null> {
  return fetchPublic(`/public/cms/by-slug/${encodeURIComponent(slug)}`)
}

export function fetchPageByDomain(host: string): Promise<PublicCmsPage | null> {
  return fetchPublic(`/public/cms/by-domain?host=${encodeURIComponent(host)}`)
}
