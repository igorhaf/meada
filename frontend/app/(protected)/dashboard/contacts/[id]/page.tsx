'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect, useState } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { ThemeToggle } from '@/components/theme-toggle'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import {
  getContact,
  getContactConversations,
  setContactBlocked,
  updateContactName,
} from '@/lib/supabase/contacts'

/**
 * Detalhe de um contato (SDK + RLS). Mostra nome (editável inline), telefone (read-only),
 * status de bloqueio (toggle), e a lista de conversas do contato (link para cada).
 *
 * Next 16: params é Promise — desembrulhado com use(). Guard de papel como nas outras
 * telas do tenant. Contato de outro tenant → RLS faz getContact lançar → erro.
 */
export default function ContactDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const router = useRouter()
  const queryClient = useQueryClient()
  const [nameDraft, setNameDraft] = useState<string>('')
  const [nameSaved, setNameSaved] = useState(false)

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data: contact, isError: contactError } = useQuery({
    queryKey: ['contact', id],
    queryFn: () => getContact(id),
    enabled: isTenant,
  })

  const { data: conversations } = useQuery({
    queryKey: ['contact-conversations', id],
    queryFn: () => getContactConversations(id),
    enabled: isTenant,
  })

  // Sincroniza o rascunho do nome quando o contato carrega.
  useEffect(() => {
    if (contact) {
      setNameDraft(contact.name ?? '')
    }
  }, [contact])

  const nameMutation = useMutation({
    mutationFn: (name: string) => updateContactName(id, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      queryClient.invalidateQueries({ queryKey: ['my-contacts'] })
      setNameSaved(true)
      setTimeout(() => setNameSaved(false), 3000)
    },
    onError: (err) => console.error('updateContactName failed:', err),
  })

  const blockMutation = useMutation({
    mutationFn: (blocked: boolean) => setContactBlocked(id, blocked),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', id] })
      queryClient.invalidateQueries({ queryKey: ['my-contacts'] })
    },
    onError: (err) => console.error('setContactBlocked failed:', err),
  })

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (contactError) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Contato</h1>
        <p className="mb-4 text-sm text-destructive">
          Erro ao carregar o contato (ou ele não pertence à sua empresa).
        </p>
        <Link href="/dashboard/contacts">
          <Button variant="outline">Voltar aos contatos</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">{contact?.name ?? contact?.phoneNumber ?? 'Contato'}</h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard/contacts">
            <Button variant="outline">Voltar</Button>
          </Link>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </div>

      {contact && (
        <div className="space-y-4 rounded-xl border border-border p-6">
          <div>
            <label htmlFor="name" className="mb-1 block text-sm font-medium">
              Nome
            </label>
            <div className="flex items-center gap-2">
              <input
                id="name"
                type="text"
                value={nameDraft}
                onChange={(e) => {
                  setNameDraft(e.target.value)
                  setNameSaved(false)
                }}
                placeholder="Sem nome"
                className="w-full max-w-xs rounded-md border border-border px-3 py-2 text-sm"
              />
              <Button
                onClick={() => nameMutation.mutate(nameDraft.trim())}
                disabled={nameMutation.isPending || nameDraft.trim() === (contact.name ?? '')}
              >
                {nameMutation.isPending ? 'Salvando…' : 'Salvar'}
              </Button>
              {nameSaved && <span className="text-sm text-green-600">Salvo!</span>}
            </div>
          </div>

          <div>
            <dt className="text-xs uppercase text-muted-foreground">Telefone</dt>
            <dd className="text-sm font-medium">{contact.phoneNumber}</dd>
          </div>

          <div className="flex items-center gap-3">
            <div>
              <dt className="text-xs uppercase text-muted-foreground">Status</dt>
              <dd className="mt-1">
                <Badge variant={contact.blocked ? 'danger' : 'success'}>
                  {contact.blocked ? 'bloqueado' : 'ativo'}
                </Badge>
              </dd>
            </div>
            <Button
              variant="outline"
              disabled={blockMutation.isPending}
              onClick={() => blockMutation.mutate(!contact.blocked)}
            >
              {contact.blocked ? 'Desbloquear' : 'Bloquear'}
            </Button>
          </div>
          {contact.blocked && (
            <p className="text-xs text-muted-foreground">
              Contato bloqueado: as mensagens dele são registradas no histórico, mas a IA não
              responde automaticamente.
            </p>
          )}
        </div>
      )}

      <h2 className="mt-8 mb-3 text-lg font-semibold">Conversas</h2>
      <div className="space-y-2 rounded-xl border border-border p-4">
        {conversations == null && <p className="text-sm text-muted-foreground">Carregando…</p>}
        {conversations?.length === 0 && (
          <p className="text-sm text-muted-foreground">Nenhuma conversa com este contato.</p>
        )}
        {conversations?.map((c) => (
          <Link
            key={c.id}
            href={`/dashboard/conversations/${c.id}`}
            className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm hover:bg-muted/40"
          >
            <span className="flex items-center gap-2">
              <Badge variant={c.status === 'open' ? 'success' : 'danger'}>{c.status}</Badge>
              <Badge variant={c.handledBy === 'ai' ? 'default' : 'warning'}>{c.handledBy}</Badge>
            </span>
            <span className="text-muted-foreground">
              {c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString('pt-BR') : '—'}
            </span>
          </Link>
        ))}
      </div>
    </div>
  )
}
