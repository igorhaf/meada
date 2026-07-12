import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { RegistroEntry, RegistroField } from '../../types'
import { Btn, GhostBtn, TextInput, inputCls } from '../ui'

type RegistroData = { template: RegistroField[]; entries: RegistroEntry[] }

export default function RegistroPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const [editingTemplate, setEditingTemplate] = useState(false)
  const [fields, setFields] = useState<RegistroField[]>([])
  const [entryForm, setEntryForm] = useState<Record<string, string> | null>(null)
  const [editingEntry, setEditingEntry] = useState<RegistroEntry | null>(null)

  const { data } = useQuery({
    queryKey: ['registro', pageId],
    queryFn: () => apiFetch<RegistroData>(`/pages/${pageId}/registro`),
  })

  const template = data?.template ?? []
  const entries = data?.entries ?? []
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['registro', pageId] })

  const saveTemplate = useMutation({
    mutationFn: () =>
      apiFetch(`/pages/${pageId}/registro/template`, {
        method: 'PUT',
        body: JSON.stringify({
          template: fields
            .filter((f) => f.label.trim())
            .map((f) => ({ ...f, key: f.key || slug(f.label) })),
        }),
      }),
    onSuccess: () => {
      setEditingTemplate(false)
      invalidate()
    },
  })

  const saveEntry = useMutation({
    mutationFn: () => {
      if (editingEntry) {
        return apiFetch(`/pages/${pageId}/registro/${editingEntry.id}`, {
          method: 'PUT',
          body: JSON.stringify({ data: entryForm }),
        })
      }
      return apiFetch(`/pages/${pageId}/registro`, { method: 'POST', body: JSON.stringify({ data: entryForm }) })
    },
    onSuccess: () => {
      setEntryForm(null)
      setEditingEntry(null)
      invalidate()
    },
  })

  const removeEntry = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/registro/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  function slug(text: string): string {
    return text.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  }

  function startTemplateEdit() {
    setFields(template.length ? [...template] : [{ key: '', label: '' }])
    setEditingTemplate(true)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-[#9b9a97]">
          Campos personalizados — dá pra criar e preencher pelo Telegram também.
        </p>
        <div className="flex gap-2">
          <GhostBtn onClick={startTemplateEdit}>⚙ Campos</GhostBtn>
          {template.length > 0 && !entryForm && (
            <Btn onClick={() => { setEditingEntry(null); setEntryForm(Object.fromEntries(template.map((f) => [f.key, '']))) }}>
              + Novo registro
            </Btn>
          )}
        </div>
      </div>

      {editingTemplate && (
        <div className="space-y-2 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4">
          <p className="text-xs font-medium text-[#787774]">Campos deste registro:</p>
          {fields.map((field, i) => (
            <div key={i} className="flex gap-2">
              <TextInput
                value={field.label}
                onChange={(e) => setFields(fields.map((f, j) => (j === i ? { ...f, label: e.target.value } : f)))}
                placeholder={`Campo ${i + 1}`}
                className="flex-1"
              />
              <select
                value={field.type ?? 'text'}
                onChange={(e) => setFields(fields.map((f, j) => (j === i ? { ...f, type: e.target.value as RegistroField['type'] } : f)))}
                className={inputCls}
              >
                <option value="text">texto</option>
                <option value="number">número</option>
                <option value="date">data</option>
              </select>
              <button onClick={() => setFields(fields.filter((_, j) => j !== i))} className="text-[#9b9a97] hover:text-red-600">✕</button>
            </div>
          ))}
          <div className="flex gap-2 pt-1">
            <GhostBtn onClick={() => setFields([...fields, { key: '', label: '' }])}>+ campo</GhostBtn>
            <Btn onClick={() => saveTemplate.mutate()} disabled={saveTemplate.isPending}>Salvar campos</Btn>
            <GhostBtn onClick={() => setEditingTemplate(false)}>Cancelar</GhostBtn>
          </div>
        </div>
      )}

      {entryForm && (
        <form
          className="grid grid-cols-2 gap-3 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4 md:grid-cols-3"
          onSubmit={(e) => { e.preventDefault(); saveEntry.mutate() }}
        >
          {template.map((field) => (
            <label key={field.key} className="flex flex-col gap-1 text-xs font-medium text-[#787774]">
              {field.label}
              <input
                type={field.type ?? 'text'}
                value={entryForm[field.key] ?? ''}
                onChange={(e) => setEntryForm({ ...entryForm, [field.key]: e.target.value })}
                className={inputCls}
              />
            </label>
          ))}
          <div className="col-span-full flex gap-2">
            <Btn disabled={saveEntry.isPending}>{editingEntry ? 'Salvar' : 'Adicionar'}</Btn>
            <GhostBtn onClick={() => { setEntryForm(null); setEditingEntry(null) }}>Cancelar</GhostBtn>
          </div>
        </form>
      )}

      {template.length === 0 ? (
        <p className="rounded-lg border border-dashed border-[#e9e9e7] p-6 text-center text-sm text-[#9b9a97]">
          Defina os campos deste registro em <b>⚙ Campos</b> (ex.: Banco, Bandeira, Vencimento…).
        </p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-[#e9e9e7]">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#e9e9e7] bg-[#f7f7f5] text-left text-xs text-[#787774]">
                {template.map((f) => <th key={f.key} className="px-3 py-2 font-medium">{f.label}</th>)}
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id} className="group border-b border-[#f1f1ef] last:border-0 hover:bg-[#fbfbfa]">
                  {template.map((f) => <td key={f.key} className="px-3 py-2">{entry.data[f.key]}</td>)}
                  <td className="px-3 py-2 text-right text-xs whitespace-nowrap">
                    <button
                      onClick={() => { setEditingEntry(entry); setEntryForm({ ...Object.fromEntries(template.map((f) => [f.key, ''])), ...entry.data }) }}
                      className="invisible text-[#9b9a97] group-hover:visible hover:text-[#37352f]"
                    >
                      editar
                    </button>{' '}
                    <button onClick={() => removeEntry.mutate(entry.id)} className="invisible text-[#9b9a97] group-hover:visible hover:text-red-600">excluir</button>
                  </td>
                </tr>
              ))}
              {entries.length === 0 && (
                <tr><td colSpan={template.length + 1} className="px-3 py-6 text-center text-sm text-[#9b9a97]">Nenhum registro ainda.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
