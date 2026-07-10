# 🌿 Pindorama — Marketplace de serviços de saúde integrativa

Plataforma **multi-tenant** onde cada tenant é um **terapeuta / profissional de saúde**
(acupuntura, reiki, ayurveda, Medicina Tradicional Chinesa e demais práticas integrativas).
Cada profissional publica seus **serviços agendáveis**, especialidades e uma **landing page
própria** (com banner de identidade); o cliente **busca, reserva e paga** uma consulta; o
profissional gerencia **agenda, disponibilidade e bloqueios**. A plataforma cobra **comissão**
sobre os agendamentos e emite **cobranças** ao profissional.

O eixo do produto é o **calendário/agendamento** — não o carrinho. A stack é a mesma do
`external_projects/muda` (de onde o projeto foi clonado), mas o domínio é serviço/agenda,
não e-commerce.

> **Especificação completa do sistema:** [`docs/PINDORAMA.md`](docs/PINDORAMA.md) — domínio,
> papéis, modelo de dados, rotas, financeiro e estado atual (recuperada das sessões do projeto).

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Backend | **Laravel 13** (PHP 8.3) |
| Banco | **PostgreSQL 16** (database `pindorama`) |
| Renderização | **SSR com Blade** (HTML server-side, ótimo para SEO) |
| Interatividade | **Vue 3** montado como _islands_ dentro do Blade |
| Estilo | **Tailwind CSS v4** |
| Build | **Vite 8** |
| Orquestração | **Docker Compose** — no compose do meada (php-fpm + nginx + Postgres dedicado) |
| Pagamentos | **Mercado Pago** Checkout Transparente (mesmo esquema do muda) |

## Papéis

- **Cliente** — busca terapeutas/serviços, agenda e paga.
- **Profissional (tenant)** — serviços, especialidades, locais, disponibilidade, agenda, eventos,
  e paga as cobranças da plataforma.
- **Root/admin** — categorias de serviço, banners, salas, regras de comissão, pagamentos, config.

## Rodando (dev, via docker do meada)

O pindorama roda como projeto externo dentro do **docker-compose do meada** (não tem infra
própria — decisão cravada na criação). Acessível via Caddy em `pindorama.meadadigital.local`
(dev) e `pindorama.meadadigital.com` (prod). Porta interna `8096`.

```bash
# na raiz do meada
cp external_projects/pindorama/.env.example external_projects/pindorama/.env   # se ainda não existir
docker compose up -d pindorama-postgres pindorama-app pindorama-nginx
docker compose exec pindorama-app php artisan key:generate
docker compose exec pindorama-app php artisan migrate --seed
# assets (Vite):
cd external_projects/pindorama && npm install && npm run build
```

> ⚠️ A integração no docker-compose do meada + rota no Caddy é a última etapa pendente
> (ver o checklist de estado em [`docs/PINDORAMA.md`](docs/PINDORAMA.md)). Enquanto não fundida,
> o `docker-compose.yml` próprio na raiz do projeto serve só de referência.

## Integrações externas

Pagamento (Mercado Pago) e login social (Google) — como ativar e credenciais em
[`docs/INTEGRACOES.md`](docs/INTEGRACOES.md). (Frete/Melhor Envio herdado do muda **não se
aplica** a serviços e será removido.)
