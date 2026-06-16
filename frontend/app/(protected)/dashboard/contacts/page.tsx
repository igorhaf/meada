'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Users } from 'lucide-react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DataTable, type Column } from '@/components/ui/data-table'
import { EmptyState } from '@/components/ui/empty-state'
import { getMe } from '@/lib/api/me'
import { getMyContacts, setContactBlocked, type Contact } from '@/lib/supabase/contacts'

const columns: Column<Contact>[] = [
  { key: 'name', header: 'Nome', render: (c) => c.name ?? '—' },
  { key: 'phoneNumber', header: 'Telefone' },
  {
    key: 'createdAt',
    header: 'Cadastrado em',
    render: (c) => new Date(c.createdAt).toLocaleString('pt-BR'),
  },
  {
    key: 'blocked',
    header: 'Bloqueado',
    render: (c) => (
      <Badge variant={c.blocked ? 'danger' : 'success'}>{c.blocked ? 'sim' : 'não'}</Badge>
    ),
  },
]

/**
 * Contatos da empresa do tenant (SDK + RLS). Super-admin NÃO usa: redireciona para
 * /dashboard. Lista todos os contatos (pessoas que mandaram mensagem), com busca e
 * toggle de bloqueio. Detalhe em /dashboard/contacts/[id].
 */
export default function ContactsPage() {
  const router = useRouter()
  const queryClient = useQueryClient()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['my-contacts'],
    queryFn: getMyContacts,
    enabled: isTenant,
  })

  const toggleBlocked = useMutation({
    mutationFn: ({ id, blocked }: { id: string; blocked: boolean }) => setContactBlocked(id, blocked),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-contacts'] }),
    onError: (err) => console.error('setContactBlocked failed:', err),
  })

  const isEmpty = !isPending && !isError && (data?.length ?? 0) === 0

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-5xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (isError) {
    console.error('failed to load contacts:', error)
    return (
      <div className="mx-auto max-w-5xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Contatos</h1>
        <p className="mb-4 text-sm text-destructive">Erro ao carregar contatos.</p>
        <Link href="/dashboard">
          <Button variant="outline">Voltar ao dashboard</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Contatos</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>
      {isEmpty ? (
        <EmptyState
          icon={<Users />}
          title="Nenhum contato ainda"
          description="Os contatos aparecerão aqui quando as pessoas mandarem mensagens para sua empresa pelo WhatsApp."
        />
      ) : (
        <DataTable<Contact>
          data={data ?? []}
          columns={columns}
          loading={isPending}
          emptyMessage="Nenhum contato cadastrado."
          searchPlaceholder="Buscar por nome ou telefone…"
          searchFn={(c, q) => `${c.name ?? ''} ${c.phoneNumber}`.toLowerCase().includes(q)}
          actions={(c) => (
            <div className="flex items-center gap-1.5">
              <Link href={`/dashboard/contacts/${c.id}`}>
                <Button variant="outline" className="h-7 px-2 text-xs">
                  Ver
                </Button>
              </Link>
              <Button
                variant="outline"
                className="h-7 px-2 text-xs"
                disabled={toggleBlocked.isPending && toggleBlocked.variables?.id === c.id}
                onClick={() => toggleBlocked.mutate({ id: c.id, blocked: !c.blocked })}
              >
                {c.blocked ? 'Desbloquear' : 'Bloquear'}
              </Button>
            </div>
          )}
        />
      )}
    </div>
  )
}
