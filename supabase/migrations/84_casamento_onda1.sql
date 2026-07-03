-- =============================================================================
-- 84_casamento_onda1.sql
-- Meada — Onda Casamento (backlog docs/FEATURES_SUGERIDAS_CASAMENTO.md #1/#2/#3/#4/#10/#14/#15/#16).
--
-- Fecha as features executáveis do nicho sem bloqueador transversal (indicação/NPS/reativação
-- esperam o motor de campanha da Onda 3; cobrança real espera o gateway #50; mood board espera
-- upload; RSVP é esforço G de fase própria):
--
--   #1  SINAL + PARCELAS DO CONTRATO (wedding_payments): o vazamento nº1 do nicho é o intervalo
--       aprovada→fechada (o casal aprova e some). A equipe monta o plano (sinal + N parcelas com
--       vencimento) e marca pago À MÃO (Pix conferido — até o #50). GATE: com plano contendo
--       'sinal' NÃO pago, aprovada→fechada → 409 deposit_required (espelho do gate do ateliê).
--       Parcelas seguem editáveis DEPOIS de fechada (vencem até o casamento) — só recusada/
--       cancelada travam. A IA INFORMA o plano (valor/vencimento/status), nunca inventa condição.
--   #2  LEMBRETES DE CHECKLIST E PARCELA (WeddingReminderJob): cron diário avisa o casal D-3 do
--       due_date de tarefa não concluída e de parcela não paga (texto fixo, não passa pela IA).
--       Idempotência por (linha, data): reminded_due_date — remarcar o prazo rearma o lembrete.
--   #3  CATÁLOGO DE PACOTES + ADICIONAIS (wedding_catalog_items, kind pacote|adicional): autofill
--       do orçamento no painel + a IA APRESENTA pacotes/adicionais COM o preço DO CATÁLOGO (não
--       inventa) e pode sugerir UMA vez um adicional (upsell controlado).
--   #4  AUTO-TRANSIÇÃO (WeddingAutoTransitionJob): 'fechada' com wedding_date passado vira
--       'realizada' (silencioso).  #16 ANIVERSÁRIO DE CASAMENTO: no aniversário (dia/mês) de uma
--       proposta realizada, parabeniza o casal 1x/ano (anniversary_notified_year).
--   #10 CUPOM NA PROPOSTA (wedding_coupons): clone do motor atelie — aplicado PELO PAINEL,
--       desconto materializado e re-derivado a cada mutação de item.
--   #14 RELATÓRIOS: sem DDL (agregações). #15 ALERTA DE DATA OCUPADA: sem DDL (exists derivado
--       na leitura — proposta aprovada/fechada/realizada na MESMA wedding_date de outra).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #3 — wedding_catalog_items: pacotes (Prata/Ouro/Diamante) e adicionais (day-use, cabine...).
-- ---------------------------------------------------------------------------
create table public.wedding_catalog_items (
  id          uuid        primary key default gen_random_uuid(),
  company_id  uuid        not null references public.companies(id) on delete restrict,
  name        text        not null check (length(trim(name)) between 1 and 200),
  kind        text        not null default 'adicional' check (kind in ('pacote','adicional')),
  description text,
  price_cents integer     not null check (price_cents >= 0),
  active      boolean     not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

comment on table public.wedding_catalog_items is
  'Catálogo de PACOTES e ADICIONAIS do tenant casamento (onda 1, backlog #3). AUTOFILL do editor de orçamento (o item da proposta continua snapshot texto) + a IA apresenta preço DO CATÁLOGO e pode sugerir UMA vez um adicional (upsell controlado, backlog #3). active=false sai do autofill/IA; delete livre (sem FK).';

create index idx_wed_catalog_company_active on public.wedding_catalog_items (company_id, active) where active = true;

alter table public.wedding_catalog_items enable row level security;
alter table public.wedding_catalog_items force  row level security;

create policy wed_catalog_select on public.wedding_catalog_items for select to authenticated using (company_id = app.company_id());
create policy wed_catalog_insert on public.wedding_catalog_items for insert to authenticated with check (company_id = app.company_id());
create policy wed_catalog_update on public.wedding_catalog_items for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy wed_catalog_delete on public.wedding_catalog_items for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.wedding_catalog_items to authenticated;
grant all on public.wedding_catalog_items to service_role;

-- ---------------------------------------------------------------------------
-- #10 — wedding_coupons: clone do motor de cupom (sushi 69 / adega 80 / atelie 82 / barbearia 83).
-- ---------------------------------------------------------------------------
create table public.wedding_coupons (
  id              uuid        primary key default gen_random_uuid(),
  company_id      uuid        not null references public.companies(id) on delete cascade,
  code            text        not null check (length(trim(code)) between 1 and 40),
  kind            text        not null check (kind in ('percent','fixed')),
  value           integer     not null check (value >= 0),
  min_order_cents integer     not null default 0 check (min_order_cents >= 0),
  max_uses        integer     check (max_uses is null or max_uses >= 0),
  uses            integer     not null default 0 check (uses >= 0),
  valid_until     date,
  active          boolean     not null default true,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  check (kind <> 'percent' or value between 1 and 100)
);

comment on table public.wedding_coupons is
  'Cupons de desconto do tenant casamento (onda 1, backlog #10 — clone do motor atelie). Aplicado NA PROPOSTA pelo PAINEL (a IA não negocia preço); desconto materializado e re-derivado a cada mutação de item. uses incrementa ao aplicar e decrementa ao remover. code único (case-insensitive) por company. Uso típico: feira de noivas, low-season.';

create unique index uniq_wedding_coupon_code on public.wedding_coupons (company_id, lower(code));
create index idx_wedding_coupons_company_active on public.wedding_coupons (company_id, active) where active = true;

alter table public.wedding_coupons enable row level security;
alter table public.wedding_coupons force  row level security;

create policy wed_coupon_select on public.wedding_coupons for select to authenticated using (company_id = app.company_id());
create policy wed_coupon_insert on public.wedding_coupons for insert to authenticated with check (company_id = app.company_id());
create policy wed_coupon_update on public.wedding_coupons for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());
create policy wed_coupon_delete on public.wedding_coupons for delete to authenticated using (company_id = app.company_id());

grant select, insert, update, delete on public.wedding_coupons to authenticated;
grant all on public.wedding_coupons to service_role;

-- Desconto materializado na proposta (espelho atelie mig 82).
alter table public.wedding_proposals
  add column discount_cents       integer not null default 0 check (discount_cents >= 0),
  add column coupon_id            uuid references public.wedding_coupons(id) on delete set null,
  add column coupon_code_snapshot text,
  add column anniversary_notified_year integer;   -- #16: último ano em que o aniversário foi parabenizado

comment on column public.wedding_proposals.discount_cents is
  'Desconto do cupom aplicado, MATERIALIZADO e re-derivado a cada mutação de item (clampado ao total). Total final = total_cents − discount_cents. Aplicado pelo painel; a IA nunca negocia preço.';
comment on column public.wedding_proposals.anniversary_notified_year is
  'Ano do último parabéns de aniversário de casamento enviado (onda 1, backlog #16) — 1 mensagem por ano, no dia/mês do wedding_date de proposta realizada.';

-- ---------------------------------------------------------------------------
-- #1 — wedding_payments: plano de pagamento do contrato (sinal + parcelas), registro MANUAL até o #50.
-- ---------------------------------------------------------------------------
create table public.wedding_payments (
  id                uuid        primary key default gen_random_uuid(),
  company_id        uuid        not null references public.companies(id) on delete restrict,
  proposal_id       uuid        not null references public.wedding_proposals(id) on delete cascade,
  kind              text        not null default 'parcela' check (kind in ('sinal','parcela')),
  label             text,        -- "Sinal", "Parcela 2/6"... (texto livre, nullable)
  due_date          date        not null,
  amount_cents      integer     not null check (amount_cents > 0),
  paid              boolean     not null default false,
  paid_at           timestamptz,
  reminded_due_date date,        -- idempotência do lembrete D-3 (remarcar o vencimento rearma)
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

comment on table public.wedding_payments is
  'Plano de pagamento do contrato de casamento (onda 1, backlog #1): sinal + N parcelas com vencimento, marcadas PAGAS à mão pela equipe (Pix conferido) até o gateway #50. GATE: proposta com ''sinal'' não pago não fecha (409 deposit_required). Parcelas seguem mutáveis após fechada (vencem até o casamento); só recusada/cancelada travam. A IA INFORMA o plano, nunca inventa condição nem confirma pagamento. Lembrete D-3 pelo WeddingReminderJob (reminded_due_date).';

create index idx_wed_payment_proposal on public.wedding_payments (proposal_id, due_date);
create index idx_wed_payment_due_pending on public.wedding_payments (due_date) where paid = false;

alter table public.wedding_payments enable row level security;
alter table public.wedding_payments force  row level security;

create policy wed_payment_select on public.wedding_payments for select to authenticated using (company_id = app.company_id());
create policy wed_payment_update on public.wedding_payments for update to authenticated using (company_id = app.company_id()) with check (company_id = app.company_id());

grant select, update on public.wedding_payments to authenticated;
grant all on public.wedding_payments to service_role;

-- ---------------------------------------------------------------------------
-- #2 — idempotência do lembrete de checklist (D-3 do due_date; remarcar rearma).
-- ---------------------------------------------------------------------------
alter table public.wedding_checklist_tasks
  add column reminded_due_date date;

comment on column public.wedding_checklist_tasks.reminded_due_date is
  'due_date para o qual o lembrete D-3 JÁ foi enviado (idempotência por tarefa+data — remarcar o prazo rearma). Espelha o reminded_due_date das provas do ateliê (mig 81).';

create index idx_wed_task_due_pending on public.wedding_checklist_tasks (due_date)
  where done = false and due_date is not null;

-- ---------------------------------------------------------------------------
-- #2/#4/#16 — toggles por tenant na config (opt-out; defaults LIGADOS).
-- ---------------------------------------------------------------------------
alter table public.wedding_config
  add column checklist_reminder_enabled boolean not null default true,
  add column payment_reminder_enabled   boolean not null default true,
  add column auto_complete_enabled      boolean not null default true,
  add column anniversary_enabled        boolean not null default true;

comment on column public.wedding_config.checklist_reminder_enabled is
  'Se true (default), o WeddingReminderJob avisa o casal D-3 do prazo de cada tarefa do checklist não concluída.';
comment on column public.wedding_config.payment_reminder_enabled is
  'Se true (default), o WeddingReminderJob avisa o casal D-3 do vencimento de cada parcela não paga.';
comment on column public.wedding_config.auto_complete_enabled is
  'Se true (default), proposta FECHADA com wedding_date passado vira REALIZADA automaticamente (silencioso).';
comment on column public.wedding_config.anniversary_enabled is
  'Se true (default), o casal de proposta REALIZADA recebe parabéns no aniversário de casamento (1x/ano).';
