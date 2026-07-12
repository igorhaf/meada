// Vazio = mesma origem (Caddy roteia /api pro backend). Native dev usa o .env.
const API_URL = import.meta.env.VITE_API_URL ?? ''

export class ApiError extends Error {
  status: number
  reason?: string

  constructor(status: number, message: string, reason?: string) {
    super(message)
    this.status = status
    this.reason = reason
  }
}

export function getToken(): string | null {
  return localStorage.getItem('soar_token')
}

export function setToken(token: string | null) {
  if (token === null) {
    localStorage.removeItem('soar_token')
  } else {
    localStorage.setItem('soar_token', token)
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(options.headers as Record<string, string>),
  }

  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_URL}/api${path}`, { ...options, headers })

  if (response.status === 401) {
    setToken(null)
    window.location.href = '/login'
    throw new ApiError(401, 'Sessão expirada.')
  }

  if (!response.ok) {
    let message = `Erro ${response.status}`
    let reason: string | undefined
    try {
      const body = await response.json()
      message = body.error ?? body.message ?? message
      reason = body.reason
    } catch {
      // corpo não-JSON: mantém a mensagem genérica
    }
    throw new ApiError(response.status, message, reason)
  }

  return response.json() as Promise<T>
}
