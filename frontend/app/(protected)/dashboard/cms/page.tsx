'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { ApiError } from '@/lib/api/client'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, Section } from '@/components/ui/card'
import {
  getCmsPage,
  saveCmsContent,
  setCmsDomain,
  setCmsPublished,
} from '@/lib/api/cms'
import {
  CMS_BLOCK_TYPES,
  blockTypeLabel,
  defaultProps,
  type CmsBlock,
  type CmsBlockTypeId,
} from '@/lib/cms/cms-block-type'

// id estável p/ blocos novos (sem depender de libs; o backend re-valida/normaliza).
function newId(): string {
  return 'b-' + Math.random().toString(36).slice(2, 10)
}

/** Editor de uma prop de string simples. */
function Field({
  label,
  value,
  onChange,
  textarea,
  placeholder,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  textarea?: boolean
  placeholder?: string
}) {
  return (
    <div>
      <label className="mb-1 block text-xs font-medium text-muted-foreground">{label}</label>
      {textarea ? (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          rows={5}
          placeholder={placeholder}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
        />
      ) : (
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
        />
      )}
    </div>
  )
}

/** Editor de props específico por tipo de bloco. */
function BlockEditor({ block, onChange }: { block: CmsBlock; onChange: (b: CmsBlock) => void }) {
  if (block.type === 'hero') {
    const p = block.props
    return (
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Título" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        <Field label="Subtítulo" value={p.subtitle} onChange={(v) => onChange({ ...block, props: { ...p, subtitle: v } })} />
        <Field label="Texto do botão" value={p.buttonLabel} onChange={(v) => onChange({ ...block, props: { ...p, buttonLabel: v } })} />
        <Field label="Link do botão" value={p.buttonHref} placeholder="https://wa.me/55…" onChange={(v) => onChange({ ...block, props: { ...p, buttonHref: v } })} />
      </div>
    )
  }
  if (block.type === 'text') {
    const p = block.props
    return <Field label="Conteúdo (markdown)" value={p.body} textarea onChange={(v) => onChange({ ...block, props: { body: v } })} />
  }
  if (block.type === 'services') {
    const p = block.props
    const setItem = (i: number, patch: Partial<(typeof p.items)[number]>) => {
      const items = p.items.map((it, idx) => (idx === i ? { ...it, ...patch } : it))
      onChange({ ...block, props: { ...p, items } })
    }
    return (
      <div className="space-y-3">
        <Field label="Título da seção" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        {p.items.map((it, i) => (
          <div key={i} className="grid gap-2 rounded-md border border-dashed border-border p-3 sm:grid-cols-3">
            <Field label="Nome" value={it.name} onChange={(v) => setItem(i, { name: v })} />
            <Field label="Descrição" value={it.description} onChange={(v) => setItem(i, { description: v })} />
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <Field label="Preço" value={it.price} onChange={(v) => setItem(i, { price: v })} />
              </div>
              <Button type="button" variant="outline" className="h-9 px-2 text-xs"
                onClick={() => onChange({ ...block, props: { ...p, items: p.items.filter((_, idx) => idx !== i) } })}>
                ✕
              </Button>
            </div>
          </div>
        ))}
        <Button type="button" variant="outline" className="h-8 px-3 text-xs"
          onClick={() => onChange({ ...block, props: { ...p, items: [...p.items, { name: '', description: '', price: '' }] } })}>
          + Item
        </Button>
      </div>
    )
  }
  // contact
  const p = block.props
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Telefone" value={p.phone} onChange={(v) => onChange({ ...block, props: { ...p, phone: v } })} />
      <Field label="WhatsApp (só números)" value={p.whatsapp} placeholder="5511999999999" onChange={(v) => onChange({ ...block, props: { ...p, whatsapp: v } })} />
      <Field label="Endereço" value={p.address} onChange={(v) => onChange({ ...block, props: { ...p, address: v } })} />
      <Field label="Horário" value={p.hours} onChange={(v) => onChange({ ...block, props: { ...p, hours: v } })} />
    </div>
  )
}

/**
 * Editor do CMS / page builder do tenant (SM-M). Atrás do gate de feature (o backend dá 403
 * feature_disabled se o nicho não tem CMS). Permite editar título, adicionar/remover/REORDENAR
 * blocos (drag-drop nativo + botões ↑↓ acessíveis), editar props por tipo, salvar rascunho,
 * publicar/despublicar e configurar o domínio próprio. O link "Ver página" abre o render público.
 */
export default function CmsPage() {
  const qc = useQueryClient()
  const { data, isPending, isError, error } = useQuery({ queryKey: ['cms-page'], queryFn: getCmsPage })

  const [title, setTitle] = useState('')
  const [blocks, setBlocks] = useState<CmsBlock[]>([])
  const [domain, setDomain] = useState('')
  const [dragIdx, setDragIdx] = useState<number | null>(null)
  const [savedAt, setSavedAt] = useState<string | null>(null)
  const [domainError, setDomainError] = useState<string | null>(null)

  useEffect(() => {
    if (data) {
      setTitle(data.title)
      setBlocks(data.blocks ?? [])
      setDomain(data.domain ?? '')
    }
  }, [data])

  const saveMutation = useMutation({
    mutationFn: () => saveCmsContent(title, blocks),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-page'] })
      setSavedAt(new Date().toLocaleTimeString('pt-BR'))
    },
  })

  const publishMutation = useMutation({
    mutationFn: (published: boolean) => setCmsPublished(published),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['cms-page'] }),
  })

  const domainMutation = useMutation({
    mutationFn: () => setCmsDomain(domain.trim() === '' ? null : domain.trim()),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cms-page'] })
      setDomainError(null)
    },
    onError: (e) => {
      if (e instanceof ApiError && e.reason === 'domain_taken') setDomainError('Esse domínio já está em uso por outro tenant.')
      else if (e instanceof ApiError && e.reason === 'invalid_domain') setDomainError('Domínio inválido (ex.: minhaempresa.com.br).')
      else setDomainError('Erro ao salvar o domínio.')
    },
  })

  if (isError && error instanceof ApiError && error.reason === 'feature_disabled') {
    return (
      <div className="space-y-6">
        <PageHeader title="Página pessoal" description="Este recurso não está habilitado para o seu plano." />
        <Link href="/dashboard"><Button variant="outline">Voltar ao dashboard</Button></Link>
      </div>
    )
  }

  function addBlock(type: CmsBlockTypeId) {
    setBlocks((bs) => [...bs, { id: newId(), type, props: defaultProps(type) } as CmsBlock])
  }
  function removeBlock(i: number) {
    setBlocks((bs) => bs.filter((_, idx) => idx !== i))
  }
  function move(i: number, dir: -1 | 1) {
    setBlocks((bs) => {
      const j = i + dir
      if (j < 0 || j >= bs.length) return bs
      const copy = [...bs]
      ;[copy[i], copy[j]] = [copy[j], copy[i]]
      return copy
    })
  }
  function onDrop(target: number) {
    if (dragIdx === null || dragIdx === target) return
    setBlocks((bs) => {
      const copy = [...bs]
      const [moved] = copy.splice(dragIdx, 1)
      copy.splice(target, 0, moved)
      return copy
    })
    setDragIdx(null)
  }
  function patchBlock(i: number, b: CmsBlock) {
    setBlocks((bs) => bs.map((old, idx) => (idx === i ? b : old)))
  }

  const published = data?.published ?? false

  return (
    <div className="space-y-6">
      <PageHeader
        title="Página pessoal"
        description="Monte a página pública do seu negócio com blocos. Salve como rascunho e publique quando quiser."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
              {saveMutation.isPending ? 'Salvando…' : 'Salvar'}
            </Button>
            <Button disabled={publishMutation.isPending} onClick={() => publishMutation.mutate(!published)}>
              {published ? 'Despublicar' : 'Publicar'}
            </Button>
          </div>
        }
      />

      {isPending ? (
        <p className="text-sm text-muted-foreground">Carregando…</p>
      ) : isError ? (
        <p className="text-sm text-destructive">Erro ao carregar a página.</p>
      ) : (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant={published ? 'success' : 'muted'}>{published ? 'publicada' : 'rascunho'}</Badge>
            {savedAt && <span className="text-xs text-muted-foreground">Salvo às {savedAt}</span>}
          </div>

          <Card>
            <Section title="Título da página">
              <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Nome do seu negócio"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </Section>
          </Card>

          {/* Blocos */}
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium">Blocos</span>
              <span className="text-xs text-muted-foreground">arraste para reordenar (ou use ↑ ↓)</span>
            </div>

            {blocks.length === 0 && (
              <p className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
                Nenhum bloco ainda. Adicione abaixo.
              </p>
            )}

            {blocks.map((b, i) => (
              <div
                key={b.id}
                draggable
                onDragStart={() => setDragIdx(i)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => onDrop(i)}
                className="rounded-lg border border-border bg-card p-4"
              >
                <div className="mb-3 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="cursor-grab text-muted-foreground" aria-hidden>⠿</span>
                    <span className="font-medium">{blockTypeLabel(b.type)}</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button type="button" variant="outline" className="h-7 w-7 p-0 text-xs" disabled={i === 0}
                      onClick={() => move(i, -1)} aria-label="Subir">↑</Button>
                    <Button type="button" variant="outline" className="h-7 w-7 p-0 text-xs" disabled={i === blocks.length - 1}
                      onClick={() => move(i, 1)} aria-label="Descer">↓</Button>
                    <Button type="button" variant="outline" className="h-7 px-2 text-xs"
                      onClick={() => removeBlock(i)} aria-label="Remover bloco">Remover</Button>
                  </div>
                </div>
                <BlockEditor block={b} onChange={(nb) => patchBlock(i, nb)} />
              </div>
            ))}

            <div className="flex flex-wrap items-center gap-2 rounded-lg border border-dashed border-border p-3">
              <span className="text-xs font-medium text-muted-foreground">Adicionar bloco:</span>
              {CMS_BLOCK_TYPES.map((t) => (
                <Button key={t.id} type="button" variant="outline" className="h-8 px-3 text-xs" onClick={() => addBlock(t.id)}>
                  + {t.label}
                </Button>
              ))}
            </div>
          </div>

          {/* Domínio */}
          <Card>
            <Section title="Domínio próprio (opcional)">
              <p className="mb-2 text-xs text-muted-foreground">
                Aponte o seu domínio para o nosso servidor e informe-o aqui. A verificação de posse e o
                certificado são etapas posteriores; por ora a página já responde no domínio se ele apontar para nós.
              </p>
              <div className="flex flex-wrap items-end gap-2">
                <div className="flex-1 min-w-[14rem]">
                  <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="minhaempresa.com.br"
                    className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
                </div>
                <Button type="button" variant="outline" disabled={domainMutation.isPending} onClick={() => domainMutation.mutate()}>
                  {domainMutation.isPending ? 'Salvando…' : 'Salvar domínio'}
                </Button>
              </div>
              {domainError && <p className="mt-2 text-sm text-destructive">{domainError}</p>}
            </Section>
          </Card>

          {/* Ver página pública */}
          <p className="text-xs text-muted-foreground">
            {published && data ? (
              <>
                Página publicada. Veja em{' '}
                <a href={`/p/${data.slug}`} target="_blank" rel="noopener noreferrer" className="underline">
                  /p/{data.slug}
                </a>
                {data.domain && (
                  <>
                    {' '}ou no seu domínio{' '}
                    <span className="font-mono">{data.domain}</span> (se já apontar para nós).
                  </>
                )}
              </>
            ) : (
              'Publique para que a página fique acessível.'
            )}
          </p>
        </div>
      )}
    </div>
  )
}
