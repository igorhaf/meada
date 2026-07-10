import { normalizeToTree } from './cms-tree'
import fallback from './meada-institutional-fallback.json'
import type { PublicCmsView } from './public-fetch'

/**
 * Fallback ESTÁTICO do site institucional do Meada (company-âncora `meada`), exportado do CMS
 * local em 2026-07-10 (`/public/cms/by-slug/meada[/{slug}]`). Usado quando o backend NÃO devolve
 * a página — ex.: produção sem banco vivo — para a raiz nunca ficar só com a casca "Entrar no
 * painel". Quando o backend voltar a servir o CMS, a versão DINÂMICA tem precedência (o fallback
 * só entra no `null`). Para regerar: reexportar do backend e substituir o .json.
 */
type RawView = {
  title: string
  blocks: unknown
  theme: PublicCmsView['theme']
  nav: PublicCmsView['nav']
}

const data = fallback as { home: RawView; pages: Record<string, RawView> }

function toView(r: RawView): PublicCmsView {
  return {
    title: r.title,
    blocks: normalizeToTree(r.blocks),
    theme: r.theme ?? null,
    nav: r.nav ?? [],
  }
}

/** View da HOME institucional (sempre existe — vem do .json versionado). */
export function institutionalHomeFallback(): PublicCmsView {
  return toView(data.home)
}

/** View de uma subpágina institucional pelo slug, ou null se não houver no export. */
export function institutionalPageFallback(pageSlug: string): PublicCmsView | null {
  const p = data.pages[pageSlug]
  return p ? toView(p) : null
}
