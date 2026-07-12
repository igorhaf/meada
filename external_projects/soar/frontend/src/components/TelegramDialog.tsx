import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../api'
import { Btn, GhostBtn } from './ui'

type LinkCodeResponse = { code: string; bot_username: string; linked: boolean }

export default function TelegramDialog({ onClose }: { onClose: () => void }) {
  const [data, setData] = useState<LinkCodeResponse | null>(null)

  const generate = useMutation({
    mutationFn: () => apiFetch<LinkCodeResponse>('/telegram/link-code', { method: 'POST', body: JSON.stringify({}) }),
    onSuccess: setData,
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onClose}>
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-semibold">🤖 Conectar ao Telegram</h2>
        <p className="mt-2 text-sm text-[#5f5e5b]">
          O assistente da família responde no Telegram: marca eventos, anota gastos, registra remédios,
          lista tarefas e muito mais — é só conversar.
        </p>

        <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm text-[#37352f]">
          <li>
            Abra o bot{' '}
            <a href="https://t.me/RosendoFrancaBot" target="_blank" rel="noreferrer" className="font-medium text-[#2383e2] hover:underline">
              @RosendoFrancaBot
            </a>{' '}
            no Telegram
          </li>
          <li>Gere seu código aqui embaixo</li>
          <li>
            Mande pro bot: <code className="rounded bg-[#f1f1ef] px-1.5 py-0.5 text-xs">/vincular CODIGO</code>
          </li>
        </ol>

        <div className="mt-5 flex items-center gap-3">
          <Btn onClick={() => generate.mutate()} disabled={generate.isPending}>
            {data ? 'Gerar outro código' : 'Gerar código'}
          </Btn>
          {data && (
            <code className="rounded-lg border border-[#e9e9e7] bg-[#f7f7f5] px-4 py-2 text-xl font-bold tracking-widest">
              {data.code}
            </code>
          )}
        </div>
        {data?.linked && (
          <p className="mt-3 text-xs text-green-700">✓ Esta conta já tem um Telegram vinculado — vincular de novo substitui.</p>
        )}

        <div className="mt-5 text-right">
          <GhostBtn onClick={onClose}>Fechar</GhostBtn>
        </div>
      </div>
    </div>
  )
}
