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
- **Lote F3** — hook `lib/use-synced-form.ts` (useSyncedForm — padrão oficial React de "adjusting
  state when props change": setState durante o render condicionado à mudança; sem lib nova) +
  12 telas de settings convertidas de `useEffect(() => setForm(...), [data])`. Arquivos: 13.
  Validação: eslint 0 no lote · `next build` ✓. Commit: cb4247f
- **Lote F4** — mais 14 telas de settings convertidas para useSyncedForm. Arquivos: 14.
  Validação: eslint 0 no lote · `next build` ✓. Commit: 8f818ca
- **Lote F5** — mais 10 telas (settings + loyalty) convertidas. Arquivos: 10.
  Validação: eslint 0 no lote · `next build` ✓. Commit: b954c8e
- **Hooks auxiliares** — useOnSync (multi-setter) e useResetWhen (reset de diálogo) somados ao
  use-synced-form.ts. Arquivos: 1. Commit: efa60a7
- **Lote F6** — 15 casos especiais: useOnSync (ai-settings, business-hours, contacts/[id], cms,
  atelie/casamento-proposals, fotografia-appointments, academia-loyalty), useResetWhen (availability,
  teams, saved-replies, create-invitation, knowledge-upload, create-service, create-faq) e
  ajuste-durante-render na invalidação de seleção do CMS. Arquivos: 15.
  Validação: eslint 0 no lote · `next build` ✓. Commit: 0031950
- **Lote F7** — resto do lint: `categories` memoizado no sushi-menu (exhaustive-deps ×2),
  `<a href="/">`→`<Link>` no meada-chrome (no-html-link-for-pages ×2), disable justificado no
  catch ES5 do widget.js, e 6 disables justificados de set-state-in-effect em efeitos LEGÍTIMOS de
  hidratação SSR/localStorage/modal (login, theme-toggle, theme-mode-provider, sidebar-context,
  global-search ×2). Arquivos: 8. Validação: eslint 0 erros no repo · `next build` ✓. Commit: 8a0514e

## Resumo final

- Lotes executados: **9** (S1 skills+CLAUDE.md · F1 types/imports · F2 eslint seguro ·
  F3/F4/F5 useSyncedForm 36 telas · hooks auxiliares · F6 casos especiais · F7 resto do lint) —
  **9 passaram, 0 revertidos**.
- ESLint: **86 problemas → 0 erros + 3 warnings** (`react-hooks/incompatible-library` —
  React Compiler pula componentes com react-hook-form; informativo, causado pela lib).
- set-state-in-effect: 60 achados zerados SEM mudança de comportamento observável — o padrão
  useEffect-sync virou sync durante o render (useSyncedForm/useOnSync/useResetWhen em
  `frontend/lib/use-synced-form.ts`, sem dependência nova); os 6 efeitos legítimos de hidratação
  ficaram como efeito com disable justificado em linha.
- Backend: sem desvios; nenhum arquivo Java alterado pela padronização (gate mvn preservado por
  construção).
- Sugestões que exigem lib/decisão: PADRONIZACAO-SUGESTOES.md (Prettier+plugin-tailwind,
  Checkstyle/Spotless, unificação dos motores de cupom).

## Pendências

- 3× `react-hooks/incompatible-library`: informativo (react-hook-form não é compilável pelo React
  Compiler) — não acionável sem trocar a lib.
- Push do branch `padronizacao-skills` + tag `pre-padronizacao`: por conta do Igor (Trava 10):
  `git push -u origin padronizacao-skills --tags`
