# CLAUDE.md — Meada WhatsApp

Instruções para qualquer instância do Claude trabalhando neste projeto. Leia antes de
agir. Estas regras foram cravadas pelo Igor ao longo das sessões e têm precedência.

> Este arquivo é **território do Claude Code** (como DEVELOPMENT.md e RISKS.md): o agente
> lê, cria seções, atualiza convenções e registra lições sem precisar de autorização por
> edição. NÃO confundir com `.env`/`.env.local` (segredos, território do Igor).

## O que é o projeto

SaaS multi-empresa de atendimento ao cliente via WhatsApp com IA. Cada empresa (tenant)
tem um atendente de IA treinado com seus próprios dados (serviços, horários, FAQs,
preços), respondendo clientes pelo WhatsApp. Dados isolados por tenant via RLS.

- **Backend:** Spring Boot 3.3.13 + Java 17 Temurin, single-module Maven. JdbcTemplate
  (não JPA), sem Lombok, sem webflux. HTTP outbound síncrono via RestClient.
- **Frontend:** Next 16 (app router) + React 19 + TypeScript + Tailwind 4 + shadcn/ui 4
  + @base-ui/react (NÃO Radix) + TanStack Query + react-hook-form + zod + @supabase/ssr.
  Em `frontend/`, isolado do Maven.
- **Banco/Auth:** Supabase (Postgres 17 + Auth + Storage). IA: Gemini Flash.
  WhatsApp: Evolution API self-hosted (evoapicloud/evolution-api:v2.3.1).
- **Detalhes operacionais vivos** (portas, envs, credenciais, estado das camadas):
  ver `CONTEXT.md` na raiz — ele é gitignored e mais detalhado que este arquivo.

## Padrão de trabalho (precedência sobre comportamento default)

- **Decisões em PROSA, nunca em widget.** Não usar o widget de perguntas com abas
  (AskUserQuestion). Igor não interage bem com ele. Apresentar opções como texto,
  com recomendação explícita, e esperar a resposta. (Regra cravada e reforçada.)
- **Proposta em prosa antes de código.** Para qualquer mudança não-trivial: descrever
  a abordagem em prosa, esperar aprovação, só então escrever.
- **Bruto LITERAL antes do Write.** Colar o conteúdo exato do arquivo (em code fence)
  para revisão ANTES de aplicar o Write. Refatoração puramente mecânica pode ser
  aprovada por descrição; lógica nova, não.
- **Write visível após cada aprovação.** Aprovar conteúdo NÃO é o mesmo que arquivo
  escrito — o Write tem que aparecer. (Lição 4.0: um arquivo foi aprovado mas nunca
  escrito; só pego no sanity pré-commit.)
- **Honestidade sobre incertezas.** Não improvisar workaround sem consultar. Não
  fabricar resultados de teste — reportar literal sempre, inclusive falhas.
- **Conferir o estado real antes de "consertar".** Não propor fix de algo sem checar
  no código/banco que o problema existe. (Lição: propus mover um log para WARN que já
  era WARN — trabalho inventado.)
- **Nunca assumir "arquivo vazio" por leitura parcial.** `wc -c`/`cat` antes de decidir
  overwrite vs append.
- **Contagem de testes vem do Surefire (`Tests run: N`), nunca de `grep @Test`.**
  (Lição: grep textual contou 137; o real era 129.)

## Padrão de git

- **Commits semânticos**, mensagem em português, prefixo `feat/fix/docs/chore(camada-N):`.
  Mensagem multi-linha via `git commit -F <arquivo>` (não `-m`), para preservar formatação.
- **`git add` arquivo a arquivo, lista explícita. NUNCA `git add .` nem wildcard.**
  Isso protege contra incluir arquivos não revisados (ex.: `CLAUDE.md` enquanto incompleto,
  ou `.env`).
- **Sanity de staging antes de todo commit:** `git status -s` + `git diff --staged --stat`
  + grep por segredo (`eyJ...`, `password`, `secret=`) + confirmar que `.env`/`.env.local`
  estão FORA da staging. Confirmar com o Igor antes do commit.
- **Segredos nunca são commitados.** `.env`, `frontend/.env.local`, `evolution-local/.env`
  e `CONTEXT.md` são gitignored. Ao validar presença de env, fazer sem expor valor
  (`grep -c`, `wc -c`, mascarar). **Senhas nunca vão em arquivo** — só em comunicação direta.
- **Tag anotada por sub-fase fechada:** `git tag -a fase-N.M-fechada -F <msg>` apontando
  para o commit que a fecha. Tags não se movem depois de criadas.
- **Trailer obrigatório** no fim de cada commit:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- **Repo é local (sem remote). NÃO fazer `git push`.** Branch `main`.
- **Critério de "fechado" de sub-fase que toca backend:** `mvn -B clean test` verde
  (gate empírico com a contagem do Surefire). Que toca frontend: `next build` limpo
  (Turbopack dev é lazy e esconde import quebrado — build de prod é a verdade) + smoke.

## Comandos de boot (ambiente local)

- **Backend** (Spring, porta 8095): `./scripts/run-local.sh` (usa Temurin JDK 17). Sobe em
  ~1.5s; conecta ao Supabase como `service_role`. Não tem rota raiz nem actuator → 404 na
  raiz é saudável; sanity real é `GET /admin/me` sem token → 401 `missing_auth_header`.
- **Frontend** (Next, porta 3000): `cd frontend && npm run dev`. `/login` → 200.
- **Testes backend:** `JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64 mvn -B clean test`.
- **Build frontend:** `cd frontend && npm run build`.
- **Banco (psql direto):** Supabase Session pooler IPv4
  (`aws-1-us-west-2.pooler.supabase.com:5432`, user `postgres.<ref>`); senha em
  `SPRING_DATASOURCE_PASSWORD` do `.env`. Smoke E2E real: login via
  `POST {SUPABASE_URL}/auth/v1/token?grant_type=password` → token ES256 → bater no
  backend ou no PostgREST (`/rest/v1/...`).

## Usuários de teste (apenas referencial — senhas só em comunicação direta)

- **super-admin:** `igorhaf@gmail.com` (na allowlist `ADMIN_SUPER_ADMIN_EMAILS`).
- **tenant-admin:** `igorhaf2@gmail.com` (linha em `public.users`, company_id = Empresa
  Alpha `52e88a0b-...`, role `admin`).
- Empresas seed: Alpha (`52e88a0b-...`), Beta (`38cdac12-...`), Meada Delta 01.

## Arquitetura de auth (camada 4 — painel admin)

Dois perfis, duas vias:
- **super-admin** (meada): allowlist `ADMIN_SUPER_ADMIN_EMAILS`, opera via Spring/
  service_role, FORA do RLS. Lê tudo (ex.: lista global de empresas).
- **tenant-admin**: opera via Supabase SDK + RLS. Só vê/escreve dados da própria empresa.
  CRUD interno do tenant é SDK+RLS; super-admin e chamadas externas são Spring REST.
- JWT do Supabase é ES256 validado por JWKS (filtro próprio `JwtAuthenticationFilter`,
  não Spring Security). RLS no banco via `app.company_id()` (lê `company_id` de
  `public.users` por `auth.uid()`). WRITE via SDK exige `company_id` explícito no payload
  (policy WITH CHECK revalida — defesa em profundidade, provado E2E na 4.4).

## Estado das camadas

- **1 — Schema multi-tenant:** FECHADA. 11 tabelas, RLS, FKs compostas anti-cross-tenant.
- **2 — Webhook Evolution inbound:** FECHADA.
- **3 — IA + outbound:** FECHADA, validada E2E.
- **4 — Painel admin:** em andamento.
  - 4.0 scaffold + login · 4.1 super-admin lista empresas (JWT ES256/JWKS) ·
    4.2 super-admin cria empresa · 4.3 tenant vê sua empresa (SDK+RLS) ·
    4.4 tenant lista+cria services (1º WRITE via SDK+RLS). Todas FECHADAS (tags).
  - Próximo: 4.4.x (update/delete de services; replicar para faqs/business_hours/
    ai_settings) · 4.5 ver conversas · 4.6 parear Evolution pelo painel.

## Incidente registrado (ver RISKS.md)

Re-sync de histórico do Baileys/Evolution disparou respostas automáticas a contatos
reais no boot (2026-06-10). MITIGADO: dry-run em local (`EVOLUTION_DRY_RUN=true`,
`EvolutionClient` loga em vez de enviar) + guard de frescor por `messageTimestamp`
(`webhook.message-max-age-seconds`, rejeita `messages.upsert` antigos). **Webhook
permanece OFF até religar consciente.** Não religar sem verificar dry-run + threshold.
