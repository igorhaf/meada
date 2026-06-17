-- =============================================================================
-- 32_restaurant.sql
-- Meada WhatsApp — Camada 7.3 (SM-D: perfil Restaurante / MesaBot). Terceiro perfil
-- vertical real depois do SushiBot (7.1) e do ProcessoBot (7.2). Tabelas exclusivas do
-- perfil 'restaurant': mesas, config de reservas e reservas.
--
-- Convenções (padrão das migrations anteriores):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - table_reservations: INSERT vem do BACKEND (service_role) — a reserva é criada pela IA
--     via ReservationConfirmHandler OU pelo tenant via API backend (POST manual). O tenant
--     não usa o SDK direto para inserir; só SELECT/UPDATE (mudar status no Kanban/agenda).
--   - Snapshot no momento da reserva: guest_name/guest_phone (o contato pode sumir) e
--     duration_minutes (o config pode mudar) ficam congelados — alterar o config NÃO altera
--     reservas já criadas.
--   - end_at é COLUNA GERADA (start_at + duration_minutes) — a janela temporal usada na
--     checagem de conflito é calculada no banco, não em Java (defesa contra race).
--   - updated_at é mantido pelos repositórios (set updated_at = now() no UPDATE), padrão do
--     projeto — não há trigger genérico de updated_at.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'restaurant' (4º perfil real; 5º contando generic)
-- ---------------------------------------------------------------------------
-- Recria a CHECK constraint com a lista atualizada. NÃO usa IF NOT EXISTS (sintaxe varia);
-- drop + add é o padrão portável. A 29 criou companies_profile_id_check com os 4 ids.
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant'));

-- ---------------------------------------------------------------------------
-- restaurant_tables — catálogo de mesas (análogo a sushi_menu_items)
-- ---------------------------------------------------------------------------
create table public.restaurant_tables (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  label       text        not null check (length(trim(label)) between 1 and 60),
  capacity    integer     not null check (capacity between 1 and 50),
  available   boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (company_id, label)
);

comment on table public.restaurant_tables is
  'Mesas do tenant restaurant (camada 7.3). UNIQUE por (company_id, label). available=false retira a mesa da disponibilidade que a IA enxerga.';

create index idx_restaurant_tables_company_available on public.restaurant_tables (company_id, available)
  where available = true;

alter table public.restaurant_tables enable row level security;
alter table public.restaurant_tables force  row level security;

create policy restaurant_tables_select on public.restaurant_tables
  for select to authenticated using (company_id = app.company_id());
create policy restaurant_tables_insert on public.restaurant_tables
  for insert to authenticated with check (company_id = app.company_id());
create policy restaurant_tables_update on public.restaurant_tables
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy restaurant_tables_delete on public.restaurant_tables
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.restaurant_tables to authenticated;
grant all on public.restaurant_tables to service_role;

-- ---------------------------------------------------------------------------
-- restaurant_reservation_config — duração + horário do restaurante (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.restaurant_reservation_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  duration_minutes integer     not null default 120 check (duration_minutes between 30 and 600),
  buffer_minutes   integer     not null default 0 check (buffer_minutes >= 0),
  opens_at         time        not null default '11:00',
  closes_at        time        not null default '23:00',
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.restaurant_reservation_config is
  'Config de reservas do tenant restaurant (camada 7.3): duração da reserva (2h padrão), buffer entre reservas, e janela de funcionamento. 1:1 com company. Ausente → defaults (120/0/11:00/23:00).';

alter table public.restaurant_reservation_config enable row level security;
alter table public.restaurant_reservation_config force  row level security;

create policy restaurant_config_select on public.restaurant_reservation_config
  for select to authenticated using (company_id = app.company_id());
create policy restaurant_config_insert on public.restaurant_reservation_config
  for insert to authenticated with check (company_id = app.company_id());
create policy restaurant_config_update on public.restaurant_reservation_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.restaurant_reservation_config to authenticated;
grant all on public.restaurant_reservation_config to service_role;

-- ---------------------------------------------------------------------------
-- table_reservations — reservas (criadas pelo backend via IA OU tenant via API)
-- ---------------------------------------------------------------------------
create table public.table_reservations (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  table_id         uuid        not null references public.restaurant_tables(id) on delete restrict,
  conversation_id  uuid        references public.conversations(id) on delete set null,
  contact_id       uuid        references public.contacts(id) on delete set null,
  guest_name       text        not null,
  guest_phone      text,
  start_at         timestamptz not null,
  duration_minutes integer     not null,
  -- end_at é materializado pelo repositório no INSERT (start_at + duration_minutes). NÃO é coluna
  -- gerada: timestamptz + interval não é IMMUTABLE (depende do timezone da sessão p/ a aritmética
  -- DST), e Postgres exige expressão immutable em GENERATED. Materializar no insert é simples e
  -- não muda a lógica de conflito (o SELECT de overlap compara start_at/end_at já gravados).
  end_at           timestamptz not null,
  num_people       integer     not null check (num_people between 1 and 50),
  status           text        not null default 'pendente' check (status in
                     ('pendente','confirmada','realizada','cancelada','no_show')),
  notes            text,
  created_at       timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.table_reservations is
  'Reservas do tenant restaurant (camada 7.3). INSERT pelo backend (service_role) — IA (ReservationConfirmHandler) ou tenant (POST manual). conversation_id/contact_id nullable (reserva manual não tem WhatsApp). guest_name/guest_phone/duration_minutes são SNAPSHOTS do momento.';

-- Índice da agenda (lista/Kanban por dia).
create index idx_reservations_company_status_start on public.table_reservations (company_id, status, start_at);
-- Índice CRÍTICO da checagem de conflito: só os status bloqueantes (pendente/confirmada) por mesa.
create index idx_reservations_table_active on public.table_reservations (table_id, start_at)
  where status in ('pendente','confirmada');

alter table public.table_reservations enable row level security;
alter table public.table_reservations force  row level security;

-- Tenant SELECT/UPDATE do próprio; INSERT é só backend (sem policy authenticated de insert).
create policy reservations_select on public.table_reservations
  for select to authenticated using (company_id = app.company_id());
create policy reservations_update on public.table_reservations
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.table_reservations to authenticated;
grant all on public.table_reservations to service_role;
