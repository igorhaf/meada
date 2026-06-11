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
    .select('id, status, handled_by, last_message_at, contact:contacts(name, phone_number)')
    .order('last_message_at', { ascending: false, nullsFirst: false })

  if (error) {
    throw error
  }

  return (data ?? []).map((c) => {
    // O join pode vir como objeto ou (dependendo da inferência do supabase-js) array de 1.
    const contact = Array.isArray(c.contact) ? c.contact[0] : c.contact
    return {
      id: c.id,
      status: c.status,
      handledBy: c.handled_by,
      lastMessageAt: c.last_message_at,
      contactName: contact?.name ?? null,
      contactPhone: contact?.phone_number ?? '',
    }
  })
}

/**
 * Cabeçalho de uma conversa específica (com contato), via SDK + RLS. Usado na tela de
 * detalhe. .single(): RLS garante 0 ou 1 (id é PK; se de outro tenant, RLS → 0 → erro).
 */
export async function getConversation(id: string): Promise<ConversationWithContact> {
  const supabase = createClient()
  const { data, error } = await supabase
    .from('conversations')
    .select('id, status, handled_by, last_message_at, contact:contacts(name, phone_number)')
    .eq('id', id)
    .single()

  if (error) {
    throw error
  }

  const contact = Array.isArray(data.contact) ? data.contact[0] : data.contact
  return {
    id: data.id,
    status: data.status,
    handledBy: data.handled_by,
    lastMessageAt: data.last_message_at,
    contactName: contact?.name ?? null,
    contactPhone: contact?.phone_number ?? '',
  }
}
