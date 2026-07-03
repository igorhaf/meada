---
name: frontend-components
description: Padrões de componentes e páginas do frontend Next.js/TypeScript do Meada. Use ao criar ou editar páginas em frontend/app, componentes em frontend/components, types em frontend/profiles ou clients em frontend/lib — nomenclatura, Server vs Client Components, types vs interface, imports absolutos, estrutura de pastas.
---

# Componentes e páginas (Next.js + TypeScript)

Padrão canônico extraído da maioria do código (auditoria 2026-07: 540 `type` vs 3 `interface`;
1869 imports `@/` vs 2 relativos; 189 de 192 páginas client).

## Estrutura de pastas (App Router — NÃO existe pages/)

- `app/(protected)/dashboard/<área>/page.tsx` — telas do painel (1 arquivo por rota; kebab-case
  prefixado pelo nicho: `atelie-proposals`, `barber-coupons`, `comida-zones`).
- `app/p/...` — páginas públicas do CMS (server components, sem shell).
- `components/ui/` — primitivos compartilhados (Button, Card, Modal, Badge, AlertDialog).
- `components/layout/` — casca do painel (sidebar, page-header, nav-config).
- `lib/api/<dominio>/<recurso>.ts` — funções de fetch tipadas por recurso (1 arquivo por recurso).
- `profiles/<nicho>/<nicho>-types.ts` — types e helpers de formatação do nicho (espelham os
  records Java 1:1; paridade garantida por *ParityTest no backend).

## Server vs Client

- Painel (`(protected)/dashboard`) = **Client Component**: primeira linha `'use client'`.
- Páginas públicas do CMS (`app/p/`) = **Server Component** (sem `'use client'`, fetch no servidor
  via `lib/cms/public-fetch.ts`).
- Não misturar: página de painel nunca faz fetch no servidor; página pública nunca usa hooks.

## Types, não interfaces

```ts
// ✅ CERTO (padrão do projeto — espelha o record Java)
export type AtelieProposal = {
  id: string
  customerName: string
  totalCents: number
}

// ❌ ERRADO (interface só sobrevive em 2 arquivos legados; não criar novas)
export interface AtelieProposal { ... }
```

Union literais para ids de status/categoria, derivadas do array canônico:

```ts
export const FITTING_STATUSES = [
  { id: 'pendente', label: 'Pendente' },
  { id: 'realizada', label: 'Realizada' },
] as const
export type FittingStatusId = (typeof FITTING_STATUSES)[number]['id']
```

## Imports

- SEMPRE absolutos com `@/`: `import { Button } from '@/components/ui/button'`.
- NUNCA relativos (`../`).
- Ordem por blocos: 1) libs externas (react, @tanstack), 2) linha em branco, 3) `@/components`,
  4) `@/lib`, 5) `@/profiles`.

## Nomenclatura

- Arquivos: kebab-case (`barber-types.ts`, `page-header.tsx`). Componentes: PascalCase exportado
  default nas páginas (`export default function AtelieProposalsPage()`).
- Estado de formulário: `type FormState = {...}` + `const EMPTY: FormState = {...}` + `useState`.
- Textos de UI em pt-BR, sem i18n (regra do workspace: pt-BR hardcoded sempre).

## Estado e formulários

- Estado local com `useState`; formulários controlados; sem lib de formulário nas telas de painel
  (react-hook-form/zod só onde já existe).
- Erros de mutação em estado local (`const [formError, setFormError] = useState<string | null>(null)`)
  renderizados como `<p className="text-sm text-destructive">`.
- Loading: `isPending` da query → `<p className="text-sm text-muted-foreground">Carregando…</p>`.
