-- =============================================================================
-- 64_cursos.sql
-- Meada — Camada 8.20 (SM: perfil Cursos / escola livre · curso online · formação). Tabelas
-- exclusivas do perfil 'cursos': cursos, MÓDULOS ordenados, config, matrículas (assinaturas),
-- progresso individual por módulo, e pagamentos manuais.
--
-- CLONA o chassi do ACADEMIA (camada 7.7): a matrícula é uma ASSINATURA (recorrência indefinida,
-- ativa-até-cancelar), anti-dupla por contato, pagamento mensal MANUAL (Stripe é #50, futuro).
--
-- DUAS ESCAPADAS (do brief "Academia + Nutri | trilha de módulos ordenados + progresso individual +
-- próximo módulo read-only"):
--   (1) TRILHA DE MÓDULOS ORDENADOS (cursos_modules): o curso não é uma "aula semanal" (academia) — é
--       uma sequência ORDENADA de módulos (position 0..N), cada um com um conteúdo/material. Substitui
--       academia_classes. A matrícula é no CURSO inteiro (não em N aulas via junction).
--   (2) PROGRESSO INDIVIDUAL + ENTREGA READ-ONLY DO PRÓXIMO MÓDULO (cursos_enrollment_progress + tag
--       <entrega_modulo>): cada matrícula tem seu progresso (quais módulos concluiu). O "próximo módulo"
--       é o 1º por position que NÃO está concluído. A IA ENTREGA o conteúdo desse módulo VERBATIM, com
--       barreira de contato (espelho EntregaPlano do nutri / EntregaMaterial do fotografia) — NUNCA
--       reescreve o material nem pula a ordem.
--
-- Convenções (padrão das migrations 30-63):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - cursos_enrollments / cursos_enrollment_progress: INSERT pelo BACKEND (service_role). Tenant
--     SELECT/UPDATE.
--   - SNAPSHOTS: a matrícula congela course_title + course_price_cents + student_name/phone. Mudar o
--     curso depois NÃO altera matrículas existentes.
--   - Aluno NÃO é entidade própria (igual academia/salon — rotatividade). Histórico via contact +
--     enrollments.
--   - LGPD: notes é administrativo, sem dado sensível.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'cursos' (26º contando generic). ESPELHA 60_fotografia (25) +
-- 'cursos'. Entra por ÚLTIMO no SCRIPTS de teste (sua CHECK tem os 26).
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia','cursos'));

-- ---------------------------------------------------------------------------
-- cursos_courses — catálogo de cursos (preço por mensalidade, igual academia_plans)
-- ---------------------------------------------------------------------------
create table public.cursos_courses (
  id            uuid        primary key default gen_random_uuid(),
  company_id    uuid        not null references public.companies(id) on delete restrict,
  title         text        not null check (length(trim(title)) between 1 and 200),
  category      text,        -- "idiomas", "música", "programação" (texto livre)
  monthly_cents integer     not null check (monthly_cents >= 0),
  description   text,
  active        boolean     not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.cursos_courses is
  'Cursos do tenant cursos (camada 8.20). monthly_cents em centavos (mensalidade). Entra como SNAPSHOT na matrícula. Espelho academia_plans. delete em uso → 409 course_in_use.';

create index idx_cursos_courses_company_active on public.cursos_courses (company_id, active) where active = true;

alter table public.cursos_courses enable row level security;
alter table public.cursos_courses force  row level security;

create policy cursos_courses_select on public.cursos_courses for select to authenticated using (company_id = app.company_id());
create policy cursos_courses_insert on public.cursos_courses for insert to authenticated with check (company_id = app.company_id());
create policy cursos_courses_update on public.cursos_courses for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy cursos_courses_delete on public.cursos_courses for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.cursos_courses to authenticated;
grant all on public.cursos_courses to service_role;

-- ---------------------------------------------------------------------------
-- cursos_modules — ESCAPADA 1: trilha ORDENADA de módulos do curso.
-- ---------------------------------------------------------------------------
create table public.cursos_modules (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,  -- denorm p/ RLS direta
  course_id   uuid        not null references public.cursos_courses(id) on delete cascade,
  position    integer     not null check (position >= 0),   -- ordem na trilha (0 = primeiro)
  title       text        not null check (length(trim(title)) between 1 and 200),
  content     text,        -- material/conteúdo do módulo (entregue VERBATIM pela IA)
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.cursos_modules is
  'Módulos ordenados do curso (camada 8.20, ESCAPADA 1). position = ordem na trilha (0..N). content é o material entregue READ-ONLY pela IA (<entrega_modulo>). delete cascade ao excluir o curso.';

create unique index uniq_cursos_module_position on public.cursos_modules (course_id, position);
create index idx_cursos_modules_company on public.cursos_modules (company_id);

alter table public.cursos_modules enable row level security;
alter table public.cursos_modules force  row level security;

create policy cursos_modules_select on public.cursos_modules for select to authenticated using (company_id = app.company_id());
create policy cursos_modules_insert on public.cursos_modules for insert to authenticated with check (company_id = app.company_id());
create policy cursos_modules_update on public.cursos_modules for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy cursos_modules_delete on public.cursos_modules for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.cursos_modules to authenticated;
grant all on public.cursos_modules to service_role;

-- ---------------------------------------------------------------------------
-- cursos_config — horário/atendimento INFORMATIVO (1:1 com company). Sem slot (curso não tem agenda).
-- ---------------------------------------------------------------------------
create table public.cursos_config (
  company_id uuid        primary key references public.companies(id) on delete cascade,
  opens_at   time        not null default '08:00',
  closes_at  time        not null default '22:00',
  notes      text,        -- aviso/observação institucional
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.cursos_config is
  'Config do tenant cursos (camada 8.20): horário de atendimento INFORMATIVO + notas. 1:1 com company. Ausente → defaults. SEM agenda/slot (o curso é assíncrono).';

alter table public.cursos_config enable row level security;
alter table public.cursos_config force  row level security;

create policy cursos_config_select on public.cursos_config for select to authenticated using (company_id = app.company_id());
create policy cursos_config_insert on public.cursos_config for insert to authenticated with check (company_id = app.company_id());
create policy cursos_config_update on public.cursos_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.cursos_config to authenticated;
grant all on public.cursos_config to service_role;

-- ---------------------------------------------------------------------------
-- cursos_enrollments — matrículas (assinaturas; clone academia_memberships).
-- ---------------------------------------------------------------------------
create table public.cursos_enrollments (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete restrict,
  course_id           uuid        not null references public.cursos_courses(id) on delete restrict,
  conversation_id     uuid        references public.conversations(id) on delete set null,
  contact_id          uuid        references public.contacts(id) on delete set null,
  student_name        text        not null,   -- snapshot
  student_phone       text,                   -- snapshot opcional
  course_title        text        not null,   -- snapshot
  course_monthly_cents integer    not null,   -- snapshot
  start_date          date        not null default current_date,
  end_date            date,                   -- materializado em concluida/cancelada
  status              text        not null default 'ativa' check (status in ('ativa','trancada','concluida','cancelada')),
  notes               text,
  created_at          timestamptz not null default now(),
  status_updated_at   timestamptz not null default now()
);

comment on table public.cursos_enrollments is
  'Matrículas (assinaturas) do tenant cursos (camada 8.20). RECORRÊNCIA INDEFINIDA: ativa-até-concluir/cancelar. trancada = pausa (mantém o vínculo). end_date em concluida/cancelada. snapshots de curso + student. Aluno não é entidade.';

create index idx_cursos_enrollments_company_status on public.cursos_enrollments (company_id, status, start_date desc);
create index idx_cursos_enrollments_contact on public.cursos_enrollments (contact_id, start_date desc) where contact_id is not null;
-- Anti-dupla: impede 2 matrículas ATIVAS no MESMO curso para o mesmo contato (irmão pode estar em
-- curso distinto; o mesmo contato pode ter cursos diferentes; NÃO 2 ativas no mesmo curso).
create unique index uniq_active_enrollment_per_contact_course on public.cursos_enrollments (company_id, contact_id, course_id)
  where status = 'ativa' and contact_id is not null;

alter table public.cursos_enrollments enable row level security;
alter table public.cursos_enrollments force  row level security;

create policy cursos_enrollments_select on public.cursos_enrollments for select to authenticated using (company_id = app.company_id());
create policy cursos_enrollments_update on public.cursos_enrollments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.cursos_enrollments to authenticated;
grant all on public.cursos_enrollments to service_role;

-- ---------------------------------------------------------------------------
-- cursos_enrollment_progress — ESCAPADA 2: progresso individual por módulo.
-- ---------------------------------------------------------------------------
create table public.cursos_enrollment_progress (
  enrollment_id uuid        not null references public.cursos_enrollments(id) on delete cascade,
  module_id     uuid        not null references public.cursos_modules(id) on delete restrict,
  completed_at  timestamptz not null default now(),
  primary key (enrollment_id, module_id)
);

comment on table public.cursos_enrollment_progress is
  'Progresso individual por módulo (camada 8.20, ESCAPADA 2). 1 linha = 1 módulo concluído por essa matrícula. O "próximo módulo" é o 1º por position do curso que NÃO está aqui. INSERT/DELETE backend (service_role); SELECT do tenant via JOIN com a matrícula.';

create index idx_cursos_progress_module on public.cursos_enrollment_progress (module_id);

alter table public.cursos_enrollment_progress enable row level security;
alter table public.cursos_enrollment_progress force  row level security;

create policy cursos_progress_select on public.cursos_enrollment_progress
  for select to authenticated using (
    exists (select 1 from public.cursos_enrollments e
            where e.id = enrollment_id and e.company_id = app.company_id()));

grant select on public.cursos_enrollment_progress to authenticated;
grant all on public.cursos_enrollment_progress to service_role;

-- ---------------------------------------------------------------------------
-- cursos_payments — pagamentos manuais mensais (clone academia_payments).
-- ---------------------------------------------------------------------------
create table public.cursos_payments (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete restrict,
  enrollment_id   uuid        not null references public.cursos_enrollments(id) on delete restrict,
  reference_month date        not null,   -- sempre dia 01 do mês de referência
  paid_at         timestamptz not null default now(),
  amount_cents    integer     not null check (amount_cents >= 0),
  method          text,
  notes           text,
  created_at      timestamptz not null default now(),
  unique (enrollment_id, reference_month)
);

comment on table public.cursos_payments is
  'Pagamentos manuais mensais do tenant cursos (camada 8.20). UNIQUE (enrollment, reference_month) impede duplicidade no mês. SEM cobrança automática (Stripe é #50, futuro).';

create index idx_cursos_payments_company_month on public.cursos_payments (company_id, reference_month desc);

alter table public.cursos_payments enable row level security;
alter table public.cursos_payments force  row level security;

create policy cursos_payments_select on public.cursos_payments for select to authenticated using (company_id = app.company_id());

grant select on public.cursos_payments to authenticated;
grant all on public.cursos_payments to service_role;
