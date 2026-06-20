'use client'

import { usePathname, useRouter } from 'next/navigation'
import { useEffect, useRef } from 'react'

/**
 * Botão "voltar" do editor CMS — leva à ÚLTIMA rota acessada ANTES de entrar no CMS.
 *
 * O CMS tem editor tela-cheia (o shell admin some lá), então o caminho de volta é o botão Voltar.
 * `useCaptureCmsReturn()` (montado no AppShell, fora do CMS também) grava em sessionStorage a rota de
 * origem na transição fora→CMS. `useCmsBack()` devolve o handler que navega pra essa rota (fallback
 * /dashboard — cobre deep-link/F5, sessionStorage indisponível e o caso da rota salva ser o próprio
 * CMS). Tudo try/catch (SSR-safe; escrita só em useEffect, leitura só no clique).
 */

const CMS_RETURN_KEY = 'meada.cms.returnTo'

function isCmsPath(p: string | null): boolean {
  return !!p?.startsWith('/dashboard/cms')
}

/** Captura a rota de origem ao ENTRAR no CMS. Chamar incondicionalmente no AppShell. */
export function useCaptureCmsReturn(): void {
  const pathname = usePathname()
  const prev = useRef<string | null>(null)
  useEffect(() => {
    const before = prev.current
    // transição de uma rota NÃO-cms para uma rota cms → guarda a de origem.
    if (before && !isCmsPath(before) && isCmsPath(pathname)) {
      try {
        window.sessionStorage.setItem(CMS_RETURN_KEY, before)
      } catch {
        /* ignora */
      }
    }
    prev.current = pathname
  }, [pathname])
}

/** Handler de voltar (usado na topbar do editor). router.push pra rota salva (fallback /dashboard). */
export function useCmsBack(): () => void {
  const router = useRouter()
  return () => {
    let target = '/dashboard'
    try {
      target = window.sessionStorage.getItem(CMS_RETURN_KEY) || '/dashboard'
    } catch {
      /* usa fallback */
    }
    if (isCmsPath(target)) {
      target = '/dashboard' // nunca voltar pro próprio CMS
    }
    router.push(target)
  }
}
