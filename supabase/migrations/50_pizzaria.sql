-- =============================================================================
-- 50_pizzaria.sql
-- Meada WhatsApp — Camada 8.6 (SM-T: perfil Pizzaria / delivery de pizza com meio-a-meio). DÉCIMO SÉTIMO perfil
-- vertical real (17º contando generic): pizzaria com pizzas inteiras e MEIO-A-MEIO. Tabelas exclusivas do perfil
-- 'pizzaria': config (taxa+mínimo), cardápio, OPÇÕES de item (modifiers), pedidos, itens de pedido e
-- OPÇÕES escolhidas por item de pedido.
--
-- Clona o chassi do COMIDA (camada 8.6) + a ESCAPADA meio-a-meio — cardápio + carrinho-na-conversa + tag de pedido + recálculo
-- de total (descarta o total da IA) + snapshot de preço/nome + taxa de entrega/pedido mínimo + Kanban
-- de status — herda o gate de aceite + modifiers do comida e adiciona a ESCAPADA meio-a-meio:
--
--   GATE DE ACEITE (herdado do comida) (ação humana, não da IA): o pedido nasce em
--     'aguardando'. A pizzaria, no painel, ACEITA (→'em_preparo') ou RECUSA (→'recusado', terminal,
--     com motivo opcional em rejection_reason). A IA NÃO aceita/recusa (espelho do "cancelamento
--     bloqueado pra IA" do dental e do callNext humano do barbearia). A IA já confirmou o RECEBIMENTO
--     na própria mensagem; por isso 'aguardando' NÃO notifica.
--   MODIFIERS (herdado do comida): Tamanho + Borda: um item do cardápio pode ter grupos de
--     opções (Tamanho: M +R$5, G +R$10; Adicionais: bacon +R$3), modelado como sub-entidade
--     pizzaria_menu_item_options (cada linha = UMA opção de UM grupo, com price_delta_cents). No pedido,
--     cada item ganha snapshot das opções escolhidas em TABELA-FILHA pizzaria_order_item_options (NÃO
--     JSONB). unit_price = base + Σ deltas, RECALCULADO no backend (descarta preço da IA). option_id
--     inválido aborta a criação.
--
-- Convenções (padrão das migrations 30-46):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated + service_role.
--   - pizzaria_orders/pizzaria_order_items/pizzaria_order_item_options: INSERT vem do BACKEND (service_role)
--     — o pedido é criado pela IA via PedidoPizzaConfirmHandler, não pelo SDK do tenant. O tenant só
--     SELECT/UPDATE (status pelo Kanban / gate de aceite).
--   - subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas (o
--     recálculo cruza linhas/tabelas — generated não serve; lição das migrations anteriores).
--   - SNAPSHOT de preço+nome em pizzaria_order_items e de group/option/delta em pizzaria_order_item_options:
--     alterar/excluir um item OU uma opção do cardápio NÃO altera pedidos passados.
--   - updated_at é mantido pelos repositórios (set updated_at = now() no UPDATE), padrão do projeto.
--   - Categorias hardcoded (CHECK) em sync com PizzariaCategory.java + pizzaria-categories.ts
--     (PizzariaCategoryParityTest garante a paridade Java↔TS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'pizzaria' (17º perfil real contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria'));

-- ---------------------------------------------------------------------------
-- pizzaria_config — taxa de entrega + pedido mínimo (1:1 com company). Clone comida_config.
-- ---------------------------------------------------------------------------
create table public.pizzaria_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.pizzaria_config is
  'Config do delivery pizzaria (camada 8.6): taxa de entrega + pedido mínimo. 1:1 com company. Ausente → taxa/mínimo = 0.';

alter table public.pizzaria_config enable row level security;
alter table public.pizzaria_config force  row level security;

create policy pizzaria_config_select on public.pizzaria_config
  for select to authenticated using (company_id = app.company_id());
create policy pizzaria_config_insert on public.pizzaria_config
  for insert to authenticated with check (company_id = app.company_id());
create policy pizzaria_config_update on public.pizzaria_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.pizzaria_config to authenticated;
grant all on public.pizzaria_config to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_menu_items — cardápio (só texto; foto bloqueada por SERVICE_ROLE_KEY). Clone comida_menu_items.
-- price_cents é o preço BASE (sem opções).
-- ---------------------------------------------------------------------------
create table public.pizzaria_menu_items (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 120),
  description text,
  price_cents integer     not null check (price_cents >= 0),
  category    text        not null check (category in
                ('pizzas_salgadas','pizzas_doces','bordas','bebidas','sobremesas','combos')),
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.pizzaria_menu_items is
  'Cardápio do tenant pizzaria (camada 8.6). price_cents = preço BASE (opções somam delta no pedido). Categorias hardcoded em sync com PizzariaCategory.java. Sem foto (bloqueador SERVICE_ROLE_KEY).';

create index idx_pizzaria_menu_company_cat on public.pizzaria_menu_items (company_id, category)
  where available = true;

alter table public.pizzaria_menu_items enable row level security;
alter table public.pizzaria_menu_items force  row level security;

create policy pizzaria_menu_select on public.pizzaria_menu_items
  for select to authenticated using (company_id = app.company_id());
create policy pizzaria_menu_insert on public.pizzaria_menu_items
  for insert to authenticated with check (company_id = app.company_id());
create policy pizzaria_menu_update on public.pizzaria_menu_items
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy pizzaria_menu_delete on public.pizzaria_menu_items
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pizzaria_menu_items to authenticated;
grant all on public.pizzaria_menu_items to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_menu_item_options — ESCAPADA 2: sub-entidade do item. Cada linha = UMA opção de UM grupo.
-- group_label agrupa no app ("Tamanho","Adicionais"); price_delta soma ao preço base no pedido.
-- ---------------------------------------------------------------------------
create table public.pizzaria_menu_item_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,   -- denormalizado p/ RLS direta
  menu_item_id      uuid        not null references public.pizzaria_menu_items(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),   -- "Tamanho","Adicionais"
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),  -- "Grande","Bacon"
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),             -- pode ser 0; NÃO negativo nesta fase
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.pizzaria_menu_item_options is
  'Opções/adicionais (modifiers) de um item do cardápio pizzaria (camada 8.6, ESCAPADA 2). Cada linha é UMA opção de UM grupo (group_label agrupa no app). price_delta_cents soma ao preço base no pedido. on delete cascade: opção é parte do item.';

create index idx_pizzaria_opt_item on public.pizzaria_menu_item_options (menu_item_id, sort_order)
  where available = true;
create index idx_pizzaria_opt_company on public.pizzaria_menu_item_options (company_id);

alter table public.pizzaria_menu_item_options enable row level security;
alter table public.pizzaria_menu_item_options force  row level security;

create policy pizzaria_opt_select on public.pizzaria_menu_item_options
  for select to authenticated using (company_id = app.company_id());
create policy pizzaria_opt_insert on public.pizzaria_menu_item_options
  for insert to authenticated with check (company_id = app.company_id());
create policy pizzaria_opt_update on public.pizzaria_menu_item_options
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy pizzaria_opt_delete on public.pizzaria_menu_item_options
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.pizzaria_menu_item_options to authenticated;
grant all on public.pizzaria_menu_item_options to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_orders — pedidos (INSERT pelo backend via IA; tenant gerencia status). Clone sushi_orders + gate.
-- ESCAPADA 1: nasce 'aguardando'; rejection_reason carrega o motivo da recusa (nullable).
-- ---------------------------------------------------------------------------
create table public.pizzaria_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'aguardando' check (status in
                       ('aguardando','em_preparo','saiu_entrega','entregue','recusado','cancelado')),
  subtotal_cents     integer     not null,
  delivery_fee_cents integer     not null default 0,
  total_cents        integer     not null,
  delivery_address   text        not null,
  notes              text,
  rejection_reason   text,                       -- ESCAPADA 1: motivo da recusa (nullable, defensivo)
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.pizzaria_orders is
  'Pedidos do tenant pizzaria (camada 8.6). INSERT pelo backend (service_role) via PedidoPizzaConfirmHandler; tenant só SELECT/UPDATE (status no Kanban). ESCAPADA 1: nasce aguardando; aceite/recusa é AÇÃO HUMANA no painel (não da IA). rejection_reason = motivo da recusa.';

create index idx_pizzaria_orders_company_status on public.pizzaria_orders (company_id, status, created_at desc);
create index idx_pizzaria_orders_conversation on public.pizzaria_orders (conversation_id);

alter table public.pizzaria_orders enable row level security;
alter table public.pizzaria_orders force  row level security;

-- Tenant SELECT/UPDATE do próprio; INSERT é só backend (sem policy authenticated de insert).
create policy pizzaria_orders_select on public.pizzaria_orders
  for select to authenticated using (company_id = app.company_id());
create policy pizzaria_orders_update on public.pizzaria_orders
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.pizzaria_orders to authenticated;
grant all on public.pizzaria_orders to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_order_items — itens do pedido com snapshot de preço+nome. Clone sushi_order_items.
-- unit_price_cents JÁ inclui a soma dos deltas das opções escolhidas.
-- ---------------------------------------------------------------------------
create table public.pizzaria_order_items (
  id                 uuid        primary key default gen_random_uuid(),
  order_id           uuid        not null references public.pizzaria_orders(id) on delete cascade,
  menu_item_id       uuid        not null references public.pizzaria_menu_items(id) on delete restrict,
  qtd                integer     not null check (qtd > 0),
  unit_price_cents   integer     not null,
  item_name_snapshot text        not null
);

comment on table public.pizzaria_order_items is
  'Itens de um pedido pizzaria (camada 8.6). unit_price_cents (JÁ inclui Σ deltas das opções) + item_name_snapshot são SNAPSHOTS do momento do pedido — alterar/excluir o item no cardápio não altera o histórico. menu_item_id on delete restrict → item com pedido não pode ser hard-deletado (409 menu_item_in_use).';

create index idx_pizzaria_order_items_order on public.pizzaria_order_items (order_id);

alter table public.pizzaria_order_items enable row level security;
alter table public.pizzaria_order_items force  row level security;

-- Tenant SELECT só dos itens de pedidos da própria empresa (via join no order).
create policy pizzaria_order_items_select on public.pizzaria_order_items
  for select to authenticated using (
    exists (select 1 from public.pizzaria_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.pizzaria_order_items to authenticated;
grant all on public.pizzaria_order_items to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_order_item_options — ESCAPADA 2: snapshot das opções escolhidas por item de pedido.
-- ---------------------------------------------------------------------------
create table public.pizzaria_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.pizzaria_order_items(id) on delete cascade,
  menu_option_id        uuid        references public.pizzaria_menu_item_options(id) on delete set null,  -- pode sumir depois
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null   -- snapshot do delta no momento do pedido
);

comment on table public.pizzaria_order_item_options is
  'Opções escolhidas de um item de pedido pizzaria (camada 8.6, ESCAPADA 2). group_label_snapshot/option_label_snapshot/price_delta_cents são SNAPSHOTS do momento — apagar a opção do cardápio NÃO apaga o histórico (menu_option_id on delete set null preserva os snapshots).';

create index idx_pizzaria_oio_item on public.pizzaria_order_item_options (order_item_id);

alter table public.pizzaria_order_item_options enable row level security;
alter table public.pizzaria_order_item_options force  row level security;

-- Tenant SELECT via exists encadeado (order_item → order → company = app.company_id()).
create policy pizzaria_oio_select on public.pizzaria_order_item_options
  for select to authenticated using (
    exists (select 1 from public.pizzaria_order_items oi
            join public.pizzaria_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.pizzaria_order_item_options to authenticated;
grant all on public.pizzaria_order_item_options to service_role;

-- ---------------------------------------------------------------------------
-- pizzaria_order_item_flavors — A ESCAPADA (camada 8.6): frações/sabores de um item-pizza.
-- Meio-a-meio: um MESMO item de pedido (uma pizza) é dividido em N FRAÇÕES (1 = inteira, 2 =
-- meio-a-meio; o modelo aceita N), cada fração um sabor do cardápio. O preço da pizza é calculado
-- no backend pela REGRA DO MAIOR VALOR: MAX(preço dos sabores no tamanho) + Σ deltas de modifiers.
-- flavor_name_snapshot/flavor_price_cents_snapshot são SNAPSHOTS do momento — alterar o preço do
-- sabor no cardápio NÃO altera pedidos passados (menu_item_id on delete set null preserva o snapshot).
-- ---------------------------------------------------------------------------
create table public.pizzaria_order_item_flavors (
  id                          uuid        primary key default gen_random_uuid(),
  order_item_id               uuid        not null references public.pizzaria_order_items(id) on delete cascade,
  menu_item_id                uuid        references public.pizzaria_menu_items(id) on delete set null,  -- pode sumir depois
  fraction_index              integer     not null check (fraction_index >= 1),                          -- 1..N (1=inteira; 1,2=meio-a-meio)
  flavor_name_snapshot        text        not null,
  flavor_price_cents_snapshot integer     not null   -- preço do sabor no tamanho escolhido, no momento do pedido
);

comment on table public.pizzaria_order_item_flavors is
  'Frações/sabores de uma pizza (camada 8.6, ESCAPADA meio-a-meio). Cada linha = UM sabor de UMA fração de um item de pedido. fraction_index 1..N (1=inteira, 1+2=meio-a-meio). O preço da pizza = MAX(flavor_price_cents_snapshot das frações) + Σ deltas de modifiers, recalculado no backend. Snapshots preservam o histórico (menu_item_id on delete set null).';

create index idx_pizzaria_flavors_item on public.pizzaria_order_item_flavors (order_item_id);

alter table public.pizzaria_order_item_flavors enable row level security;
alter table public.pizzaria_order_item_flavors force  row level security;

-- Tenant SELECT via exists encadeado (order_item → order → company = app.company_id()).
create policy pizzaria_flavors_select on public.pizzaria_order_item_flavors
  for select to authenticated using (
    exists (select 1 from public.pizzaria_order_items oi
            join public.pizzaria_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.pizzaria_order_item_flavors to authenticated;
grant all on public.pizzaria_order_item_flavors to service_role;
