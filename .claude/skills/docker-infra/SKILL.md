---
name: docker-infra
description: Padrões de Docker e infra local do Meada. Use ao editar Dockerfile, frontend/Dockerfile, docker-compose.yml, caddy/Caddyfile ou a estrutura de variáveis de ambiente (nunca os valores).
---

# Docker e infra local

## Dockerfiles — multi-stage com stage `dev` explícito

Ambos os Dockerfiles seguem o mesmo desenho: stage de **deps/builder** cacheável, stage **prod**
enxuto e stage **dev** usado pelo compose (código real vem por volume, hot-reload).

- Backend (`Dockerfile`): `eclipse-temurin:17-jdk AS builder` → `17-jre AS runtime` →
  `17-jdk AS dev` (mvn spring-boot:run com volume em `src/`).
- Frontend (`frontend/Dockerfile`): `node:20-alpine AS dev` (deps primeiro — `COPY package.json
  package-lock.json*` antes do `COPY . .` para cachear o npm install) → `AS builder` →
  `AS prod` (standalone: copia `.next/standalone`, `.next/static`, `public`).

Regra: dependências SEMPRE copiadas/instaladas em camada própria antes do código; imagens base
fixadas por major (`node:20-alpine`, `temurin:17`), nunca `latest`.

## docker-compose.yml (dev local — fase 0.5)

- Serviços: `backend` (8095, hot-reload via volume em `src/`), `frontend` (3000, volume),
  `embeddings` (sidecar 7080), `caddy` (`caddy:2-alpine`, proxy 80 com os vhosts
  `*.meadadigital.local`). O BANCO fica fora do compose (Supabase local via CLI: 54321/54322).
- Overrides de rede vão em `environment` do compose (ex.: `embeddings:7080`,
  `api.meadadigital.local`) — o `.env` NUNCA é alterado pelo compose.
- Subir/derrubar SEMPRE pelos scripts: `./scripts/meada-up.sh` / `./scripts/meada-down.sh`
  (param o Apache da porta 80 antes).

## Env — estrutura (NUNCA valores)

- Backend: `.env` na raiz (gitignored), espelhado por `.env.example` — toda variável NOVA entra
  no `.env.example` com placeholder (`GEMINI_API_KEY=<TOKEN>`), nunca com valor real.
- Frontend: `frontend/.env.local` (gitignored) espelhado por `frontend/.env.example`. Só chaves
  `NEXT_PUBLIC_*` públicas por design (anon key protegida por RLS); NUNCA `SERVICE_ROLE_KEY` aqui.
- Em skills/docs/commits: valores sempre como placeholder (`<API_URL>`, `<TOKEN>`).

## Caddy

- `caddy/Caddyfile` roteia os subdomínios de dev; o template `on_demand_tls` do CMS fica
  COMENTADO em dev (emissão real de cert é operação de prod — ver docs/CMS.md).

## O que não fazer

- ❌ Instalar dependência de sistema no stage prod que só o dev usa.
- ❌ `COPY . .` antes do install de deps (mata o cache de camada).
- ❌ Expor porta nova no compose sem registrar na tabela de portas do CLAUDE.md do workspace.
