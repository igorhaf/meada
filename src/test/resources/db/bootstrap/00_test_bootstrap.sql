-- =============================================================================
-- 00_test_bootstrap.sql — ANDAIME DE TESTE (NÃO roda em produção).
--
-- SIMULA a superfície da plataforma Supabase que as migrations 01..06 assumem
-- existir, mas que um PostgreSQL puro (Testcontainers) não tem:
--   - schema auth + auth.users + auth.uid()
--   - schema storage + storage.buckets + storage.objects + storage.foldername()
--   - roles service_role / authenticated / anon
--
-- O loader de teste roda este script ANTES de 01..06. Em produção, só 01..06
-- rodam (o Supabase já provê tudo o que está aqui).
--
-- ATUALIZAR este arquivo quando o Supabase mudar a assinatura de auth.uid(),
-- de storage.foldername(), os schemas internos, ou os roles. Verde aqui NÃO
-- garante verde em produção — é uma simulação fiel-o-suficiente, não a plataforma.
-- =============================================================================

-- ---- auth (identidade) ------------------------------------------------------
create schema if not exists auth;

-- Superfície mínima da FK users.id -> auth.users.id (migration 02). Só o id.
create table if not exists auth.users (
  id uuid primary key
);

-- auth.uid() lê o 'sub' do claim JWT no formato PLURAL (JSON inteiro em
-- request.jwt.claims), idêntico ao Supabase real e ao validation.sql.
--   true em current_setting → não estoura se o GUC estiver ausente.
--   nullif(...,'') → string vazia vira NULL antes do ::uuid.
--   ::json (não jsonb) → replica o Supabase.
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
  select nullif(
    current_setting('request.jwt.claims', true)::json ->> 'sub',
    ''
  )::uuid
$$;

-- ---- storage ----------------------------------------------------------------
create schema if not exists storage;

-- storage.buckets: o 05 faz INSERT ... ON CONFLICT DO NOTHING no bucket 'documents'.
create table if not exists storage.buckets (
  id     text primary key,
  name   text,
  public boolean
);

-- storage.objects: mínimo que o 05 referencia (bucket_id, name) + RLS habilitável.
create table if not exists storage.objects (
  id        uuid primary key default gen_random_uuid(),
  bucket_id text,
  name      text,
  owner     uuid
);

-- Em produção (Supabase), storage.objects já vem com RLS habilitado pela
-- plataforma. Aqui simulamos esse estado pra que as policies do 05 fiquem
-- ATIVAS em teste, não apenas definidas-mas-dormindo.
alter table storage.objects enable row level security;

-- storage.foldername(text) → segmentos do path. [1] = 1º segmento (company_id).
create or replace function storage.foldername(text)
returns text[]
language sql
immutable
as $$
  select string_to_array($1, '/')
$$;

-- ---- roles da plataforma Supabase -------------------------------------------
-- Criados ANTES de 01..06 para que: (a) o connection-init-sql SET ROLE service_role
-- funcione; (b) o 04_grants.sql consiga GRANT ... TO service_role/authenticated.
-- service_role tem BYPASSRLS (igual Supabase). noinherit: o role não herda
-- privilégios automaticamente — só ao fazer SET ROLE explícito.
create role service_role with bypassrls noinherit;
create role authenticated noinherit;
create role anon noinherit;

-- O superuser corrente precisa poder ASSUMIR esses roles via SET ROLE (o
-- connection-init-sql conecta como o superuser e faz SET ROLE service_role).
-- current_user resolve para o superuser real em qualquer ambiente: 'test' no
-- Testcontainers, 'postgres' no Supabase. Sem hardcode.
grant service_role, authenticated, anon to current_user;
