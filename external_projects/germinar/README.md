# Germinar — site institucional de doulas

Site institucional da **Germinar** (acompanhamento de gestantes e puérperas, práticas
integrativas e formação de doulas), portado do protótipo estático em
`Site institucional para doulas/` para **Laravel 13 + PostgreSQL**, com painel de
administração dinâmico (Blade + Vue 3 nas partes interativas).

## Stack

- **Backend:** Laravel 13 (PHP 8.3), Blade no público e no admin.
- **Frontend dinâmico:** Vue 3 via Vite só onde há interação rica (listas do admin
  com drag & drop de reordenação, ativar/desativar, excluir).
- **Banco:** PostgreSQL 16 (container `germinar-postgres`).
- **CSS:** design system "Organic" do protótipo (tokens em `resources/css/tokens.css`)
  — cream `#f5ead8`, terracota `#c67139`, sálvia `#7a8a5e`, Caprasimo + Figtree.
- **Infra local:** integrado ao compose do meada (`docker-compose.yml` da raiz):
  serviços `germinar-postgres`, `germinar-app` (php-fpm) e `germinar-nginx`, atrás do
  Caddy em `http://germinar.meadadigital.local`.

## O que é dinâmico (admin)

| Seção da home | Onde edita |
|---|---|
| Contato (WhatsApp, Instagram, e-mail) | Configurações |
| Herói (kicker, título, subtítulo, CTAs, foto) | Configurações |
| Kickers/títulos das seções + rodapé | Configurações |
| Quem somos (textos + foto) | Configurações |
| "O que fazemos" (linhas com dot terracota/sálvia) | O que fazemos (CRUD + ordem) |
| Práticas integrativas (cards) | Práticas integrativas (CRUD + ordem) |
| Cursos e treinamentos (cards com tag) | Cursos (CRUD + ordem) |

Uploads de imagem vão para `storage/app/public/uploads` (exposto via `storage:link`).

## Subir local (via compose do meada)

```bash
cd /home/igorhaf/meada
docker compose up -d germinar-postgres germinar-app germinar-nginx
docker compose exec germinar-app php artisan migrate --seed
docker compose exec germinar-app php artisan storage:link

# assets (host, persiste no bind mount)
cd external_projects/germinar && npm install && npm run build
```

Acesso: `http://germinar.meadadigital.local` (via Caddy). Admin em `/admin`
(login em `/entrar` — usuário seed de dev: `root@germinar.com.br` / `password`).

## Referência de design

A pasta `Site institucional para doulas/` é o protótipo original (ferramenta de canvas):
- `Germinar - Home.dc.html` — home final consolidada (fonte da verdade do layout).
- `_ds/organic-*/styles.css` — tokens do design system.
- `index.html` — template de referência com os padrões responsivos do sistema.
