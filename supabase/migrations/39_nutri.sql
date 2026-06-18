-- =============================================================================
-- 39_nutri.sql
-- Meada WhatsApp — Camada 8.0 (SM-K: perfil Nutri / NutriBot). DÉCIMO perfil vertical real e
-- PRIMEIRA da camada 8.x. Tabelas exclusivas do perfil 'nutri': profissionais, config, pacientes
-- (sub-entidade do contact), planos alimentares (sub-entidade do paciente — DOIS níveis) e
-- consultas (agenda com conflito por profissional).
--
-- EVOLUÇÃO ESTRUTURAL — combina agenda (espelho dental/salon) + sub-entidade de cliente (pet/
-- oficina), com DUAS escapadas novas:
--   - DOIS NÍVEIS de sub-entidade: nutri_patients é sub-entidade do contact; nutri_plans é
--     sub-entidade do nutri_patient. Primeiro perfil com sub-entidade aninhada.
--   - Artefato READ-ONLY-PRA-IA: nutri_plans.body é escrito SÓ pelo profissional no painel; a IA
--     tem um modo de ENTREGA (envia o texto gravado) mas NUNCA o edita/resume/adapta.
--
-- TRAVA DE SEGURANÇA CLÍNICA (CFN/CRN): plano alimentar individualizado é conduta privativa do
-- nutricionista. A IA não calcula caloria/macro, não responde "posso comer X", não opina sobre
-- patologia/suplementação, não monta/ajusta/resume plano. A trava vive na persona (ProfilePrompt
-- Context), não no schema — mas o schema garante que a IA NÃO tem caminho de escrita de plano
-- (sem policy de INSERT/UPDATE de plano para a IA; o body só é LIDO na entrega).
--
-- Convenções (padrão das migrations 30-38):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - nutri_appointments + nutri_plans: INSERT pelo BACKEND (service_role). Tenant SELECT/UPDATE.
--   - end_at materializado no INSERT (start_at + duration_minutes); NÃO coluna gerada.
--   - SNAPSHOTS no appointment: patient_name/phone + professional_name. Mudar paciente/prof depois
--     NÃO altera consultas passadas.
--   - Paciente NÃO é entidade do core (continua o contact). nutri_patients.contact_id é a verdade.
--   - goal/dietary_restrictions/notes texto livre administrativo, SEM número nutricional.
--   - 1 plano 'ativo' por paciente (índice parcial UNIQUE); novo ativo arquiva o anterior (service).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'nutri' (10º perfil real; 11º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada','academia','pet','oficina','nutri'));

-- ---------------------------------------------------------------------------
-- nutri_professionals — nutricionistas (catálogo). conflito de agenda por profissional.
-- ---------------------------------------------------------------------------
create table public.nutri_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "nutrição clínica", "nutrição esportiva" (texto livre)
  crn         text,        -- registro profissional (texto livre, nullable)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.nutri_professionals is
  'Nutricionistas do tenant nutri (camada 8.0). Conflito de agenda é por profissional. active=false retira da disponibilidade da IA.';

create index idx_nutri_prof_company_active on public.nutri_professionals (company_id, active) where active = true;
create index idx_nutri_prof_company_name on public.nutri_professionals (company_id, name);

alter table public.nutri_professionals enable row level security;
alter table public.nutri_professionals force  row level security;

create policy nutri_prof_select on public.nutri_professionals for select to authenticated using (company_id = app.company_id());
create policy nutri_prof_insert on public.nutri_professionals for insert to authenticated with check (company_id = app.company_id());
create policy nutri_prof_update on public.nutri_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy nutri_prof_delete on public.nutri_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.nutri_professionals to authenticated;
grant all on public.nutri_professionals to service_role;

-- ---------------------------------------------------------------------------
-- nutri_config — horário de funcionamento (1:1 com company). espelho pet_config.
-- ---------------------------------------------------------------------------
create table public.nutri_config (
  company_id     uuid        primary key references public.companies(id) on delete cascade,
  opens_at       time        not null default '08:00',
  closes_at      time        not null default '18:00',
  buffer_minutes integer     not null default 0 check (buffer_minutes >= 0),
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

comment on table public.nutri_config is
  'Config do tenant nutri (camada 8.0): janela de funcionamento + buffer. 1:1 com company. Ausente → defaults (08:00/18:00/0).';

alter table public.nutri_config enable row level security;
alter table public.nutri_config force  row level security;

create policy nutri_config_select on public.nutri_config for select to authenticated using (company_id = app.company_id());
create policy nutri_config_insert on public.nutri_config for insert to authenticated with check (company_id = app.company_id());
create policy nutri_config_update on public.nutri_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.nutri_config to authenticated;
grant all on public.nutri_config to service_role;

-- ---------------------------------------------------------------------------
-- nutri_patients — pacientes (SUB-ENTIDADE do contact). nível 1 de aninhamento.
-- ---------------------------------------------------------------------------
create table public.nutri_patients (
  id                   uuid        primary key default gen_random_uuid(),
  company_id           uuid        not null references public.companies(id) on delete restrict,
  contact_id           uuid        not null references public.contacts(id) on delete restrict,  -- CLIENTE
  name                 text        not null check (length(trim(name)) between 1 and 120),
  goal                 text,        -- "emagrecimento", "ganho de massa" (texto livre, SEM cálculo)
  dietary_restrictions text,        -- texto livre administrativo (SEM número)
  birth_date           date,
  notes                text,
  active               boolean     not null default true,  -- false = arquivado (não perde histórico)
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

comment on table public.nutri_patients is
  'Pacientes do tenant nutri (camada 8.0). SUB-ENTIDADE do contact (nível 1). active=false arquiva sem perder histórico. goal/dietary_restrictions/notes texto livre administrativo, SEM número nutricional.';

create index idx_nutri_patients_company_contact_active on public.nutri_patients (company_id, contact_id, active) where active = true;
create index idx_nutri_patients_company_name on public.nutri_patients (company_id, name);

alter table public.nutri_patients enable row level security;
alter table public.nutri_patients force  row level security;

create policy nutri_patients_select on public.nutri_patients for select to authenticated using (company_id = app.company_id());
create policy nutri_patients_insert on public.nutri_patients for insert to authenticated with check (company_id = app.company_id());
create policy nutri_patients_update on public.nutri_patients for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy nutri_patients_delete on public.nutri_patients for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.nutri_patients to authenticated;
grant all on public.nutri_patients to service_role;

-- ---------------------------------------------------------------------------
-- nutri_plans — planos alimentares (SUB-ENTIDADE do paciente — nível 2). body escrito SÓ pelo
-- profissional no painel; a IA só LÊ na entrega. 1 plano 'ativo' por paciente (índice parcial).
-- ---------------------------------------------------------------------------
create table public.nutri_plans (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete restrict,
  patient_id      uuid        not null references public.nutri_patients(id) on delete restrict,
  professional_id uuid        references public.nutri_professionals(id) on delete set null,
  title           text        not null check (length(trim(title)) between 1 and 200),
  body            text        not null,   -- markdown livre, escrito pelo profissional. A IA NÃO edita.
  starts_on       date,
  ends_on         date,
  status          text        not null default 'ativo' check (status in ('ativo','arquivado')),
  notes           text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

comment on table public.nutri_plans is
  'Planos alimentares do tenant nutri (camada 8.0). SUB-ENTIDADE do paciente (nível 2). body é conduta clínica escrita SÓ pelo profissional — a IA ENTREGA o texto exato, NUNCA edita/resume/adapta. 1 plano ativo por paciente (índice parcial UNIQUE).';

-- 1 plano 'ativo' por paciente: novo ativo arquiva o anterior (no service).
create unique index uniq_active_plan_per_patient on public.nutri_plans (patient_id) where status = 'ativo';
create index idx_nutri_plans_company_patient on public.nutri_plans (company_id, patient_id, status);

alter table public.nutri_plans enable row level security;
alter table public.nutri_plans force  row level security;

create policy nutri_plans_select on public.nutri_plans for select to authenticated using (company_id = app.company_id());
create policy nutri_plans_update on public.nutri_plans for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.nutri_plans to authenticated;
grant all on public.nutri_plans to service_role;

-- ---------------------------------------------------------------------------
-- nutri_appointments — consultas (snapshots de paciente/profissional). conflito por profissional.
-- ---------------------------------------------------------------------------
create table public.nutri_appointments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  professional_id   uuid        not null references public.nutri_professionals(id) on delete restrict,
  patient_id        uuid        not null references public.nutri_patients(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,   -- cliente (snapshot/atalho)
  conversation_id   uuid        references public.conversations(id) on delete set null,
  patient_name      text        not null,   -- snapshot
  patient_phone     text,                   -- snapshot opcional
  professional_name text        not null,   -- snapshot
  appointment_type  text        not null check (appointment_type in ('primeira','retorno','avaliacao')),
  duration_minutes  integer     not null,   -- snapshot
  start_at          timestamptz not null,
  end_at            timestamptz not null,   -- materializado no INSERT
  status            text        not null default 'agendado' check (status in
                      ('agendado','confirmado','realizado','cancelado','falta')),
  notes             text,
  created_at        timestamptz not null default now(),
  status_updated_at timestamptz not null default now()
);

comment on table public.nutri_appointments is
  'Consultas do tenant nutri (camada 8.0). INSERT pelo backend (service_role). Conflito por professional_id. Snapshots de paciente/profissional. appointment_type primeira|retorno|avaliacao.';

create index idx_nutri_appts_company_status_start on public.nutri_appointments (company_id, status, start_at);
-- Índice CRÍTICO do conflito: por PROFISSIONAL, só status bloqueantes.
create index idx_nutri_appts_prof_active on public.nutri_appointments (professional_id, start_at)
  where status in ('agendado','confirmado');
create index idx_nutri_appts_patient on public.nutri_appointments (patient_id, start_at desc);
create index idx_nutri_appts_contact on public.nutri_appointments (contact_id, start_at desc);

alter table public.nutri_appointments enable row level security;
alter table public.nutri_appointments force  row level security;

create policy nutri_appts_select on public.nutri_appointments for select to authenticated using (company_id = app.company_id());
create policy nutri_appts_update on public.nutri_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.nutri_appointments to authenticated;
grant all on public.nutri_appointments to service_role;
