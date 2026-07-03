-- =============================================================================
-- 86_concessionaria_onda1.sql
-- Meada — Onda Concessionária (backlog docs/FEATURES_SUGERIDAS_CONCESSIONARIA.md #1/#2/#3/#9/#10).
--
-- Fecha as features executáveis do nicho sem bloqueador transversal (reserva com sinal espera o
-- gateway #50; campanha/NPS/indicação esperam o motor da Onda 3; simulação de financiamento é
-- integração externa; galeria real espera upload; multi-loja é fase própria):
--
--   #1 LISTA DE DESEJOS + ALERTA DE ESTOQUE (concessionaria_wishlists — o maior ROI do nicho):
--      cliente que não achou o carro registra o interesse (marca/modelo/teto de preço/ano mínimo)
--      pela conversa (tag <desejo_carro>) ou pelo painel. Quando um veículo DISPONÍVEL entra/volta
--      ao estoque e CASA com o desejo, o cliente é avisado automaticamente (texto fixo, não passa
--      pela IA). ONE-SHOT: ao notificar, o desejo é desativado (notified_at + notified_vehicle_id)
--      — o cliente pode registrar de novo.
--   #2 REATIVAÇÃO DE LEAD PARADO (followup_sent_at + ConcessionariaLeadFollowupJob): lead 'novo'/
--      'em_negociacao' sem movimento há N dias (config) recebe follow-up gentil 1x por janela de
--      inatividade (re-arma quando o status muda depois do último follow-up).
--   #3 LEMBRETE + CONFIRMAÇÃO DE TEST-DRIVE (reminded_24h + tag <confirmacao_testdrive>): lembrete
--      nas 24h anteriores ("confirma? SIM ou CANCELAR"); a resposta muda o status pela tag
--      (barreira de contato; confirmar só de 'agendado'; cancelar de agendado/confirmado).
--   #9 AUTO-TRANSIÇÃO (ConcessionariaAutoTransitionJob): test-drive 'confirmado' com end_at
--      passado (2h de graça) vira 'realizado' (silencioso).
--   #10 DASHBOARD DE FUNIL (sem DDL): funil de leads, conversão, desempenho por vendedor,
--      vendas por mês (status_updated_at do 'vendido') e test-drives realizados.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #1 — concessionaria_wishlists: o "carro dos sonhos" que ainda não está no estoque.
-- ---------------------------------------------------------------------------
create table public.concessionaria_wishlists (
  id                  uuid        primary key default gen_random_uuid(),
  company_id          uuid        not null references public.companies(id) on delete cascade,
  contact_id          uuid        not null references public.contacts(id) on delete cascade,
  conversation_id     uuid        references public.conversations(id) on delete set null,
  brand               text,        -- critério (texto livre; casa por ILIKE)
  model               text,        -- critério (texto livre; casa por ILIKE)
  max_price_cents     integer     check (max_price_cents is null or max_price_cents > 0),
  min_year            integer     check (min_year is null or min_year >= 1950),
  notes               text,
  active              boolean     not null default true,
  notified_at         timestamptz,   -- quando o alerta foi disparado (one-shot: active vira false)
  notified_vehicle_id uuid        references public.concessionaria_vehicles(id) on delete set null,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  check (brand is not null or model is not null)   -- pelo menos um critério de identificação
);

comment on table public.concessionaria_wishlists is
  'Lista de desejos do tenant concessionaria (onda 1, backlog #1). Cliente registra o carro que procura (tag <desejo_carro> na conversa ou painel); quando um veículo DISPONÍVEL entra/volta ao estoque e casa (brand/model ILIKE + preço <= teto + ano >= mínimo), o contato é avisado automaticamente (texto fixo). ONE-SHOT: notificar desativa o desejo (notified_at + notified_vehicle_id).';

create index idx_conc_wishlist_company_active on public.concessionaria_wishlists (company_id, active) where active = true;
create index idx_conc_wishlist_contact on public.concessionaria_wishlists (company_id, contact_id);

alter table public.concessionaria_wishlists enable row level security;
alter table public.concessionaria_wishlists force  row level security;

create policy conc_wishlist_select on public.concessionaria_wishlists for select to authenticated using (company_id = app.company_id());
create policy conc_wishlist_update on public.concessionaria_wishlists for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.concessionaria_wishlists to authenticated;
grant all on public.concessionaria_wishlists to service_role;

-- ---------------------------------------------------------------------------
-- #2 — follow-up de lead parado (idempotência re-armável por movimento de status).
-- ---------------------------------------------------------------------------
alter table public.concessionaria_leads
  add column followup_sent_at timestamptz;

comment on column public.concessionaria_leads.followup_sent_at is
  'Último follow-up automático enviado (onda 1, backlog #2). Re-arma quando status_updated_at > followup_sent_at (o lead voltou a se mover e estagnou de novo).';

-- ---------------------------------------------------------------------------
-- #3 — lembrete de test-drive (idempotência simples: 1 lembrete por test-drive).
-- ---------------------------------------------------------------------------
alter table public.concessionaria_test_drives
  add column reminded_24h boolean not null default false;

comment on column public.concessionaria_test_drives.reminded_24h is
  'Lembrete de véspera enviado (onda 1, backlog #3 — "confirma? SIM ou CANCELAR"). A resposta muda o status pela tag <confirmacao_testdrive>.';

create index idx_conc_td_reminder on public.concessionaria_test_drives (start_at)
  where status = 'agendado' and reminded_24h = false;

-- ---------------------------------------------------------------------------
-- #2/#3/#9 — toggles por tenant na config (opt-out; defaults LIGADOS).
-- ---------------------------------------------------------------------------
alter table public.concessionaria_config
  add column followup_enabled           boolean not null default true,
  add column followup_days              integer not null default 3 check (followup_days >= 1),
  add column testdrive_reminder_enabled boolean not null default true,
  add column auto_complete_enabled      boolean not null default true;

comment on column public.concessionaria_config.followup_enabled is
  'Se true (default), lead novo/em_negociacao parado há followup_days recebe follow-up automático (onda 1, backlog #2).';
comment on column public.concessionaria_config.followup_days is
  'Dias de inatividade (status_updated_at) para o follow-up automático do lead.';
comment on column public.concessionaria_config.testdrive_reminder_enabled is
  'Se true (default), test-drive agendado nas próximas 24h recebe lembrete de confirmação (onda 1, backlog #3).';
comment on column public.concessionaria_config.auto_complete_enabled is
  'Se true (default), test-drive confirmado com end_at passado (2h de graça) vira realizado (onda 1, backlog #9).';
