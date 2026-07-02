-- =============================================================================
-- 81_atelie_lembrete_sinal.sql
-- Meada — Onda Ateliê (backlog docs/FEATURES_SUGERIDAS_ATELIE.md #1/#2/#12).
--
-- Três features de OPERAÇÃO/RECEITA do nicho atelie (camada 8.14), sem bloqueador transversal:
--
--   #1 LEMBRETE AUTOMÁTICO DE PROVA/AJUSTE (AtelieFittingReminderJob): cron diário varre as
--      atelie_fittings 'pendente' com due_date = AMANHÃ (America/Sao_Paulo) e dispara mensagem
--      outbound FIXA e defensiva pela Evolution (NÃO passa pela IA — respeita a trava do nicho:
--      nada de prometer prazo/medida). Idempotência por (prova, data): reminded_due_date guarda
--      QUAL due_date já foi lembrada — remarcar a prova pra outra data dispara lembrete novo
--      (espelho do overdue_notified_month da academia, mig 72). Toggle por tenant na config.
--
--   #2 REGISTRO DE SINAL/ENTRADA + GATE NO FECHAMENTO: o ateliê compra tecido/material com o
--      sinal; encomenda aprovada sem sinal é risco de caixa. deposit_cents (valor combinado) +
--      deposit_paid (a equipe marca ao confirmar o Pix — manual até o gateway #50). Com sinal
--      REGISTRADO (deposit_cents > 0) e NÃO PAGO, a transição aprovada→fechada é bloqueada no
--      service → 409 deposit_required (espelho do empty_budget). Sem sinal registrado, o
--      fechamento segue livre (nem todo ateliê cobra sinal). A IA NÃO toca em valor/pagamento
--      (trava preservada — persona só orienta que "a equipe combina o sinal").
--
--   #12 PRAZO DE ENTREGA + ALERTA DE ATRASO: o prazo prometido JÁ EXISTE (estimated_date);
--      o alerta de atraso é derivado no painel (estimated_date < hoje + status não-terminal)
--      — SEM coluna nova. Esta migration não altera nada para o #12 (registrado aqui só pra
--      documentar a onda).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- #1 — toggle do lembrete por tenant (opt-out; default LIGADO) + idempotência por prova/data.
-- ---------------------------------------------------------------------------
alter table public.atelie_config
  add column if not exists fitting_reminder_enabled boolean not null default true;

comment on column public.atelie_config.fitting_reminder_enabled is
  'Se true (default), o AtelieFittingReminderJob envia lembrete WhatsApp na VÉSPERA do due_date de cada prova/ajuste pendente. Ausência de linha de config = ligado.';

alter table public.atelie_fittings
  add column if not exists reminded_due_date date;

comment on column public.atelie_fittings.reminded_due_date is
  'due_date para o qual o lembrete de véspera JÁ foi enviado (idempotência por prova+data — remarcar a prova pra outra data rearma o lembrete). Espelha overdue_notified_month da academia (mig 72).';

-- Varredura do job: provas pendentes por data (parcial — só pendente interessa ao cron).
create index if not exists idx_atl_fitting_due_pending
  on public.atelie_fittings (due_date)
  where status = 'pendente' and due_date is not null;

-- ---------------------------------------------------------------------------
-- #2 — sinal/entrada na proposta (registro manual até o gateway #50).
-- ---------------------------------------------------------------------------
alter table public.atelie_proposals
  add column if not exists deposit_cents integer check (deposit_cents is null or deposit_cents >= 0);
alter table public.atelie_proposals
  add column if not exists deposit_paid boolean not null default false;
alter table public.atelie_proposals
  add column if not exists deposit_paid_at timestamptz;

comment on column public.atelie_proposals.deposit_cents is
  'Valor do SINAL/entrada combinado com o cliente (centavos). NULL/0 = sem sinal (fechamento livre). Com sinal registrado e não pago, aprovada→fechada → 409 deposit_required.';
comment on column public.atelie_proposals.deposit_paid is
  'Sinal marcado como RECEBIDO pela equipe (confirmação manual do Pix até o gateway #50). Gate do fechamento quando deposit_cents > 0.';
comment on column public.atelie_proposals.deposit_paid_at is
  'Quando o sinal foi marcado como recebido (auditoria leve). Zerado se a equipe desmarcar.';
