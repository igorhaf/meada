-- =============================================================================
-- 56_otica.sql
-- Meada — Camada 8.12 (SM: perfil Ótica / loja de ótica). PRIMEIRO HÍBRIDO: combina DOIS chassis
-- já existentes no MESMO perfil — agenda-clínica-leve (clona DENTAL 33) + order-com-receita-e-prazo
-- (clona COMIDA/FLORICULTURA 47/49). Os dois fluxos coexistem HARMONICAMENTE; o perfil é único mas expõe
-- DOIS subdomínios funcionais com DUAS TAGS distintas.
--
--   FLUXO A — AGENDA DE EXAME (clona dental): optometrista (otica_professionals) + horário + CONFLITO POR
--   PROFISSIONAL (janela half-open, re-verificado na transação do INSERT) + end_at MATERIALIZADO no INSERT
--   (start_at + duration_minutes; NÃO gerada — timestamptz+interval não é IMMUTABLE) + duração SNAPSHOT do
--   config. Status OticaExamStatus (parity): agendado→confirmado→realizado; cancelado/falta.
--
--   FLUXO B — ENCOMENDA DE ÓCULOS (clona comida/floricultura): pedido com itens (armação + lentes via
--   modifiers de tipo de lente/tratamento) + total RECALCULADO no backend + SNAPSHOT + gate de aceite
--   humano (aguardando→em_montagem ou recusado) + PRAZO DE ENTREGA (lead_time da montagem) + DADOS DE
--   RECEITA como CAMPOS ADMINISTRATIVOS (esf/cil/eixo OD e OE + DP + prescription_pending). A IA registra,
--   NÃO interpreta o grau. Status OticaOrderStatus (parity): aguardando→em_montagem→pronto→retirado;
--   recusado/cancelado. Óculos pronto = RETIRADA na loja (sem taxa de entrega nesta SM).
--
-- Convenções (padrão 30-67): RLS enable+force; policies via app.company_id(); grants authenticated +
-- service_role; exam_appointments/orders/order_items/order_item_options INSERT só backend; total/unit_price/
-- end_at materializados; categorias/status hardcoded (parity).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'otica'. ESPELHA a CHECK mais completa (52_padaria, 30 perfis) + 'otica'.
-- Como 56 entra DEPOIS no SCRIPTS de teste, sua lista tem TODOS os 31.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos','lingerie','moda_infantil','las',
                        'padaria','otica'));

-- ---------------------------------------------------------------------------
-- otica_config — config FUNDIDA (exame: janela/duração; encomenda: lead + mínimo). 1:1 com company.
-- ---------------------------------------------------------------------------
create table public.otica_config (
  company_id            uuid        primary key references public.companies(id) on delete cascade,
  -- FLUXO A (exame):
  opens_at              time        not null default '09:00',
  closes_at             time        not null default '18:00',
  exam_duration_minutes integer     not null default 30 check (exam_duration_minutes between 15 and 240),
  -- FLUXO B (encomenda):
  min_order_cents       integer     not null default 0 check (min_order_cents >= 0),
  lead_time_days_default integer    not null default 7 check (lead_time_days_default >= 0),
  created_at            timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.otica_config is
  'Config FUNDIDA do tenant otica (camada 8.12): janela+duração do EXAME (fluxo A) + mínimo+lead da ENCOMENDA (fluxo B). 1:1 com company. Ausente → defaults. Clone dental_clinic_config + floricultura_config (lead/mínimo).';

alter table public.otica_config enable row level security;
alter table public.otica_config force  row level security;

create policy otica_config_select on public.otica_config for select to authenticated using (company_id = app.company_id());
create policy otica_config_insert on public.otica_config for insert to authenticated with check (company_id = app.company_id());
create policy otica_config_update on public.otica_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.otica_config to authenticated;
grant all on public.otica_config to service_role;

-- ---------------------------------------------------------------------------
-- otica_professionals — optometristas (FLUXO A: conflito de agenda POR profissional). Clone salon/dental.
-- ---------------------------------------------------------------------------
create table public.otica_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.otica_professionals is
  'Optometristas do tenant otica (camada 8.12, FLUXO A). Conflito de agenda do exame é POR professional_id. delete em uso → 409 professional_in_use.';

create index idx_otica_prof_company_active on public.otica_professionals (company_id, active) where active = true;

alter table public.otica_professionals enable row level security;
alter table public.otica_professionals force  row level security;

create policy otica_prof_select on public.otica_professionals for select to authenticated using (company_id = app.company_id());
create policy otica_prof_insert on public.otica_professionals for insert to authenticated with check (company_id = app.company_id());
create policy otica_prof_update on public.otica_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy otica_prof_delete on public.otica_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.otica_professionals to authenticated;
grant all on public.otica_professionals to service_role;

-- ---------------------------------------------------------------------------
-- otica_exam_appointments — exames (FLUXO A; clone dental_appointments com professional_id).
-- ---------------------------------------------------------------------------
create table public.otica_exam_appointments (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  professional_id    uuid        not null references public.otica_professionals(id) on delete restrict,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  contact_id         uuid        references public.contacts(id) on delete set null,
  customer_name      text        not null,   -- snapshot (cliente = contact)
  professional_name  text        not null,   -- snapshot
  start_at           timestamptz not null,
  duration_minutes   integer     not null,   -- snapshot do config
  end_at             timestamptz not null,   -- MATERIALIZADO = start_at + duration_minutes
  status             text        not null default 'agendado' check (status in
                       ('agendado','confirmado','realizado','cancelado','falta')),
  notes              text,        -- ADMINISTRATIVO
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.otica_exam_appointments is
  'Exames de vista do tenant otica (camada 8.12, FLUXO A). INSERT pelo backend. Conflito POR professional_id (half-open, re-verificado na transação). end_at MATERIALIZADO. Status feminino-neutro com parity. Texto de notificação SEM promessa clínica.';

create index idx_otica_exam_company_status on public.otica_exam_appointments (company_id, status, start_at);
create index idx_otica_exam_company_prof_active on public.otica_exam_appointments (company_id, professional_id, start_at)
  where status in ('agendado','confirmado');

alter table public.otica_exam_appointments enable row level security;
alter table public.otica_exam_appointments force  row level security;

create policy otica_exam_select on public.otica_exam_appointments for select to authenticated using (company_id = app.company_id());
create policy otica_exam_update on public.otica_exam_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.otica_exam_appointments to authenticated;
grant all on public.otica_exam_appointments to service_role;

-- ---------------------------------------------------------------------------
-- otica_catalog_items — catálogo (armações/lentes/acessórios; FLUXO B; + made_to_order + lead_time).
-- ---------------------------------------------------------------------------
create table public.otica_catalog_items (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  name          text        not null check (length(trim(name)) between 1 and 120),
  description   text,
  price_cents   integer     not null check (price_cents >= 0),   -- preço BASE
  category      text        not null check (category in ('armacoes','lentes','acessorios')),
  made_to_order boolean     not null default false,              -- armação/lente sob encomenda; acessório = false
  lead_time_days integer    check (lead_time_days is null or lead_time_days >= 0),  -- override do default da config
  available     boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.otica_catalog_items is
  'Catálogo do tenant otica (camada 8.12, FLUXO B). category hardcoded (OticaCategory: armacoes/lentes/acessorios). made_to_order = sob encomenda (exige ready_date que respeite lead_time). Sem foto.';

create index idx_otica_catalog_company_cat on public.otica_catalog_items (company_id, category) where available = true;

alter table public.otica_catalog_items enable row level security;
alter table public.otica_catalog_items force  row level security;

create policy otica_catalog_select on public.otica_catalog_items for select to authenticated using (company_id = app.company_id());
create policy otica_catalog_insert on public.otica_catalog_items for insert to authenticated with check (company_id = app.company_id());
create policy otica_catalog_update on public.otica_catalog_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy otica_catalog_delete on public.otica_catalog_items for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.otica_catalog_items to authenticated;
grant all on public.otica_catalog_items to service_role;

-- ---------------------------------------------------------------------------
-- otica_catalog_item_options — modifiers (Tipo de lente/Tratamento). Clone floricultura opts.
-- ---------------------------------------------------------------------------
create table public.otica_catalog_item_options (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  catalog_item_id   uuid        not null references public.otica_catalog_items(id) on delete cascade,
  group_label       text        not null check (length(trim(group_label)) between 1 and 60),   -- "Tipo de lente","Tratamento"
  option_label      text        not null check (length(trim(option_label)) between 1 and 80),  -- "Multifocal","Antirreflexo"
  price_delta_cents integer     not null default 0 check (price_delta_cents >= 0),
  available         boolean     not null default true,
  sort_order        integer     not null default 0,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.otica_catalog_item_options is
  'Modifiers de um item do catálogo otica (camada 8.12, FLUXO B). Tipo de lente (Monofocal/Multifocal/Transitions) e Tratamento (Antirreflexo/Blue). price_delta soma ao preço base. on delete cascade.';

create index idx_otica_opt_item on public.otica_catalog_item_options (catalog_item_id, sort_order) where available = true;
create index idx_otica_opt_company on public.otica_catalog_item_options (company_id);

alter table public.otica_catalog_item_options enable row level security;
alter table public.otica_catalog_item_options force  row level security;

create policy otica_opt_select on public.otica_catalog_item_options for select to authenticated using (company_id = app.company_id());
create policy otica_opt_insert on public.otica_catalog_item_options for insert to authenticated with check (company_id = app.company_id());
create policy otica_opt_update on public.otica_catalog_item_options for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy otica_opt_delete on public.otica_catalog_item_options for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.otica_catalog_item_options to authenticated;
grant all on public.otica_catalog_item_options to service_role;

-- ---------------------------------------------------------------------------
-- otica_orders — encomendas de óculos (FLUXO B; INSERT backend; gate de aceite + lead + RECEITA).
-- ---------------------------------------------------------------------------
create table public.otica_orders (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  conversation_id    uuid        not null references public.conversations(id) on delete restrict,
  contact_id         uuid        not null references public.contacts(id) on delete restrict,
  status             text        not null default 'aguardando' check (status in
                       ('aguardando','em_montagem','pronto','retirado','recusado','cancelado')),
  subtotal_cents     integer     not null,
  total_cents        integer     not null,   -- = subtotal (retirada na loja, sem taxa nesta SM)
  ready_date         date,                    -- prazo prometido = hoje + lead (validado no backend; null se só acessório)
  notes              text,
  rejection_reason   text,                    -- gate de aceite
  -- DADOS DE RECEITA (ADMINISTRATIVOS — a IA registra, NÃO interpreta o grau):
  rx_od_spherical    numeric(4,2),
  rx_od_cylindrical  numeric(4,2),
  rx_od_axis         integer     check (rx_od_axis is null or rx_od_axis between 0 and 180),
  rx_oe_spherical    numeric(4,2),
  rx_oe_cylindrical  numeric(4,2),
  rx_oe_axis         integer     check (rx_oe_axis is null or rx_oe_axis between 0 and 180),
  rx_pd              numeric(4,1),            -- distância pupilar (mm)
  prescription_pending boolean   not null default false,   -- true = cliente vai "trazer receita"
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.otica_orders is
  'Encomendas de óculos do tenant otica (camada 8.12, FLUXO B). INSERT pelo backend. Nasce ''aguardando'' (gate de aceite). ready_date = prazo de montagem (hoje + lead). Os campos rx_* são ADMINISTRATIVOS — a IA registra o grau que o cliente forneceu, NÃO calcula/valida/interpreta. prescription_pending = cliente vai trazer a receita; a loja confirma no painel antes de montar. Óculos pronto = RETIRADA (sem taxa nesta SM).';

create index idx_otica_orders_company_status on public.otica_orders (company_id, status, created_at desc);
create index idx_otica_orders_conversation on public.otica_orders (conversation_id);

alter table public.otica_orders enable row level security;
alter table public.otica_orders force  row level security;

create policy otica_orders_select on public.otica_orders for select to authenticated using (company_id = app.company_id());
create policy otica_orders_update on public.otica_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.otica_orders to authenticated;
grant all on public.otica_orders to service_role;

-- ---------------------------------------------------------------------------
-- otica_order_items — itens da encomenda (snapshot preço+nome + made_to_order). Clone floricultura.
-- ---------------------------------------------------------------------------
create table public.otica_order_items (
  id                     uuid        primary key default gen_random_uuid(),
  order_id               uuid        not null references public.otica_orders(id) on delete cascade,
  catalog_item_id        uuid        not null references public.otica_catalog_items(id) on delete restrict,
  qtd                    integer     not null check (qtd > 0),
  unit_price_cents       integer     not null,   -- JÁ inclui Σ deltas
  item_name_snapshot     text        not null,
  made_to_order_snapshot boolean     not null default false
);

comment on table public.otica_order_items is
  'Itens de uma encomenda otica (camada 8.12). unit_price_cents (JÁ inclui Σ deltas) + item_name_snapshot + made_to_order_snapshot são SNAPSHOTS. catalog_item_id on delete restrict → 409 catalog_item_in_use.';

create index idx_otica_order_items_order on public.otica_order_items (order_id);

alter table public.otica_order_items enable row level security;
alter table public.otica_order_items force  row level security;

create policy otica_order_items_select on public.otica_order_items
  for select to authenticated using (
    exists (select 1 from public.otica_orders o
            where o.id = order_id and o.company_id = app.company_id()));

grant select on public.otica_order_items to authenticated;
grant all on public.otica_order_items to service_role;

-- ---------------------------------------------------------------------------
-- otica_order_item_options — snapshot das opções (tipo de lente/tratamento) escolhidas. Clone floricultura.
-- ---------------------------------------------------------------------------
create table public.otica_order_item_options (
  id                    uuid        primary key default gen_random_uuid(),
  order_item_id         uuid        not null references public.otica_order_items(id) on delete cascade,
  catalog_option_id     uuid        references public.otica_catalog_item_options(id) on delete set null,
  group_label_snapshot  text        not null,
  option_label_snapshot text        not null,
  price_delta_cents     integer     not null
);

comment on table public.otica_order_item_options is
  'Opções (tipo de lente/tratamento) escolhidas de um item de encomenda otica (camada 8.12). Snapshots de group/option/delta. catalog_option_id on delete set null preserva o histórico.';

create index idx_otica_oio_item on public.otica_order_item_options (order_item_id);

alter table public.otica_order_item_options enable row level security;
alter table public.otica_order_item_options force  row level security;

create policy otica_oio_select on public.otica_order_item_options
  for select to authenticated using (
    exists (select 1 from public.otica_order_items oi
            join public.otica_orders o on o.id = oi.order_id
            where oi.id = order_item_id and o.company_id = app.company_id()));

grant select on public.otica_order_item_options to authenticated;
grant all on public.otica_order_item_options to service_role;
