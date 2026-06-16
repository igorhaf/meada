'use client'

import { useQuery, keepPreviousData } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/client'
import { getCompanies, type Company, type CompanyFilters } from '@/lib/api/admin/companies'
import { CreateCompanyDialog } from './create-company-dialog'

const PAGE_SIZE = 20

/**
 * Lista GLOBAL de empresas (rota canônica do super-admin — camada 6.1). Filtros (status,
 * busca) e paginação são server-side: o backend faz o ilike/limit/offset, o frontend só
 * carrega a query key e renderiza a página. A autorização é do backend (403
 * forbidden_not_super_admin → tratado inline). "Detalhes" leva ao drill-down /[id].
 */
export default function CompaniesPage() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [status, setStatus] = useState<'' | 'active' | 'suspended'>('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  const filters: CompanyFilters = {
    status: status || undefined,
    q: q || undefined,
    page,
    pageSize: PAGE_SIZE,
  }

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['companies', filters],
    queryFn: () => getCompanies(filters),
    placeholderData: keepPreviousData,
  })

  // 403: tenant-admin acessou a rota direto. Trata inline (não quebra).
  if (isError && error instanceof ApiError && error.status === 403) {
    return (
      <div className="space-y-6">
        <PageHeader title="Acesso restrito" description="Esta área é restrita ao super-admin." />
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  if (isError) {
    console.error('failed to load /admin/companies:', error)
    return (
      <div className="space-y-6">
        <PageHeader title="Empresas" />
        <p className="text-sm text-destructive">Erro ao carregar empresas.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const items = data?.items ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Empresas"
        description="Todas as empresas cadastradas na plataforma."
        actions={<Button onClick={() => setDialogOpen(true)}>Nova empresa</Button>}
      />

      {/* Filtros: status + busca por nome/slug (server-side). */}
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as '' | 'active' | 'suspended')
            setPage(0)
          }}
          className="h-9 rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <option value="">Todos os status</option>
          <option value="active">Ativas</option>
          <option value="suspended">Suspensas</option>
        </select>
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          placeholder="Buscar por nome ou slug…"
          className="h-9 w-64 rounded-md border border-border bg-background px-3 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        />
        <span className="ml-auto text-xs text-muted-foreground">
          {total} empresa{total !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="overflow-hidden rounded-lg border border-border bg-card">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Nome
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Slug
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Status
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Criada em
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Ações
                </th>
              </tr>
            </thead>
            <tbody>
              {isPending ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                    Carregando…
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                    Nenhuma empresa encontrada.
                  </td>
                </tr>
              ) : (
                items.map((c: Company) => (
                  <tr
                    key={c.id}
                    className="border-t border-border first:border-t-0 hover:bg-muted/40"
                  >
                    <td className="px-4 py-3.5">{c.name}</td>
                    <td className="px-4 py-3.5 font-mono text-xs">{c.slug}</td>
                    <td className="px-4 py-3.5">
                      <Badge variant={c.status === 'active' ? 'success' : 'danger'}>
                        {c.status === 'active' ? 'ativa' : 'suspensa'}
                      </Badge>
                    </td>
                    <td className="px-4 py-3.5">{new Date(c.createdAt).toLocaleString('pt-BR')}</td>
                    <td className="px-4 py-3.5">
                      <Link href={`/dashboard/companies/${c.id}`}>
                        <Button variant="outline" size="sm">
                          Detalhes
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages}
          </span>
          <div className="flex items-center gap-1">
            <Button
              variant="outline"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="h-7 px-2 text-xs"
            >
              ←
            </Button>
            <Button
              variant="outline"
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="h-7 px-2 text-xs"
            >
              →
            </Button>
          </div>
        </div>
      )}

      <CreateCompanyDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </div>
  )
}
