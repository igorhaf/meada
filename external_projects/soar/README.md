# Soar

Dashboard colaborativo estilo Notion: páginas em árvore, com um espaço **compartilhado**
(grupo único — todos os usuários veem e editam) e um espaço **pessoal** por usuário.

É a plataforma de organização da família: cada página é uma mini-aplicação —
agenda (Google Calendar), tarefas, gastos (Google Sheets), cofre de senhas cifrado,
registros dinâmicos (cartões, filhos, cachorro), remédios com lembretes e dietas
geradas por IA. Tudo operável pelo **assistente no Telegram** (@RosendoFrancaBot),
que conversa via **Elo** (proxy do Claude Code).

## Stack

- **Backend:** Laravel 13 (PHP 8.3) + **Supabase Postgres** (nuvem) — API REST com
  Sanctum. Scheduler com lembretes de remédio, resumo diário e alertas de receita/estoque.
- **Frontend:** React 19 + Vite + TypeScript + Tailwind 4 + TanStack Query + React Router.
- **IA:** Elo (proxy Claude Code, host:8200) — ações por tag `<acao>` no Telegram e
  geração de dietas. **Google:** service account (Calendar por usuário + Sheets de gastos).

## Subir (pelo compose do MEADA — não há docker próprio)

O soar é integrado ao `docker-compose.yml` da raiz do meada (serviços `soar-backend`
e `soar-frontend`; `soar-bot` no override, só local), sem portas expostas no host — o acesso é só via
Caddy, no padrão dos demais projetos externos:

```bash
cd ~/meada
docker compose up -d soar-backend soar-frontend soar-bot
docker compose restart caddy   # se o Caddyfile mudou
```

- **Local:** http://soar.meadadigital.local (Caddy roteia `/api/*` → `soar-backend:8000`,
  resto → `soar-frontend:3130` — mesma origem, sem CORS).
- **Prod:** https://soar.meadadigital.com (bloco equivalente no `Caddyfile.prod`).

O backend migra e semeia sozinho no boot (seed idempotente — só roda com o banco vazio).
O banco é o **Supabase** (compartilhado entre local e produção — cuidado: dados reais).
Segredos vivem SÓ em `backend/.env` (gitignored): senha do banco, `SEED_USER_PASSWORD`,
`TELEGRAM_BOT_TOKEN`, `SOAR_ELO_KEY` e credenciais Google. ⚠️ O `APP_KEY` precisa ser o
MESMO em local e produção (o cofre de senhas cifra com ele).

O serviço `soar-bot` (bot Telegram + scheduler) roda **só local** (precisa do Elo no
host:8200) e opera os mesmos dados da produção via Supabase.

### Google (Calendar + Sheets) — ativar depois

1. No Google Cloud: criar service account, baixar o JSON e apontar `GOOGLE_SA_JSON_PATH`.
2. Igor e Aline compartilham seus calendários (gmail) com o `client_email` da SA
   (permissão "Fazer alterações"); `users.google_calendar_id` já aponta os gmails.
3. Compartilhar a planilha de gastos com a SA e setar `GOOGLE_SHEET_ID_GASTOS`.
Sem credenciais, o sync fica desligado (best-effort) e o resto do app funciona normal.

## Usuários (seed)

| Usuário | E-mail                    | Senha |
|---------|---------------------------|-------|
| Aline   | alinecarla.rs@gmail.com   | `SEED_USER_PASSWORD` |
| Igor    | igorhaf@gmail.com         | `SEED_USER_PASSWORD` |

Ambos compartilham a árvore **Compartilhado** e cada um tem sua árvore **Pessoal**
com páginas de exemplo. E-mail geral do projeto (reservado para uso futuro):
`rfsolucoesfamiliares@gmail.com`.

## API

Autenticação: `Authorization: Bearer <token>` (obtido no login). Erros seguem o
contrato `{ "error": "...", "reason": "..." }`.

| Método | Rota                    | Descrição |
|--------|-------------------------|-----------|
| POST   | `/api/auth/login`       | Login → `{ token, user }` |
| POST   | `/api/auth/logout`      | Revoga o token atual |
| GET    | `/api/auth/me`          | Usuário logado |
| GET    | `/api/tree`             | `{ shared: [...], personal: [...] }` (árvores aninhadas) |
| POST   | `/api/pages`            | Cria página (`scope`, `parent_id?`, `title`, `icon?`, `content?`) |
| GET    | `/api/pages/{id}`       | Detalhe da página (com `content`) |
| PUT    | `/api/pages/{id}`       | Atualiza `title` / `icon` / `content` |
| PATCH  | `/api/pages/{id}/move`  | Move (`parent_id`, `position`) — valida escopo e ciclo |
| DELETE | `/api/pages/{id}`       | Exclui (subpáginas caem em cascata) |

Regras de acesso: páginas `shared` são visíveis/editáveis por qualquer usuário
autenticado; páginas `personal` só pelo dono (`403` para os demais). Página filha
herda obrigatoriamente o escopo do pai (`422 scope_mismatch`).
