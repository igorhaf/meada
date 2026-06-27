-- =============================================================================
-- 54_lavanderia.sql
-- Meada — Camada 8.10 (SM: perfil Lavanderia / lavagem com coleta e entrega agendadas).
-- Tabelas exclusivas do perfil 'lavanderia': config (taxa+mínimo+turnaround default), catálogo de
-- SERVIÇOS (com turnaround_days por serviço), OPÇÕES de serviço (modifiers), pedidos, itens e opções
-- escolhidas por item.
--
-- CLONA o chassi do FLORICULTURA (8.5, que clonou o COMIDA) — catálogo + modifiers + carrinho-na-
-- conversa + tag de pedido + recálculo de total (descarta o da IA) + snapshot de preço/nome + taxa/
-- mínimo + Kanban + GATE DE ACEITE HUMANO + DATA agendada + PERÍODO (manhã/tarde). UMA escapada NOVA:
--
--   ESCAPADA — DUAS DATAS LIGADAS POR UM PRAZO DE TURNAROUND: o pedido tem COLETA e ENTREGA, acopladas.
--     Cada serviço tem turnaround_days (prazo de processamento). delivery_date = collect_date +
--     MAX(turnaround_days entre TODOS os itens). O backend VALIDA e MATERIALIZA: entrega < collect +
--     MAX(turnaround) → 422 turnaround_violation (devolve a primeira data possível). MAX (não soma):
--     processamento paralelo, vale o serviço mais lento. delivery_date MATERIALIZADA no INSERT (date +
--     interval não é IMMUTABLE — lição end_at reaplicada às DATAS). SEMPRE coleta+entrega
--     (delivery_address obrigatório; sem retirada de balcão).
--
-- Convenções (padrão das migrations 30-63):
--   - RLS enable+force; policies via app.company_id(); grants authenticated + service_role.
--   - orders/order_items/order_item_options: INSERT pelo BACKEND (service_role) via
--     PedidoLavanderiaConfirmHandler; tenant só SELECT/UPDATE (status no Kanban / gate de aceite).
--   - subtotal/total/unit_price MATERIALIZADOS no INSERT; delivery_date MATERIALIZADA. NÃO generated.
--   - SNAPSHOT de nome+preço em order_items + turnaround_snapshot; group/option/delta em
--     order_item_options. Alterar o catálogo NÃO altera pedidos passados.
--   - Categorias/period hardcoded (CHECK) em sync com LavanderiaServiceCategory/LavanderiaPeriod.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'lavanderia' (23º perfil contando generic). ESPELHA a CHECK mais
-- completa (61_concessionaria, 22 perfis) e APENDA 'lavanderia'. Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia'));

-- ---------------------------------------------------------------------------
-- lavanderia_config — taxa de entrega + pedido mínimo + turnaround default. 1:1 com company.
-- ---------------------------------------------------------------------------
create table public.lavanderia_config (
  company_id              uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents      integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents         integer     not null default 0 check (min_order_cents >= 0),
  turnaround_days_default integer     not null default 1 check (turnaround_days_default >= 0),
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now()
);

comment on table public.lavanderia_config is
  'Config do tenant lavanderia (camada 8.10): taxa de entrega + pedido mínimo + turnaround default (sugestão de prazo). 1:1 com company. Ausente → 0/0/1. Clone floricultura_config + turnaround default.';

alter table public.lavanderia_config enable row level security;
alter table public.lavanderia_config force  row level security;

create policy lav_config_select on public.lavanderia_config for select to authenticated using (company_id = app.company_id());
create policy lav_config_insert on public.lavanderia_config for insert to authenticated with check (company_id = app.company_id());
create policy lav_config_update on public.lavanderia_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.lavanderia_config to authenticated;
grant all on public.lavanderia_config to service_role;

-- ---------------------------------------------------------------------------
-- lavanderia_services — catálogo de SERVIÇOS (preço POR PEÇA + turnaround_days por serviço).
-- ---------------------------------------------------------------------------
create table public.lavanderia_services (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  name              text        not null check (length(trim(name)) between 1 and 120),
  description       text,
  price_cents       integer     not null check (price_cents >= 0),   -- preço por peça/unidade
  category          text        not null check (category in
                      ('lavar','lavar_passar','lavagem_seco','passar','edredom_pesados')),
  turnaround_days   integer     not null check (turnaround_days >= 0),   -- prazo do serviço
  care_instructions text,        -- texto livre informativo de cuidado
  available         boolean     not null default true,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.lavanderia_services is
  'Serviços do tenant lavanderia (camada 8.10). price_cents = preço POR PEÇA. turnaround_days = prazo de processamento do serviço (entra no MAX que calcula a entrega). Categorias hardcoded em sync com LavanderiaServiceCategory.';

create index idx_lav_service_company_cat on public.lavanderia_services (company_id, category) where available = true;

alter table public.lavanderia_services enable row level security;
alter table public.lavanderia_services force  row level security;

create policy lav_service_select on public.lavanderia_services for select to authenticated using (company_id = app.company_id());
create policy lav_service_insert on public.lavanderia_services for insert to authenticated with check (company_id = app.company_id());
create policy lav_service_update on public.lavanderia_services for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy lav_service_delete on public.lavanderia_services for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.lavanderia_services to authenticated;
grant all on public.lavanderia_services to service_role;

-- ---------------------------------------------------------------------------
-- lavanderia_service_options — modifiers (grupos: Acabamento, Cuidado). Clone floricultura options.
-- ---------------------------------------------------------------------------
create table public.lavanderia_service_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  service_id        uuid        not null references public.lavanderia_services(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.lavanderia_service_options is
  'Opções/modifiers (Acabamento, Cuidado) de um serviço de lavanderia (camada 8.10). Cada linha = UMA opção de UM grupo. price_delta soma ao preço base. on delete cascade.';

create index idx_lav_opt_service on public.lavanderia_service_options (service_id, sort_order) where available = true;
create index idx_lav_opt_company on public.lavanderia_service_options (company_id);

alter table public.lavanderia_service_options enable row level security;
alter table public.lavanderia_service_options force  row level security;

create policy lav_opt_select on public.lavanderia_service_options for select to authenticated using (company_id = app.company_id());
create policy lav_opt_insert on public.lavanderia_service_options for insert to authenticated with check (company_id = app.company_id());
create policy lav_opt_update on public.lavanderia_service_options for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy lav_opt_delete on public.lavanderia_service_options for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.lavanderia_service_options to authenticated;
grant all on public.lavanderia_service_options to service_role;

-- ---------------------------------------------------------------------------
-- lavanderia_orders — pedidos. DUAS DATAS (collect_date obrigatória + delivery_date MATERIALIZADA).
-- ---------------------------------------------------------------------------
create table public.lavanderia_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'aguardando' check (status in
                       ('aguardando','coletado','em_processo','pronto','saiu_entrega','entregue','recusado','cancelado')),
  subtotal_cents     integer     not null,
  delivery_fee_cents integer     not null default 0,
  total_cents        integer     not null,
  delivery_address   text        not null,   -- sempre coleta+entrega
  collect_date       date        not null,   -- coleta (>= hoje, validado no backend)
  delivery_date      date        not null,   -- MATERIALIZADA = collect + MAX(turnaround)
  period             text        not null check (period in ('manha','tarde')),  -- período da coleta
  notes              text,
  rejection_reason   text,
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.lavanderia_orders is
  'Pedidos do tenant lavanderia (camada 8.10). INSERT pelo backend (service_role). DUAS datas: collect_date (coleta, obrigatória) + delivery_date (MATERIALIZADA = collect + MAX(turnaround dos itens)). period é o da coleta. Gate de aceite humano (aguardando→coletado). Sempre coleta+entrega (delivery_address NOT NULL).';

create index idx_lav_orders_company_status on public.lavanderia_orders (company_id, status, created_at desc);
create index idx_lav_orders_conversation on public.lavanderia_orders (conversation_id);

alter table public.lavanderia_orders enable row level security;
alter table public.lavanderia_orders force  row level security;

create policy lav_orders_select on public.lavanderia_orders for select to authenticated using (company_id = app.company_id());
create policy lav_orders_update on public.lavanderia_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.lavanderia_orders to authenticated;
grant all on public.lavanderia_orders to service_role;

-- ---------------------------------------------------------------------------
-- lavanderia_order_items — itens com snapshot de nome+preço + turnaround_snapshot + qty.
-- ---------------------------------------------------------------------------
create table public.lavanderia_order_items (
  id                   uuid        primary key default gen_random_uuid(),
  order_id             uuid        not null references public.lavanderia_orders(id) on delete cascade,
  service_id           uuid        not null references public.lavanderia_services(id) on delete restrict,
  qty                  integer     not null check (qty > 0),
  unit_price_cents     integer     not null,   -- JÁ inclui Σ deltas das opções
  service_name_snapshot text       not null,
  turnaround_snapshot  integer     not null    -- prazo do serviço no momento do pedido
);

comment on table public.lavanderia_order_items is
  'Itens de um pedido lavanderia (camada 8.10). unit_price_cents (inclui Σ deltas) + service_name_snapshot + turnaround_snapshot são SNAPSHOTS. service_id on delete restrict → serviço com pedido não hard-deletável (409 service_in_use).';

create index idx_lav_order_items_order on public.lavanderia_order_items (order_id);

alter table public.lavanderia_order_items enable row level security;
alter table public.lavanderia_order_items force  row level security;

create policy lav_order_items_select on public.lavanderia_order_items for select to authenticated using (
  exists (select 1 from public.lavanderia_orders o where o.id = order_id and o.company_id = app.company_id()));

grant select on public.lavanderia_order_items to authenticated;
grant all on public.lavanderia_order_items to service_role;

-- ---------------------------------------------------------------------------
-- lavanderia_order_item_options — opções escolhidas por item (snapshots). Clone floricultura.
-- ---------------------------------------------------------------------------
create table public.lavanderia_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.lavanderia_order_items(id) on delete cascade,
  service_option_id     uuid        references public.lavanderia_service_options(id) on delete set null,
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null
);

comment on table public.lavanderia_order_item_options is
  'Opções escolhidas de um item de pedido lavanderia (camada 8.10). Snapshots do momento — apagar a opção do catálogo NÃO apaga o histórico (service_option_id on delete set null).';

create index idx_lav_oio_item on public.lavanderia_order_item_options (order_item_id);

alter table public.lavanderia_order_item_options enable row level security;
alter table public.lavanderia_order_item_options force  row level security;

create policy lav_oio_select on public.lavanderia_order_item_options for select to authenticated using (
  exists (select 1 from public.lavanderia_order_items oi
          join public.lavanderia_orders o on o.id = oi.order_id
          where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.lavanderia_order_item_options to authenticated;
grant all on public.lavanderia_order_item_options to service_role;
