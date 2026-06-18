import type {
  CmsBlock,
  ContactProps,
  HeroProps,
  ServicesProps,
  TextProps,
} from '@/lib/cms/cms-block-type'

/**
 * Renderizador PÚBLICO dos blocos do CMS (SM-M). Server component puro (sem 'use client'): recebe os
 * blocos da página publicada e renderiza cada tipo. Markdown do bloco 'text' é renderizado de forma
 * mínima (parágrafos por linha em branco) — sem lib externa, sem HTML do usuário (escape natural do
 * React). Os 4 tipos: hero, text, services, contact.
 */

function HeroBlock({ props }: { props: HeroProps }) {
  return (
    <section className="bg-slate-900 px-6 py-20 text-center text-white">
      {props.title && <h1 className="mx-auto max-w-3xl text-4xl font-bold tracking-tight">{props.title}</h1>}
      {props.subtitle && <p className="mx-auto mt-4 max-w-2xl text-lg text-slate-300">{props.subtitle}</p>}
      {props.buttonLabel && props.buttonHref && (
        <a
          href={props.buttonHref}
          className="mt-8 inline-block rounded-md bg-white px-6 py-3 font-medium text-slate-900 hover:bg-slate-100"
        >
          {props.buttonLabel}
        </a>
      )}
    </section>
  )
}

function TextBlock({ props }: { props: TextProps }) {
  // markdown mínimo: separa parágrafos por linha em branco; preserva quebras simples.
  const paragraphs = (props.body ?? '').split(/\n\s*\n/).filter((p) => p.trim() !== '')
  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      {paragraphs.map((p, i) => (
        <p key={i} className="mb-4 whitespace-pre-line leading-relaxed text-slate-700">
          {p}
        </p>
      ))}
    </section>
  )
}

function ServicesBlock({ props }: { props: ServicesProps }) {
  return (
    <section className="mx-auto max-w-4xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold text-slate-900">{props.title}</h2>}
      <div className="grid gap-6 sm:grid-cols-2">
        {(props.items ?? []).map((it, i) => (
          <div key={i} className="rounded-lg border border-slate-200 p-5">
            <div className="flex items-baseline justify-between gap-3">
              <h3 className="font-semibold text-slate-900">{it.name}</h3>
              {it.price && <span className="shrink-0 text-sm font-medium text-slate-600">{it.price}</span>}
            </div>
            {it.description && <p className="mt-2 text-sm text-slate-600">{it.description}</p>}
          </div>
        ))}
      </div>
    </section>
  )
}

function ContactBlock({ props }: { props: ContactProps }) {
  const waHref = props.whatsapp ? `https://wa.me/${props.whatsapp.replace(/\D/g, '')}` : null
  return (
    <section className="bg-slate-50 px-6 py-12">
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="mb-6 text-2xl font-bold text-slate-900">Contato</h2>
        <dl className="space-y-2 text-slate-700">
          {props.phone && <div><dt className="sr-only">Telefone</dt><dd>{props.phone}</dd></div>}
          {props.address && <div><dt className="sr-only">Endereço</dt><dd>{props.address}</dd></div>}
          {props.hours && <div><dt className="sr-only">Horário</dt><dd>{props.hours}</dd></div>}
        </dl>
        {waHref && (
          <a
            href={waHref}
            className="mt-6 inline-block rounded-md bg-emerald-600 px-6 py-3 font-medium text-white hover:bg-emerald-700"
          >
            Falar no WhatsApp
          </a>
        )}
      </div>
    </section>
  )
}

export function CmsRender({ title, blocks }: { title: string; blocks: CmsBlock[] }) {
  return (
    <main className="min-h-screen bg-white">
      {blocks.map((b) => {
        switch (b.type) {
          case 'hero':
            return <HeroBlock key={b.id} props={b.props} />
          case 'text':
            return <TextBlock key={b.id} props={b.props} />
          case 'services':
            return <ServicesBlock key={b.id} props={b.props} />
          case 'contact':
            return <ContactBlock key={b.id} props={b.props} />
          default:
            return null
        }
      })}
      {blocks.length === 0 && (
        <div className="flex min-h-screen items-center justify-center text-slate-400">
          <p>{title || 'Página em construção.'}</p>
        </div>
      )}
    </main>
  )
}
