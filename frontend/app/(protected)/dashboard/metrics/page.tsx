'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { getTenantMetrics, type MessagesByDay, type TenantMetrics } from '@/lib/supabase/metrics'

/** Card de número grande com rótulo. */
function MetricCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <div className="text-3xl font-semibold tabular-nums">{value.toLocaleString('pt-BR')}</div>
      <div className="mt-1 text-sm text-muted-foreground">{label}</div>
      <div className="text-xs text-muted-foreground">últimos 30 dias</div>
    </div>
  )
}

/** Formata segundos em "12s" / "1m 23s" / "—". */
function formatSeconds(s: number | null): string {
  if (s == null) return '—'
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const rest = s % 60
  return rest === 0 ? `${m}m` : `${m}m ${rest}s`
}

/**
 * Gráfico de linhas SVG manual (sem dependência): mensagens/dia, 30 dias, 2 séries
 * (recebidas/enviadas). viewBox 600x200; escala Y pelo máximo das duas séries; grid
 * horizontal discreto; rótulos do primeiro/meio/último dia no eixo X.
 */
function MessagesChart({ data }: { data: MessagesByDay[] }) {
  const W = 600
  const H = 200
  const PAD = 24
  const n = data.length
  const maxY = Math.max(1, ...data.map((d) => Math.max(d.inbound, d.outbound)))

  const x = (i: number) => PAD + (i * (W - 2 * PAD)) / Math.max(1, n - 1)
  const y = (v: number) => H - PAD - (v * (H - 2 * PAD)) / maxY

  const line = (key: 'inbound' | 'outbound') =>
    data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(d[key]).toFixed(1)}`).join(' ')

  // 3 linhas de grade horizontais (0, meio, topo).
  const gridYs = [0, maxY / 2, maxY]

  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-medium">Mensagens por dia</h2>
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <span className="inline-block size-2 rounded-full bg-[#2b5c8a]" /> Recebidas
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block size-2 rounded-full bg-[#3a6b4a]" /> Enviadas
          </span>
        </div>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} className="h-48 w-full" preserveAspectRatio="none">
        {gridYs.map((gv, i) => (
          <line
            key={i}
            x1={PAD}
            x2={W - PAD}
            y1={y(gv)}
            y2={y(gv)}
            className="stroke-border"
            strokeWidth={1}
          />
        ))}
        <path d={line('inbound')} fill="none" stroke="#2b5c8a" strokeWidth={2} />
        <path d={line('outbound')} fill="none" stroke="#3a6b4a" strokeWidth={2} />
      </svg>
      <div className="mt-1 flex justify-between text-xs text-muted-foreground">
        <span>{data[0]?.day.slice(5)}</span>
        <span>{data[Math.floor(n / 2)]?.day.slice(5)}</span>
        <span>{data[n - 1]?.day.slice(5)}</span>
      </div>
    </div>
  )
}

export default function MetricsPage() {
  const router = useRouter()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery<TenantMetrics>({
    queryKey: ['tenant-metrics'],
    queryFn: getTenantMetrics,
    enabled: isTenant,
  })

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    console.error('failed to load metrics:', error)
    return (
      <div className="mx-auto max-w-5xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Métricas</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar métricas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Métricas</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}

      {data && (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <MetricCard label="Mensagens recebidas" value={data.messagesInbound30d} />
            <MetricCard label="Mensagens enviadas" value={data.messagesOutbound30d} />
            <MetricCard label="Conversas iniciadas" value={data.conversationsStarted30d} />
            <MetricCard label="Contatos novos" value={data.contactsNew30d} />
          </div>

          <MessagesChart data={data.messagesByDay} />

          <div className="grid gap-6 md:grid-cols-2">
            <div className="rounded-xl border border-border bg-card p-5">
              <h2 className="mb-1 text-sm font-medium">Tempo médio de resposta da IA</h2>
              <div className="text-3xl font-semibold tabular-nums">
                {formatSeconds(data.avgResponseSeconds)}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                média entre a mensagem do cliente e a resposta da IA (últimos 30 dias)
              </p>
            </div>

            <div className="rounded-xl border border-border bg-card p-5">
              <h2 className="mb-2 text-sm font-medium">FAQs ativas</h2>
              {data.topFaqs.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhuma FAQ ativa.</p>
              ) : (
                <ol className="space-y-1 text-sm">
                  {data.topFaqs.map((f, i) => (
                    <li key={f.id} className="flex gap-2">
                      <span className="text-muted-foreground">{i + 1}.</span>
                      <span className="truncate">{f.question}</span>
                    </li>
                  ))}
                </ol>
              )}
              <p className="mt-3 text-xs text-muted-foreground">
                Ordem cronológica. O ranking por uso real fica para uma fase futura, quando
                houver rastreamento de qual FAQ a IA usou em cada resposta.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
