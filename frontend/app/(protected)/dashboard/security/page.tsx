'use client'

import { useQuery } from '@tanstack/react-query'
import { Construction, ShieldCheck } from 'lucide-react'

import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getAccessLogs, type AccessLogEntry } from '@/lib/api/access-logs'
import { getMe } from '@/lib/api/me'

/** Rótulos legíveis das ações de acesso (o enum cru fica feio na tabela). */
const ACTION_LABELS: Record<AccessLogEntry['action'], string> = {
  login_success: 'Login bem-sucedido',
  login_failed: 'Login falhou',
  password_changed: 'Senha alterada',
}

const columns: Column<AccessLogEntry>[] = [
  {
    key: 'createdAt',
    header: 'Quando',
    render: (e) => new Date(e.createdAt).toLocaleString('pt-BR'),
  },
  {
    key: 'action',
    header: 'Ação',
    render: (e) => (
      <Badge variant={e.action === 'login_failed' ? 'danger' : 'success'}>
        {ACTION_LABELS[e.action]}
      </Badge>
    ),
  },
  { key: 'email', header: 'Email', render: (e) => e.email ?? '—' },
  {
    key: 'ip',
    header: 'IP',
    render: (e) => <span className="font-mono text-xs text-muted-foreground">{e.ip ?? '—'}</span>,
  },
]

/**
 * Tela de segurança do tenant (camada 5.24 #92): lista os logs de acesso da própria empresa
 * (login_success/failed, password_changed) via backend REST (/admin/access-logs). Super-admin
 * não usa: redireciona para /dashboard.
 */
export default function SecurityPage() {
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'
  const isSuperAdmin = me?.role === 'super_admin'

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['access-logs'],
    queryFn: getAccessLogs,
    enabled: isTenant,
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (isError) {
    console.error('failed to load access logs:', error)
  }

  // Super-admin: a versão GLOBAL de segurança/acessos é fase 6.5 (placeholder por ora;
  // substitui o redirect antigo — camada 6.0). Tenant segue com a tela atual.
  if (isSuperAdmin) {
    return (
      <div className="space-y-6">
        <PageHeader title="Segurança" description="Acessos e segurança da plataforma." />
        <Card className="flex flex-col items-center justify-center gap-3 py-16 text-center">
          <Construction className="size-10 text-muted-foreground" />
          <h2 className="text-base font-medium">Versão global em construção</h2>
          <p className="max-w-sm text-sm text-muted-foreground">
            A segurança global da plataforma será implementada na fase 6.5.
          </p>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Segurança"
        description="Tentativas de login e mudanças de senha da sua empresa, mais recentes primeiro."
      />

      {isError ? (
        <p className="text-sm text-destructive">Erro ao carregar os acessos.</p>
      ) : isEmpty ? (
        <EmptyState
          icon={<ShieldCheck />}
          title="Nenhum acesso registrado"
          description="Logins e alterações de senha da sua empresa aparecem aqui conforme acontecem."
        />
      ) : (
        <DataTable<AccessLogEntry>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum acesso registrado."
        />
      )}
    </div>
  )
}
