-- =============================================================================
-- 61_concessionaria.sql
-- Meada WhatsApp — Camada 8.17 (SM: perfil Concessionária / loja de carros). 21º perfil vertical
-- real. HÍBRIDO TRIPLO: a IA faz as três coisas de uma concessionária — (1) MOSTRA o ESTOQUE
-- (catálogo de veículos com ciclo de estoque próprio), (2) AGENDA test-drive (agenda por vendedor),
-- (3) registra LEAD de compra (funil). O VEÍCULO é a entidade central: test-drive e lead o
-- REFERENCIAM (FK); o ciclo de estoque do veículo é independente dos dois.
--
-- Combina TRÊS moldes:
--   * ESTOQUE (catálogo com CICLO DE VIDA próprio): concessionaria_vehicles. Status de estoque
--     `disponivel → reservado → vendido` (vendido SAI da disponibilidade). É ITEM DE ESTOQUE com
--     identidade única e status próprio (≠ catálogo reabastecível de comida/floricultura, onde o item
--     é um TIPO). Pertence à LOJA (FK company), NÃO a um contact (≠ os_vehicles da oficina, que é do
--     cliente).
--   * TEST-DRIVE (agenda, clona dental_appointments): conflito POR VENDEDOR (não por veículo) — janela
--     half-open re-verificada DENTRO da transação; end_at MATERIALIZADO no INSERT (start_at +
--     duration_minutes; NÃO coluna gerada — timestamptz+interval não é IMMUTABLE). Referencia
--     vehicle_id + salesperson_id.
--   * LEAD (funil, clona service_orders/event_proposals SEM itens/total): interesse em UM veículo,
--     condição avista/financiado, status `novo → em_negociacao → fechado/perdido`. Preço = SNAPSHOT do
--     catálogo (a IA NUNCA fecha preço).
--
-- TRAVA cravada: a IA só MOSTRA estoque disponível, AGENDA test-drive e REGISTRA lead. NUNCA fecha
-- preço/desconto/financiamento, NUNCA aprova crédito, NUNCA muda o status de ESTOQUE do veículo nem o
-- status do LEAD (ações HUMANAS do painel). test-drive e lead SÓ de veículo 'disponivel' (→ 422
-- vehicle_not_available no backend).
--
-- Convenções (padrão das migrations 30-63; dental 33 + oficina 38 são as referências diretas):
--   * RLS enable+force; policies via app.company_id(); grants authenticated + service_role.
--   * vehicles/salespeople/config: tenant CRUD completo (gerido no painel).
--   * test_drives/leads: INSERT pelo BACKEND (service_role) — IA via handler OU POST manual; tenant só
--     SELECT/UPDATE (status na agenda/funil). Sem policy authenticated de insert.
--   * end_at do test-drive MATERIALIZADO no INSERT. SNAPSHOTS de veículo (marca/modelo/ano [+preço no
--     lead]) e cliente — alterar/vender o veículo depois NÃO altera test-drives/leads passados.
--   * Status hardcoded (CHECK) em sync com VehicleStatus/TestDriveStatus/LeadStatus (Java) + *.ts.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'concessionaria' (21º perfil real; 22º contando generic).
-- A lista ESPELHA a CHECK mais completa no disco (51_casamento, 21 perfis) e APENDA 'concessionaria'
-- — nenhum nicho some. (Esta migration entra por ÚLTIMO no SCRIPTS do AbstractIntegrationTest — a que
-- reescreve a CHECK por último precisa ter a lista completa.)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria'));

-- ---------------------------------------------------------------------------
-- concessionaria_config — janela/duração do test-drive + nome da loja (1:1 com company).
-- Clone dental_clinic_config + business_name (estilo event_config).
-- ---------------------------------------------------------------------------
create table public.concessionaria_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  business_name    text,        -- nome da loja (texto livre, nullable)
  duration_minutes integer     not null default 45 check (duration_minutes between 15 and 240),
  buffer_minutes   integer     not null default 0 check (buffer_minutes >= 0),
  opens_at         time        not null default '09:00',
  closes_at        time        not null default '18:00',
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.concessionaria_config is
  'Config do tenant concessionaria (camada 8.17): nome da loja + duração/janela do TEST-DRIVE (45min padrão). 1:1 com company. Ausente → defaults (45/0/09:00/18:00). Clone dental_clinic_config + business_name.';

alter table public.concessionaria_config enable row level security;
alter table public.concessionaria_config force  row level security;

create policy conc_config_select on public.concessionaria_config for select to authenticated using (company_id = app.company_id());
create policy conc_config_insert on public.concessionaria_config for insert to authenticated with check (company_id = app.company_id());
create policy conc_config_update on public.concessionaria_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.concessionaria_config to authenticated;
grant all on public.concessionaria_config to service_role;

-- ---------------------------------------------------------------------------
-- concessionaria_salespeople — vendedores (catálogo simples). Conflito de test-drive é POR salesperson.
-- ---------------------------------------------------------------------------
create table public.concessionaria_salespeople (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  phone       text,
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.concessionaria_salespeople is
  'Vendedores do tenant concessionaria (camada 8.17). Catálogo simples (~salon_professionals/os_mechanics). O conflito de agenda do test-drive é POR salesperson_id (paralelismo entre vendedores). active=false arquiva; delete em uso → 409 salesperson_in_use.';

create index idx_conc_sp_company_active on public.concessionaria_salespeople (company_id, active) where active = true;

alter table public.concessionaria_salespeople enable row level security;
alter table public.concessionaria_salespeople force  row level security;

create policy conc_sp_select on public.concessionaria_salespeople for select to authenticated using (company_id = app.company_id());
create policy conc_sp_insert on public.concessionaria_salespeople for insert to authenticated with check (company_id = app.company_id());
create policy conc_sp_update on public.concessionaria_salespeople for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy conc_sp_delete on public.concessionaria_salespeople for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.concessionaria_salespeople to authenticated;
grant all on public.concessionaria_salespeople to service_role;

-- ---------------------------------------------------------------------------
-- concessionaria_vehicles — ESTOQUE de veículos da loja (catálogo com CICLO DE ESTOQUE).
-- Status disponivel→reservado→vendido (vendido sai da disponibilidade). Foto é LINK (sem upload).
-- ---------------------------------------------------------------------------
create table public.concessionaria_vehicles (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  brand             text        not null check (length(trim(brand)) between 1 and 80),   -- "Toyota"
  model             text        not null check (length(trim(model)) between 1 and 120),  -- "Corolla XEi"
  model_year        integer     check (model_year between 1900 and 2100),
  mileage_km        integer     check (mileage_km >= 0),                                  -- 0 = zero-km
  price_cents       integer     not null check (price_cents >= 0),                        -- preço de referência (snapshot no lead)
  color             text,
  fuel              text,        -- "flex","diesel","eletrico" (texto livre, SEM enum)
  transmission      text,        -- "manual","automatico"
  plate             text,        -- placa/identificador (opcional, sem unique forte)
  photo_url         text,        -- LINK colado (sem upload — bloqueador SERVICE_ROLE_KEY)
  description       text,
  status            text        not null default 'disponivel' check (status in ('disponivel','reservado','vendido')),
  active            boolean     not null default true,   -- false = oculto do painel/vitrine sem perder histórico
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.concessionaria_vehicles is
  'ESTOQUE de veículos da loja (camada 8.17). É ITEM DE ESTOQUE com identidade única e CICLO próprio: status disponivel→reservado→vendido; vendido SAI da disponibilidade (não entra na vitrine, não aceita test-drive/lead). Pertence à company (NÃO a um contact — ≠ os_vehicles da oficina). Mudança de status é AÇÃO HUMANA do painel (a IA não toca). Foto é LINK (photo_url). price_cents é o preço de referência (snapshot no lead).';

create index idx_conc_veh_company_status_active on public.concessionaria_vehicles (company_id, status, active) where active = true;
create index idx_conc_veh_company_brand_model on public.concessionaria_vehicles (company_id, brand, model);

alter table public.concessionaria_vehicles enable row level security;
alter table public.concessionaria_vehicles force  row level security;

create policy conc_veh_select on public.concessionaria_vehicles for select to authenticated using (company_id = app.company_id());
create policy conc_veh_insert on public.concessionaria_vehicles for insert to authenticated with check (company_id = app.company_id());
create policy conc_veh_update on public.concessionaria_vehicles for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy conc_veh_delete on public.concessionaria_vehicles for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.concessionaria_vehicles to authenticated;
grant all on public.concessionaria_vehicles to service_role;

-- ---------------------------------------------------------------------------
-- concessionaria_test_drives — test-drives (clone dental_appointments + salesperson_id + vehicle_id).
-- Conflito POR salesperson_id. end_at MATERIALIZADO no INSERT. Snapshots de veículo.
-- ---------------------------------------------------------------------------
create table public.concessionaria_test_drives (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  vehicle_id        uuid        not null references public.concessionaria_vehicles(id) on delete restrict,
  salesperson_id    uuid        not null references public.concessionaria_salespeople(id) on delete restrict,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,
  customer_name     text,        -- snapshot do contact (nullable p/ POST manual)
  vehicle_brand     text        not null,   -- SNAPSHOT
  vehicle_model     text        not null,   -- SNAPSHOT
  vehicle_year      integer,                -- SNAPSHOT
  start_at          timestamptz not null,
  duration_minutes  integer     not null,   -- snapshot do config
  end_at            timestamptz not null,   -- materializado no INSERT (start_at + duration_minutes)
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','no_show')),
  notes             text,        -- ADMINISTRATIVO
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.concessionaria_test_drives is
  'Test-drives do tenant concessionaria (camada 8.17). INSERT pelo backend (service_role) — IA (TestDriveConfirmHandler) ou tenant (POST manual). Clone dental_appointments com vehicle_id + salesperson_id. Conflito POR salesperson_id (paralelismo entre vendedores). end_at MATERIALIZADO no INSERT. SNAPSHOTS de marca/modelo/ano do veículo. SÓ de veículo disponível (422 vehicle_not_available).';

create index idx_conc_td_company_status_start on public.concessionaria_test_drives (company_id, status, start_at);
-- Índice CRÍTICO da checagem de conflito: só status bloqueantes, POR salesperson.
create index idx_conc_td_company_sp_active on public.concessionaria_test_drives (company_id, salesperson_id, start_at)
  where status in ('agendado','confirmado');
create index idx_conc_td_vehicle on public.concessionaria_test_drives (vehicle_id, start_at desc);

alter table public.concessionaria_test_drives enable row level security;
alter table public.concessionaria_test_drives force  row level security;

create policy conc_td_select on public.concessionaria_test_drives for select to authenticated using (company_id = app.company_id());
create policy conc_td_update on public.concessionaria_test_drives for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.concessionaria_test_drives to authenticated;
grant all on public.concessionaria_test_drives to service_role;

-- ---------------------------------------------------------------------------
-- concessionaria_leads — interesse de compra de UM veículo (funil, SEM itens/total).
-- Clone do funil service_orders/event_proposals. Preço = SNAPSHOT do catálogo (a IA não fecha preço).
-- ---------------------------------------------------------------------------
create table public.concessionaria_leads (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete restrict,
  vehicle_id          uuid        not null references public.concessionaria_vehicles(id) on delete restrict,
  conversation_id     uuid        references public.conversations(id) on delete set null,
  contact_id          uuid        references public.contacts(id) on delete set null,
  customer_name       text,        -- snapshot (nullable p/ POST manual)
  customer_phone      text,        -- snapshot
  vehicle_brand       text        not null,   -- SNAPSHOT
  vehicle_model       text        not null,   -- SNAPSHOT
  vehicle_year        integer,                -- SNAPSHOT
  vehicle_price_cents integer     not null,   -- SNAPSHOT do preço do catálogo no momento do lead
  payment_condition   text        not null default 'avista' check (payment_condition in ('avista','financiado')),
  status              text        not null default 'novo' check (status in ('novo','em_negociacao','fechado','perdido')),
  salesperson_id      uuid        references public.concessionaria_salespeople(id) on delete set null,  -- atribuído no painel (opcional)
  lost_reason         text,        -- motivo de 'perdido' (defensivo)
  notes               text,
  created_at          timestamptz not null default now(),
  status_updated_at   timestamptz not null default now()
);

comment on table public.concessionaria_leads is
  'Leads de compra do tenant concessionaria (camada 8.17). INSERT pelo backend (service_role) — IA (LeadCarroConfirmHandler) ou tenant (POST manual). É INTERESSE em UM veículo, NÃO pedido (sem itens/total). Funil novo→em_negociacao→fechado/perdido. vehicle_price_cents é SNAPSHOT do catálogo (a IA NUNCA fecha preço). A IA cria ''novo'' e NÃO move; a equipe trabalha o funil. SÓ de veículo disponível (422 vehicle_not_available).';

create index idx_conc_lead_company_status_created on public.concessionaria_leads (company_id, status, created_at desc);
create index idx_conc_lead_vehicle on public.concessionaria_leads (company_id, vehicle_id);
create index idx_conc_lead_contact on public.concessionaria_leads (company_id, contact_id);

alter table public.concessionaria_leads enable row level security;
alter table public.concessionaria_leads force  row level security;

create policy conc_lead_select on public.concessionaria_leads for select to authenticated using (company_id = app.company_id());
create policy conc_lead_update on public.concessionaria_leads for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.concessionaria_leads to authenticated;
grant all on public.concessionaria_leads to service_role;
