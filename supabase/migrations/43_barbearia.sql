-- =============================================================================
-- 43_barbearia.sql
-- Meada WhatsApp — Camada 8.1 (SM-O: perfil Barbearia / BarbeariaBot). 11º perfil vertical real
-- (sushi 7.1, legal 7.2, restaurant 7.3, dental 7.4, salon 7.5, pousada 7.6, academia 7.7,
-- pet 7.8, oficina 7.9, nutri 8.0, barbearia 8.1). Tabelas exclusivas do perfil 'barbearia':
-- barbeiros, serviços, config, agendamentos E a fila de walk-in.
--
-- CLONE do chassi do SALON (7.5): agenda com conflito POR barbeiro (barber_id), duração por
-- serviço, snapshots, end_at materializado no INSERT.
--
-- ESCAPADA NOVA — FILA DE WALK-IN COM POSIÇÃO DERIVADA (barber_queue_tickets): a posição do
-- cliente na fila NÃO é uma coluna persistida — é DERIVADA por query (count de tickets
-- 'aguardando' do mesmo escopo com enqueued_at menor). Atender/desistir de quem está à frente
-- RECOMPUTA todas as posições sem nenhum UPDATE de reordenação. Primeiro perfil com ordem
-- RELATIVA (sem âncora temporal absoluta). enqueued_at é a ÂNCORA DE ORDEM.
--
-- Convenções (padrão das migrations 30-42):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - barber_appointments / barber_queue_tickets: INSERT vem do BACKEND (service_role) — IA
--     (handlers) ou tenant (POST manual). Tenant só SELECT/UPDATE.
--   - end_at do appointment é MATERIALIZADO no INSERT (start_at + duration_minutes). NÃO é coluna
--     gerada (timestamptz + interval não é IMMUTABLE — lição da SM-D/E).
--   - SNAPSHOTS: barber_name + service_name + price_cents + duration_minutes congelados no
--     momento — alterar serviço/barbeiro depois NÃO altera agendamentos/tickets passados.
--   - Cliente NÃO é entidade própria (decisão cravada, igual salon/pousada): alta rotatividade;
--     histórico vem do contact + appointments/tickets. guest_name/guest_phone são snapshots.
--   - LGPD: notes é administrativo, sem dado sensível.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'barbearia' (11º perfil real; 12º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia'));

-- ---------------------------------------------------------------------------
-- barber_barbers — barbeiros do tenant (catálogo) — espelho salon_professionals
-- ---------------------------------------------------------------------------
create table public.barber_barbers (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "corte/barba", "degradê" (texto livre, opcional)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.barber_barbers is
  'Barbeiros do tenant barbearia (camada 8.1). O conflito de agenda é POR barbeiro. active=false retira da disponibilidade que a IA enxerga.';

create index idx_barber_barbers_company_active on public.barber_barbers (company_id, active)
  where active = true;
create index idx_barber_barbers_company_name on public.barber_barbers (company_id, name);

alter table public.barber_barbers enable row level security;
alter table public.barber_barbers force  row level security;

create policy barber_barbers_select on public.barber_barbers
  for select to authenticated using (company_id = app.company_id());
create policy barber_barbers_insert on public.barber_barbers
  for insert to authenticated with check (company_id = app.company_id());
create policy barber_barbers_update on public.barber_barbers
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy barber_barbers_delete on public.barber_barbers
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.barber_barbers to authenticated;
grant all on public.barber_barbers to service_role;

-- ---------------------------------------------------------------------------
-- barber_services — catálogo de serviços — espelho salon_offerings
-- ---------------------------------------------------------------------------
create table public.barber_services (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),
  category         text,        -- "Cabelo", "Barba" (texto livre)
  duration_minutes integer     not null check (duration_minutes between 5 and 480),
  price_cents      integer,     -- nullable (pode não expor preço pela IA)
  active           boolean     not null default true,
  description      text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.barber_services is
  'Serviços do tenant barbearia (camada 8.1). duration_minutes por serviço (varia). price_cents nullable. A duração entra como SNAPSHOT no agendamento/ticket.';

create index idx_barber_services_company_active on public.barber_services (company_id, active)
  where active = true;
create index idx_barber_services_company_cat on public.barber_services (company_id, category);

alter table public.barber_services enable row level security;
alter table public.barber_services force  row level security;

create policy barber_services_select on public.barber_services
  for select to authenticated using (company_id = app.company_id());
create policy barber_services_insert on public.barber_services
  for insert to authenticated with check (company_id = app.company_id());
create policy barber_services_update on public.barber_services
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy barber_services_delete on public.barber_services
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.barber_services to authenticated;
grant all on public.barber_services to service_role;

-- ---------------------------------------------------------------------------
-- barber_config — horário + slot + fila (1:1 com company) — espelho salon_config + 2 campos
-- ---------------------------------------------------------------------------
create table public.barber_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  opens_at       time        not null default '09:00',
  closes_at      time        not null default '20:00',
  slot_minutes   integer     not null default 15 check (slot_minutes > 0),
  queue_enabled  boolean     not null default true,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.barber_config is
  'Config do tenant barbearia (camada 8.1): janela de funcionamento + granularidade de slot + se a fila de walk-in está ligada. 1:1 com company. Ausente → defaults (09:00/20:00/15/true).';

alter table public.barber_config enable row level security;
alter table public.barber_config force  row level security;

create policy barber_config_select on public.barber_config
  for select to authenticated using (company_id = app.company_id());
create policy barber_config_insert on public.barber_config
  for insert to authenticated with check (company_id = app.company_id());
create policy barber_config_update on public.barber_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.barber_config to authenticated;
grant all on public.barber_config to service_role;

-- ---------------------------------------------------------------------------
-- barber_appointments — agendamentos (clone 1:1 de salon_appointments). Conflito POR barber_id.
-- ---------------------------------------------------------------------------
create table public.barber_appointments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  barber_id         uuid        not null references public.barber_barbers(id) on delete restrict,
  service_id        uuid        not null references public.barber_services(id) on delete restrict,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,
  guest_name        text        not null,   -- snapshot do contato
  guest_phone       text,                   -- snapshot opcional
  start_at          timestamptz not null,
  duration_minutes  integer     not null,   -- snapshot do service.duration_minutes
  end_at            timestamptz not null,   -- materializado no INSERT (ver cabeçalho)
  service_name      text        not null,   -- snapshot
  barber_name       text        not null,   -- snapshot
  price_cents       integer,                -- snapshot opcional
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','falta')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.barber_appointments is
  'Agendamentos do tenant barbearia (camada 8.1). INSERT pelo backend (service_role). Conflito é POR barber_id (não company) — 2 clientes no mesmo horário com barbeiros DIFERENTES é OK. barber_name/service_name/price_cents/duration_minutes são SNAPSHOTS.';

create index idx_barber_appts_company_status_start on public.barber_appointments (company_id, status, start_at);
-- Índice CRÍTICO da checagem de conflito: por BARBEIRO, só status bloqueantes.
create index idx_barber_appts_barber_active on public.barber_appointments (barber_id, start_at)
  where status in ('agendado','confirmado');
create index idx_barber_appts_contact on public.barber_appointments (contact_id, start_at desc)
  where contact_id is not null;

alter table public.barber_appointments enable row level security;
alter table public.barber_appointments force  row level security;

create policy barber_appts_select on public.barber_appointments
  for select to authenticated using (company_id = app.company_id());
create policy barber_appts_update on public.barber_appointments
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.barber_appointments to authenticated;
grant all on public.barber_appointments to service_role;

-- ---------------------------------------------------------------------------
-- barber_queue_tickets — A ENTIDADE NOVA: fila FIFO de walk-in com POSIÇÃO DERIVADA.
-- SEM coluna 'position' — a posição é calculada por query (count de 'aguardando' com
-- enqueued_at menor no mesmo escopo). enqueued_at é a ÂNCORA DE ORDEM.
-- ---------------------------------------------------------------------------
create table public.barber_queue_tickets (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  barber_id         uuid        references public.barber_barbers(id) on delete set null,  -- null = "qualquer barbeiro"
  service_id        uuid        not null references public.barber_services(id) on delete restrict,
  conversation_id   uuid        references public.conversations(id) on delete set null,
  contact_id        uuid        references public.contacts(id) on delete set null,
  guest_name        text        not null,   -- snapshot do contato
  guest_phone       text,                   -- snapshot opcional
  service_name      text        not null,   -- snapshot
  duration_minutes  integer     not null,   -- snapshot do service.duration_minutes (pro ETA)
  barber_name       text,                   -- snapshot nullable (null = qualquer barbeiro)
  status            text        not null default 'aguardando' check (status in
                      ('aguardando','chamado','atendido','desistiu','expirado')),
  enqueued_at       timestamptz not null default now(),   -- a ÂNCORA DE ORDEM
  called_at         timestamptz,
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.barber_queue_tickets is
  'Fila de walk-in do tenant barbearia (camada 8.1). A POSIÇÃO é DERIVADA por query (count de tickets aguardando com enqueued_at menor no mesmo escopo de barbeiro) — NÃO há coluna position. barber_id null = "qualquer barbeiro" (fila geral). enqueued_at é a âncora de ordem. INSERT pelo backend (service_role).';

-- Índice parcial do cálculo de posição (fila ativa por barbeiro / geral).
create index idx_barber_queue_waiting on public.barber_queue_tickets (company_id, barber_id, enqueued_at)
  where status = 'aguardando';
create index idx_barber_queue_company_status on public.barber_queue_tickets (company_id, status);
create index idx_barber_queue_contact on public.barber_queue_tickets (contact_id, enqueued_at desc)
  where contact_id is not null;

alter table public.barber_queue_tickets enable row level security;
alter table public.barber_queue_tickets force  row level security;

create policy barber_queue_select on public.barber_queue_tickets
  for select to authenticated using (company_id = app.company_id());
create policy barber_queue_update on public.barber_queue_tickets
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.barber_queue_tickets to authenticated;
grant all on public.barber_queue_tickets to service_role;
