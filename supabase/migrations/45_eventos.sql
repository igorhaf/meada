-- =============================================================================
-- 45_eventos.sql
-- Meada WhatsApp — Camada 8.2 (SM-P: perfil Eventos / EventosBot). DÉCIMO SEGUNDO perfil vertical
-- real (13º contando generic): casa de festas / buffet / cerimonial / espaço de eventos. Tabelas
-- exclusivas do perfil 'eventos': cerimonialistas, config, propostas (orçamento), itens de
-- orçamento e itens de cronograma.
--
-- EVOLUÇÃO ESTRUTURAL — CLONA o chassi do OFICINA (camada 7.9) + UMA escapada nova:
--   - order-based com itens + total materializado + gate de aprovação em 2 fases (espelho OFICINA:
--     service_orders → event_proposals; os_items → event_proposal_items; <aprovacao_os> →
--     <aprovacao_proposta>). A IA ABRE a proposta e, num turno POSTERIOR (proposta 'orcada'), MUTA
--     o estado pra 'aprovada'/'recusada'.
--   - SEM sub-entidade de cliente (cliente é o contact; snapshots na proposta) — a tag de abertura
--     tem UM modo só (não os 2 modos vehicle_id/new_vehicle do Oficina).
--   - SEM conflito de agenda transacional: a casa de festas faz ~1 evento/data; event_date é CAMPO
--     livre na proposta, não recurso disputado por minuto (igual expected_delivery do Oficina).
--   - NOVIDADE: CRONOGRAMA ORDENADO do dia do evento (event_timeline_items). Primeiro perfil com
--     DOIS tipos de sub-item no mesmo artefato: (1) itens de ORÇAMENTO (entram no total) e (2)
--     marcos de CRONOGRAMA (horário+título, ordenados por start_time, NÃO entram no total).
--
-- Convenções (padrão das migrations 30-44):
--   - RLS enable + force; policies via app.company_id(); grants authenticated + service_role.
--   - event_proposals + itens: INSERT pelo BACKEND (service_role). Tenant SELECT/UPDATE.
--   - total_cents (na proposta) e line_total_cents (no item de orçamento) MATERIALIZADOS no
--     INSERT/UPDATE; NÃO colunas geradas (recálculo cruza linhas — lição end_at das SMs anteriores).
--   - SNAPSHOTS na proposta: customer_name/phone. Mudar o contato depois NÃO altera propostas
--     passadas.
--   - Cliente NÃO é entidade própria (continua o contact). LGPD: notes é administrativo.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- companies.profile_id — aceitar 'eventos' (12º perfil real; 13º contando generic)
-- ---------------------------------------------------------------------------
alter table public.companies drop constraint companies_profile_id_check;
alter table public.companies add constraint companies_profile_id_check
  check (profile_id in ('generic','legal','dental','sushi','restaurant','salon','pousada',
                        'academia','pet','oficina','nutri','barbearia','eventos'));

-- ---------------------------------------------------------------------------
-- event_planners — cerimonialistas/responsáveis (catálogo SIMPLES, sem agenda/conflito)
-- ---------------------------------------------------------------------------
create table public.event_planners (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  specialty   text,        -- "casamentos", "corporativo" (texto livre)
  active      boolean     not null default true,
  notes       text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.event_planners is
  'Cerimonialistas/responsáveis do tenant eventos (camada 8.2). Catálogo simples SEM agenda — atribuição opcional na proposta. Espelho os_mechanics. active=false retira da disponibilidade.';

create index idx_evt_planner_company_active on public.event_planners (company_id, active) where active = true;
create index idx_evt_planner_company_name on public.event_planners (company_id, name);

alter table public.event_planners enable row level security;
alter table public.event_planners force  row level security;

create policy evt_planner_select on public.event_planners for select to authenticated using (company_id = app.company_id());
create policy evt_planner_insert on public.event_planners for insert to authenticated with check (company_id = app.company_id());
create policy evt_planner_update on public.event_planners for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy evt_planner_delete on public.event_planners for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.event_planners to authenticated;
grant all on public.event_planners to service_role;

-- ---------------------------------------------------------------------------
-- event_config — config simples/informativa (1:1 com company; SEM horário/slot — não há agenda)
-- ---------------------------------------------------------------------------
create table public.event_config (
  company_id    uuid        primary key references public.companies(id) on delete cascade,
  business_name text,        -- nome do espaço/buffet (texto livre, nullable)
  notes         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.event_config is
  'Config do tenant eventos (camada 8.2): nome do espaço + notas. 1:1 com company. Ausente → defaults (vazios). SEM horário/slot — a proposta é order-based, não agendada por horário. Espelho leve do os_config.';

alter table public.event_config enable row level security;
alter table public.event_config force  row level security;

create policy evt_config_select on public.event_config for select to authenticated using (company_id = app.company_id());
create policy evt_config_insert on public.event_config for insert to authenticated with check (company_id = app.company_id());
create policy evt_config_update on public.event_config for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, insert, update on public.event_config to authenticated;
grant all on public.event_config to service_role;

-- ---------------------------------------------------------------------------
-- event_proposals — propostas de evento (order-based, total materializado, snapshots)
-- ---------------------------------------------------------------------------
create table public.event_proposals (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  contact_id        uuid        references public.contacts(id) on delete set null,        -- cliente (atalho)
  planner_id        uuid        references public.event_planners(id) on delete set null,  -- opcional
  conversation_id   uuid        references public.conversations(id) on delete set null,
  customer_name     text        not null,   -- snapshot do contact
  customer_phone    text,                   -- snapshot opcional
  event_type        text,                   -- casamento/aniversário/corporativo/outro (texto livre, SEM enum)
  event_date        date,                   -- previsão (campo-data livre, sem slot)
  guest_count       integer      check (guest_count >= 0),
  briefing          text,                   -- descrição do evento desejado
  total_cents       integer     not null default 0,   -- MATERIALIZADO a cada mutação de item de orçamento
  status            text        not null default 'rascunho' check (status in
                      ('rascunho','orcada','aprovada','recusada','fechada','realizada','cancelada')),
  notes             text,
  opened_at         timestamptz not null default now(),
  closed_at         timestamptz,            -- preenchido em terminais (realizada/recusada/cancelada)
  status_updated_at timestamptz not null default now(),
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.event_proposals is
  'Propostas de evento do tenant eventos (camada 8.2). INSERT pelo backend (service_role). total_cents materializado a cada mutação de item de orçamento. Snapshots de cliente. Status com gate de aprovação em 2 fases. event_date é campo livre (SEM conflito de agenda). Espelho service_orders.';

create index idx_evt_prop_company_status_opened on public.event_proposals (company_id, status, opened_at desc);
create index idx_evt_prop_company_planner on public.event_proposals (company_id, planner_id);
create index idx_evt_prop_company_contact on public.event_proposals (company_id, contact_id, opened_at desc);
create index idx_evt_prop_company_date on public.event_proposals (company_id, event_date);

alter table public.event_proposals enable row level security;
alter table public.event_proposals force  row level security;

create policy evt_prop_select on public.event_proposals for select to authenticated using (company_id = app.company_id());
create policy evt_prop_update on public.event_proposals for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.event_proposals to authenticated;
grant all on public.event_proposals to service_role;

-- ---------------------------------------------------------------------------
-- event_proposal_items — itens de ORÇAMENTO (entram no total). line_total materializado.
-- ---------------------------------------------------------------------------
create table public.event_proposal_items (
  id               uuid        primary key default gen_random_uuid(),
  company_id       uuid        not null references public.companies(id) on delete restrict,
  proposal_id      uuid        not null references public.event_proposals(id) on delete cascade,
  description      text        not null check (length(trim(description)) between 1 and 200),
  quantity         integer     not null default 1 check (quantity > 0),
  unit_price_cents integer     not null check (unit_price_cents >= 0),
  line_total_cents integer     not null check (line_total_cents >= 0),   -- = quantity * unit_price (materializado)
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

comment on table public.event_proposal_items is
  'Itens de ORÇAMENTO de uma proposta de evento (camada 8.2). line_total_cents materializado (quantity*unit_price); o total_cents da proposta é recalculado na mesma transação. Espelho os_items (linha de PREÇO — entra no total).';

create index idx_evt_pitem_proposal on public.event_proposal_items (proposal_id);
create index idx_evt_pitem_company on public.event_proposal_items (company_id);

alter table public.event_proposal_items enable row level security;
alter table public.event_proposal_items force  row level security;

create policy evt_pitem_select on public.event_proposal_items for select to authenticated using (company_id = app.company_id());
create policy evt_pitem_update on public.event_proposal_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.event_proposal_items to authenticated;
grant all on public.event_proposal_items to service_role;

-- ---------------------------------------------------------------------------
-- event_timeline_items — marcos de CRONOGRAMA do dia (A ENTIDADE NOVA). NÃO entra no total.
-- Ordenado por start_time na leitura. Espelho de os_items só na FORMA — semântica TOTALMENTE
-- diferente: é TEMPO (roteiro do evento), não PREÇO.
-- ---------------------------------------------------------------------------
create table public.event_timeline_items (
  id           uuid        primary key default gen_random_uuid(),
  company_id   uuid        not null references public.companies(id) on delete restrict,
  proposal_id  uuid        not null references public.event_proposals(id) on delete cascade,
  start_time   time        not null,   -- horário do marco no dia (ex.: '19:00')
  title        text        not null check (length(trim(title)) between 1 and 200),
  description  text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

comment on table public.event_timeline_items is
  'Marcos de CRONOGRAMA do dia do evento (camada 8.2). A ESCAPADA da SM: roteiro ORDENADO por start_time (ex.: 19:00 recepção / 20:00 cerimônia / 23:00 festa). NÃO entra no total_cents (≠ event_proposal_items). É o "dia do evento" organizacional.';

create index idx_evt_timeline_proposal_time on public.event_timeline_items (proposal_id, start_time);
create index idx_evt_timeline_company on public.event_timeline_items (company_id);

alter table public.event_timeline_items enable row level security;
alter table public.event_timeline_items force  row level security;

create policy evt_timeline_select on public.event_timeline_items for select to authenticated using (company_id = app.company_id());
create policy evt_timeline_update on public.event_timeline_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.event_timeline_items to authenticated;
grant all on public.event_timeline_items to service_role;
