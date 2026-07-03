---
name: tailwind-styling
description: Padrões de estilização Tailwind 4 do frontend do Meada. Use ao escrever ou editar className em frontend/ — ordem das classes, tokens de tema vs valores arbitrários, quando usar cn() vs template literal, primitivos de UI.
---

# Estilização (Tailwind 4)

## Tokens do tema, não valores arbitrários

Use os tokens semânticos do tema (definidos no CSS do projeto) — eles respondem ao dark mode e à
paleta por nicho:

```tsx
// ✅ CERTO
<p className="text-sm text-muted-foreground">…</p>
<div className="rounded-lg border border-border bg-background">…</div>
<p className="text-sm text-destructive">{error}</p>

// ❌ ERRADO (cor crua quebra dark mode/paleta do nicho)
<p className="text-sm text-[#6b7280]">…</p>
<div className="border-gray-200 bg-white">…</div>
```

Tokens correntes: `background`, `border`, `muted`, `muted-foreground`, `primary`, `destructive`.
Exceções toleradas (já majoritárias no código): verdes/vermelhos utilitários de status
(`text-emerald-600`, `text-red-600`, `bg-green-100`) — manter consistência com o Badge.

## Ordem das classes

Layout → espaçamento → dimensão → borda/raio → fundo → tipografia → estados/efeitos:

```tsx
// ✅ CERTO (ordem canônica do projeto)
className="flex items-center justify-between gap-3 px-4 py-3 rounded-lg border border-border bg-background text-sm hover:bg-muted/40"
```

## Condicionais: template literal como padrão; cn() para merge de className

```tsx
// ✅ CERTO — condicional simples (padrão dominante nas páginas)
className={`rounded-full border px-3 py-1 text-xs ${active ? 'border-primary bg-primary/10' : 'border-border'}`}

// ✅ CERTO — componente de UI que aceita className externo (usa cn de @/lib/utils)
import { cn } from '@/lib/utils'
<div className={cn('rounded-lg border p-4', className)} />

// ❌ ERRADO — concatenação manual de className de props (conflitos não resolvidos)
<div className={'rounded-lg border p-4 ' + className} />
```

## Primitivos antes de classes soltas

Para padrões já encapsulados, use os componentes de `components/ui/` em vez de recriar:
`<Badge variant="success|warning|danger|info|muted">`, `<Button variant="outline" className="h-7 px-2 text-xs">`,
`<Card>`, `<Section title="…">`, `<Modal size="sm|md|lg">`, `<AlertDialog>`.

## Tabelas e listas

- Lista de registros: `divide-y divide-border rounded-lg border border-border` com linhas
  `flex items-center justify-between gap-3 px-4 py-3`.
- Tabela de relatório: wrapper `overflow-x-auto rounded-lg border border-border` + `<table
  className="w-full text-sm">` + números com `tabular-nums text-right`.
