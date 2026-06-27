-- =============================================================================
-- 60_fotografia.sql
-- Meada — Camada 8.16 (SM: perfil Fotografia / estúdio · cinema · audiovisual). Tabelas
-- exclusivas do perfil 'fotografia': fotógrafos, pacotes (catálogo), config, sessões/coberturas.
--
-- COMBINA dois chassis + uma escapada:
--   (1) AGENDA da sessão com profissional (fotógrafo) + conflito POR professional_id (half-open,
--       re-verificado na transação; end_at MATERIALIZADO no INSERT — timestamptz+interval não é
--       IMMUTABLE; paralelismo entre fotógrafos).
--   (2) PACOTE escolhido na hora (espelho LEVE do aesthetic_procedures: preço + duração + delivery_days
--       — SEM saldo multi-sessão). A sessão referencia package_id e SNAPSHOTA name+price+duration+
--       delivery_days. A duração da sessão VEM DO PACOTE (snapshot), não de config.
--   (3) ESCAPADA — ENTREGA DO MATERIAL por LINK + PRAZO read-only: delivery_due_date MATERIALIZADA =
--       data da sessão + delivery_days; delivery_link nullable (gravado pelo estúdio DEPOIS da sessão).
--       A IA ENTREGA o link READ-ONLY, VERBATIM, com barreira de contato (espelho EntregaPlanoHandler
--       do nutri, mas o LINK mora NA PRÓPRIA SESSÃO). Status: agendada→confirmada→realizada→entregue
--       (+ cancelada/falta).
--
-- Convenções: RLS enable+force; policies via app.company_id(); grants authenticated + service_role.
-- sessions: INSERT pelo backend (service_role) — IA via handler OU POST manual; tenant SELECT/UPDATE
-- (status + gravar delivery_link). Demais: CRUD do tenant. end_at + delivery_due_date materializados.
-- SNAPSHOTS de pacote/profissional/cliente na sessão. Status feminino (sessão).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'fotografia' (25º contando generic). ESPELHA 55_dermatologia (24) +
-- 'fotografia'. Entra por ÚLTIMO no SCRIPTS de teste.
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos','estetica','comida',
                        'floricultura','pizzaria','adega','escola','atelie','casamento','concessionaria',
                        'lavanderia','dermatologia','fotografia'));

-- ---------------------------------------------------------------------------
-- fotografia_professionals — fotógrafos/operadores (conflito de agenda POR profissional).
-- ---------------------------------------------------------------------------
create table public.fotografia_professionals (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "fotografia social", "vídeo", "ensaio"
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.fotografia_professionals is
  'Fotógrafos do tenant fotografia (camada 8.16). Conflito de agenda da sessão é POR professional_id (paralelismo). delete em uso → 409 professional_in_use.';

create index idx_foto_prof_company_active on public.fotografia_professionals (company_id, active) where active = true;

alter table public.fotografia_professionals enable row level security;
alter table public.fotografia_professionals force  row level security;

create policy foto_prof_select on public.fotografia_professionals for select to authenticated using (company_id = app.company_id());
create policy foto_prof_insert on public.fotografia_professionals for insert to authenticated with check (company_id = app.company_id());
create policy foto_prof_update on public.fotografia_professionals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy foto_prof_delete on public.fotografia_professionals for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.fotografia_professionals to authenticated;
grant all on public.fotografia_professionals to service_role;

-- ---------------------------------------------------------------------------
-- fotografia_packages — catálogo de pacotes (preço + duração + delivery_days). Espelho leve estetica.
-- ---------------------------------------------------------------------------
create table public.fotografia_packages (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  name             text        not null check (length(trim(name)) between 1 and 200),  -- "Ensaio 1h / 30 fotos"
  category         text,        -- "ensaio", "evento", "vídeo" (texto livre)
  duration_minutes integer     not null check (duration_minutes between 15 and 1440),
  price_cents      integer     not null check (price_cents >= 0),
  delivery_days    integer     not null default 0 check (delivery_days >= 0),  -- prazo de entrega após a sessão
  active           boolean     not null default true,
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.fotografia_packages is
  'Pacotes do tenant fotografia (camada 8.16). duração + preço + delivery_days por pacote; a sessão SNAPSHOTA todos no INSERT. Espelho leve aesthetic_procedures SEM saldo. delete em uso → 409 package_in_use.';

create index idx_foto_pkg_company_active on public.fotografia_packages (company_id, active) where active = true;

alter table public.fotografia_packages enable row level security;
alter table public.fotografia_packages force  row level security;

create policy foto_pkg_select on public.fotografia_packages for select to authenticated using (company_id = app.company_id());
create policy foto_pkg_insert on public.fotografia_packages for insert to authenticated with check (company_id = app.company_id());
create policy foto_pkg_update on public.fotografia_packages for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy foto_pkg_delete on public.fotografia_packages for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.fotografia_packages to authenticated;
grant all on public.fotografia_packages to service_role;

-- ---------------------------------------------------------------------------
-- fotografia_config — horário + slot (1:1 com company). SEM duration (vem do pacote).
-- ---------------------------------------------------------------------------
create table public.fotografia_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  opens_at      time        not null default '08:00',
  closes_at     time        not null default '20:00',
  slot_minutes  integer     not null default 30 check (slot_minutes between 5 and 240),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.fotografia_config is
  'Config do tenant fotografia (camada 8.16): horário + slot. 1:1 com company. Ausente → defaults (08:00/20:00/30). SEM duration — vem do pacote (snapshot).';

alter table public.fotografia_config enable row level security;
alter table public.fotografia_config force  row level security;

create policy foto_config_select on public.fotografia_config for select to authenticated using (company_id = app.company_id());
create policy foto_config_insert on public.fotografia_config for insert to authenticated with check (company_id = app.company_id());
create policy foto_config_update on public.fotografia_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.fotografia_config to authenticated;
grant all on public.fotografia_config to service_role;

-- ---------------------------------------------------------------------------
-- fotografia_session_appointments — sessões/coberturas (snapshots; conflito POR profissional;
-- delivery_link + delivery_due_date — a ESCAPADA).
-- ---------------------------------------------------------------------------
create table public.fotografia_session_appointments (
  id                 uuid        primary key default gen_random_uuid(),
  company_id         uuid        not null references public.companies(id) on delete restrict,
  professional_id    uuid        not null references public.fotografia_professionals(id) on delete restrict,
  package_id         uuid        not null references public.fotografia_packages(id) on delete restrict,
  conversation_id    uuid        references public.conversations(id) on delete set null,
  contact_id         uuid        references public.contacts(id) on delete set null,   -- p/ barreira da entrega
  customer_name      text        not null,   -- snapshot
  customer_phone     text,                   -- snapshot
  professional_name  text        not null,   -- snapshot
  package_name       text        not null,   -- snapshot
  price_cents        integer     not null,   -- snapshot
  duration_minutes   integer     not null,   -- snapshot
  delivery_days      integer     not null,   -- snapshot
  start_at           timestamptz not null,
  end_at             timestamptz not null,   -- MATERIALIZADO = start_at + duration_minutes
  delivery_due_date  date        not null,   -- MATERIALIZADO = date(start_at) + delivery_days
  delivery_link      text,        -- URL da galeria/material (gravada DEPOIS pelo estúdio; vazio = nada a entregar)
  status             text        not null default 'agendada' check (status in
                       ('agendada','confirmada','realizada','entregue','cancelada','falta')),
  notes              text,        -- ADMINISTRATIVO
  created_at         timestamptz not null default now(),
  status_updated_at  timestamptz not null default now()
);

comment on table public.fotografia_session_appointments is
  'Sessões do tenant fotografia (camada 8.16). INSERT pelo backend (service_role). Conflito POR professional_id. end_at MATERIALIZADO; delivery_due_date MATERIALIZADA (data da sessão + delivery_days). SNAPSHOTS de pacote/profissional/cliente. delivery_link gravado pelo estúdio DEPOIS; entregue READ-ONLY pela IA (verbatim, barreira de contato). Status feminino.';

create index idx_foto_sess_company_status_start on public.fotografia_session_appointments (company_id, status, start_at);
create index idx_foto_sess_company_prof_active on public.fotografia_session_appointments (company_id, professional_id, start_at)
  where status in ('agendada','confirmada');
create index idx_foto_sess_contact on public.fotografia_session_appointments (contact_id, start_at desc) where contact_id is not null;

alter table public.fotografia_session_appointments enable row level security;
alter table public.fotografia_session_appointments force  row level security;

create policy foto_sess_select on public.fotografia_session_appointments for select to authenticated using (company_id = app.company_id());
create policy foto_sess_update on public.fotografia_session_appointments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.fotografia_session_appointments to authenticated;
grant all on public.fotografia_session_appointments to service_role;
