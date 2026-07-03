# PADRONIZACAO-LOG — padronização retroativa via skills (branch `padronizacao-skills`)

Rollback global: tag `pre-padronizacao`. Cada lote é um commit atômico revertível com `git revert`.

## Fase 1 — Auditoria (2026-07-02)

Cânone extraído da MAIORIA do código existente:

| Eixo | Medição | Cânone |
|------|---------|--------|
| Router (front) | `pages/` inexistente; 192 páginas em `app/` | App Router |
| Server×Client | 189/192 páginas com `'use client'` (públicas do CMS são server) | painel client, público server |
| type × interface | 540 `export type` × 3 `export interface` | `type` |
| Imports | 1869 `@/` × 2 relativos | absolutos `@/` |
| Data fetching | 186 páginas com `useQuery`; 0 fetch-em-useEffect | TanStack Query + `lib/api` |
| Classes condicionais | template literal dominante; `cn()` (clsx+twMerge) em 37 usos (ui/) | template literal; `cn()` p/ merge de className |
| DI (back) | 0 `@Autowired` real (1 menção em comentário) | construtor |
| DTO (back) | records aninhados nos controllers | records |
| Erro (back) | catch local + `{error, reason}` em 186 controllers; `GlobalExceptionHandler` p/ genéricos | dupla camada (domínio local, genérico global) |
| Docker | backend 17-jdk builder→jre runtime→jdk dev; front node:20-alpine dev/builder/prod standalone | multi-stage com stage dev |

Baseline ESLint (referência para "não piorar"): **86 problemas** (74 errors, 12 warnings) —
60× `react-hooks/set-state-in-effect`, 12× `react/no-unescaped-entities`, 4× `no-unused-vars`,
4× `exhaustive-deps`, 3× `incompatible-library`, 2× `no-html-link-for-pages`, 1× `no-unused-expressions`.
Backend não tem lint configurado (gate = `mvn -B clean test`, 1848 verdes na baseline).

## Fase 2 — Skills

- Lote S1: `.claude/skills/{frontend-components,tailwind-styling,nextjs-data-fetching,spring-controllers,spring-error-handling,docker-infra}/SKILL.md` + seção "Padrões de código" no CLAUDE.md.
  Arquivos: 7 (.md apenas — sem impacto em lint/test/build; gates da baseline seguem valendo).
  Commit: a7bc539

## Fase 3 — Lotes aplicados

- **Lote F1** — `type` no lugar de `interface` (cms-block-schemas ×2, data-table ×2 incl. props
  interno) + imports relativos→`@/` (page-templates ×2). Arquivos: 3 (frontend).
  Validação: eslint 86 (= baseline, não piorou) · `next build` ✓ · backend intocado (mvn n/a).
  Commit: 0f44324
- **Lote F2** — achados seguros do ESLint: aspas escapadas em texto JSX (academia-checkins/
  memberships/waitlist), imports/types não usados removidos (cms, concessionaria-reports,
  nutri-plans, cms-render) e ternário-expressão → if/else equivalente (cms). Arquivos: 7 (frontend).
  Validação: eslint 86→75 (nenhum erro novo) · `next build` ✓ · backend intocado (mvn n/a).
  Commit: fe94d52
- **Backend/Infra** — auditoria não encontrou desvios do cânone (0 @Autowired, 0 System.out,
  DTOs records, catch local + GlobalExceptionHandler, Dockerfiles já multi-stage): NENHUM lote
  necessário. mvn -B clean test da baseline: 1848 verdes.

## Resumo final

- Lotes executados: **3** (S1 skills+CLAUDE.md · F1 types/imports · F2 eslint seguro) — **3 passaram,
  0 revertidos**.
- ESLint: 86 → 75 problemas (só restam os comportamentais listados em Pendências).
- Backend: sem desvios; nenhum arquivo Java alterado pela padronização.
- Sugestões que exigem lib/decisão: PADRONIZACAO-SUGESTOES.md (Prettier+plugin-tailwind, hook
  useSyncedForm p/ zerar set-state-in-effect, Checkstyle/Spotless, unificação dos motores de cupom).

## Pendências (não aplicáveis sem risco de comportamento — Trava 2)

- 60× `react-hooks/set-state-in-effect`: o padrão `useEffect(() => setForm(...), [data])` de sync
  de formulário é usado em TODAS as telas de settings; a correção recomendada pelo React (derivar
  estado/initialData ou key) MUDA comportamento de render — fora do escopo da padronização.
- 4× `react-hooks/exhaustive-deps`: adicionar deps altera quando efeitos disparam (comportamental).
- 2× `@next/next/no-html-link-for-pages`: trocar `<a>`→`<Link>` muda navegação (client-side) — comportamental.
- 3× `react-hooks/incompatible-library`: informativo (bibliotecas de terceiros).
