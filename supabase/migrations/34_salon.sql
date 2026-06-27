-- =============================================================================
-- 34_salon.sql
-- Meada — Camada 7.5 (SM-F: perfil Salão / SalãoBot). Quinto perfil vertical real
-- (sushi 7.1, legal 7.2, restaurant 7.3, dental 7.4, salon 7.5). Tabelas exclusivas do
-- perfil 'salon': profissionais, serviços (offerings), config e agendamentos.
--
-- EVOLUÇÃO do padrão (vs dental/restaurant): o conflito de horário é por PROFISSIONAL
-- (salon_appointments.professional_id), não por company — 2 clientes no mesmo horário com
-- profissionais DIFERENTES é OK (paralelismo). Índice idx_salon_appts_prof_active reflete isso.
--
-- Convenções (padrão das migrations 30-33):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - salon_appointments: INSERT vem do BACKEND (service_role) — IA (AgendamentoConfirmHandler)
--     ou tenant (POST manual). Tenant só SELECT/UPDATE (status na agenda).
--   - end_at é MATERIALIZADO no INSERT (start_at + duration_minutes). NÃO é coluna gerada
--     (timestamptz + interval não é IMMUTABLE — lição da SM-D/E).
--   - SNAPSHOTS em salon_appointments: professional_name + service_name + price_cents +
--     duration_minutes congelados no momento — alterar serviço/profissional depois NÃO altera
--     agendamentos passados.
--   - Cliente NÃO é entidade própria (decisão cravada): salão tem alta rotatividade; o histórico
--     vem do contact + appointments. guest_name/guest_phone são snapshots do contato.
--   - LGPD: notes é administrativo, sem dado sensível.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'salon' (5º perfil real; 6º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon'));

-- ---------------------------------------------------------------------------
-- salon_professionals — profissionais do salão (catálogo)
-- ---------------------------------------------------------------------------
create table public.salon_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "Cabeleireira", "Manicure", "Esteticista" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.salon_professionals is
  'Profissionais do tenant salon (camada 7.5). O conflito de agenda é POR profissional. active=false retira da disponibilidade que a IA enxerga.';

create index idx_salon_prof_company_active on public.salon_professionals (company_id, active)
  where active = true;
create index idx_salon_prof_company_name on public.salon_professionals (company_id, name);

alter table public.salon_professionals enable row level security;
alter table public.salon_professionals force  row level security;

create policy salon_prof_select on public.salon_professionals
  for select to authenticated using (company_id = app.company_id());
create policy salon_prof_insert on public.salon_professionals
  for insert to authenticated with check (company_id = app.company_id());
create policy salon_prof_update on public.salon_professionals
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy salon_prof_delete on public.salon_professionals
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.salon_professionals to authenticated;
grant all on public.salon_professionals to service_role;

-- ---------------------------------------------------------------------------
-- salon_offerings — catálogo de serviços (nome "offering" no backend p/ não colidir com o
-- Spring SalonService; a UI/rota continua "serviços")
-- ---------------------------------------------------------------------------
create table public.salon_offerings (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  category         text,        -- "Cabelo", "Unha", "Pele" (texto livre)
  duration_minutes integer     not null check (duration_minutes between 15 and 480),
  price_cents      integer,     -- nullable (salão pode não expor preço pela IA)
  active           boolean     not null default true,
  description      text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.salon_offerings is
  'Serviços do tenant salon (camada 7.5). duration_minutes por serviço (varia). price_cents nullable. A duração entra como SNAPSHOT no agendamento.';

create index idx_salon_offer_company_active on public.salon_offerings (company_id, active)
  where active = true;
create index idx_salon_offer_company_cat on public.salon_offerings (company_id, category);

alter table public.salon_offerings enable row level security;
alter table public.salon_offerings force  row level security;

create policy salon_offer_select on public.salon_offerings
  for select to authenticated using (company_id = app.company_id());
create policy salon_offer_insert on public.salon_offerings
  for insert to authenticated with check (company_id = app.company_id());
create policy salon_offer_update on public.salon_offerings
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy salon_offer_delete on public.salon_offerings
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.salon_offerings to authenticated;
grant all on public.salon_offerings to service_role;

-- ---------------------------------------------------------------------------
-- salon_config — horário do salão (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.salon_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  opens_at       time        not null default '09:00',
  closes_at      time        not null default '20:00',
  buffer_minutes integer     not null default 0 check (buffer_minutes >= 0),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.salon_config is
  'Config do tenant salon (camada 7.5): janela de funcionamento + buffer. 1:1 com company. Ausente → defaults (09:00/20:00/0).';

alter table public.salon_config enable row level security;
alter table public.salon_config force  row level security;

create policy salon_config_select on public.salon_config
  for select to authenticated using (company_id = app.company_id());
create policy salon_config_insert on public.salon_config
  for insert to authenticated with check (company_id = app.company_id());
create policy salon_config_update on public.salon_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.salon_config to authenticated;
grant all on public.salon_config to service_role;

-- ---------------------------------------------------------------------------
-- salon_appointments — agendamentos (criados pelo backend via IA OU tenant via API)
-- ---------------------------------------------------------------------------
create table public.salon_appointments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  professional_id   uuid        not null references public.salon_professionals(id) on delete restrict,
  service_id        uuid        not null references public.salon_offerings(id) on delete restrict,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,
  guest_name        text        not null,   -- snapshot do contato
  guest_phone       text,                   -- snapshot opcional
  start_at          timestamptz not null,
  duration_minutes  integer     not null,   -- snapshot do offering.duration_minutes
  end_at            timestamptz not null,   -- materializado no INSERT (ver cabeçalho)
  service_name      text        not null,   -- snapshot
  professional_name text        not null,   -- snapshot
  price_cents       integer,                -- snapshot opcional
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','falta')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.salon_appointments is
  'Agendamentos do tenant salon (camada 7.5). INSERT pelo backend (service_role). Conflito é POR professional_id (não company). professional_name/service_name/price_cents/duration_minutes são SNAPSHOTS.';

create index idx_salon_appts_company_status_start on public.salon_appointments (company_id, status, start_at);
-- Índice CRÍTICO da checagem de conflito: por PROFISSIONAL, só status bloqueantes.
create index idx_salon_appts_prof_active on public.salon_appointments (professional_id, start_at)
  where status in ('agendado','confirmado');
create index idx_salon_appts_contact on public.salon_appointments (contact_id, start_at desc)
  where contact_id is not null;

alter table public.salon_appointments enable row level security;
alter table public.salon_appointments force  row level security;

create policy salon_appts_select on public.salon_appointments
  for select to authenticated using (company_id = app.company_id());
create policy salon_appts_update on public.salon_appointments
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.salon_appointments to authenticated;
grant all on public.salon_appointments to service_role;
