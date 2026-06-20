'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Menu, MessagesSquare, Settings, X } from 'lucide-react'
import Link from 'next/link'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { Section } from '@/components/ui/card'
import { FieldRenderer } from '@/components/cms/field-renderer'
import { renderCmsBlock, cmsShellStyle } from '@/components/cms/cms-render'
import { allBlockSchemas, blockSchema } from '@/lib/cms/cms-block-schemas'
import { blockTypeLabel, defaultProps, type CmsBlock, type CmsBlockTypeId } from '@/lib/cms/cms-block-type'
import {
  createCmsPage,
  deleteCmsPage,
  getCmsSite,
  saveCmsPage,
  setCmsDomain,
  setCmsHome,
  setCmsPublished,
  setCmsTheme,
  startDomainVerification,
  verifyDomain,
  type CmsPage,
  type CmsSiteView,
} from '@/lib/api/cms'
import { useCmsBack } from '@/lib/cms/use-cms-return'
import { cn } from '@/lib/utils'

function newId(): string {
  return 'b-' + Math.random().toString(36).slice(2, 10)
}

/**
 * Editor do CMS multi-página (SM-N) — TELA CHEIA. O AppShell esconde o shell admin nas rotas
 * /dashboard/cms; aqui montamos o editor próprio: topbar (logo Meada→/dashboard + Voltar + seletor de
 * página + Configurações + Salvar + Publicar), sidebar ESQUERDO de blocos (clique-toggle, empurra o
 * preview), PREVIEW central (blocos reais), e painel DIREITO de propriedades (overlay ao clicar num
 * bloco; fecha com X / Escape / clicar-fora). Configs de site (Páginas/Tema/Domínio) num MODAL.
 */
export default function CmsEditorPage() {
  const qc = useQueryClient()
  const back = useCmsBack()
  const { data, isPending, isError, error } = useQuery<CmsSiteView>({ queryKey: ['cms-site'], queryFn: getCmsSite })

  const [selectedId, setSelectedId] = useState<string | null>(null) // página selecionada
  const [title, setTitle] = useState('')
  const [blocks, setBlocks] = useState<CmsBlock[]>([])
  const [pagePublished, setPagePublished] = useState(false)
  const [savedAt, setSavedAt] = useState<string | null>(null)

  // estado do EDITOR (tela-cheia)
  const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null) // bloco — inicia null (overlay só ao clicar)
  const [leftOpen, setLeftOpen] = useState(true)
  const [showCatalog, setShowCatalog] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)

  const [domain, setDomain] = useState('')
  const [domainError, setDomainError] = useState<string | null>(null)
  const [primaryColor, setPrimaryColor] = useState('#0f172a')
  const [dark, setDark] = useState(false)

  const [newSlug, setNewSlug] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  const site = data?.site
  const pages = data?.pages ?? []
  const selected = pages.find((p) => p.id === selectedId) ?? null

  const selectedBlock = blocks.find((b) => b.id === selectedBlockId) ?? null
  const blockSchemaSel = selectedBlock ? blockSchema(selectedBlock.type) : undefined

  // sincroniza estado do site quando carrega.
  useEffect(() => {
    if (site) {
      setDomain(site.domain ?? '')
      setPrimaryColor(site.theme?.primaryColor ?? '#0f172a')
      setDark(site.theme?.dark === true)
    }
  }, [site])

  // seleciona a 1ª página (home preferida) ao carregar.
  useEffect(() => {
    if (pages.length > 0 && (selectedId === null || !pages.some((p) => p.id === selectedId))) {
      const home = pages.find((p) => p.isHome) ?? pages[0]
      setSelectedId(home.id)
    }
  }, [pages, selectedId])

  // carrega o conteúdo da página selecionada no editor; zera o bloco selecionado (troca de página).
  useEffect(() => {
    if (selected) {
      setTitle(selected.title)
      setBlocks(selected.blocks ?? [])
      setPagePublished(selected.published)
    }
    setSelectedBlockId(null)
  }, [selectedId]) // eslint-disable-line react-hooks/exhaustive-deps

  // se o bloco selecionado sumiu dos blocks atuais, fecha o painel direito.
  useEffect(() => {
    if (selectedBlockId && !blocks.some((b) => b.id === selectedBlockId)) setSelectedBlockId(null)
  }, [blocks, selectedBlockId])

  // Escape fecha o painel direito.
  useEffect(() => {
    if (!selectedBlock) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setSelectedBlockId(null) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [selectedBlock])

  const savePageMut = useMutation({
    mutationFn: () => {
      if (!selectedId) throw new Error('sem página')
      return saveCmsPage(selectedId, { title, blocks, published: pagePublished })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSavedAt(new Date().toLocaleTimeString('pt-BR'))
    },
  })
  const createPageMut = useMutation({
    mutationFn: () => createCmsPage(newSlug, newTitle),
    onSuccess: (p: CmsPage) => {
      qc.invalidateQueries({ queryKey: ['cms-site'] })
      setSelectedId(p.id); setNewSlug(''); setNewTitle(''); setCreateError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'page_slug_taken') setCreateError('Já existe uma página com esse endereço.')
      else if (e instanceof ApiError && e.reason === 'invalid_page_slug') setCreateError('Endereço inválido (use letras, números e hífen).')
      else if (e instanceof ApiError && e.reason === 'too_many_pages') setCreateError('Limite de páginas atingido.')
      else setCreateError('Erro ao criar a página.')
    },
  })
  const deletePageMut = useMutation({
    mutationFn: (id: string) => deleteCmsPage(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cms-site'] }); setSelectedId(null) },
  })
  const setHomeMut = useMutation({
    mutationFn: (id: string) => setCmsHome(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const publishMut = useMutation({
    mutationFn: (p: boolean) => setCmsPublished(p),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const themeMut = useMutation({
    mutationFn: () => setCmsTheme({ primaryColor, dark }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }),
  })
  const domainMut = useMutation({
    mutationFn: () => setCmsDomain(domain.trim() === '' ? null : domain.trim()),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['cms-site'] }); setDomainError(null) },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'domain_taken') setDomainError('Esse domínio já está em uso.')
      else if (e instanceof ApiError && e.reason === 'invalid_domain') setDomainError('Domínio inválido (ex.: minhaempresa.com.br).')
      else setDomainError('Erro ao salvar o domínio.')
    },
  })
  const verifyStartMut = useMutation({ mutationFn: () => startDomainVerification(), onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }) })
  const verifyMut = useMutation({ mutationFn: () => verifyDomain(), onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-site'] }) })

  // ---- ações de bloco ----
  function updateBlockProps(id: string, props: CmsBlock['props']) {
    setBlocks((bs) => bs.map((b) => (b.id === id ? ({ ...b, props } as CmsBlock) : b)))
  }
  function moveBlock(id: string, dir: -1 | 1) {
    setBlocks((bs) => {
      const i = bs.findIndex((b) => b.id === id)
      const target = i + dir
      if (i < 0 || target < 0 || target >= bs.length) return bs
      const next = [...bs]
      ;[next[i], next[target]] = [next[target], next[i]]
      return next
    })
  }
  function removeBlock(id: string) {
    setBlocks((bs) => bs.filter((b) => b.id !== id))
    if (selectedBlockId === id) setSelectedBlockId(null)
  }
  function addBlock(type: CmsBlockTypeId) {
    const block = { id: newId(), type, props: defaultProps(type) } as CmsBlock
    setBlocks((bs) => [...bs, block])
    setSelectedBlockId(block.id)
    setShowCatalog(false)
  }

  // ---- estados de borda ----
  if (isError && error instanceof ApiError && error.reason === 'feature_disabled') {
    return (
      <div className="mx-auto max-w-3xl space-y-6 p-8">
        <PageHeader title="Site" description="Este recurso não está habilitado para o seu plano." />
        <Link href="/dashboard"><Button variant="outline">Voltar ao dashboard</Button></Link>
      </div>
    )
  }
  if (isPending || !site) {
    return <div className="flex h-full items-center justify-center text-sm text-muted-foreground">Carregando…</div>
  }

  const shell = cmsShellStyle({ primaryColor, dark })

  return (
    <div className="flex h-full flex-col">
      {/* ---- TOPBAR ---- */}
      <div className="flex shrink-0 items-center gap-3 border-b border-border px-3 py-2">
        {/* logo Meada — 2ª saída sempre disponível */}
        <Link href="/dashboard" className="flex items-center gap-2 text-sm font-semibold" aria-label="Ir para o dashboard">
          <MessagesSquare className="size-5 text-primary" /> Meada
        </Link>
        <Button variant="ghost" size="sm" onClick={back}>← Voltar</Button>
        <div className="h-5 w-px bg-border" />
        {/* seletor de página */}
        <select
          value={selectedId ?? ''}
          onChange={(e) => setSelectedId(e.target.value)}
          className="rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          {pages.map((p) => (
            <option key={p.id} value={p.id}>{p.title || p.pageSlug}{p.isHome ? ' (home)' : ''}{!p.published ? ' · rascunho' : ''}</option>
          ))}
        </select>
        {savedAt && <span className="text-xs text-muted-foreground">Salvo às {savedAt}</span>}

        <div className="ml-auto flex items-center gap-2">
          <Badge variant={site.published ? 'success' : 'muted'}>{site.published ? 'publicado' : 'rascunho'}</Badge>
          <Button variant="outline" size="sm" onClick={() => setSettingsOpen(true)}>
            <Settings className="size-4" /> Configurações
          </Button>
          <Button size="sm" disabled={savePageMut.isPending || !selected} onClick={() => savePageMut.mutate()}>
            {savePageMut.isPending ? 'Salvando…' : 'Salvar página'}
          </Button>
          <Button variant="outline" size="sm" disabled={publishMut.isPending} onClick={() => publishMut.mutate(!site.published)}>
            {site.published ? 'Despublicar' : 'Publicar site'}
          </Button>
        </div>
      </div>

      {/* ---- CORPO: esquerdo (push) + preview + direito (overlay) ---- */}
      <div className="relative flex min-h-0 flex-1">
        {/* ESQUERDO — blocos (empurra o preview) */}
        <aside className={cn(
          'shrink-0 overflow-hidden border-r border-border transition-[width] duration-200 ease-out',
          leftOpen ? 'w-64' : 'w-0 border-r-0',
        )}>
          <div className="flex h-full w-64 flex-col">
            <div className="border-b border-border p-3">
              <Button className="w-full" onClick={() => setShowCatalog(true)}>+ Adicionar bloco</Button>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
              {blocks.length === 0 && (
                <p className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">Nenhum bloco ainda.</p>
              )}
              <ul className="space-y-1">
                {blocks.map((b, i) => {
                  const s = blockSchema(b.type)
                  const isSel = b.id === selectedBlockId
                  return (
                    <li key={b.id}>
                      <div className={cn('group flex items-center gap-1 rounded-md border px-2 py-2 text-sm', isSel ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50')}>
                        <button type="button" className="flex min-w-0 flex-1 items-center gap-2 text-left" onClick={() => setSelectedBlockId(b.id)}>
                          <span aria-hidden>{s?.emoji ?? '▫️'}</span>
                          <span className="truncate">{s?.label ?? blockTypeLabel(b.type)}</span>
                        </button>
                        <span className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                          <button type="button" onClick={() => moveBlock(b.id, -1)} disabled={i === 0} className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Subir">↑</button>
                          <button type="button" onClick={() => moveBlock(b.id, 1)} disabled={i === blocks.length - 1} className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Descer">↓</button>
                          <button type="button" onClick={() => removeBlock(b.id)} className="rounded px-1 text-xs text-destructive hover:bg-destructive/10" aria-label="Excluir">✕</button>
                        </span>
                      </div>
                    </li>
                  )
                })}
              </ul>
            </div>
          </div>
        </aside>

        {/* abinha (clique-toggle) — acompanha a borda do aside esquerdo */}
        <button
          type="button"
          onClick={() => setLeftOpen((v) => !v)}
          aria-label={leftOpen ? 'Recolher blocos' : 'Abrir blocos'}
          className="absolute top-1/2 z-20 flex h-16 w-6 -translate-y-1/2 items-center justify-center rounded-r-xl border border-l-0 border-border bg-background text-muted-foreground shadow-md transition-[left] duration-200 hover:bg-muted hover:text-foreground"
          style={{ left: leftOpen ? '16rem' : '0' }}
        >
          {leftOpen ? <X className="size-4" /> : <Menu className="size-4" />}
        </button>

        {/* PREVIEW — clicar em área neutra (alvo = container) fecha o painel direito */}
        <div
          className="min-w-0 flex-1 overflow-auto"
          onClick={(e) => { if (e.target === e.currentTarget) setSelectedBlockId(null) }}
        >
          {blocks.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
              Adicione blocos pela barra à esquerda — o preview aparece aqui.
            </div>
          ) : (
            <div style={shell} onClick={(e) => { if (e.target === e.currentTarget) setSelectedBlockId(null) }}>
              {blocks.map((b) => {
                const isSel = b.id === selectedBlockId
                return (
                  <div key={b.id} onClick={() => setSelectedBlockId(b.id)} className={cn('relative cursor-pointer', isSel && 'ring-2 ring-inset ring-primary')}>
                    <div className="pointer-events-none">{renderCmsBlock(b)}</div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* DIREITO — propriedades (overlay absolute ao container; NÃO cobre a topbar) */}
        {selectedBlock && blockSchemaSel && (
          <aside className="absolute inset-y-0 right-0 z-40 flex w-[340px] flex-col overflow-y-auto border-l border-border bg-card shadow-xl">
            <div className="flex items-center justify-between border-b border-border px-4 py-3">
              <span className="flex items-center gap-2 font-medium"><span aria-hidden>{blockSchemaSel.emoji}</span> {blockSchemaSel.label}</span>
              <button type="button" onClick={() => setSelectedBlockId(null)} aria-label="Fechar"><X className="size-4" /></button>
            </div>
            <div className="space-y-3 p-4">
              <p className="text-xs text-muted-foreground">{blockSchemaSel.description}</p>
              {blockSchemaSel.fields.map((f) => (
                <FieldRenderer
                  key={f.key}
                  field={f}
                  value={(selectedBlock.props as Record<string, unknown>)[f.key]}
                  onChange={(v) => updateBlockProps(selectedBlock.id, { ...selectedBlock.props, [f.key]: v } as CmsBlock['props'])}
                />
              ))}
            </div>
          </aside>
        )}
      </div>

      {/* ---- CATÁLOGO ---- */}
      <Modal open={showCatalog} onClose={() => setShowCatalog(false)} title="Adicionar bloco" size="lg">
        <div className="grid gap-3 sm:grid-cols-2">
          {allBlockSchemas().map((s) => (
            <button key={s.type} type="button" onClick={() => addBlock(s.type)}
              className="rounded-lg border border-border p-4 text-left transition-colors hover:border-primary hover:bg-primary/5">
              <div className="flex items-center gap-2 font-medium"><span aria-hidden className="text-lg">{s.emoji}</span> {s.label}</div>
              <p className="mt-1 text-xs text-muted-foreground">{s.description}</p>
            </button>
          ))}
        </div>
      </Modal>

      {/* ---- CONFIGURAÇÕES DO SITE (Páginas / Tema / Domínio) ---- */}
      <Modal open={settingsOpen} onClose={() => setSettingsOpen(false)} title="Configurações do site" size="lg">
        <div className="space-y-6">
          {/* Página atual: título / home / publicar / excluir */}
          {selected && (
            <Section title={`Página: ${selected.pageSlug}`}>
              <div className="space-y-3">
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Título da página</label>
                  <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Título"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-1 text-xs text-muted-foreground">
                    <input type="checkbox" checked={pagePublished} onChange={(e) => setPagePublished(e.target.checked)} /> página publicada
                  </label>
                  {!selected.isHome && (
                    <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={setHomeMut.isPending}
                      onClick={() => setHomeMut.mutate(selected.id)}>Definir como home</Button>
                  )}
                  <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={deletePageMut.isPending}
                    onClick={() => { deletePageMut.mutate(selected.id); setSettingsOpen(false) }}>Excluir página</Button>
                </div>
              </div>
            </Section>
          )}

          {/* Páginas: lista + criar */}
          <Section title="Páginas">
            <div className="flex flex-wrap gap-2">
              {pages.map((p) => (
                <button key={p.id} onClick={() => setSelectedId(p.id)}
                  className={'rounded-md border px-3 py-1.5 text-sm ' + (p.id === selectedId ? 'border-primary bg-primary/10' : 'border-border')}>
                  {p.title || p.pageSlug}
                  {p.isHome && <span className="ml-1 text-xs text-muted-foreground">(home)</span>}
                  {!p.published && <span className="ml-1 text-xs text-amber-600">rascunho</span>}
                </button>
              ))}
            </div>
            <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border pt-4">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Endereço (slug)</label>
                <input value={newSlug} onChange={(e) => setNewSlug(e.target.value)} placeholder="servicos"
                  className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Título</label>
                <input value={newTitle} onChange={(e) => setNewTitle(e.target.value)} placeholder="Serviços"
                  className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </div>
              <Button type="button" variant="outline" disabled={createPageMut.isPending || !newSlug.trim()}
                onClick={() => createPageMut.mutate()}>Nova página</Button>
            </div>
            {createError && <p className="mt-2 text-sm text-destructive">{createError}</p>}
          </Section>

          {/* Tema */}
          <Section title="Tema">
            <div className="flex flex-wrap items-end gap-4">
              <div>
                <label className="mb-1 block text-xs font-medium text-muted-foreground">Cor primária</label>
                <input type="color" value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)}
                  className="h-9 w-16 rounded-md border border-border bg-background" />
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={dark} onChange={(e) => setDark(e.target.checked)} /> Fundo escuro
              </label>
              <Button type="button" variant="outline" disabled={themeMut.isPending} onClick={() => themeMut.mutate()}>Salvar tema</Button>
            </div>
          </Section>

          {/* Domínio */}
          <Section title="Domínio próprio (opcional)">
            <div className="flex flex-wrap items-end gap-2">
              <div className="min-w-[14rem] flex-1">
                <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="minhaempresa.com.br"
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </div>
              <Button type="button" variant="outline" disabled={domainMut.isPending} onClick={() => domainMut.mutate()}>Salvar domínio</Button>
            </div>
            {domainError && <p className="mt-2 text-sm text-destructive">{domainError}</p>}

            {site.domain && (
              <div className="mt-4 space-y-2 border-t border-border pt-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm">Verificação de posse:</span>
                  <Badge variant={site.domainVerified ? 'success' : 'muted'}>{site.domainVerified ? 'verificado' : 'não verificado'}</Badge>
                </div>
                {!site.domainVerified && (
                  <>
                    {site.verifyToken ? (
                      <p className="text-xs text-muted-foreground">
                        Crie um registro <strong>TXT</strong> no DNS de <span className="font-mono">{site.domain}</span> com o valor:{' '}
                        <code className="rounded bg-muted px-1">_meada-verify={site.verifyToken}</code>, depois clique em Verificar.
                      </p>
                    ) : (
                      <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={verifyStartMut.isPending}
                        onClick={() => verifyStartMut.mutate()}>Gerar token de verificação</Button>
                    )}
                    {site.verifyToken && (
                      <Button type="button" variant="outline" className="h-8 px-3 text-xs" disabled={verifyMut.isPending}
                        onClick={() => verifyMut.mutate()}>{verifyMut.isPending ? 'Verificando…' : 'Verificar agora'}</Button>
                    )}
                  </>
                )}
                <p className="text-xs text-muted-foreground">
                  Após verificar, aponte o domínio para o nosso servidor. O certificado HTTPS é emitido automaticamente quando o domínio responde por aqui.
                </p>
              </div>
            )}
          </Section>

          {site.published && (
            <p className="text-xs text-muted-foreground">
              Site publicado — ver em <a href={`/p/${site.slug}`} target="_blank" rel="noopener noreferrer" className="underline">/p/{site.slug}</a>
            </p>
          )}
        </div>
      </Modal>
    </div>
  )
}
