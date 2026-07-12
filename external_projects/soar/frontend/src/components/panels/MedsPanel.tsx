import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { Medication } from '../../types'
import { Btn, Field, GhostBtn, TextInput, dateBr } from '../ui'

const EMPTY = {
  person: '', name: '', dose: '', times: '08:00', controlled: false,
  prescription_until: '', stock: '', low_stock_threshold: '', notes: '',
}

export default function MedsPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Medication | null>(null)
  const [form, setForm] = useState(EMPTY)

  const { data: meds = [] } = useQuery({
    queryKey: ['meds', pageId],
    queryFn: () => apiFetch<Medication[]>(`/pages/${pageId}/medications`),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['meds', pageId] })

  const save = useMutation({
    mutationFn: () => {
      const body = {
        person: form.person,
        name: form.name,
        dose: form.dose || null,
        schedule_times: form.times.split(',').map((t) => t.trim()).filter((t) => /^\d{2}:\d{2}$/.test(t)),
        controlled: form.controlled,
        prescription_until: form.prescription_until || null,
        stock: form.stock === '' ? null : Number(form.stock),
        low_stock_threshold: form.low_stock_threshold === '' ? null : Number(form.low_stock_threshold),
        notes: form.notes || null,
      }
      return editing
        ? apiFetch(`/pages/${pageId}/medications/${editing.id}`, { method: 'PUT', body: JSON.stringify(body) })
        : apiFetch(`/pages/${pageId}/medications`, { method: 'POST', body: JSON.stringify(body) })
    },
    onSuccess: () => { closeForm(); invalidate() },
  })

  const take = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/medications/${id}/log`, { method: 'POST', body: JSON.stringify({}) }),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/medications/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  function closeForm() { setShowForm(false); setEditing(null); setForm(EMPTY) }

  function openEdit(med: Medication) {
    setEditing(med)
    setForm({
      person: med.person, name: med.name, dose: med.dose ?? '',
      times: (med.schedule_times ?? []).join(', '),
      controlled: med.controlled,
      prescription_until: med.prescription_until?.slice(0, 10) ?? '',
      stock: med.stock === null ? '' : String(med.stock),
      low_stock_threshold: med.low_stock_threshold === null ? '' : String(med.low_stock_threshold),
      notes: med.notes ?? '',
    })
    setShowForm(true)
  }

  const byPerson = new Map<string, Medication[]>()
  for (const med of meds) byPerson.set(med.person, [...(byPerson.get(med.person) ?? []), med])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-xs text-[#9b9a97]">💬 Lembretes chegam no Telegram na hora marcada; responda “tomou” pra registrar.</p>
        {!showForm && <Btn onClick={() => setShowForm(true)}>+ Remédio</Btn>}
      </div>

      {showForm && (
        <form
          className="grid grid-cols-2 gap-3 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4 md:grid-cols-3"
          onSubmit={(e) => { e.preventDefault(); if (form.person && form.name) save.mutate() }}
        >
          <Field label="Pessoa *"><TextInput value={form.person} onChange={(e) => setForm({ ...form, person: e.target.value })} required /></Field>
          <Field label="Remédio *"><TextInput value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <Field label="Dose"><TextInput value={form.dose} onChange={(e) => setForm({ ...form, dose: e.target.value })} placeholder="1 comprimido" /></Field>
          <Field label="Horários (HH:MM, vírgula)"><TextInput value={form.times} onChange={(e) => setForm({ ...form, times: e.target.value })} placeholder="08:00, 20:00" /></Field>
          <Field label="Receita válida até"><TextInput type="date" value={form.prescription_until} onChange={(e) => setForm({ ...form, prescription_until: e.target.value })} /></Field>
          <label className="flex items-center gap-2 self-end pb-1 text-sm">
            <input type="checkbox" checked={form.controlled} onChange={(e) => setForm({ ...form, controlled: e.target.checked })} className="h-4 w-4 accent-[#2383e2]" />
            Controlado
          </label>
          <Field label="Estoque (unidades)"><TextInput type="number" value={form.stock} onChange={(e) => setForm({ ...form, stock: e.target.value })} /></Field>
          <Field label="Alertar estoque abaixo de"><TextInput type="number" value={form.low_stock_threshold} onChange={(e) => setForm({ ...form, low_stock_threshold: e.target.value })} /></Field>
          <Field label="Notas"><TextInput value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></Field>
          <div className="col-span-full flex gap-2">
            <Btn disabled={save.isPending}>{editing ? 'Salvar' : 'Adicionar'}</Btn>
            <GhostBtn onClick={closeForm}>Cancelar</GhostBtn>
          </div>
        </form>
      )}

      {[...byPerson.entries()].map(([person, personMeds]) => (
        <div key={person}>
          <h3 className="mb-1 text-sm font-semibold text-[#5f5e5b]">{person}</h3>
          <div className="space-y-2">
            {personMeds.map((med) => (
              <div key={med.id} className="group rounded-lg border border-[#e9e9e7] p-3 hover:bg-[#fbfbfa]">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{med.name}</span>
                  {med.dose && <span className="text-sm text-[#787774]">{med.dose}</span>}
                  {(med.schedule_times ?? []).map((t) => (
                    <span key={t} className="rounded-full bg-[#e7f0fb] px-2 py-0.5 text-xs text-[#1a5da0]">⏰ {t}</span>
                  ))}
                  {med.controlled && <span className="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">controlado</span>}
                  {med.prescription_until && (
                    <span className="text-xs text-[#9b9a97]">receita até {dateBr(med.prescription_until)}</span>
                  )}
                  {med.stock !== null && (
                    <span className={`text-xs ${med.low_stock_threshold !== null && med.stock <= med.low_stock_threshold ? 'font-medium text-red-600' : 'text-[#9b9a97]'}`}>
                      estoque: {med.stock}
                    </span>
                  )}
                  <span className="flex-1" />
                  <Btn onClick={() => take.mutate(med.id)} disabled={take.isPending} className="!px-2 !py-1 !text-xs">✓ Tomou agora</Btn>
                  <button onClick={() => openEdit(med)} className="invisible text-xs text-[#9b9a97] group-hover:visible hover:text-[#37352f]">editar</button>
                  <button onClick={() => confirm(`Excluir ${med.name}?`) && remove.mutate(med.id)} className="invisible text-xs text-[#9b9a97] group-hover:visible hover:text-red-600">excluir</button>
                </div>
                {med.notes && <p className="mt-1 text-xs text-[#9b9a97]">{med.notes}</p>}
                {med.logs.length > 0 && (
                  <p className="mt-1 text-xs text-[#9b9a97]">
                    Últimas tomadas:{' '}
                    {med.logs.map((log) => new Date(log.taken_at).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })).join(' · ')}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
      {meds.length === 0 && <p className="rounded-lg border border-dashed border-[#e9e9e7] p-6 text-center text-sm text-[#9b9a97]">Nenhum remédio cadastrado.</p>}
    </div>
  )
}
