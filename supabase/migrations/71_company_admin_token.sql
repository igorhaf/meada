-- =============================================================================
-- 71_company_admin_token.sql
-- Token curto por empresa (8 chars [a-z0-9]) que compõe o email DETERMINÍSTICO do tenant-admin:
--   meada_{slug}_{admin_token}@meadadigital.com
-- Substitui o padrão antigo (igorhaf{N}@gmail.com, vindo do seed). Gerado na criação da empresa
-- pelo backend (CompanyAdminController provisiona o admin); backfill aqui pras existentes.
-- =============================================================================

alter table public.companies
  add column if not exists admin_token text;

-- backfill: gera um token aleatório (8 hex) pra cada empresa sem token.
update public.companies
  set admin_token = substr(md5(random()::text || id::text), 1, 8)
  where admin_token is null;

-- obrigatório daqui pra frente, com default gerado (o backend também passa explícito ao provisionar).
alter table public.companies
  alter column admin_token set not null;

alter table public.companies
  alter column admin_token set default substr(md5(random()::text || clock_timestamp()::text), 1, 8);

comment on column public.companies.admin_token is
  'Token curto (8 chars hex) que compõe o email do tenant-admin: meada_{slug}_{admin_token}@meadadigital.com (camada de provisionamento, 71). Gerado na criação; imutável.';
