-- =============================================================================
-- 53_adega.sql
-- Meada — Camada 8.9 (SM: perfil Adega / delivery de bebidas). 18º perfil vertical real
-- (19º contando generic): adega / delivery de vinhos, espumantes, cervejas, destilados, sem-álcool e
-- acessórios. Tabelas exclusivas do perfil 'adega': config (taxa+mínimo), cardápio, OPÇÕES de item
-- (modifiers: Volume/Temperatura), pedidos, itens de pedido e OPÇÕES escolhidas por item de pedido.
--
-- Clona o chassi do COMIDA (camada 8.4) — cardápio + carrinho-na-conversa + tag de pedido + recálculo
-- de total (descarta o total da IA) + snapshot de preço/nome + opções/adicionais (modifiers) + taxa de
-- entrega/pedido mínimo + Kanban de status + GATE DE ACEITE HUMANO — e adiciona UMA ESCAPADA NOVA que
-- nem o comida nem o pizzaria têm:
--
--   ESCAPADA — TRAVA DE FAIXA ETÁRIA (+18, VENDA DE ÁLCOOL): a venda de bebida alcoólica exige
--     confirmação de MAIORIDADE. O pedido carrega adega_orders.age_confirmed (boolean NOT NULL, SEM
--     default). A IA confirma a maioridade na conversa e só então emite a tag com age_confirmed=true;
--     o backend RECUSA criar pedido sem ele (422 age_not_confirmed) — NÃO há pedido "menor de idade" no
--     banco. O flag é PERSISTIDO pra auditoria/compliance e fica visível no painel. A trava vale mesmo
--     pra carrinho 100% sem-álcool nesta SM (simplicidade; refinar é fase futura). É a regra que
--     justifica adega ser perfil próprio, e não um preset do comida.
--
-- Herda do comida o GATE DE ACEITE (ação humana, não da IA): o pedido nasce 'aguardando'. A loja, no
-- painel, ACEITA (→'em_preparo') ou RECUSA (→'recusado', terminal, com motivo em rejection_reason). A
-- IA NÃO aceita/recusa; já confirmou o RECEBIMENTO na mensagem, por isso 'aguardando' NÃO notifica. E
-- os modifiers (Volume: 375ml/750ml/1L; Temperatura: natural/gelado +R$), modelados como sub-entidade
-- adega_menu_item_options (cada linha = UMA opção de UM grupo, com price_delta_cents); no pedido, cada
-- item ganha snapshot das opções em TABELA-FILHA adega_order_item_options. unit_price = base + Σ deltas,
-- RECALCULADO no backend (descarta preço da IA). option_id inválido aborta a criação. SEM regra do
-- maior valor (isso é do pizzaria — adega não tem meio-a-meio).
--
-- Convenções (padrão das migrations 30-50):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated + service_role.
--   - adega_orders/adega_order_items/adega_order_item_options: INSERT vem do BACKEND (service_role) —
--     o pedido é criado pela IA via PedidoAdegaConfirmHandler, não pelo SDK do tenant. O tenant só
--     SELECT/UPDATE (status pelo Kanban / gate de aceite).
--   - subtotal_cents/total_cents/unit_price_cents MATERIALIZADOS no INSERT; NÃO colunas geradas.
--   - SNAPSHOT de preço+nome em adega_order_items e de group/option/delta em adega_order_item_options:
--     alterar/excluir um item OU uma opção do cardápio NÃO altera pedidos passados.
--   - Categorias hardcoded (CHECK) em sync com AdegaCategory.java + adega-categories.ts
--     (AdegaCategoryParityTest garante a paridade Java↔TS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'adega' (18º perfil real; 19º contando generic).
-- A lista ESPELHA a CHECK mais recente (50_pizzaria, 17 perfis) e APENDA 'adega' — nenhum nicho some.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega'));

-- ---------------------------------------------------------------------------
-- adega_config — taxa de entrega + pedido mínimo (1:1 com company). Clone comida_config.
-- ---------------------------------------------------------------------------
create table public.adega_config (
  company_id         uuid        primary key references public.companies(id) on delete cascade,
  delivery_fee_cents integer     not null default 0 check (delivery_fee_cents >= 0),
  min_order_cents    integer     not null default 0 check (min_order_cents >= 0),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

comment on table public.adega_config is
  'Config do delivery adega (camada 8.9): taxa de entrega + pedido mínimo. 1:1 com company. Ausente → taxa/mínimo = 0.';

alter table public.adega_config enable row level security;
alter table public.adega_config force  row level security;

create policy adega_config_select on public.adega_config
  for select to authenticated using (company_id = app.company_id());
create policy adega_config_insert on public.adega_config
  for insert to authenticated with check (company_id = app.company_id());
create policy adega_config_update on public.adega_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.adega_config to authenticated;
grant all on public.adega_config to service_role;

-- ---------------------------------------------------------------------------
-- adega_menu_items — cardápio (só texto; foto bloqueada por SERVICE_ROLE_KEY). Clone comida_menu_items.
-- price_cents é o preço BASE (sem opções). Volume/safra/teor vão na description nesta SM.
-- ---------------------------------------------------------------------------
create table public.adega_menu_items (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 120),
  description text,
  price_cents integer     not null check (price_cents >= 0),
  category    text        not null check (category in
                ('vinhos','espumantes','cervejas','destilados','sem_alcool','acessorios')),
  available   boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.adega_menu_items is
  'Cardápio do tenant adega (camada 8.9). price_cents = preço BASE (opções somam delta no pedido). Categorias hardcoded em sync com AdegaCategory.java. Volume/safra/teor na description. Sem foto (bloqueador SERVICE_ROLE_KEY).';

create index idx_adega_menu_company_cat on public.adega_menu_items (company_id, category)
  where available = true;

alter table public.adega_menu_items enable row level security;
alter table public.adega_menu_items force  row level security;

create policy adega_menu_select on public.adega_menu_items
  for select to authenticated using (company_id = app.company_id());
create policy adega_menu_insert on public.adega_menu_items
  for insert to authenticated with check (company_id = app.company_id());
create policy adega_menu_update on public.adega_menu_items
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy adega_menu_delete on public.adega_menu_items
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.adega_menu_items to authenticated;
grant all on public.adega_menu_items to service_role;

-- ---------------------------------------------------------------------------
-- adega_menu_item_options — modifiers (Volume, Temperatura). Cada linha = UMA opção de UM grupo.
-- group_label agrupa no app ("Volume","Temperatura"); price_delta soma ao preço base no pedido.
-- Clone comida_menu_item_options.
-- ---------------------------------------------------------------------------
create table public.adega_menu_item_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,   -- denormalizado p/ RLS direta
  menu_item_id      uuid        not null references public.adega_menu_items(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),   -- "Volume","Temperatura"
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),  -- "1L","Gelado"
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),             -- pode ser 0; NÃO negativo nesta fase
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.adega_menu_item_options is
  'Opções/modifiers (Volume, Temperatura) de um item do cardápio adega (camada 8.9). Cada linha é UMA opção de UM grupo (group_label agrupa no app). price_delta_cents soma ao preço base no pedido. on delete cascade: opção é parte do item.';

create index idx_adega_opt_item on public.adega_menu_item_options (menu_item_id, sort_order)
  where available = true;
create index idx_adega_opt_company on public.adega_menu_item_options (company_id);

alter table public.adega_menu_item_options enable row level security;
alter table public.adega_menu_item_options force  row level security;

create policy adega_opt_select on public.adega_menu_item_options
  for select to authenticated using (company_id = app.company_id());
create policy adega_opt_insert on public.adega_menu_item_options
  for insert to authenticated with check (company_id = app.company_id());
create policy adega_opt_update on public.adega_menu_item_options
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy adega_opt_delete on public.adega_menu_item_options
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.adega_menu_item_options to authenticated;
grant all on public.adega_menu_item_options to service_role;

-- ---------------------------------------------------------------------------
-- adega_orders — pedidos (INSERT pelo backend via IA; tenant gerencia status). Clone comida_orders + gate
-- + a ESCAPADA +18. nasce 'aguardando'; rejection_reason carrega o motivo da recusa (nullable).
-- age_confirmed (boolean NOT NULL, SEM default): venda de álcool exige maioridade — o backend só insere
-- com true; pedido sem age_confirmed NÃO é criado (422 age_not_confirmed). Persistido pra compliance.
-- ---------------------------------------------------------------------------
create table public.adega_orders (
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
  rejection_reason   text,                       -- gate de aceite: motivo da recusa (nullable, defensivo)
  age_confirmed      boolean     not null,       -- ESCAPADA +18: maioridade declarada; sem default — só true entra
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.adega_orders is
  'Pedidos do tenant adega (camada 8.9). INSERT pelo backend (service_role) via PedidoAdegaConfirmHandler; tenant só SELECT/UPDATE (status no Kanban). Gate de aceite: nasce aguardando; aceite/recusa é AÇÃO HUMANA no painel. ESCAPADA +18: venda de álcool exige maioridade confirmada — age_confirmed (NOT NULL) é persistido pra compliance; pedido sem age_confirmed NÃO é criado (422 age_not_confirmed no backend).';

create index idx_adega_orders_company_status on public.adega_orders (company_id, status, created_at desc);
create index idx_adega_orders_conversation on public.adega_orders (conversation_id);

alter table public.adega_orders enable row level security;
alter table public.adega_orders force  row level security;

-- Tenant SELECT/UPDATE do próprio; INSERT é só backend (sem policy authenticated de insert).
create policy adega_orders_select on public.adega_orders
  for select to authenticated using (company_id = app.company_id());
create policy adega_orders_update on public.adega_orders
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.adega_orders to authenticated;
grant all on public.adega_orders to service_role;

-- ---------------------------------------------------------------------------
-- adega_order_items — itens do pedido com snapshot de preço+nome. Clone comida_order_items.
-- unit_price_cents JÁ inclui a soma dos deltas das opções escolhidas.
-- ---------------------------------------------------------------------------
create table public.adega_order_items (
  id                 uuid        primary key default gen_random_uuid(),
  order_id           uuid        not null references public.adega_orders(id) on delete cascade,
  menu_item_id       uuid        not null references public.adega_menu_items(id) on delete restrict,
  qtd                integer     not null check (qtd > 0),
  unit_price_cents   integer     not null,
  item_name_snapshot text        not null
);

comment on table public.adega_order_items is
  'Itens de um pedido adega (camada 8.9). unit_price_cents (JÁ inclui Σ deltas das opções) + item_name_snapshot são SNAPSHOTS do momento do pedido — alterar/excluir o item no cardápio não altera o histórico. menu_item_id on delete restrict → item com pedido não pode ser hard-deletado (409 menu_item_in_use).';

create index idx_adega_order_items_order on public.adega_order_items (order_id);

alter table public.adega_order_items enable row level security;
alter table public.adega_order_items force  row level security;

-- Tenant SELECT só dos itens de pedidos da própria empresa (via join no order).
create policy adega_order_items_select on public.adega_order_items
  for select to authenticated using (
    exists (select 1 from public.adega_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.adega_order_items to authenticated;
grant all on public.adega_order_items to service_role;

-- ---------------------------------------------------------------------------
-- adega_order_item_options — snapshot das opções/modifiers escolhidos por item de pedido.
-- Clone comida_order_item_options.
-- ---------------------------------------------------------------------------
create table public.adega_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.adega_order_items(id) on delete cascade,
  menu_option_id        uuid        references public.adega_menu_item_options(id) on delete set null,  -- pode sumir depois
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null   -- snapshot do delta no momento do pedido
);

comment on table public.adega_order_item_options is
  'Opções/modifiers escolhidos de um item de pedido adega (camada 8.9). group_label_snapshot/option_label_snapshot/price_delta_cents são SNAPSHOTS do momento — apagar a opção do cardápio NÃO apaga o histórico (menu_option_id on delete set null preserva os snapshots).';

create index idx_adega_oio_item on public.adega_order_item_options (order_item_id);

alter table public.adega_order_item_options enable row level security;
alter table public.adega_order_item_options force  row level security;

-- Tenant SELECT via exists encadeado (order_item → order → company = app.company_id()).
create policy adega_oio_select on public.adega_order_item_options
  for select to authenticated using (
    exists (select 1 from public.adega_order_items oi
            join public.adega_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.adega_order_item_options to authenticated;
grant all on public.adega_order_item_options to service_role;
