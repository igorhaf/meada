# CMS — Site por tenant (page builder multi-página)

O CMS é a primeira feature gateada por **feature flag** (camada 9.0). O tenant cujo nicho tem o CMS
ligado (pelo root, em `Plataforma → Features`) monta um **site** com **várias páginas** de blocos,
escolhe um **tema**, e pode apontar um **domínio próprio** com posse verificada.

## Para o tenant (painel — `Site`)

- **Páginas:** crie quantas precisar (ex.: `home`, `servicos`, `sobre`). Uma é a **home** (responde
  na raiz). Defina home, exclua, e publique **cada página** individualmente.
- **Blocos** por página (arraste para reordenar, ou ↑ ↓): **Hero**, **Texto** (markdown), **Serviços**,
  **Contato**, **Galeria** (imagens por URL), **FAQ**, **Depoimentos**, **Mapa** (embed do Google Maps).
- **Tema:** cor primária + fundo claro/escuro, aplicado em todas as páginas.
- **Publicar site:** o site inteiro tem um interruptor; só quando publicado (e a página publicada) a
  URL pública responde.
- **Domínio próprio:** informe o domínio, gere o **token de verificação**, crie um registro **TXT**
  `_meada-verify=<token>` no DNS do domínio e clique em **Verificar**. Depois aponte o domínio para o
  nosso servidor.

## URLs públicas

- `/p/{slug}` — home do tenant; `/p/{slug}/{pageSlug}` — páginas internas.
- Domínio próprio (verificado) — o site responde direto no domínio (home na raiz, páginas em `/{pageSlug}`).
- Só conteúdo **publicado** aparece; rascunho → 404.

## Para desenvolvedores

- **Gate:** todo `/api/cms/**` chama `requireFeature(user, ProfileFeature.CMS)` → 403 `feature_disabled`.
- **Tabelas:** `cms_sites` (config 1:1) + `cms_pages` (N por company). 1 home/company (índice parcial),
  UNIQUE (company_id, page_slug).
- **Tipos de bloco:** `CmsBlockType` (Java) ↔ `cms-block-type.ts` (TS) + parity. Adicionar um tipo =
  os 2 arquivos + `block-editor.tsx` (editor) + `cms-render.tsx` (render) + paridade.
- **Endpoints tenant:** `GET /api/cms/site` (site + páginas); `PUT /api/cms/site/{publish,theme,domain}`;
  `POST /api/cms/site/verify/start` e `/verify`; `POST /api/cms/pages`; `PUT /api/cms/pages/{id}`,
  `/{id}/home`; `DELETE /api/cms/pages/{id}`.
- **Endpoints públicos (sem auth):** `GET /public/cms/by-slug/{slug}[/{pageSlug}]`,
  `GET /public/cms/by-domain?host=[&...]`, `GET /public/cms/tls-allowed?domain=` (ask do Caddy).
- **Verificação de posse:** `DnsTxtResolver` (impl `JndiDnsTxtResolver`, JNDI DNS). O `verifyDomain`
  procura `_meada-verify=<token>` nos TXT do domínio. Mudar o domínio reseta `domain_verified`.
- **SSR:** o render público usa `CMS_BACKEND_URL` (rede interna do compose) pra falar com o backend.

## Runbook — HTTPS de domínio custom (produção)

A emissão de certificado para domínios de tenant é **on-demand TLS no Caddy** — não roda em dev.

1. No `caddy/Caddyfile` (prod), descomente o bloco `on_demand_tls` + o site catch-all (no fim do
   arquivo). O `ask` aponta pra `http://backend:8095/public/cms/tls-allowed` — o Caddy só emite cert
   pra domínios **verificados + publicados** (o backend responde 200/404).
2. O tenant configura o domínio no painel, **verifica a posse** (TXT) e aponta o DNS (A/AAAA, ou
   CNAME conforme o setup de prod) para o IP/host do Caddy.
3. No 1º acesso ao domínio, o Caddy pergunta ao `ask`, recebe 200, emite o cert (Let's Encrypt) e
   serve o site. Sem o `ask`, qualquer um forçaria emissão apontando um domínio pro nosso IP.

> Verificação de posse e o `ask` já estão no código/migration. A ativação do bloco Caddy + o
> apontamento DNS real são passos de operação de produção — não há como exercitá-los no smoke local.

## Limitações conhecidas

- Imagens da galeria são por **URL colada** (upload de arquivo depende de Storage/SERVICE_ROLE_KEY —
  bloqueador conhecido).
- O cache do resolver de feature flag tem TTL 20s: ligar/desligar o CMS leva até 20s para propagar.
- Sem editor WYSIWYG (é um formulário por bloco), sem versionamento de rascunho separado do publicado.
