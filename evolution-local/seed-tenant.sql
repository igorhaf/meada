-- IMPORTANTE: este é um EXEMPLO de seed para validação E2E.
-- Substitua os placeholders 'COLE_AQUI_*' pelos valores reais do seu ambiente
-- (AUTHENTICATION_API_KEY da Evolution, etc) ANTES de rodar.
-- NÃO commitar com valores reais — segredo não entra no histórico do git.
--
-- Seed do tenant de teste (meada-delta-01) para a validação E2E.
-- Rodar no SQL Editor do Supabase (ou via psql), como service_role/owner.
-- Idempotente: ON CONFLICT DO NOTHING onde aplicável; rode quantas vezes quiser.
--
-- Valores:
--   instance_name   = meada-delta-01   (igual ao da Evolution; chega no webhook)
--   evolution_token = a apikey da Evolution (o backend usa no header apikey do sendText)

-- IDs fixos para facilitar inspeção/limpeza (UUIDs válidos — só hex).
do $$
declare
  v_company  uuid := 'de17a000-0000-0000-0000-000000000001';
  v_instance uuid := 'a1570000-0000-0000-0000-000000000001';
begin
  -- 1) empresa
  insert into companies (id, name, slug)
  values (v_company, 'Meada Delta 01', 'meada-delta-01')
  on conflict (id) do nothing;

  -- 2) instância WhatsApp — instance_name CASA com a Evolution; token = apikey Evolution
  insert into whatsapp_instances (id, company_id, instance_name, evolution_token)
  values (v_instance, v_company, 'meada-delta-01',
          'COLE_AQUI_O_TOKEN_REAL_DA_EVOLUTION')
  on conflict (id) do nothing;

  -- 3) config de IA do tenant (tom + regras + handoff)
  insert into ai_settings (company_id, tone, system_rules, restrictions, handoff_triggers, model_provider)
  values (v_company,
          'Cordial, prestativo e objetivo.',
          'Responda em português do Brasil. Seja breve. Se não souber, ofereça transferir para um atendente.',
          'Não fale de assuntos fora do escopo do negócio. Não invente preços ou horários.',
          'Quando o cliente pedir explicitamente falar com um humano/atendente, ou demonstrar irritação.',
          'gemini')
  on conflict (company_id) do nothing;

  -- 4) um serviço (para o prompt ter conteúdo real)
  insert into services (company_id, name, description, price_cents, active)
  values (v_company, 'Consultoria', 'Sessão de consultoria de 1 hora', 15000, true);

  -- 5) uma FAQ
  insert into faqs (company_id, question, answer, active)
  values (v_company, 'Vocês atendem aos sábados?', 'Sim, das 9h às 13h.', true);

  -- 6) horário de atendimento (segunda, 09:00–18:00)
  insert into business_hours (company_id, weekday, closed, opens_at, closes_at)
  values (v_company, 1, false, '09:00'::time, '18:00'::time)
  on conflict (company_id, weekday, opens_at) do nothing;
end $$;

-- Conferência: deve listar a empresa + a instância com o token.
select c.slug, c.name, w.instance_name, left(w.evolution_token, 12) || '…' as token_prefix
from companies c
join whatsapp_instances w on w.company_id = c.id
where c.slug = 'meada-delta-01';
