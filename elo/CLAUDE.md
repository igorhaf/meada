# Elo — proxy transparente do Claude Code como API Anthropic

## O que é (premissa)

**Elo é um proxy transparente para o Claude Code.** Usa a assinatura do Claude (via CLI
`claude` já autenticado na máquina) e expõe endpoints **compatíveis com a Anthropic
Messages API** — qualquer cliente que fala com `api.anthropic.com` fala com o Elo trocando
só a `base_url`. Não há reimplementação de modelo: o Elo traduz request → CLI → response
no formato da API.

Cópia do `claudius` (que vive em `~/orbit/claudius`), **renomeada para Elo** para não
confundir com o Claude/Claude Code originais nem com o `claudius` do orbit. Os dois são
independentes: código, banco, portas e processos separados.

## Nomes (regra cravada)

- **Marca pública = Elo.** Nome do projeto, logger, título da API, env vars (`ELO_*`),
  banco (`elo.db`), wrapper (`run_elo.sh`), usuário do container (`elo`).
- **Identificadores técnicos do Claude PERMANECEM originais** — o proxy quebra se forem
  renomeados: binário `claude`, env vars `CLAUDECODE`/`CLAUDE_CODE_ENTRYPOINT`, diretório
  `~/.claude`, model IDs (`claude-opus-4-7`, `claude-sonnet-4-6`, `claude-haiku-4-5`) e o
  provider `claude_code`.

## Portas (sem colisão no workspace)

- **Backend (FastAPI/uvicorn): 8200** — o `claudius` do orbit usa 8001, que no workspace é
  a Meada IA API. O Elo foi movido para 8200 para coexistir sem conflito.
- **Frontend (Next.js): 3200** — o `claudius` usa 3001.

## Estrutura

```
elo/
├── backend/
│   ├── main.py                  # FastAPI — /v1/messages (Anthropic-compat) e /api/chat
│   ├── run_elo.sh               # wrapper do CLI (unset CLAUDECODE/ENTRYPOINT + exec claude)
│   ├── config.py                # API key, model IDs, timeouts, ordem de providers
│   ├── core/{orchestrator,provider}.py   # orquestração + fallback entre providers
│   ├── providers/{claude_code,deepseek}.py
│   ├── auth.py                  # x-api-key (ou Bearer)
│   ├── database.py              # SQLite (elo.db) — tabelas criadas no import (IF NOT EXISTS)
│   ├── persona.py, sessions.py, quota_tracker.py, admin_routes.py
│   └── requirements.txt
├── frontend/                    # UI de chat + admin (Next.js)
├── start.sh                     # start|stop|restart|status|logs
└── postman_collection.json      # coleção com todos os endpoints
```

## Subir

```bash
# 1ª vez (deps do backend):
cd backend && python3 -m venv venv && ./venv/bin/pip install -r requirements.txt

./start.sh start      # backend (8200) + frontend (3200)
./start.sh status
./start.sh logs backend
```

Sanity da premissa (responde no formato Anthropic, com `"_provider":"claude_code"`):

```bash
curl -s -X POST http://localhost:8200/v1/messages \
  -H "content-type: application/json" -H "x-api-key: 123456789" \
  -d '{"model":"claude-haiku-4-5","max_tokens":64,
       "messages":[{"role":"user","content":"diga: ok"}]}'
```

## Auth

Header `x-api-key` (ou `Authorization: Bearer`). Default `123456789`, sobrescrito por
`ELO_API_KEY`. O CLI usa a credencial já existente em `~/.claude` — **não** há API key da
Anthropic envolvida.

## Convenções herdadas (mantidas)

- **Postman:** atualizar `postman_collection.json` a cada mudança de endpoint/parâmetro.
- **`cwd`** em `/v1/messages` define o diretório de trabalho do CLI (passado ao subprocess).
- **CLI flags:** `setsid` (detach), `--output-format stream-json --verbose`,
  `--permission-mode bypassPermissions`, via `run_elo.sh`.
- **`thinking`** (`{"type":"enabled","budget_tokens":N}`) é suportado; blocos de thinking só
  aparecem na resposta quando o parâmetro é enviado explicitamente.
- **Parâmetros ignorados** (aceitos, sem efeito — o CLI não suporta): `temperature`,
  `top_p`, `top_k`, `stop_sequences`, `metadata`, `tool_choice`. Voltam em `_ignored_params`.
- **Frontend:** usar modo produção (`npm start`); o dev trava em "Compiling...". Remover
  `.next/dev/lock` se necessário.

## O que NÃO veio do claudius

Artefatos de análise do orbit (`ORBIT_*`, `BUSINESS_RULES_*`, `orbit_cards.json`), o
`graphify-out/` (cache) e o `claudius.db` (dados do orbit). O `elo.db` nasce vazio no
primeiro boot.
