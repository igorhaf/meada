import type {
  CmsBlock,
  ContactProps,
  FaqProps,
  GalleryProps,
  HeroProps,
  MapProps,
  ServicesProps,
  TestimonialsProps,
  TextProps,
} from '@/lib/cms/cms-block-type'
import type { CmsNavItem, CmsTheme } from '@/lib/cms/public-fetch'

/**
 * Renderizador PÚBLICO do CMS (SM-N). Server component: recebe a view (title+blocks+theme+nav) e
 * renderiza os 8 tipos de bloco. Tema: cor primária (CSS var) + modo claro/escuro. Navegação entre
 * páginas no topo. Markdown do bloco 'text' é mínimo (parágrafos por linha em branco).
 *
 * navBase: base das URLs de nav. Em /p/{slug} é "/p/{slug}"; sob domínio custom é "" (raiz),
 * porque o middleware reescreve a raiz pro tenant.
 */

function HeroBlock({ props }: { props: HeroProps }) {
  return (
    <section className="px-6 py-20 text-center" style={{ background: 'var(--cms-primary)', color: '#fff' }}>
      {props.title && <h1 className="mx-auto max-w-3xl text-4xl font-bold tracking-tight">{props.title}</h1>}
      {props.subtitle && <p className="mx-auto mt-4 max-w-2xl text-lg opacity-90">{props.subtitle}</p>}
      {props.buttonLabel && props.buttonHref && (
        <a href={props.buttonHref} className="mt-8 inline-block rounded-md bg-white px-6 py-3 font-medium text-slate-900 hover:bg-slate-100">
          {props.buttonLabel}
        </a>
      )}
    </section>
  )
}

function TextBlock({ props }: { props: TextProps }) {
  const paragraphs = (props.body ?? '').split(/\n\s*\n/).filter((p) => p.trim() !== '')
  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      {paragraphs.map((p, i) => (
        <p key={i} className="mb-4 whitespace-pre-line leading-relaxed">{p}</p>
      ))}
    </section>
  )
}

function ServicesBlock({ props }: { props: ServicesProps }) {
  return (
    <section className="mx-auto max-w-4xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="grid gap-6 sm:grid-cols-2">
        {(props.items ?? []).map((it, i) => (
          <div key={i} className="rounded-lg border border-black/10 p-5">
            <div className="flex items-baseline justify-between gap-3">
              <h3 className="font-semibold">{it.name}</h3>
              {it.price && <span className="shrink-0 text-sm opacity-70">{it.price}</span>}
            </div>
            {it.description && <p className="mt-2 text-sm opacity-80">{it.description}</p>}
          </div>
        ))}
      </div>
    </section>
  )
}

function ContactBlock({ props }: { props: ContactProps }) {
  const waHref = props.whatsapp ? `https://wa.me/${props.whatsapp.replace(/\D/g, '')}` : null
  return (
    <section className="px-6 py-12" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-2xl text-center">
        <h2 className="mb-6 text-2xl font-bold">Contato</h2>
        <dl className="space-y-2">
          {props.phone && <dd>{props.phone}</dd>}
          {props.address && <dd>{props.address}</dd>}
          {props.hours && <dd>{props.hours}</dd>}
        </dl>
        {waHref && (
          <a href={waHref} className="mt-6 inline-block rounded-md bg-emerald-600 px-6 py-3 font-medium text-white hover:bg-emerald-700">
            Falar no WhatsApp
          </a>
        )}
      </div>
    </section>
  )
}

function GalleryBlock({ props }: { props: GalleryProps }) {
  return (
    <section className="mx-auto max-w-5xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        {(props.images ?? []).map((img, i) => (
          <figure key={i} className="overflow-hidden rounded-lg border border-black/10">
            {/* eslint-disable-next-line @next/next/no-img-element -- URLs externas coladas pelo tenant */}
            <img src={img.url} alt={img.caption || ''} className="h-48 w-full object-cover" />
            {img.caption && <figcaption className="px-3 py-2 text-sm opacity-70">{img.caption}</figcaption>}
          </figure>
        ))}
      </div>
    </section>
  )
}

function FaqBlock({ props }: { props: FaqProps }) {
  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
      <div className="space-y-3">
        {(props.items ?? []).map((it, i) => (
          <details key={i} className="rounded-lg border border-black/10 p-4">
            <summary className="cursor-pointer font-medium">{it.question}</summary>
            <p className="mt-2 whitespace-pre-line opacity-80">{it.answer}</p>
          </details>
        ))}
      </div>
    </section>
  )
}

function TestimonialsBlock({ props }: { props: TestimonialsProps }) {
  return (
    <section className="px-6 py-12" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-4xl">
        {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
        <div className="grid gap-6 sm:grid-cols-2">
          {(props.items ?? []).map((t, i) => (
            <blockquote key={i} className="rounded-lg border border-black/10 bg-white/60 p-5">
              <p className="italic">“{t.text}”</p>
              <footer className="mt-3 text-sm font-medium">
                {t.name}{t.rating && <span className="ml-2 opacity-70">{t.rating}</span>}
              </footer>
            </blockquote>
          ))}
        </div>
      </div>
    </section>
  )
}

function MapBlock({ props }: { props: MapProps }) {
  return (
    <section className="mx-auto max-w-4xl px-6 py-12">
      {props.title && <h2 className="mb-4 text-center text-2xl font-bold">{props.title}</h2>}
      {props.address && <p className="mb-4 text-center opacity-80">{props.address}</p>}
      {props.embedUrl && (
        <div className="overflow-hidden rounded-lg border border-black/10">
          <iframe src={props.embedUrl} title="Mapa" className="h-80 w-full" loading="lazy" />
        </div>
      )}
    </section>
  )
}

function renderBlock(b: CmsBlock) {
  switch (b.type) {
    case 'hero': return <HeroBlock key={b.id} props={b.props} />
    case 'text': return <TextBlock key={b.id} props={b.props} />
    case 'services': return <ServicesBlock key={b.id} props={b.props} />
    case 'contact': return <ContactBlock key={b.id} props={b.props} />
    case 'gallery': return <GalleryBlock key={b.id} props={b.props} />
    case 'faq': return <FaqBlock key={b.id} props={b.props} />
    case 'testimonials': return <TestimonialsBlock key={b.id} props={b.props} />
    case 'map': return <MapBlock key={b.id} props={b.props} />
    default: return null
  }
}

export function CmsRender({
  title,
  blocks,
  theme,
  nav,
  navBase,
}: {
  title: string
  blocks: CmsBlock[]
  theme: CmsTheme | null
  nav: CmsNavItem[]
  navBase: string
}) {
  const primary = theme?.primaryColor || '#0f172a'
  const dark = theme?.dark === true
  const shell: React.CSSProperties = {
    ['--cms-primary' as string]: primary,
    background: dark ? '#0b1120' : '#ffffff',
    color: dark ? '#e2e8f0' : '#0f172a',
  }
  return (
    <main className="min-h-screen" style={shell}>
      {nav.length > 1 && (
        <nav className="flex flex-wrap items-center justify-center gap-4 border-b border-black/10 px-6 py-4 text-sm">
          {nav.map((n) => (
            <a key={n.pageSlug} href={n.isHome ? `${navBase || '/'}` : `${navBase}/${n.pageSlug}`} className="hover:underline">
              {n.title || n.pageSlug}
            </a>
          ))}
        </nav>
      )}
      {blocks.map(renderBlock)}
      {blocks.length === 0 && (
        <div className="flex min-h-[50vh] items-center justify-center opacity-50">
          <p>{title || 'Página em construção.'}</p>
        </div>
      )}
    </main>
  )
}
