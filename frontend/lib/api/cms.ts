import { apiFetch } from '@/lib/api/client'
import type { CmsBlock } from '@/lib/cms/cms-block-type'

/**
 * SDK do CMS do TENANT (SM-M). Edição da própria página, atrás do gate de feature (o backend
 * retorna 403 feature_disabled se o nicho não tem o CMS ligado). O render público NÃO usa este SDK
 * — é server-side, via /public/cms (ver app/p/).
 */

export type CmsPage = {
  companyId: string
  slug: string
  domain: string | null
  published: boolean
  title: string
  blocks: CmsBlock[]
  createdAt: string
  updatedAt: string
}

export function getCmsPage(): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/page')
}

export function saveCmsContent(title: string, blocks: CmsBlock[]): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/page', { method: 'PUT', body: JSON.stringify({ title, blocks }) })
}

export function setCmsPublished(published: boolean): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/page/publish', { method: 'PUT', body: JSON.stringify({ published }) })
}

export function setCmsDomain(domain: string | null): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/page/domain', { method: 'PUT', body: JSON.stringify({ domain }) })
}
