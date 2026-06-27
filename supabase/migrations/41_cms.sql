-- =============================================================================
-- 41_cms.sql
-- Meada — Camada 9.x (SM-M: CMS / página pessoal por tenant). PRIMEIRA feature gateada por
-- feature flag (ProfileFeature.CMS, camada 9.0). Cada tenant cujo nicho tem o CMS ligado monta uma
-- página pública com BLOCOS arrastáveis (page builder) e pode apontar um DOMÍNIO próprio.
--
-- MODELO:
--   - cms_pages é 1:1 com company (company_id PK). 1 página por tenant.
--   - blocks é JSONB ORDENADO: array de {id, type, props}. type ∈ CmsBlockType (hardcoded:
--     hero|text|services|contact; enum Java ↔ TS + parity). A ordem do array = ordem na página.
--     A validação de type/props é APP-LEVEL (CmsService) — sem CHECK no JSONB (blocos evoluem).
--   - domain é o host próprio do tenant (ex.: "oficinadoze.com.br"), nullable, UNIQUE GLOBAL (dois
--     tenants não apontam o mesmo host). Verificação de POSSE (TXT/CNAME) e cert são fase futura —
--     esta SM só guarda + valida formato/unicidade; a página responde no host se ele apontar pra nós.
--   - published: rascunho (false) vs publicado (true). O endpoint PÚBLICO só serve publicada.
--   - O backend opera via service_role (tenant edita via /api/cms/** gateado por requireFeature(CMS);
--     o público lê via /public/cms/** sem auth, pelo backend). RLS de tenant é defesa em profundidade.
-- =============================================================================

create table public.cms_pages (
  company_id  uuid        primary key references public.companies(id) on delete cascade,
  domain      text        unique,                       -- host próprio do tenant; nullable; UNIQUE global
  published   boolean     not null default false,
  title       text        not null default '',
  blocks      jsonb       not null default '[]'::jsonb,  -- array ordenado de {id, type, props}
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.cms_pages is
  'Página pessoal (CMS) por tenant (camada 9.x / SM-M). 1:1 com company. blocks é array JSONB ordenado de {id,type,props} (type hardcoded em CmsBlockType). domain é o host próprio (nullable, UNIQUE global; posse/cert é fase futura). published gateia o endpoint público. Gateado por ProfileFeature.CMS.';

-- Resolução pública por domínio (host → company). domain já é UNIQUE; índice parcial p/ os não-nulos.
create index idx_cms_pages_domain on public.cms_pages (domain) where domain is not null;

alter table public.cms_pages enable row level security;
alter table public.cms_pages force  row level security;

-- Tenant (defesa em profundidade): lê/escreve só a própria página. O público NÃO usa RLS — lê via
-- backend (service_role). Por isso não há policy de select para anon.
create policy cms_pages_select on public.cms_pages for select to authenticated using (company_id = app.company_id());
create policy cms_pages_insert on public.cms_pages for insert to authenticated with check (company_id = app.company_id());
create policy cms_pages_update on public.cms_pages for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.cms_pages to authenticated;
grant all on public.cms_pages to service_role;
