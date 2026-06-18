# CMS — Página pessoal por tenant (SM-M, page builder)

O CMS é a primeira feature gateada por **feature flag** (camada 9.0). O tenant cujo nicho tem o CMS
ligado (pelo root, em `Plataforma → Features`) monta uma **página pública** com blocos e pode apontar
um **domínio próprio**.

## Para o tenant (painel)

Em **Site → Página** (`/dashboard/cms`):

- **Título** da página.
- **Blocos** (page builder): adicione, remova e **reordene** (arraste ou use ↑ ↓). 4 tipos:
  - **Destaque (Hero)** — título, subtítulo, botão (texto + link).
  - **Texto** — conteúdo livre em markdown.
  - **Serviços** — título + lista de itens (nome, descrição, preço).
  - **Contato** — telefone, WhatsApp, endereço, horário + botão de WhatsApp.
- **Salvar** (rascunho) e **Publicar / Despublicar**.
- **Domínio próprio** (opcional): informe o domínio do seu negócio. Aponte-o para o nosso servidor;
  a verificação de posse e o certificado são etapas posteriores — por ora a página já responde no
  domínio se ele apontar para nós.
- Quando publicada, a página fica acessível em **`/p/{slug}`** (e no seu domínio, se configurado).

## Como a página pública funciona

- `/p/{slug}` — render público pelo slug da empresa (server-side, sem login).
- Domínio próprio — quando o host não é um domínio do Meada, o middleware resolve o tenant pelo
  domínio e serve a mesma página.
- Só páginas **publicadas** aparecem; rascunho dá 404.

## Para desenvolvedores

- **Gate:** todo `/api/cms/**` chama `ProfileFeatureGuard.requireFeature(user, ProfileFeature.CMS)`
  → 403 `feature_disabled` se o nicho não tem a flag.
- **Tabela:** `cms_pages` (migration 41), 1:1 com company. `blocks` JSONB ordenado de `{id, type, props}`.
- **Tipos de bloco:** hardcoded em `CmsBlockType` (Java) ↔ `cms-block-type.ts` (TS), com parity test.
  Adicionar um tipo = editar os 2 arquivos + o editor (`BlockEditor`) + o render (`cms-render.tsx`) +
  rodar a paridade.
- **Endpoints tenant:** `GET/PUT /api/cms/page` (title+blocks), `PUT /api/cms/page/publish`,
  `PUT /api/cms/page/domain`.
- **Endpoints públicos (sem auth):** `GET /public/cms/by-slug/{slug}`, `GET /public/cms/by-domain?host=`.
- **SSR:** o render público usa `CMS_BACKEND_URL` (rede interna do compose) para falar com o backend.

## Pendências (fases futuras)

- Verificação de posse de domínio (TXT/CNAME) + emissão de certificado.
- Mais tipos de bloco (galeria, FAQ, mapa, depoimentos…), tema/cores por página, multi-página.
- O cache do resolver de feature flag tem TTL de 20s: ligar/desligar o CMS leva até 20s para
  propagar ao gate e ao `/admin/me`.
