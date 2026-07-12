import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { apiFetch } from '../../api'
import type { PageDetail } from '../../types'
import { Btn, Field, TextInput, inputCls } from '../ui'

export default function DietPanel({ page }: { page: PageDetail }) {
  const queryClient = useQueryClient()
  const [person, setPerson] = useState(page.meta?.person ?? '')
  const [restrictions, setRestrictions] = useState(page.meta?.restrictions ?? '')
  const [goals, setGoals] = useState(page.meta?.goals ?? '')

  const generating = page.meta?.generate_requested === true

  // Enquanto gera, refaz o fetch da página a cada 5s até o plano chegar.
  useEffect(() => {
    if (!generating) return
    const timer = setInterval(
      () => queryClient.invalidateQueries({ queryKey: ['page', page.id] }),
      5000,
    )
    return () => clearInterval(timer)
  }, [generating, page.id, queryClient])

  const saveProfile = useMutation({
    mutationFn: () =>
      apiFetch(`/pages/${page.id}/diet/profile`, {
        method: 'PUT',
        body: JSON.stringify({ person, restrictions, goals }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['page', page.id] }),
  })

  const generate = useMutation({
    mutationFn: async () => {
      await apiFetch(`/pages/${page.id}/diet/profile`, {
        method: 'PUT',
        body: JSON.stringify({ person, restrictions, goals }),
      })
      return apiFetch(`/pages/${page.id}/diet/generate`, { method: 'POST', body: JSON.stringify({}) })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['page', page.id] }),
  })

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-3 rounded-lg border border-[#e9e9e7] bg-[#fbfbfa] p-4 md:grid-cols-3">
        <Field label="Pessoa *">
          <TextInput value={person} onChange={(e) => setPerson(e.target.value)} placeholder="Igor, Aline, Filho 1…" />
        </Field>
        <Field label="Restrições (alergias, saúde, remédios…)">
          <textarea value={restrictions} onChange={(e) => setRestrictions(e.target.value)} rows={2} className={inputCls} placeholder="ex.: intolerância a lactose; evitar açúcar…" />
        </Field>
        <Field label="Objetivos">
          <textarea value={goals} onChange={(e) => setGoals(e.target.value)} rows={2} className={inputCls} placeholder="ex.: perder peso; mais proteína…" />
        </Field>
        <div className="col-span-full flex flex-wrap items-center gap-2">
          <Btn onClick={() => saveProfile.mutate()} disabled={!person.trim() || saveProfile.isPending}>
            Salvar perfil
          </Btn>
          <Btn onClick={() => generate.mutate()} disabled={!person.trim() || generating || generate.isPending}>
            {generating ? '🤖 Gerando… (~1 min)' : '✨ Gerar dieta com IA'}
          </Btn>
          {page.meta?.generated_at && !generating && (
            <span className="text-xs text-[#9b9a97]">
              gerada em {new Date(page.meta.generated_at).toLocaleString('pt-BR')}
            </span>
          )}
          <span className="text-xs text-[#9b9a97]">— também dá pelo Telegram: “gera a dieta do {person || '…'}”</span>
        </div>
      </div>

      {generating && (
        <div className="rounded-lg border border-[#e9e9e7] bg-[#fffbeb] p-4 text-sm text-[#92610e]">
          A IA está montando o plano alimentar da semana… esta página atualiza sozinha.
        </div>
      )}

      {page.content ? (
        <div className="rounded-lg border border-[#e9e9e7] p-5">
          <pre className="font-sans text-[15px] leading-7 whitespace-pre-wrap text-[#37352f]">{page.content}</pre>
        </div>
      ) : (
        !generating && (
          <p className="rounded-lg border border-dashed border-[#e9e9e7] p-6 text-center text-sm text-[#9b9a97]">
            Nenhum plano gerado ainda. Preencha o perfil e clique em “Gerar dieta com IA”.
          </p>
        )
      )}
    </div>
  )
}
