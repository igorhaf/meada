import { createClient } from '@/lib/supabase/client'

const API_BASE = process.env.NEXT_PUBLIC_API_URL

/**
 * Erro de chamada ao backend admin. Carrega o status HTTP e o {@code reason} do corpo
 * JSON ({error, reason}) — o shape que o filtro JWT e os controllers do admin produzem.
 * O caller usa {@code status}/{@code reason} para tratar inline (ex. 403 → "acesso restrito").
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly reason: string,
  ) {
    super(`API ${status}: ${reason}`)
    this.name = 'ApiError'
  }
}

/**
 * Wrapper de fetch para o backend admin (Spring /admin/**). Concerns transversais:
 *  - injeta Authorization: Bearer <token> (token da sessão Supabase, lido a cada chamada);
 *  - 401 → sessão morta: signOut + redirect /login (não adianta tratar inline);
 *  - 403 e demais erros → lança ApiError(status, reason) para o caller tratar.
 *
 * O token vem de supabase.auth.getSession() (client-side) — sem provider próprio, o
 * @supabase/ssr já gerencia o state da sessão. getSession é tratado defensivamente:
 * se falhar (storage corrompido, race), segue sem token → backend dá 401 → branch abaixo.
 */
export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  if (!API_BASE) {
    throw new Error('NEXT_PUBLIC_API_URL não configurada (ver .env.local / .env.example).')
  }

  const supabase = createClient()
  const { data, error } = await supabase.auth.getSession()
  if (error) {
    console.error('getSession failed:', error.message)
  }
  const token = data?.session?.access_token

  const headers: Record<string, string> = {
    ...(options?.headers as Record<string, string> | undefined),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
  // Content-Type só quando há body (POST/PUT/PATCH): evita preflight CORS desnecessário
  // e não faz sentido semântico num GET.
  if (options?.body) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (response.status === 401) {
    // Sessão morta/expirada: sai e manda para o login. Não há tratamento inline útil.
    await supabase.auth.signOut()
    window.location.href = '/login'
    // Lança para interromper o fluxo do caller (o redirect já está em curso).
    throw new ApiError(401, await readReason(response))
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readReason(response))
  }

  return response.json() as Promise<T>
}

/** Extrai o {@code reason} do corpo de erro {error, reason}; tolerante se o corpo não for JSON. */
async function readReason(response: Response): Promise<string> {
  try {
    const body = await response.json()
    return typeof body?.reason === 'string' ? body.reason : 'unknown'
  } catch {
    return 'unknown'
  }
}
