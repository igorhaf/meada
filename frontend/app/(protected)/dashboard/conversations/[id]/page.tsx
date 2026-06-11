'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { use, useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getMe } from '@/lib/api/me'
import { getConversation } from '@/lib/supabase/conversations'
import { getConversationMessages } from '@/lib/supabase/messages'

/**
 * Detalhe de uma conversa (SDK + RLS, só leitura). Bolhas: inbound à esquerda, outbound
 * à direita. Últimas 50 mensagens em ordem cronológica; indicador se há mais. Polling 5s.
 *
 * <p>Next 16: params é Promise — desembrulhado com use(). Guard de papel como nas outras
 * telas do tenant. Se a conversa for de outro tenant, o RLS faz getConversation lançar
 * (0 linhas no .single()) → estado de erro.
 */
export default function ConversationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()

  const { data: me } = useQuery({ queryKey: ['me'], queryFn: getMe })
  const isTenant = me?.role === 'tenant_admin'

  useEffect(() => {
    if (me && me.role !== 'tenant_admin') {
      router.replace('/dashboard')
    }
  }, [me, router])

  const { data: conversation, isError: convError } = useQuery({
    queryKey: ['conversation', id],
    queryFn: () => getConversation(id),
    enabled: isTenant,
    refetchInterval: 5000,
  })

  const { data: page, isPending, isError: msgError } = useQuery({
    queryKey: ['conversation-messages', id],
    queryFn: () => getConversationMessages(id, 50),
    enabled: isTenant,
    refetchInterval: 5000,
  })

  if (me && !isTenant) {
    return (
      <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
    )
  }

  if (convError || msgError) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <h1 className="mb-2 text-xl font-semibold">Conversa</h1>
        <p className="mb-4 text-sm text-destructive">
          Erro ao carregar a conversa (ou ela não pertence à sua empresa).
        </p>
        <Link href="/dashboard/conversations">
          <Button variant="outline">Voltar às conversas</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">
          {conversation?.contactName ?? conversation?.contactPhone ?? 'Conversa'}
        </h1>
        <div className="flex items-center gap-2">
          <Link href="/dashboard/conversations">
            <Button variant="outline">Voltar</Button>
          </Link>
          <SignOutButton />
        </div>
      </div>

      {conversation && (
        <div className="mb-4 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>{conversation.contactPhone}</span>
          <Badge variant={conversation.status === 'open' ? 'success' : 'danger'}>
            {conversation.status}
          </Badge>
          <Badge variant={conversation.handledBy === 'ai' ? 'default' : 'warning'}>
            {conversation.handledBy}
          </Badge>
        </div>
      )}

      {page && page.total > page.messages.length && (
        <p className="mb-2 text-xs text-muted-foreground">
          Mostrando últimas {page.messages.length} de {page.total} mensagens.
        </p>
      )}

      <div className="space-y-2 rounded-xl border border-border p-4">
        {isPending && <p className="text-sm text-muted-foreground">Carregando…</p>}
        {page?.messages.length === 0 && (
          <p className="text-sm text-muted-foreground">Nenhuma mensagem nesta conversa.</p>
        )}
        {page?.messages.map((m) => {
          const isInbound = m.direction === 'inbound'
          return (
            <div key={m.id} className={`flex ${isInbound ? 'justify-start' : 'justify-end'}`}>
              <div
                className={`max-w-[75%] rounded-lg px-3 py-2 text-sm ${
                  isInbound ? 'bg-muted' : 'bg-primary text-primary-foreground'
                }`}
              >
                <p>{m.content}</p>
                <p
                  className={`mt-1 text-[10px] ${
                    isInbound ? 'text-muted-foreground' : 'text-primary-foreground/70'
                  }`}
                >
                  {m.sender} · {new Date(m.createdAt).toLocaleString('pt-BR')}
                </p>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
