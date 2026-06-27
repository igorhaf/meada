-- =============================================================================
-- 38_oficina.sql
-- Meada — Camada 7.9 (SM-J: perfil Oficina / OficinaBot). NONO perfil vertical real
-- e PRIMEIRO além da fila planejada de 8. Tabelas exclusivas do perfil 'oficina': mecânicos,
-- veículos (sub-entidade do cliente), ordens de serviço (order-based) e itens da OS.
--
-- EVOLUÇÃO ESTRUTURAL — combina dois padrões anteriores + uma escapada nova:
--   - order-based com itens + total materializado (espelho SUSHI: order + line items).
--   - sub-entidade de cliente (espelho PET: os_vehicles ~ pet_animals, contact é o dono).
--   - NOVIDADE: gate de aprovação em DUAS FASES. A IA (1) ABRE a OS a partir da queixa e,
--     num turno POSTERIOR (OS 'orcada'), (2) MUTA o estado pra 'aprovada'/'recusada' a partir
--     da resposta do cliente. Primeiro perfil em que a IA altera o estado de um artefato
--     existente via conversa, não só cria.
--
-- Convenções (padrão das migrations 30-37):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - service_orders + os_items: INSERT pelo BACKEND (service_role). Tenant SELECT/UPDATE
--     (e UPDATE de status/itens via SDK também é permitido p/ o painel — ver grants).
--   - total_cents (na OS) e line_total_cents (no item) MATERIALIZADOS no INSERT/UPDATE; NÃO
--     colunas geradas (recálculo cruza linhas — lição end_at das SMs anteriores).
--   - SNAPSHOTS na OS: customer_name/phone + vehicle_plate/model. Mudar cliente/veículo depois
--     NÃO altera OS passadas.
--   - Cliente NÃO é entidade própria (continua o contact). os_vehicles.contact_id é a verdade.
--   - LGPD: notes é administrativo.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'oficina' (9º perfil real; 10º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada','academia','pet','oficina'));

-- ---------------------------------------------------------------------------
-- os_mechanics — mecânicos/responsáveis (catálogo SIMPLES, sem agenda/conflito)
-- ---------------------------------------------------------------------------
create table public.os_mechanics (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "motor/suspensão", "elétrica/ar" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.os_mechanics is
  'Mecânicos do tenant oficina (camada 7.9). Catálogo simples SEM agenda — atribuição opcional na OS. active=false retira da disponibilidade.';

create index idx_os_mech_company_active on public.os_mechanics (company_id, active) where active = true;
create index idx_os_mech_company_name on public.os_mechanics (company_id, name);

alter table public.os_mechanics enable row level security;
alter table public.os_mechanics force  row level security;

create policy os_mech_select on public.os_mechanics for select to authenticated using (company_id = app.company_id());
create policy os_mech_insert on public.os_mechanics for insert to authenticated with check (company_id = app.company_id());
create policy os_mech_update on public.os_mechanics for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy os_mech_delete on public.os_mechanics for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.os_mechanics to authenticated;
grant all on public.os_mechanics to service_role;

-- ---------------------------------------------------------------------------
-- os_config — horário INFORMATIVO da oficina (1:1 com company; sem lógica de slot)
-- ---------------------------------------------------------------------------
create table public.os_config (
  company_id  uuid        primary key references public.companies(id) on delete cascade,
  opens_at    time        not null default '08:00',
  closes_at   time        not null default '18:00',
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.os_config is
  'Config do tenant oficina (camada 7.9): horário informativo. 1:1 com company. Ausente → defaults (08:00/18:00). Sem lógica de slot — a OS é order-based.';

alter table public.os_config enable row level security;
alter table public.os_config force  row level security;

create policy os_config_select on public.os_config for select to authenticated using (company_id = app.company_id());
create policy os_config_insert on public.os_config for insert to authenticated with check (company_id = app.company_id());
create policy os_config_update on public.os_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.os_config to authenticated;
grant all on public.os_config to service_role;

-- ---------------------------------------------------------------------------
-- os_vehicles — veículos (SUB-ENTIDADE do cliente/contact). placa UNIQUE por tenant.
-- ---------------------------------------------------------------------------
create table public.os_vehicles (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  contact_id  uuid        not null references public.contacts(id) on delete restrict,  -- CLIENTE
  plate       text        not null check (length(trim(plate)) between 1 and 10),
  brand       text,        -- "Honda", "Chevrolet" (texto livre)
  model       text,        -- "Civic", "Onix"
  year        integer      check (year between 1900 and 2100),
  color       text,
  mileage_km  integer      check (mileage_km >= 0),
  notes       text,
  active      boolean     not null default true,  -- false = arquivado (não perde OS)
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (company_id, plate)
);

comment on table public.os_vehicles is
  'Veículos do tenant oficina (camada 7.9). SUB-ENTIDADE do contact (cliente) — espelho pet_animals. placa UNIQUE por tenant. active=false arquiva sem perder histórico de OS.';

create index idx_os_veh_company_contact_active on public.os_vehicles (company_id, contact_id, active) where active = true;
create index idx_os_veh_company_plate on public.os_vehicles (company_id, plate);

alter table public.os_vehicles enable row level security;
alter table public.os_vehicles force  row level security;

create policy os_veh_select on public.os_vehicles for select to authenticated using (company_id = app.company_id());
create policy os_veh_insert on public.os_vehicles for insert to authenticated with check (company_id = app.company_id());
create policy os_veh_update on public.os_vehicles for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy os_veh_delete on public.os_vehicles for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.os_vehicles to authenticated;
grant all on public.os_vehicles to service_role;

-- ---------------------------------------------------------------------------
-- service_orders — ordens de serviço (order-based, total materializado, snapshots)
-- ---------------------------------------------------------------------------
create table public.service_orders (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,   -- cliente (atalho)
  vehicle_id        uuid        not null references public.os_vehicles(id) on delete restrict,
  mechanic_id       uuid        references public.os_mechanics(id) on delete set null,   -- opcional
  conversation_id   uuid        references public.conversations(id) on delete set null,
  customer_name     text        not null,   -- snapshot do contact
  customer_phone    text,                   -- snapshot opcional
  vehicle_plate     text        not null,   -- snapshot
  vehicle_model     text,                   -- snapshot
  complaint         text        not null,   -- queixa do cliente
  diagnosis         text,                   -- diagnóstico do mecânico (nullable)
  total_cents       integer     not null default 0,   -- MATERIALIZADO a cada mutação de item
  status            text        not null default 'aberta' check (status in
                      ('aberta','orcada','aprovada','recusada','em_execucao','concluida','entregue','cancelada')),
  expected_delivery date,                   -- previsão de entrega (campo-data livre, sem slot)
  notes             text,
  opened_at         timestamptz not null default now(),
  closed_at         timestamptz,            -- preenchido em concluida/entregue/cancelada
  status_updated_at timestamptz not null default now(),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.service_orders is
  'Ordens de serviço do tenant oficina (camada 7.9). INSERT pelo backend (service_role). total_cents materializado a cada mutação de item. Snapshots de cliente/veículo. Status com gate de aprovação em 2 fases.';

create index idx_so_company_status_opened on public.service_orders (company_id, status, opened_at desc);
create index idx_so_company_vehicle on public.service_orders (company_id, vehicle_id, opened_at desc);
create index idx_so_company_mechanic on public.service_orders (company_id, mechanic_id);
create index idx_so_company_contact on public.service_orders (company_id, contact_id, opened_at desc);

alter table public.service_orders enable row level security;
alter table public.service_orders force  row level security;

create policy so_select on public.service_orders for select to authenticated using (company_id = app.company_id());
create policy so_update on public.service_orders for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.service_orders to authenticated;
grant all on public.service_orders to service_role;

-- ---------------------------------------------------------------------------
-- os_items — itens da OS (peça/mão-de-obra). line_total materializado.
-- ---------------------------------------------------------------------------
create table public.os_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  service_order_id uuid        not null references public.service_orders(id) on delete cascade,
  kind             text        not null check (kind in ('peca','mao_de_obra')),
  description      text        not null check (length(trim(description)) between 1 and 200),
  quantity         integer     not null default 1 check (quantity > 0),
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  line_total_cents integer     not null check (line_total_cents >= 0),   -- = quantity * unit_price (materializado)
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.os_items is
  'Itens de uma ordem de serviço (camada 7.9). kind peca|mao_de_obra. line_total_cents materializado (quantity*unit_price); o total_cents da OS é recalculado na mesma transação.';

create index idx_os_items_order on public.os_items (service_order_id);
create index idx_os_items_company on public.os_items (company_id);

alter table public.os_items enable row level security;
alter table public.os_items force  row level security;

create policy os_items_select on public.os_items for select to authenticated using (company_id = app.company_id());
create policy os_items_update on public.os_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.os_items to authenticated;
grant all on public.os_items to service_role;
