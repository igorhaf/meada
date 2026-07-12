import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../api'
import type { PageDetail, PageKind, PageScope } from '../types'
import { KIND_INFO } from '../types'
import { Btn, GhostBtn, TextInput } from './ui'

type NewPageDialogProps = {
  scope: PageScope
  parentId: number | null
  parentTitle?: string
  onCreated: (page: PageDetail) => void
  onClose: () => void
}

const KIND_ORDER: PageKind[] = ['note', 'tasks', 'calendar', 'gastos', 'vault', 'registro', 'meds', 'diet']

export default function NewPageDialog({ scope, parentId, parentTitle, onCreated, onClose }: NewPageDialogProps) {
  const [kind, setKind] = useState<PageKind>('note')
  const [title, setTitle] = useState('')

  const create = useMutation({
    mutationFn: () =>
      apiFetch<PageDetail>('/pages', {
        method: 'POST',
        body: JSON.stringify({
          scope,
          parent_id: parentId,
          kind,
          title: title.trim() || KIND_INFO[kind].label,
          icon: KIND_INFO[kind].icon,
        }),
      }),
    onSuccess: onCreated,
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-semibold">
          Nova página {scope === 'shared' ? 'compartilhada' : 'pessoal'}
          {parentTitle && <span className="font-normal text-[#9b9a97]"> em {parentTitle}</span>}
        </h2>

        <div className="mt-4 grid grid-cols-2 gap-2">
          {KIND_ORDER.map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => setKind(k)}
              className={`rounded-lg border p-2.5 text-left transition ${
                kind === k ? 'border-[#2383e2] bg-[#e7f0fb]' : 'border-[#e9e9e7] hover:bg-[#f7f7f5]'
              }`}
            >
              <span className="text-sm font-medium">
                {KIND_INFO[k].icon} {KIND_INFO[k].label}
              </span>
              <p className="mt-0.5 text-xs leading-tight text-[#787774]">{KIND_INFO[k].desc}</p>
            </button>
          ))}
        </div>

        <form
          className="mt-4 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate()
          }}
        >
          <TextInput
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={`Título (ex.: ${KIND_INFO[kind].label})`}
            className="flex-1"
            autoFocus
          />
          <Btn disabled={create.isPending}>Criar</Btn>
          <GhostBtn onClick={onClose}>Cancelar</GhostBtn>
        </form>
      </div>
    </div>
  )
}
