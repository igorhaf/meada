import { createClient } from './client'

/**
 * Conversa do tenant com o contato embutido (join PostgREST). O nome/telefone do contato
 * vivem em contacts; trazemos via select aninhado. RLS de conversations e contacts ambos
 * filtram por company_id = app.company_id() — o tenant só vê suas conversas/contatos.
 */
export type ConversationWithContact = {
  id: string
  status: string
  handledBy: string
  lastMessageAt: string | null
  contactName: string | null
  contactPhone: string
}

const SELECT_WITH_CONTACT =
  'id, status, handled_by, last_message_at, contact:contacts(name, phone_number)'

/** Normaliza a linha crua (snake_case + join possivelmente array) para ConversationWithContact. */
function toConversation(row: {
  id: string
  status: string
  handled_by: string
  last_message_at: string | null
  contact:
    | { name: string | null; phone_number: string }
    | { name: string | null; phone_number: string }[]
    | null
}): ConversationWithContact {
  const contact = Array.isArray(row.contact) ? row.contact[0] : row.contact
  return {
    id: row.id,
    status: row.status,
    handledBy: row.handled_by,
    lastMessageAt: row.last_message_at,
    contactName: contact?.name ?? null,
    contactPhone: contact?.phone_number ?? '',
  }
}

/**
 * Lista as conversas da empresa do tenant (SDK + RLS), com o contato via join, ordenadas
 * por last_message_at desc (mais recente primeiro; nulls por último). Polling na tela.
 *
 * <p>Join PostgREST: contact:contacts(name, phone_number) — alias "contact" para o
 * objeto aninhado. O RLS de contacts também aplica (defesa em profundidade).
 */
export async function getMyConversations(): Promise<ConversationWithContact[]> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select(SELECT_WITH_CONTACT)
    .order('last_message_at', { ascending: false, nullsFirst: false })

  if (error) {
    throw error
  }

  return (data ?? []).map(toConversation)
}

/**
 * Cabeçalho de uma conversa específica (com contato), via SDK + RLS. Usado na tela de
 * detalhe. .single(): RLS garante 0 ou 1 (id é PK; se de outro tenant, RLS → 0 → erro).
 */
export async function getConversation(id: string): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select(SELECT_WITH_CONTACT)
    .eq('id', id)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Troca quem atende a conversa (ai ↔ human) via SDK + RLS. A policy conversations_update
 * tem USING e WITH CHECK = company_id = app.company_id(): o tenant só atualiza conversa
 * da própria empresa, e o resultado precisa continuar dela. company_id NÃO é tocado aqui
 * (só handled_by) — o WITH CHECK passa naturalmente. Retorna a conversa atualizada.
 */
export async function updateConversationHandledBy(
  id: string,
  handledBy: 'ai' | 'human',
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ handled_by: handledBy })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Abre/fecha a conversa (open ↔ closed) via SDK + RLS. Mesmo contrato de RLS do
 * updateConversationHandledBy. Retorna a conversa atualizada.
 */
export async function updateConversationStatus(
  id: string,
  status: 'open' | 'closed',
): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .update({ status })
    .eq('id', id)
    .select(SELECT_WITH_CONTACT)
    .single()

  if (error) {
    throw error
  }

  return toConversation(data)
}

/**
 * Conta conversas com mensagem pendente de resposta (camada 5.10): conversas open cuja
 * ÚLTIMA mensagem é inbound (o contato falou por último). Via RPC, porque correlação de
 * "última msg por created_at" não é expressível em uma única .select() do PostgREST.
 *
 * RPC SECURITY INVOKER respeita o RLS do tenant — só conta as próprias conversas.
 * Consumida pelo badge do menu (ConversationsNavLink) com polling.
 */
export async function countUnreadConversations(): Promise<number> {
  const supabase = createClient()
  const { data, error } = await supabase.rpc('count_unread_conversations')
  if (error) {
    throw error
  }
  return typeof data === 'number' ? data : 0
}
