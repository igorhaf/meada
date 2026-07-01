-- =============================================================================
-- 72_academia_inadimplencia.sql
-- Meada — Onda 2 (piloto Academia): régua de inadimplência + lembrete de vencimento.
--
-- Feature de RECEITA/RETENÇÃO (backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md): a mensalidade
-- hoje é registrada à mão e a inadimplência é invisível. Este job (AcademiaInadimplenciaJob)
-- varre matrículas 'ativa', calcula meses em aberto (reusa a lógica do AcademiaPaymentService),
-- envia lembrete de vencimento pelo WhatsApp e — se o tenant ligou a política — SUSPENDE a
-- matrícula após N dias de atraso. A COBRANÇA real (link Pix/cartão) espera o gateway #50; aqui
-- o que entra é o lembrete + a suspensão local, 100% independentes do gateway.
--
-- Decisões cravadas:
--   - Política POR TENANT na academia_config (opt-in explícito: default NÃO suspende).
--   - grace_days: dias de tolerância após o vencimento antes de considerar atraso.
--   - auto_suspend_days: dias de atraso p/ suspensão automática (0/null = nunca suspende).
--   - overdue_notified_month na matrícula: idempotência do lembrete (1 lembrete por mês de
--     referência) — espelha o padrão reminded_24h/reactivated_at dos jobs existentes.
--   - Suspensa MANTÉM a vaga (regra cravada da 7.7); só cancelamento libera. O job só muta
--     status 'ativa'→'suspensa' (transição já válida na máquina de status do nicho).
-- =============================================================================

-- Política de cobrança por tenant (opt-in; ausência de linha = defaults abaixo).
alter table public.academia_config
  add column if not exists billing_reminder_enabled boolean not null default true;
alter table public.academia_config
  add column if not exists grace_days integer not null default 5 check (grace_days >= 0);
alter table public.academia_config
  add column if not exists auto_suspend_days integer check (auto_suspend_days is null or auto_suspend_days >= 0);

comment on column public.academia_config.billing_reminder_enabled is
  'Se true, o job envia lembrete de vencimento pela IA/WhatsApp quando há mês em aberto.';
comment on column public.academia_config.grace_days is
  'Dias de tolerância após o vencimento antes de considerar a mensalidade atrasada.';
comment on column public.academia_config.auto_suspend_days is
  'Dias de atraso para suspensão automática da matrícula. NULL = nunca suspende (só lembra).';

-- Idempotência do lembrete de vencimento: mês de referência já notificado (dia 01).
alter table public.academia_memberships
  add column if not exists overdue_notified_month date;

comment on column public.academia_memberships.overdue_notified_month is
  'Mês de referência (dia 01) do último lembrete de vencimento enviado — evita renotificar o mesmo mês. Espelha reminded_24h/reactivated_at dos jobs de scheduler.';

-- Índice para o job varrer só matrículas ativas por company (barato; já há idx por status).
-- (reusa idx_academia_memberships_company_status existente — sem novo índice.)
