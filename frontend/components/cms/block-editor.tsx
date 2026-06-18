'use client'

import { Button } from '@/components/ui/button'
import type { CmsBlock } from '@/lib/cms/cms-block-type'

/** Campo simples (input ou textarea) pra editar uma prop string. */
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
        <textarea value={value} onChange={(e) => onChange(e.target.value)} rows={5} placeholder={placeholder}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
      ) : (
        <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder}
          className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm" />
      )}
    </div>
  )
}

/** Editor de uma lista de itens genérica (add/remover + campos por item). */
function ItemList<T>({
  items,
  onChange,
  empty,
  render,
  addLabel,
}: {
  items: T[]
  onChange: (items: T[]) => void
  empty: T
  render: (item: T, set: (patch: Partial<T>) => void) => React.ReactNode
  addLabel: string
}) {
  return (
    <div className="space-y-2">
      {items.map((it, i) => (
        <div key={i} className="grid gap-2 rounded-md border border-dashed border-border p-3">
          {render(it, (patch) => onChange(items.map((x, idx) => (idx === i ? { ...x, ...patch } : x))))}
          <div className="flex justify-end">
            <Button type="button" variant="outline" className="h-7 px-2 text-xs"
              onClick={() => onChange(items.filter((_, idx) => idx !== i))}>Remover item</Button>
          </div>
        </div>
      ))}
      <Button type="button" variant="outline" className="h-8 px-3 text-xs" onClick={() => onChange([...items, empty])}>
        {addLabel}
      </Button>
    </div>
  )
}

/** Editor de props específico por tipo de bloco (8 tipos — SM-N). */
export function BlockEditor({ block, onChange }: { block: CmsBlock; onChange: (b: CmsBlock) => void }) {
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
    return (
      <div className="space-y-3">
        <Field label="Título da seção" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        <ItemList items={p.items} empty={{ name: '', description: '', price: '' }} addLabel="+ Serviço"
          onChange={(items) => onChange({ ...block, props: { ...p, items } })}
          render={(it, set) => (
            <div className="grid gap-2 sm:grid-cols-3">
              <Field label="Nome" value={it.name} onChange={(v) => set({ name: v })} />
              <Field label="Descrição" value={it.description} onChange={(v) => set({ description: v })} />
              <Field label="Preço" value={it.price} onChange={(v) => set({ price: v })} />
            </div>
          )} />
      </div>
    )
  }
  if (block.type === 'contact') {
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
  if (block.type === 'gallery') {
    const p = block.props
    return (
      <div className="space-y-3">
        <Field label="Título" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        <ItemList items={p.images} empty={{ url: '', caption: '' }} addLabel="+ Imagem (URL)"
          onChange={(images) => onChange({ ...block, props: { ...p, images } })}
          render={(it, set) => (
            <div className="grid gap-2 sm:grid-cols-2">
              <Field label="URL da imagem" value={it.url} placeholder="https://…" onChange={(v) => set({ url: v })} />
              <Field label="Legenda" value={it.caption} onChange={(v) => set({ caption: v })} />
            </div>
          )} />
      </div>
    )
  }
  if (block.type === 'faq') {
    const p = block.props
    return (
      <div className="space-y-3">
        <Field label="Título" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        <ItemList items={p.items} empty={{ question: '', answer: '' }} addLabel="+ Pergunta"
          onChange={(items) => onChange({ ...block, props: { ...p, items } })}
          render={(it, set) => (
            <div className="grid gap-2">
              <Field label="Pergunta" value={it.question} onChange={(v) => set({ question: v })} />
              <Field label="Resposta" value={it.answer} textarea onChange={(v) => set({ answer: v })} />
            </div>
          )} />
      </div>
    )
  }
  if (block.type === 'testimonials') {
    const p = block.props
    return (
      <div className="space-y-3">
        <Field label="Título" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
        <ItemList items={p.items} empty={{ name: '', text: '', rating: '' }} addLabel="+ Depoimento"
          onChange={(items) => onChange({ ...block, props: { ...p, items } })}
          render={(it, set) => (
            <div className="grid gap-2 sm:grid-cols-3">
              <Field label="Nome" value={it.name} onChange={(v) => set({ name: v })} />
              <Field label="Depoimento" value={it.text} onChange={(v) => set({ text: v })} />
              <Field label="Nota (ex.: ★★★★★)" value={it.rating} onChange={(v) => set({ rating: v })} />
            </div>
          )} />
      </div>
    )
  }
  // map
  const p = block.props
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Título" value={p.title} onChange={(v) => onChange({ ...block, props: { ...p, title: v } })} />
      <Field label="Endereço" value={p.address} onChange={(v) => onChange({ ...block, props: { ...p, address: v } })} />
      <div className="sm:col-span-2">
        <Field label="URL de embed do mapa (Google Maps → Compartilhar → Incorporar)" value={p.embedUrl}
          placeholder="https://www.google.com/maps/embed?…" onChange={(v) => onChange({ ...block, props: { ...p, embedUrl: v } })} />
      </div>
    </div>
  )
}
