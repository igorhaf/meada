---
name: nextjs-data-fetching
description: Padrões de data fetching do frontend do Meada. Use ao buscar ou mutar dados em frontend/ — TanStack Query, apiFetch, clients em lib/api, invalidação de cache, tratamento de erro por reason, loading/error states.
---

# Data fetching (TanStack Query + apiFetch)

Padrão canônico: 186 páginas usam `useQuery`; ZERO usam fetch em `useEffect`. Nunca introduzir
fetch manual em componente.

## Client de API por recurso (`lib/api/<dominio>/<recurso>.ts`)

Funções tipadas finas sobre `apiFetch` (que injeta o token Supabase e lança `ApiError` com
`reason` do backend):

```ts
import { apiFetch } from '@/lib/api/client'
import type { AtelieCoupon } from '@/profiles/atelie/atelie-types'

export function listCoupons(): Promise<{ items: AtelieCoupon[] }> {
  return apiFetch<{ items: AtelieCoupon[] }>('/api/atelie/coupons')
}

export function createCoupon(input: CreateCouponInput): Promise<AtelieCoupon> {
  return apiFetch<AtelieCoupon>('/api/atelie/coupons', { method: 'POST', body: JSON.stringify(input) })
}
```

- Inputs como `export type XInput = {...}`; campos parciais com `Partial<>` + flags `clearX` quando
  o PATCH precisa distinguir "não veio" de "limpar".
- Valores monetários SEMPRE em cents (`priceCents`), convertidos pra R$ só na borda da UI
  (`formatBrl` do profiles/<nicho>-types).

## Leitura (useQuery)

```tsx
const { data, isPending, isError } = useQuery({
  queryKey: ['atelie-coupons'],              // kebab-case, prefixado pelo nicho
  queryFn: () => listCoupons(),
})
// listas paginadas: queryKey inclui os filtros → ['atelie-proposals', status, page]
// + placeholderData: keepPreviousData
// queries dependentes: enabled: detailId !== null
```

Render em 3 estados, nesta ordem:

```tsx
{isError ? (
  <p className="text-sm text-destructive">Erro ao carregar…</p>
) : isPending ? (
  <p className="text-sm text-muted-foreground">Carregando…</p>
) : items.length === 0 ? (
  <p className="text-sm text-muted-foreground">Nenhum registro ainda.</p>
) : ( /* lista */ )}
```

## Escrita (useMutation)

```tsx
const saveMutation = useMutation({
  mutationFn: () => createCoupon(payload),
  onSuccess: () => {
    qc.invalidateQueries({ queryKey: ['atelie-coupons'] })   // invalida TODAS as keys afetadas
    setModalOpen(false); setForm(EMPTY); setFormError(null)
  },
  onError: (e) => {
    // ✅ trata por reason do backend (contrato de erro), com fallback genérico por último
    if (e instanceof ApiError && e.reason === 'duplicate_coupon') setFormError('Já existe um cupom com esse código.')
    else if (e instanceof ApiError && e.reason === 'invalid_coupon') setFormError('Dados do cupom inválidos.')
    else setFormError('Erro ao salvar o cupom.')
  },
})
```

- Mutação que muda dado exibido em N telas invalida N queryKeys (detalhe + lista + agregados).
- `disabled={mutation.isPending}` no botão de submit; label alterna ("Salvando…").

## O que NÃO fazer

```tsx
// ❌ fetch manual em efeito (zero ocorrências no projeto — manter assim)
useEffect(() => { fetch('/api/...').then(...) }, [])

// ❌ engolir o erro sem reason (perde o contrato de erro do backend)
onError: () => alert('erro')
```
