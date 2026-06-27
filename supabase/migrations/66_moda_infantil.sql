-- =============================================================================
-- 66_moda_infantil.sql
-- Meada — Camada 8.22 (SM: perfil Moda Infantil / roupa de criança · varejo). CLONA o chassi
-- de VAREJO COM VARIANTES inaugurado pela Lingerie (65_lingerie.sql), com DUAS adaptações:
--   (1) O eixo de tamanho da variante é por FAIXA ETÁRIA (KidsSize: RN, 0-3m, 3-6m, ... 10a, 12a), e a
--       IA tem uma SUGESTÃO idade→tamanho (suggestForAgeMonths no enum, lado Java). size continua
--       hardcoded com parity; color é texto livre.
--   (2) RESTOCK ON CANCEL: cancelar/recusar um pedido DEVOLVE o estoque das variantes (UPDATE +qtd),
--       transacionalmente — o varejo de roupa tem troca/cancelamento frequente; o estoque volta pra
--       prateleira. (Lingerie não devolvia; aqui é a adaptação.)
--
-- Estrutura idêntica à lingerie: products + variants (size×color com estoque) + orders + order_items +
-- config. Decremento transacional na criação (UPDATE condicional stock_qty >= qtd → out_of_stock
-- aborta). Pedido nasce 'aguardando' (gate de aceite humano). Total materializado. Snapshots.
--
-- Convenções (padrão das migrations 30-65): RLS enable+force; policies via app.company_id(); grants
-- authenticated + service_role; orders/order_items INSERT só backend; categorias hardcoded em sync com
-- ModaInfantilCategory; size hardcoded em sync com KidsSize.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'moda_infantil' (28º contando generic). ESPELHA 65_lingerie (27) +
-- 'moda_infantil'. Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil'));

-- ---------------------------------------------------------------------------
-- moda_infantil_config — taxa de entrega + mínimo (1:1; clone lingerie_config).
-- ---------------------------------------------------------------------------
create table public.moda_infantil_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.moda_infantil_config is
  'Config do tenant moda_infantil (camada 8.22): taxa + mínimo. 1:1 com company. Clone lingerie_config.';

alter table public.moda_infantil_config enable row level security;
alter table public.moda_infantil_config force  row level security;

create policy mi_config_select on public.moda_infantil_config for select to authenticated using (company_id = app.company_id());
create policy mi_config_insert on public.moda_infantil_config for insert to authenticated with check (company_id = app.company_id());
create policy mi_config_update on public.moda_infantil_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.moda_infantil_config to authenticated;
grant all on public.moda_infantil_config to service_role;

-- ---------------------------------------------------------------------------
-- moda_infantil_products — catálogo (clone lingerie_products; categorias infantis).
-- ---------------------------------------------------------------------------
create table public.moda_infantil_products (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  description text,
  category    text        not null check (category in
                ('bebe','menino','menina','calcados','acessorios','pijamas','kits')),
  base_price_cents integer not null check (base_price_cents >= 0),
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.moda_infantil_products is
  'Produtos do tenant moda_infantil (camada 8.22). category hardcoded (sync ModaInfantilCategory). A grade de variantes (faixa-etária × cor) é moda_infantil_variants. delete em uso → 409 product_in_use.';

create index idx_mi_products_company_cat on public.moda_infantil_products (company_id, category) where available = true;

alter table public.moda_infantil_products enable row level security;
alter table public.moda_infantil_products force  row level security;

create policy mi_products_select on public.moda_infantil_products for select to authenticated using (company_id = app.company_id());
create policy mi_products_insert on public.moda_infantil_products for insert to authenticated with check (company_id = app.company_id());
create policy mi_products_update on public.moda_infantil_products for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy mi_products_delete on public.moda_infantil_products for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.moda_infantil_products to authenticated;
grant all on public.moda_infantil_products to service_role;

-- ---------------------------------------------------------------------------
-- moda_infantil_variants — grade FAIXA-ETÁRIA × cor com estoque (clone lingerie_variants).
-- ---------------------------------------------------------------------------
create table public.moda_infantil_variants (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  product_id       uuid        not null references public.moda_infantil_products(id) on delete cascade,
  size             text        not null check (length(trim(size)) between 1 and 20),  -- faixa etária (sync KidsSize)
  color            text        not null check (length(trim(color)) between 1 and 40),  -- texto livre
  sku              text,
  price_cents      integer,     -- nullable: herda base_price do produto
  stock_qty        integer     not null default 0 check (stock_qty >= 0),
  available        boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.moda_infantil_variants is
  'Variantes (faixa-etária×cor) do produto (camada 8.22). size é a FAIXA ETÁRIA (sync KidsSize, com suggestForAgeMonths no enum). Estoque DECREMENTADO transacionalmente na criação e DEVOLVIDO no cancelamento/recusa. UNIQUE(product_id, size, color).';

create unique index uniq_mi_variant_combo on public.moda_infantil_variants (product_id, size, color);
create index idx_mi_variants_product on public.moda_infantil_variants (product_id) where available = true;
create index idx_mi_variants_company on public.moda_infantil_variants (company_id);

alter table public.moda_infantil_variants enable row level security;
alter table public.moda_infantil_variants force  row level security;

create policy mi_variants_select on public.moda_infantil_variants for select to authenticated using (company_id = app.company_id());
create policy mi_variants_insert on public.moda_infantil_variants for insert to authenticated with check (company_id = app.company_id());
create policy mi_variants_update on public.moda_infantil_variants for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy mi_variants_delete on public.moda_infantil_variants for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.moda_infantil_variants to authenticated;
grant all on public.moda_infantil_variants to service_role;

-- ---------------------------------------------------------------------------
-- moda_infantil_orders — pedidos (clone lingerie_orders; INSERT só backend).
-- ---------------------------------------------------------------------------
create table public.moda_infantil_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'aguardando' check (status in
                       ('aguardando','separando','enviado','entregue','recusado','cancelado')),
  fulfillment        text        not null default 'entrega' check (fulfillment in ('entrega','retirada')),
  subtotal_cents     integer     not null,
  delivery_fee_cents integer     not null default 0,
  total_cents        integer     not null,
  delivery_address   text,
  notes              text,
  rejection_reason   text,
  stock_returned     boolean     not null default false,  -- RESTOCK ON CANCEL: marca se o estoque já voltou
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.moda_infantil_orders is
  'Pedidos do tenant moda_infantil (camada 8.22). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite). stock_returned: ao recusar/cancelar, o backend DEVOLVE o estoque das variantes (UPDATE +qtd) e marca true (idempotência — não devolve 2x). Clone lingerie_orders + restock.';

create index idx_mi_orders_company_status on public.moda_infantil_orders (company_id, status, created_at desc);
create index idx_mi_orders_conversation on public.moda_infantil_orders (conversation_id);

alter table public.moda_infantil_orders enable row level security;
alter table public.moda_infantil_orders force  row level security;

create policy mi_orders_select on public.moda_infantil_orders for select to authenticated using (company_id = app.company_id());
create policy mi_orders_update on public.moda_infantil_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.moda_infantil_orders to authenticated;
grant all on public.moda_infantil_orders to service_role;

-- ---------------------------------------------------------------------------
-- moda_infantil_order_items — itens (snapshot; clone lingerie_order_items).
-- ---------------------------------------------------------------------------
create table public.moda_infantil_order_items (
  id                    uuid        primary key default gen_random_uuid(),
  order_id              uuid        not null references public.moda_infantil_orders(id) on delete cascade,
  variant_id            uuid        not null references public.moda_infantil_variants(id) on delete restrict,
  qtd                   integer     not null check (qtd > 0),
  unit_price_cents      integer     not null,
  product_name_snapshot text        not null,
  size_snapshot         text        not null,
  color_snapshot        text        not null
);

comment on table public.moda_infantil_order_items is
  'Itens do pedido moda_infantil (camada 8.22) com SNAPSHOT de produto+variante+preço. variant_id on delete restrict (→ 409 variant_in_use). O restock no cancelamento usa variant_id + qtd destes itens.';

create index idx_mi_order_items_order on public.moda_infantil_order_items (order_id);

alter table public.moda_infantil_order_items enable row level security;
alter table public.moda_infantil_order_items force  row level security;

create policy mi_order_items_select on public.moda_infantil_order_items
  for select to authenticated using (
    exists (select 1 from public.moda_infantil_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.moda_infantil_order_items to authenticated;
grant all on public.moda_infantil_order_items to service_role;
