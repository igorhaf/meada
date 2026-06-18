import { apiFetch } from '@/lib/api/client'
import type { CmsBlock } from '@/lib/cms/cms-block-type'

/**
 * SDK do CMS do TENANT (SM-N), multi-página. Atrás do gate de feature (403 feature_disabled se o
 * nicho não tem CMS). Site (config) + páginas. O render público NÃO usa este SDK — é server-side.
 */

export type CmsTheme = { primaryColor?: string; dark?: boolean }

export type CmsSite = {
  companyId: string
  slug: string
  domain: string | null
  domainVerified: boolean
  verifyToken: string | null
  published: boolean
  theme: CmsTheme
  createdAt: string
  updatedAt: string
}

export type CmsPage = {
  id: string
  companyId: string
  pageSlug: string
  title: string
  blocks: CmsBlock[]
  isHome: boolean
  position: number
  published: boolean
  createdAt: string
  updatedAt: string
}

export type CmsSiteView = { site: CmsSite; pages: CmsPage[] }

export function getCmsSite(): Promise<CmsSiteView> {
  return apiFetch<CmsSiteView>('/api/cms/site')
}

export function setCmsPublished(published: boolean): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/publish', { method: 'PUT', body: JSON.stringify({ published }) })
}

export function setCmsTheme(theme: CmsTheme): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/theme', { method: 'PUT', body: JSON.stringify({ theme }) })
}

export function setCmsDomain(domain: string | null): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/domain', { method: 'PUT', body: JSON.stringify({ domain }) })
}

export function startDomainVerification(): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/verify/start', { method: 'POST' })
}

export function verifyDomain(): Promise<CmsSite> {
  return apiFetch<CmsSite>('/api/cms/site/verify', { method: 'POST' })
}

export function createCmsPage(pageSlug: string, title: string): Promise<CmsPage> {
  return apiFetch<CmsPage>('/api/cms/pages', { method: 'POST', body: JSON.stringify({ pageSlug, title }) })
}

export function saveCmsPage(
  id: string,
  input: { title?: string; blocks?: CmsBlock[]; published?: boolean },
): Promise<CmsPage> {
  return apiFetch<CmsPage>(`/api/cms/pages/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function setCmsHome(id: string): Promise<CmsPage> {
  return apiFetch<CmsPage>(`/api/cms/pages/${id}/home`, { method: 'PUT' })
}

export function deleteCmsPage(id: string): Promise<void> {
  return apiFetch<void>(`/api/cms/pages/${id}`, { method: 'DELETE' })
}
