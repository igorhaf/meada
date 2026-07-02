-- =============================================================================
-- 82_atelie_onda2.sql
-- Meada — Onda 2 do Ateliê (backlog docs/FEATURES_SUGERIDAS_ATELIE.md #9/#10/#13/#14/#15).
--
-- Fecha as features LOCAIS restantes do nicho (as demais seguem bloqueadas por gateway #50,
-- upload/SERVICE_ROLE_KEY e motor de campanha da Onda 3):
--
--   #15 CATÁLOGO DE MATERIAIS/TÉCNICAS (atelie_catalog_items): itens pré-cadastrados (tecido,
--       bordado, mão de obra...) com preço unitário — AUTOFILL do editor de orçamento (menos erro
--       de preço, orçamento mais rápido). O item de orçamento continua SNAPSHOT texto (mudar o
--       catálogo não altera propostas passadas).
--   #13 CUPOM DE DESCONTO NA PROPOSTA (atelie_coupons + desconto materializado): clone do motor
--       sushi/academia/adega (percent/fixed, mínimo, validade, max usos, UNIQUE case-insensitive
--       por lower(code)). Aplicado PELO PAINEL (a IA não toca preço — trava do nicho); o desconto
--       é RE-DERIVADO a cada mutação de item na mesma transação do recalc do total.
--   #9  TABELA DE MEDIDAS POR CLIENTE (atelie_measurements): medidas do CONTATO (não da proposta)
--       — reuso na recompra; linhas label+valor livres (costura/arte/design medem coisas
--       diferentes), UNIQUE por (contato, lower(label)) com upsert. ADMINISTRATIVA do painel; a IA
--       NÃO recebe as medidas no contexto (trava: nunca confirma medida não cravada pela equipe).
--   #10 UPSELL DA IA NO BRIEFING: sem DDL — o AtelieContextCache injeta os NOMES do catálogo (sem
--       preço) e a persona permite UMA sugestão de complemento cadastrado, sem valor, sem insistir.
--   #14 RELATÓRIO DE FATURAMENTO: sem DDL — agregações sobre atelie_proposals 'realizada'
--       (faturamento = total − desconto, por mês / project_type / artesão).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #15 — atelie_catalog_items: catálogo de materiais/técnicas/serviços do ateliê.
-- ---------------------------------------------------------------------------
create table public.atelie_catalog_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  category         text,        -- "tecido", "acabamento", "mão de obra"... (texto livre, nullable)
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  active           boolean     not null default true,
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.atelie_catalog_items is
  'Catálogo de materiais/técnicas/serviços do tenant atelie (onda 2, backlog #15). AUTOFILL do editor de orçamento (o item da proposta continua snapshot texto) + fonte do upsell da IA (só NOMES, sem preço — backlog #10). active=false retira do autofill/upsell; delete é livre (nada referencia o catálogo por FK).';

create index idx_atl_catalog_company_active on public.atelie_catalog_items (company_id, active) where active = true;
create index idx_atl_catalog_company_name on public.atelie_catalog_items (company_id, name);

alter table public.atelie_catalog_items enable row level security;
alter table public.atelie_catalog_items force  row level security;

create policy atl_catalog_select on public.atelie_catalog_items for select to authenticated using (company_id = app.company_id());
create policy atl_catalog_insert on public.atelie_catalog_items for insert to authenticated with check (company_id = app.company_id());
create policy atl_catalog_update on public.atelie_catalog_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy atl_catalog_delete on public.atelie_catalog_items for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.atelie_catalog_items to authenticated;
grant all on public.atelie_catalog_items to service_role;

-- ---------------------------------------------------------------------------
-- #13 — atelie_coupons: clone do motor de cupom (sushi 69 / academia 77 / adega 80).
-- ---------------------------------------------------------------------------
create table public.atelie_coupons (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete cascade,
  code            text        not null check (length(trim(code)) between 1 and 40),
  kind            text        not null check (kind in ('percent','fixed')),
  value           integer     not null check (value >= 0),
  min_order_cents integer     not null default 0 check (min_order_cents >= 0),
  max_uses        integer     check (max_uses is null or max_uses >= 0),
  uses            integer     not null default 0 check (uses >= 0),
  valid_until     date,
  active          boolean     not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  check (kind <> 'percent' or value between 1 and 100)
);

comment on table public.atelie_coupons is
  'Cupons de desconto do tenant atelie (onda 2, backlog #13 — clone do motor sushi/academia/adega). Aplicado NA PROPOSTA pelo PAINEL (a IA não toca preço); o backend valida (active + valid_until + min_order sobre o total do orçamento + max_uses) e materializa discount_cents, re-derivado a cada mutação de item. uses incrementa ao aplicar e decrementa ao remover. code único (case-insensitive) por company.';

create unique index uniq_atelie_coupon_code on public.atelie_coupons (company_id, lower(code));
create index idx_atelie_coupons_company_active on public.atelie_coupons (company_id, active) where active = true;

alter table public.atelie_coupons enable row level security;
alter table public.atelie_coupons force  row level security;

create policy atl_coupon_select on public.atelie_coupons for select to authenticated using (company_id = app.company_id());
create policy atl_coupon_insert on public.atelie_coupons for insert to authenticated with check (company_id = app.company_id());
create policy atl_coupon_update on public.atelie_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy atl_coupon_delete on public.atelie_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.atelie_coupons to authenticated;
grant all on public.atelie_coupons to service_role;

-- Desconto materializado na proposta (espelho adega_orders, mig 80 §3).
alter table public.atelie_proposals
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.atelie_coupons(id) on delete set null,
  add column coupon_code_snapshot text;

comment on column public.atelie_proposals.discount_cents is
  'Desconto do cupom aplicado, MATERIALIZADO e re-derivado a cada mutação de item de orçamento (clampado ao total). Total final = total_cents − discount_cents. Aplicado pelo painel; a IA nunca toca preço.';

-- ---------------------------------------------------------------------------
-- #9 — atelie_measurements: medidas por CONTATO (reuso na recompra). Upsert por lower(label).
-- ---------------------------------------------------------------------------
create table public.atelie_measurements (
  id         uuid        primary key default gen_random_uuid(),
  company_id uuid        not null references public.companies(id) on delete restrict,
  contact_id uuid        not null references public.contacts(id) on delete cascade,
  label      text        not null check (length(trim(label)) between 1 and 100),  -- "busto", "cintura", "manga"...
  value      text        not null check (length(trim(value)) between 1 and 100),  -- "92 cm", "36", texto livre
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.atelie_measurements is
  'Tabela de medidas do CLIENTE (contato) do tenant atelie (onda 2, backlog #9). Linhas label+valor LIVRES (costura/arte/design medem coisas diferentes), keyed pelo CONTATO — reuso automático na recompra. Upsert por (contact, lower(label)). ADMINISTRATIVA do painel: a IA NÃO recebe medidas no contexto (trava: nunca confirma medida não cravada pela equipe).';

create unique index uniq_atl_measurement_label on public.atelie_measurements (company_id, contact_id, lower(label));
create index idx_atl_measurement_contact on public.atelie_measurements (company_id, contact_id);

alter table public.atelie_measurements enable row level security;
alter table public.atelie_measurements force  row level security;

create policy atl_meas_select on public.atelie_measurements for select to authenticated using (company_id = app.company_id());
create policy atl_meas_insert on public.atelie_measurements for insert to authenticated with check (company_id = app.company_id());
create policy atl_meas_update on public.atelie_measurements for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy atl_meas_delete on public.atelie_measurements for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.atelie_measurements to authenticated;
grant all on public.atelie_measurements to service_role;
