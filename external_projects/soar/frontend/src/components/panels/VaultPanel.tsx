import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { VaultEntry } from '../../types'
import { Btn, Field, GhostBtn, TextInput } from '../ui'

export default function VaultPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<VaultEntry | null>(null)
  const [form, setForm] = useState({ title: '', username: '', secret: '', url: '', notes: '' })
  const [revealed, setRevealed] = useState<Record<number, string>>({})
  const [copied, setCopied] = useState<number | null>(null)

  const { data: entries = [] } = useQuery({
    queryKey: ['vault', pageId],
    queryFn: () => apiFetch<VaultEntry[]>(`/pages/${pageId}/vault`),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['vault', pageId] })

  const save = useMutation({
    mutationFn: () => {
      const body: Record<string, string | null> = {
        title: form.title,
        username: form.username || null,
        url: form.url || null,
        notes: form.notes || null,
      }
      if (form.secret) body.secret = form.secret
      if (editing) {
        return apiFetch(`/pages/${pageId}/vault/${editing.id}`, { method: 'PUT', body: JSON.stringify(body) })
      }
      return apiFetch(`/pages/${pageId}/vault`, { method: 'POST', body: JSON.stringify({ ...body, secret: form.secret }) })
    },
    onSuccess: () => {
      closeForm()
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/vault/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  function closeForm() {
    setShowForm(false)
    setEditing(null)
    setForm({ title: '', username: '', secret: '', url: '', notes: '' })
  }

  function openEdit(entry: VaultEntry) {
    setEditing(entry)
    setForm({ title: entry.title, username: entry.username ?? '', secret: '', url: entry.url ?? '', notes: entry.notes ?? '' })
    setShowForm(true)
  }

  async function reveal(id: number) {
    if (revealed[id]) {
      setRevealed((r) => { const copy = { ...r }; delete copy[id]; return copy })
      return
    }
    const { secret } = await apiFetch<{ secret: string }>(`/pages/${pageId}/vault/${id}/reveal`)
    setRevealed((r) => ({ ...r, [id]: secret }))
    setTimeout(() => setRevealed((r) => { const copy = { ...r }; delete copy[id]; return copy }), 20000)
  }

  async function copy(id: number) {
    const secret = revealed[id] ?? (await apiFetch<{ secret: string }>(`/pages/${pageId}/vault/${id}/reveal`)).secret
    await navigator.clipboard.writeText(secret)
    setCopied(id)
    setTimeout(() => setCopied(null), 1500)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-[#9b9a97]">🔒 Senhas cifradas no banco. Nunca saem pelo Telegram.</p>
        {!showForm && <Btn onClick={() => setShowForm(true)}>+ Nova senha</Btn>}
      </div>

      {showForm && (
        <form
          className="grid grid-cols-2 gap-3 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (form.title && (form.secret || editing)) save.mutate()
          }}
        >
          <Field label="Título *"><TextInput value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required /></Field>
          <Field label="Usuário / login"><TextInput value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></Field>
          <Field label={editing ? 'Senha (vazio = manter atual)' : 'Senha *'}>
            <TextInput type="text" value={form.secret} onChange={(e) => setForm({ ...form, secret: e.target.value })} autoComplete="off" />
          </Field>
          <Field label="URL"><TextInput value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })} placeholder="https://…" /></Field>
          <Field label="Notas"><TextInput value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></Field>
          <div className="col-span-2 flex gap-2">
            <Btn disabled={save.isPending}>{editing ? 'Salvar' : 'Adicionar'}</Btn>
            <GhostBtn onClick={closeForm}>Cancelar</GhostBtn>
          </div>
        </form>
      )}

      <div className="overflow-x-auto rounded-lg border border-[#e9e9e7]">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[#e9e9e7] bg-[#f7f7f5] text-left text-xs text-[#787774]">
              <th className="px-3 py-2 font-medium">Título</th>
              <th className="px-3 py-2 font-medium">Usuário</th>
              <th className="px-3 py-2 font-medium">Senha</th>
              <th className="px-3 py-2 font-medium">URL</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.id} className="group border-b border-[#f1f1ef] last:border-0 hover:bg-[#fbfbfa]">
                <td className="px-3 py-2 font-medium">{entry.title}
                  {entry.notes && <p className="text-xs font-normal text-[#9b9a97]">{entry.notes}</p>}
                </td>
                <td className="px-3 py-2 text-[#5f5e5b]">{entry.username}</td>
                <td className="px-3 py-2 font-mono text-xs">
                  <button onClick={() => reveal(entry.id)} className="rounded bg-[#f1f1ef] px-2 py-0.5 hover:bg-[#e3e2e0]" title={revealed[entry.id] ? 'Ocultar' : 'Revelar'}>
                    {revealed[entry.id] ?? '••••••••'}
                  </button>{' '}
                  <button onClick={() => copy(entry.id)} className="text-[#9b9a97] hover:text-[#37352f]" title="Copiar">
                    {copied === entry.id ? '✓' : '⧉'}
                  </button>
                </td>
                <td className="px-3 py-2 text-xs">
                  {entry.url && (
                    <a href={entry.url} target="_blank" rel="noreferrer" className="text-[#2383e2] hover:underline">
                      {entry.url.replace(/^https?:\/\//, '').slice(0, 30)}
                    </a>
                  )}
                </td>
                <td className="px-3 py-2 text-right text-xs whitespace-nowrap">
                  <button onClick={() => openEdit(entry)} className="invisible text-[#9b9a97] group-hover:visible hover:text-[#37352f]">editar</button>{' '}
                  <button onClick={() => confirm(`Excluir "${entry.title}"?`) && remove.mutate(entry.id)} className="invisible text-[#9b9a97] group-hover:visible hover:text-red-600">excluir</button>
                </td>
              </tr>
            ))}
            {entries.length === 0 && (
              <tr><td colSpan={5} className="px-3 py-6 text-center text-sm text-[#9b9a97]">Cofre vazio.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
