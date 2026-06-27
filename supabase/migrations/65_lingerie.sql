-- =============================================================================
-- 65_lingerie.sql
-- Meada — Camada 8.21 (SM: perfil Lingerie / moda íntima · varejo). Tabelas exclusivas do
-- perfil 'lingerie': catálogo de produtos, VARIANTES (tamanho×cor) com ESTOQUE por variante, config,
-- pedidos, itens (snapshot), e gate de aceite humano.
--
-- ⭐ INAUGURA O CHASSI DE VAREJO COM VARIANTES (reusado por Moda Infantil 8.22 e Lãs 8.23):
--   - Um PRODUTO (lingerie_products) tem uma GRADE de VARIANTES (lingerie_variants), cada uma uma
--     combinação (size, color) com SEU PRÓPRIO preço e ESTOQUE (stock_qty). A variante é o SKU real.
--   - O pedido referencia a VARIANTE (não o produto). O estoque é DECREMENTADO TRANSACIONALMENTE na
--     criação do pedido: o UPDATE condicional `where stock_qty >= qtd` fecha a janela de corrida; se a
--     linha não decrementa, o backend lança out_of_stock → 409 e ABORTA o pedido inteiro.
--   - SNAPSHOT no item: product_name + variant_label (size/color) + unit_price_cents do momento.
--
-- CLONA o resto do chassi do COMIDA (camada 8.4): pedido nasce 'aguardando' (gate de aceite humano:
--   a loja ACEITA → 'separando' ou RECUSA → 'recusado' c/ motivo; a IA não aceita/recusa), Kanban,
--   recálculo de total no backend (descarta o da IA), taxa de entrega + mínimo, retirada×entrega.
--
-- Convenções (padrão das migrations 30-64):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - lingerie_orders / lingerie_order_items: INSERT pelo BACKEND (service_role). Tenant SELECT/UPDATE.
--   - total/unit_price MATERIALIZADOS (não generated). Categorias hardcoded em sync com LingerieCategory.
--   - size hardcoded em sync com LingerieSize (parity); color é TEXTO LIVRE.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'lingerie' (27º contando generic). ESPELHA 64_cursos (26) +
-- 'lingerie'. Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie'));

-- ---------------------------------------------------------------------------
-- lingerie_config — taxa de entrega + mínimo (1:1 com company; clone comida_config).
-- ---------------------------------------------------------------------------
create table public.lingerie_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.lingerie_config is
  'Config do tenant lingerie (camada 8.21): taxa de entrega + pedido mínimo. 1:1 com company. Ausente → ZERO. Clone comida_config.';

alter table public.lingerie_config enable row level security;
alter table public.lingerie_config force  row level security;

create policy lingerie_config_select on public.lingerie_config for select to authenticated using (company_id = app.company_id());
create policy lingerie_config_insert on public.lingerie_config for insert to authenticated with check (company_id = app.company_id());
create policy lingerie_config_update on public.lingerie_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.lingerie_config to authenticated;
grant all on public.lingerie_config to service_role;

-- ---------------------------------------------------------------------------
-- lingerie_products — catálogo de produtos (preço BASE; a variante pode sobrepor).
-- ---------------------------------------------------------------------------
create table public.lingerie_products (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  description text,
  category    text        not null check (category in
                ('sutias','calcinhas','conjuntos','pijamas','modeladores','meias','acessorios')),
  base_price_cents integer not null check (base_price_cents >= 0),  -- preço base; variante pode sobrepor
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.lingerie_products is
  'Produtos do tenant lingerie (camada 8.21). category hardcoded (sync LingerieCategory). base_price_cents é o preço quando a variante não sobrepõe. A GRADE de variantes (tamanho×cor) é lingerie_variants. delete em uso → 409 product_in_use.';

create index idx_lingerie_products_company_cat on public.lingerie_products (company_id, category) where available = true;

alter table public.lingerie_products enable row level security;
alter table public.lingerie_products force  row level security;

create policy lingerie_products_select on public.lingerie_products for select to authenticated using (company_id = app.company_id());
create policy lingerie_products_insert on public.lingerie_products for insert to authenticated with check (company_id = app.company_id());
create policy lingerie_products_update on public.lingerie_products for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy lingerie_products_delete on public.lingerie_products for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.lingerie_products to authenticated;
grant all on public.lingerie_products to service_role;

-- ---------------------------------------------------------------------------
-- lingerie_variants — ⭐ GRADE de variantes (size × color) com ESTOQUE por variante. O SKU real.
-- ---------------------------------------------------------------------------
create table public.lingerie_variants (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,  -- denorm p/ RLS direta
  product_id       uuid        not null references public.lingerie_products(id) on delete cascade,
  size             text        not null check (length(trim(size)) between 1 and 20),  -- sync LingerieSize (parity)
  color            text        not null check (length(trim(color)) between 1 and 40),  -- texto livre
  sku              text,        -- código opcional da loja
  price_cents      integer,     -- nullable: se null, usa o base_price do produto
  stock_qty        integer     not null default 0 check (stock_qty >= 0),  -- ESTOQUE da variante
  available        boolean     not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.lingerie_variants is
  'Variantes (tamanho×cor) do produto (camada 8.21, ⭐ chassi de varejo). O SKU real: cada variante tem preço (ou herda do produto) e ESTOQUE. O pedido referencia a variante; o estoque é DECREMENTADO transacionalmente na criação (UPDATE condicional stock_qty >= qtd → out_of_stock se não decrementa). UNIQUE(product_id, size, color).';

create unique index uniq_lingerie_variant_combo on public.lingerie_variants (product_id, size, color);
create index idx_lingerie_variants_product on public.lingerie_variants (product_id) where available = true;
create index idx_lingerie_variants_company on public.lingerie_variants (company_id);

alter table public.lingerie_variants enable row level security;
alter table public.lingerie_variants force  row level security;

create policy lingerie_variants_select on public.lingerie_variants for select to authenticated using (company_id = app.company_id());
create policy lingerie_variants_insert on public.lingerie_variants for insert to authenticated with check (company_id = app.company_id());
create policy lingerie_variants_update on public.lingerie_variants for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy lingerie_variants_delete on public.lingerie_variants for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.lingerie_variants to authenticated;
grant all on public.lingerie_variants to service_role;

-- ---------------------------------------------------------------------------
-- lingerie_orders — pedidos (INSERT só backend; clone comida_orders + gate de aceite).
-- ---------------------------------------------------------------------------
create table public.lingerie_orders (
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
  delivery_address   text,        -- null em retirada
  notes              text,
  rejection_reason   text,        -- motivo da recusa (gate de aceite)
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.lingerie_orders is
  'Pedidos do tenant lingerie (camada 8.21). INSERT pelo backend (service_role). Nasce ''aguardando'' (gate de aceite humano: a loja aceita→separando ou recusa→recusado). Total MATERIALIZADO. fulfillment entrega(c/ endereço)/retirada. Clone comida_orders.';

create index idx_lingerie_orders_company_status on public.lingerie_orders (company_id, status, created_at desc);
create index idx_lingerie_orders_conversation on public.lingerie_orders (conversation_id);

alter table public.lingerie_orders enable row level security;
alter table public.lingerie_orders force  row level security;

create policy lingerie_orders_select on public.lingerie_orders for select to authenticated using (company_id = app.company_id());
create policy lingerie_orders_update on public.lingerie_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.lingerie_orders to authenticated;
grant all on public.lingerie_orders to service_role;

-- ---------------------------------------------------------------------------
-- lingerie_order_items — itens do pedido (snapshot de produto+variante+preço; clone comida_order_items).
-- ---------------------------------------------------------------------------
create table public.lingerie_order_items (
  id                    uuid        primary key default gen_random_uuid(),
  order_id              uuid        not null references public.lingerie_orders(id) on delete cascade,
  variant_id            uuid        not null references public.lingerie_variants(id) on delete restrict,
  qtd                   integer     not null check (qtd > 0),
  unit_price_cents      integer     not null,   -- snapshot (preço da variante ou base do produto)
  product_name_snapshot text        not null,
  size_snapshot         text        not null,
  color_snapshot        text        not null
);

comment on table public.lingerie_order_items is
  'Itens do pedido lingerie (camada 8.21) com SNAPSHOT de produto+variante+preço. Alterar/excluir produto/variante depois NÃO altera pedidos passados. variant_id on delete restrict (não dá pra apagar variante com pedido → 409 variant_in_use).';

create index idx_lingerie_order_items_order on public.lingerie_order_items (order_id);

alter table public.lingerie_order_items enable row level security;
alter table public.lingerie_order_items force  row level security;

create policy lingerie_order_items_select on public.lingerie_order_items
  for select to authenticated using (
    exists (select 1 from public.lingerie_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.lingerie_order_items to authenticated;
grant all on public.lingerie_order_items to service_role;
