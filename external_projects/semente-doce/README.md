# 🍬 Semente Doce — monorepo

Doceria & salgaderia artesanal. O projeto é um monorepo com três frentes:

| Pasta | O que é | Stack |
|-------|---------|-------|
| [`backend/`](backend/) | API + loja web (SSR Blade + ilhas Vue) + painel administrativo | Laravel 13 · PHP 8.3 · PostgreSQL 16 |
| [`frontend/`](frontend/) | **Reservada.** A vitrine web hoje é servida pelo backend (Blade). Extração futura p/ SPA/Next | — |
| [`mobile/`](mobile/) | Aplicativo do cliente (cardápio, kits, sacola, encomendas) | React Native · Expo |

## Infra

O backend roda **dentro do ambiente Docker do meada** (serviços `semente-app` + `semente-nginx`
+ `semente-postgres` no `docker-compose.yml` da raiz do meada), atrás do proxy `meada-caddy`:
**http://semente-doce.meadadigital.local**. Detalhes de subida: [`backend/README.md`](backend/README.md).

O app mobile roda com Expo (`cd mobile && npm install && npx expo start`) e consome a API
JSON do backend (`/api/v1/...`). Detalhes: [`mobile/README.md`](mobile/README.md).
