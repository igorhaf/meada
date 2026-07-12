import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { ApiError } from '../api'
import { useAuth } from '../auth'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao entrar. Tente novamente.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex h-full items-center justify-center bg-[#f7f7f5]">
      <div className="w-full max-w-sm rounded-xl border border-[#e9e9e7] bg-white p-8 shadow-sm">
        <div className="mb-6 text-center">
          <div className="text-3xl">🪁</div>
          <h1 className="mt-2 text-xl font-semibold">Soar</h1>
          <p className="mt-1 text-sm text-[#787774]">Entre para acessar seu espaço</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="mb-1 block text-xs font-medium text-[#787774]">
              E-mail
            </label>
            <input
              id="email"
              type="email"
              required
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="voce@exemplo.com"
              className="w-full rounded-md border border-[#e9e9e7] px-3 py-2 text-sm outline-none focus:border-[#2383e2] focus:ring-2 focus:ring-[#2383e2]/20"
            />
          </div>
          <div>
            <label htmlFor="password" className="mb-1 block text-xs font-medium text-[#787774]">
              Senha
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full rounded-md border border-[#e9e9e7] px-3 py-2 text-sm outline-none focus:border-[#2383e2] focus:ring-2 focus:ring-[#2383e2]/20"
            />
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-[#2383e2] py-2 text-sm font-medium text-white transition hover:bg-[#1a75d2] disabled:opacity-60"
          >
            {submitting ? 'Entrando…' : 'Entrar'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-[#9b9a97]">
          Contas de exemplo: igor@soar.test · ana@soar.test (senha: password)
        </p>
      </div>
    </div>
  )
}
