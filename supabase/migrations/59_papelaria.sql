-- =============================================================================
-- 59_papelaria.sql
-- Meada WhatsApp — Camada 8.15 (SM: perfil Papelaria / Convites personalizados). CLONA o chassi do
-- PADARIA (52_padaria.sql) — pedido com lead time + made_to_order + data condicional + gate de aceite +
-- catálogo + modifiers + total recalculado + fulfillment — e INAUGURA a ESCAPADA:
--
--   ESCAPADA — PROVA DE ARTE (gate de aprovação do layout pelo cliente DENTRO de um pedido order-based):
--   a papelaria é encomenda GRÁFICA; antes de imprimir, a equipe faz a ARTE e precisa do OK do cliente. O
--   pedido ganha um estado EXTRA no funil: 'arte_aprovacao' (entre 'aceito' e 'em_producao'). Campo
--   `art_approved boolean` + `art_url text` (a "arte subida" é link colado — sem upload por
--   SERVICE_ROLE_KEY). A transição 'arte_aprovacao'→'em_producao' SÓ é permitida com art_approved=true
--   (senão 409 art_not_approved). A IA captura a aprovação do cliente via tag <aprovacao_arte> (muta o
--   estado de um pedido EXISTENTE — espelho do AprovacaoOs/AprovacaoProposta, mas a aprovação é da ARTE),
--   só quando o pedido está em 'arte_aprovacao'. A IA NÃO sobe arte nem move pra arte_aprovacao (ação
--   humana). TIRAGEM: order_item.quantity é a tiragem (50/100/200) e escala o line total (unit × qtd).
--
-- Convenções (padrão 30-67): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; orders/order_items/order_item_options INSERT só backend; total/unit_price materializados;
-- categorias/fulfillment hardcoded (parity).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'papelaria'. ESPELHA a CHECK mais completa (56_otica, 31 perfis) +
-- 'papelaria'. Entra por ÚLTIMO no SCRIPTS de teste (sua lista tem os 32).
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las',
                        'padaria','otica','papelaria'));

-- ---------------------------------------------------------------------------
-- papelaria_config — taxa + mínimo + lead_time_days_default (clone padaria_config).
-- ---------------------------------------------------------------------------
create table public.papelaria_config (
  company_id            uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents    integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents       integer     not null default 0 check (min_order_cents >= 0),
  lead_time_days_default integer    not null default 5 check (lead_time_days_default >= 0),
  created_at            timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.papelaria_config is
  'Config do tenant papelaria (camada 8.15): taxa + mínimo + lead_time_days_default. 1:1 com company. Clone padaria_config.';

alter table public.papelaria_config enable row level security;
alter table public.papelaria_config force  row level security;

create policy papelaria_config_select on public.papelaria_config for select to authenticated using (company_id = app.company_id());
create policy papelaria_config_insert on public.papelaria_config for insert to authenticated with check (company_id = app.company_id());
create policy papelaria_config_update on public.papelaria_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.papelaria_config to authenticated;
grant all on public.papelaria_config to service_role;

-- ---------------------------------------------------------------------------
-- papelaria_catalog_items — catálogo (+ made_to_order + lead_time + specs). Clone padaria_menu_items.
-- ---------------------------------------------------------------------------
create table public.papelaria_catalog_items (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  name          text        not null check (length(trim(name)) between 1 and 200),
  description   text,
  price_cents   integer     not null check (price_cents >= 0),   -- preço BASE unitário
  category      text        not null check (category in
                  ('convites','save_the_date','cartoes','papelaria','adesivos','embalagens')),
  made_to_order boolean     not null default false,
  lead_time_days integer    check (lead_time_days is null or lead_time_days >= 0),
  specs         text,                                            -- gramatura/material (texto livre informativo)
  available     boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.papelaria_catalog_items is
  'Catálogo do tenant papelaria (camada 8.15). price_cents = preço BASE UNITÁRIO (× tiragem no pedido). made_to_order = sob encomenda (exige data que respeite lead_time). Categorias hardcoded (PapelariaCategory). Sem foto.';

create index idx_papelaria_catalog_company_cat on public.papelaria_catalog_items (company_id, category) where available = true;

alter table public.papelaria_catalog_items enable row level security;
alter table public.papelaria_catalog_items force  row level security;

create policy papelaria_catalog_select on public.papelaria_catalog_items for select to authenticated using (company_id = app.company_id());
create policy papelaria_catalog_insert on public.papelaria_catalog_items for insert to authenticated with check (company_id = app.company_id());
create policy papelaria_catalog_update on public.papelaria_catalog_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy papelaria_catalog_delete on public.papelaria_catalog_items for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.papelaria_catalog_items to authenticated;
grant all on public.papelaria_catalog_items to service_role;

-- ---------------------------------------------------------------------------
-- papelaria_catalog_item_options — modifiers (Papel/Acabamento/Cor/Tamanho). Clone padaria opts.
-- ---------------------------------------------------------------------------
create table public.papelaria_catalog_item_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  catalog_item_id   uuid        not null references public.papelaria_catalog_items(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),   -- "Papel","Acabamento","Cor"
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.papelaria_catalog_item_options is
  'Modifiers/personalização de um item da papelaria (camada 8.15). Papel/Acabamento/Cor/Tamanho. price_delta soma ao preço base. on delete cascade.';

create index idx_papelaria_opt_item on public.papelaria_catalog_item_options (catalog_item_id, sort_order) where available = true;
create index idx_papelaria_opt_company on public.papelaria_catalog_item_options (company_id);

alter table public.papelaria_catalog_item_options enable row level security;
alter table public.papelaria_catalog_item_options force  row level security;

create policy papelaria_opt_select on public.papelaria_catalog_item_options for select to authenticated using (company_id = app.company_id());
create policy papelaria_opt_insert on public.papelaria_catalog_item_options for insert to authenticated with check (company_id = app.company_id());
create policy papelaria_opt_update on public.papelaria_catalog_item_options for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy papelaria_opt_delete on public.papelaria_catalog_item_options for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.papelaria_catalog_item_options to authenticated;
grant all on public.papelaria_catalog_item_options to service_role;

-- ---------------------------------------------------------------------------
-- papelaria_orders — pedidos (INSERT backend; gate de aceite + PROVA DE ARTE + data condicional + fulfillment).
-- ---------------------------------------------------------------------------
create table public.papelaria_orders (
  id                      uuid        primary key default gen_random_uuid(),
  company_id              uuid        not null references public.companies(id) on delete restrict,
  conversation_id         uuid        not null references public.conversations(id) on delete restrict,
  contact_id              uuid        not null references public.contacts(id) on delete restrict,
  status                  text        not null default 'aguardando' check (status in
                            ('aguardando','aceito','arte_aprovacao','em_producao','pronto','retirado',
                             'saiu_entrega','entregue','recusado','cancelado')),
  fulfillment             text        not null default 'retirada' check (fulfillment in ('retirada','entrega')),
  subtotal_cents          integer     not null,
  delivery_fee_cents      integer     not null default 0,
  total_cents             integer     not null,
  delivery_address        text,
  pickup_or_delivery_date date,
  delivery_period         text        check (delivery_period is null or delivery_period in ('manha','tarde')),
  art_approved            boolean     not null default false,    -- ESCAPADA: gate de aprovação da arte
  art_url                 text,                                  -- link/ref da arte subida (sem upload)
  notes                   text,
  rejection_reason        text,
  created_at              timestamptz not null default now(),
  status_updated_at       timestamptz not null default now()
);

comment on table public.papelaria_orders is
  'Pedidos do tenant papelaria (camada 8.15). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite humano). ESCAPADA prova de arte: estado ''arte_aprovacao'' (entre aceito e em_producao); art_approved + art_url; arte_aprovacao→em_producao exige art_approved=true (409 art_not_approved). A IA só CAPTURA a aprovação via <aprovacao_arte> (nunca sobe arte). fulfillment retirada/entrega. Total materializado.';

create index idx_papelaria_orders_company_status on public.papelaria_orders (company_id, status, created_at desc);
create index idx_papelaria_orders_conversation on public.papelaria_orders (conversation_id);

alter table public.papelaria_orders enable row level security;
alter table public.papelaria_orders force  row level security;

create policy papelaria_orders_select on public.papelaria_orders for select to authenticated using (company_id = app.company_id());
create policy papelaria_orders_update on public.papelaria_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.papelaria_orders to authenticated;
grant all on public.papelaria_orders to service_role;

-- ---------------------------------------------------------------------------
-- papelaria_order_items — itens (snapshot + TIRAGEM(quantity) + custom_text). Clone padaria_order_items.
-- ---------------------------------------------------------------------------
create table public.papelaria_order_items (
  id                      uuid        primary key default gen_random_uuid(),
  order_id                uuid        not null references public.papelaria_orders(id) on delete cascade,
  catalog_item_id         uuid        not null references public.papelaria_catalog_items(id) on delete restrict,
  quantity                integer     not null check (quantity > 0),   -- TIRAGEM (50/100/200…)
  unit_price_cents        integer     not null,                        -- JÁ inclui Σ deltas; line = unit × quantity
  item_name_snapshot      text        not null,
  made_to_order_snapshot  boolean     not null default false,
  custom_text             text                                         -- ESCAPADA: texto personalizado (snapshot, nullable)
);

comment on table public.papelaria_order_items is
  'Itens de um pedido papelaria (camada 8.15). quantity = TIRAGEM (escala o line total = unit × quantity). unit_price_cents (JÁ inclui Σ deltas) + item_name_snapshot + made_to_order_snapshot + custom_text são SNAPSHOTS. catalog_item_id on delete restrict → 409 catalog_item_in_use.';

create index idx_papelaria_order_items_order on public.papelaria_order_items (order_id);

alter table public.papelaria_order_items enable row level security;
alter table public.papelaria_order_items force  row level security;

create policy papelaria_order_items_select on public.papelaria_order_items
  for select to authenticated using (
    exists (select 1 from public.papelaria_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.papelaria_order_items to authenticated;
grant all on public.papelaria_order_items to service_role;

-- ---------------------------------------------------------------------------
-- papelaria_order_item_options — snapshot das opções escolhidas. Clone padaria.
-- ---------------------------------------------------------------------------
create table public.papelaria_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.papelaria_order_items(id) on delete cascade,
  catalog_option_id     uuid        references public.papelaria_catalog_item_options(id) on delete set null,
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null
);

comment on table public.papelaria_order_item_options is
  'Opções/personalização escolhidas de um item de pedido papelaria (camada 8.15). Snapshots de group/option/delta. catalog_option_id on delete set null preserva o histórico.';

create index idx_papelaria_oio_item on public.papelaria_order_item_options (order_item_id);

alter table public.papelaria_order_item_options enable row level security;
alter table public.papelaria_order_item_options force  row level security;

create policy papelaria_oio_select on public.papelaria_order_item_options
  for select to authenticated using (
    exists (select 1 from public.papelaria_order_items oi
            join public.papelaria_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.papelaria_order_item_options to authenticated;
grant all on public.papelaria_order_item_options to service_role;
