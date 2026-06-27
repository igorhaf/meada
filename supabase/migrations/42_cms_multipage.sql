-- =============================================================================
-- 42_cms_multipage.sql
-- Meada — Camada 9.x (SM-N: CMS multi-página + tema + verificação de domínio).
-- Evolui o CMS da SM-M (1 página por tenant) para SITE com N PÁGINAS, tema por site, e posse de
-- domínio verificada por TXT.
--
-- REFACTOR DE MODELO (sem perda — a SM-M deixou cms_pages vazia após o smoke):
--   - cms_pages (SM-M, config 1:1) VIRA cms_sites: config do SITE do tenant (domain, verificação,
--     tema, published). Renomeado in-place (preserva company_id/domain/published/created_at).
--   - NOVA cms_pages: N páginas por company. Cada uma {page_slug, title, blocks ordenado, is_home,
--     position, published}. A HOME (is_home) responde em /p/{companySlug}; as outras em
--     /p/{companySlug}/{page_slug}.
--
-- CRAVADO:
--   - 1 home por company (índice parcial UNIQUE where is_home). UNIQUE (company_id, page_slug).
--   - domain_verified + verify_token: posse por registro TXT _meada-verify=<token>. A página pública
--     por domínio exige verified. Emissão de cert HTTPS é INFRA (Caddy on-demand-TLS) — fora do banco.
--   - theme é JSONB livre (ex.: {primaryColor, dark}) aplicado no render. blocks segue JSONB ordenado.
--   - RLS de tenant (defesa em profundidade); backend opera via service_role; público lê via backend.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- cms_pages (SM-M) → cms_sites: a config do SITE por tenant.
-- ---------------------------------------------------------------------------
alter table public.cms_pages rename to cms_sites;
-- renomeia constraints/índices herdados pra refletir o novo nome (não-funcional, só clareza).
alter table public.cms_sites rename constraint cms_pages_pkey to cms_sites_pkey;
alter table public.cms_sites rename constraint cms_pages_domain_key to cms_sites_domain_key;

-- a config do site não tem mais 'title'/'blocks' (isso é por-página agora). Remove e adiciona o novo.
alter table public.cms_sites drop column if exists title;
alter table public.cms_sites drop column if exists blocks;
alter table public.cms_sites add column if not exists theme jsonb not null default '{}'::jsonb;
alter table public.cms_sites add column if not exists domain_verified boolean not null default false;
alter table public.cms_sites add column if not exists verify_token text;

comment on table public.cms_sites is
  'Config do SITE (CMS) por tenant (camada 9.x / SM-N). 1:1 com company. domain (host próprio, UNIQUE,
   nullable) + domain_verified + verify_token (posse por TXT _meada-verify). theme jsonb. published
   gateia o público. As PÁGINAS vivem em cms_pages. Backend via service_role; tenant via RLS.';

-- renomeia o índice de domínio herdado (era idx_cms_pages_domain).
alter index if exists idx_cms_pages_domain rename to idx_cms_sites_domain;

-- as policies herdadas da cms_pages continuam válidas (mesma coluna company_id); renomeia p/ clareza.
alter policy cms_pages_select on public.cms_sites rename to cms_sites_select;
alter policy cms_pages_insert on public.cms_sites rename to cms_sites_insert;
alter policy cms_pages_update on public.cms_sites rename to cms_sites_update;

-- ---------------------------------------------------------------------------
-- NOVA cms_pages: as páginas do site (N por company).
-- ---------------------------------------------------------------------------
create table public.cms_pages (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete cascade,
  page_slug   text        not null check (length(trim(page_slug)) between 1 and 80),
  title       text        not null default '',
  blocks      jsonb       not null default '[]'::jsonb,   -- array ordenado de {id, type, props}
  is_home     boolean     not null default false,
  position    integer     not null default 0,             -- ordem na navegação
  published   boolean     not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (company_id, page_slug)
);

comment on table public.cms_pages is
  'Páginas do CMS (camada 9.x / SM-N). N por company. is_home=true → responde em /p/{companySlug};
   demais em /p/{companySlug}/{page_slug}. blocks JSONB ordenado de {id,type,props}. position = ordem
   na nav. published por página (além do published do site).';

-- 1 home por company (índice parcial UNIQUE).
create unique index uniq_cms_home_per_company on public.cms_pages (company_id) where is_home = true;
create index idx_cms_pages_company_pos on public.cms_pages (company_id, position);

alter table public.cms_pages enable row level security;
alter table public.cms_pages force  row level security;

create policy cms_pages_select on public.cms_pages for select to authenticated using (company_id = app.company_id());
create policy cms_pages_insert on public.cms_pages for insert to authenticated with check (company_id = app.company_id());
create policy cms_pages_update on public.cms_pages for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy cms_pages_delete on public.cms_pages for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.cms_pages to authenticated;
grant all on public.cms_pages to service_role;
