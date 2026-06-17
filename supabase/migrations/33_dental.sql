-- =============================================================================
-- 33_dental.sql
-- Meada WhatsApp — Camada 7.4 (SM-E: perfil Dental / DentalBot). Quarto perfil vertical
-- real (sushi 7.1, legal 7.2, restaurant 7.3, dental 7.4). Tabelas exclusivas do perfil
-- 'dental': pacientes, config do consultório e consultas (appointments).
--
-- Convenções (padrão das migrations 30/31/32):
--   - RLS enable + force; policies do tenant via app.company_id(); grants authenticated +
--     service_role.
--   - dental_appointments: INSERT vem do BACKEND (service_role) — a consulta é criada pela
--     IA via ConsultaConfirmHandler OU pelo tenant via API (POST manual). O tenant não usa
--     o SDK direto pra inserir; só SELECT/UPDATE (mudar status na agenda/Kanban).
--   - end_at é MATERIALIZADO pelo repositório no INSERT (start_at + duration_minutes). NÃO é
--     coluna gerada: timestamptz + interval não é IMMUTABLE (depende do timezone da sessão p/
--     a aritmética DST), e Postgres exige expressão immutable em GENERATED (lição da SM-D).
--   - duration_minutes em dental_appointments é SNAPSHOT do config no momento — alterar o
--     config NÃO altera consultas já criadas.
--   - LGPD: notes (em patients e appointments) é ADMINISTRATIVO (preferências de horário,
--     contato), NÃO clínico. Sem prontuário/diagnóstico/alergia/odontograma nesta SM — dados
--     clínicos ficam pra fase futura com criptografia at-rest e log de acesso por usuário.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- dental_patients — pacientes da clínica (catálogo, ~ legal_clients)
-- ---------------------------------------------------------------------------
create table public.dental_patients (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  email       text,
  phone       text,
  document    text,        -- CPF, sem máscara
  birth_date  date,        -- opcional; idade calculada client-side
  contact_id  uuid        references public.contacts(id) on delete set null,
  notes       text,        -- ADMINISTRATIVO, não clínico (ver cabeçalho)
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.dental_patients is
  'Pacientes do tenant dental (camada 7.4). contact_id (nullable) liga ao contato WhatsApp — a IA resolve contact → patient. notes é ADMINISTRATIVO, não clínico (LGPD).';

create index idx_dental_patients_company_name on public.dental_patients (company_id, name);
create index idx_dental_patients_company_contact on public.dental_patients (company_id, contact_id)
  where contact_id is not null;

alter table public.dental_patients enable row level security;
alter table public.dental_patients force  row level security;

create policy dental_patients_select on public.dental_patients
  for select to authenticated using (company_id = app.company_id());
create policy dental_patients_insert on public.dental_patients
  for insert to authenticated with check (company_id = app.company_id());
create policy dental_patients_update on public.dental_patients
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());
create policy dental_patients_delete on public.dental_patients
  for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.dental_patients to authenticated;
grant all on public.dental_patients to service_role;

-- ---------------------------------------------------------------------------
-- dental_clinic_config — duração + horário do consultório (1:1 com company)
-- ---------------------------------------------------------------------------
create table public.dental_clinic_config (
  company_id       uuid        primary key references public.companies(id) on delete cascade,
  duration_minutes integer     not null default 30 check (duration_minutes between 15 and 240),
  buffer_minutes   integer     not null default 0 check (buffer_minutes >= 0),
  opens_at         time        not null default '08:00',
  closes_at        time        not null default '18:00',
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.dental_clinic_config is
  'Config do consultório dental (camada 7.4): duração da consulta (30min padrão), buffer, janela de funcionamento. 1:1 com company. Ausente → defaults (30/0/08:00/18:00).';

alter table public.dental_clinic_config enable row level security;
alter table public.dental_clinic_config force  row level security;

create policy dental_config_select on public.dental_clinic_config
  for select to authenticated using (company_id = app.company_id());
create policy dental_config_insert on public.dental_clinic_config
  for insert to authenticated with check (company_id = app.company_id());
create policy dental_config_update on public.dental_clinic_config
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, insert, update on public.dental_clinic_config to authenticated;
grant all on public.dental_clinic_config to service_role;

-- ---------------------------------------------------------------------------
-- dental_appointments — consultas (criadas pelo backend via IA OU tenant via API)
-- ---------------------------------------------------------------------------
create table public.dental_appointments (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  patient_id       uuid        not null references public.dental_patients(id) on delete restrict,
  conversation_id  uuid        references public.conversations(id) on delete set null,
  start_at         timestamptz not null,
  duration_minutes integer     not null,
  -- end_at materializado no INSERT (start_at + duration_minutes). NÃO é coluna gerada (ver
  -- cabeçalho — timestamptz+interval não é IMMUTABLE). O SELECT de conflito usa start_at/end_at
  -- já gravados.
  end_at           timestamptz not null,
  type             text        not null check (length(trim(type)) between 1 and 100),
  status           text        not null default 'agendada' check (status in
                     ('agendada','confirmada','realizada','cancelada','falta')),
  notes            text,        -- ADMINISTRATIVO (ver cabeçalho)
  created_at       timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.dental_appointments is
  'Consultas do tenant dental (camada 7.4). INSERT pelo backend (service_role) — IA (ConsultaConfirmHandler) ou tenant (POST manual). conversation_id nullable (consulta manual não tem WhatsApp). duration_minutes é SNAPSHOT do config. notes é ADMINISTRATIVO (LGPD).';

create index idx_dental_appts_company_status_start on public.dental_appointments (company_id, status, start_at);
-- Índice CRÍTICO da checagem de conflito: só status bloqueantes por company (1 dentista/tenant).
create index idx_dental_appts_company_active on public.dental_appointments (company_id, start_at)
  where status in ('agendada','confirmada');
create index idx_dental_appts_patient on public.dental_appointments (patient_id, start_at desc);

alter table public.dental_appointments enable row level security;
alter table public.dental_appointments force  row level security;

-- Tenant SELECT/UPDATE do próprio; INSERT é só backend (sem policy authenticated de insert).
create policy dental_appts_select on public.dental_appointments
  for select to authenticated using (company_id = app.company_id());
create policy dental_appts_update on public.dental_appointments
  for update to authenticated using (company_id = app.company_id())
  with check (company_id = app.company_id());

grant select, update on public.dental_appointments to authenticated;
grant all on public.dental_appointments to service_role;
