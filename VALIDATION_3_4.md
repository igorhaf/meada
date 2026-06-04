# Validação manual E2E — Fase 3.4 (pipeline outbound async)

Checklist para validar o fluxo ponta-a-ponta (webhook inbound → IA → envio Evolution)
contra ambientes REAIS, depois que o código async da 3.4 está mergeado. Os testes
automatizados cobrem a lógica (matriz de fluxo + wiring); este documento cobre o que
só a integração real prova: Supabase real, Evolution real, Gemini real.

> A execução manual NÃO bloqueia o fechamento da 3.4 (código + testes verdes fecham).
> O item de RISKS.md "Nome do modelo Gemini vigente" só passa a **Mitigado** após o
> cenário PROCESSED abaixo confirmar o `GEMINI_MODEL` real.

## Pré-requisitos a preparar no momento da execução

| Recurso | O que prover |
|---------|--------------|
| **Supabase** | Projeto descartável. Rodar as migrations `supabase/migrations/01..06` + (em prod NÃO se usa o bootstrap de teste). Connection string + `service_role` key. |
| **Evolution API** | Uma instância conectada a um número WhatsApp. Anotar `instance_name` e `evolution_token`. Configurar o **webhook** da instância apontando para `{backend}/webhook/evolution` (túnel ngrok/cloudflared se o backend é local). |
| **Gemini** | `GEMINI_API_KEY` válida + `GEMINI_MODEL` = nome do modelo flash vigente (confirmar contra `GET /v1beta/models` ou doc atual). |
| **Tenant seed** | Uma `companies` + a `whatsapp_instances` (com o token/nome reais) + uma `ai_settings` (tone/system_rules) + ao menos 1 `services`, 1 `faqs`, 1 `business_hours` para o prompt ter conteúdo. |
| **Número de teste** | Um segundo WhatsApp (ex. celular pessoal) para ENVIAR mensagens ao número conectado à instância. |

## Variáveis de ambiente do backend (STAGE=local ou production)

```
SUPABASE_DB_URL / spring.datasource.url ...   # connection string do Supabase
WEBHOOK_SECRET=...                            # o mesmo configurado no webhook da Evolution
GEMINI_API_KEY=...
GEMINI_MODEL=...                              # ← o nome a CONFIRMAR (RISKS.md)
EVOLUTION_BASE_URL=https://<sua-evolution>    # base da API Evolution self-hosted
```

## Cenários — 1 por outcome reproduzível sem fault injection

Cada cenário: enviar a mensagem do número de teste → observar no WhatsApp + conferir
no banco + conferir o log `outbound outcome=...`.

### 1. PROCESSED (caminho feliz) — caso 6
- **Ação:** enviar "Quais os horários de atendimento?"
- **Esperado WhatsApp:** chega uma resposta da IA com os horários do tenant.
- **Banco:** `messages` tem a inbound (direction=inbound, sender=contact) E a outbound
  (direction=outbound, sender=ai, evolution_message_id preenchido). `conversations.handled_by` = `ai`.
- **Log:** `outbound outcome=PROCESSED company_id=... conversation_id=... tokens_in=... tokens_out=... latency_ms=... needs_human=false`
- **✓ Fecha o RISK do GEMINI_MODEL** se a IA respondeu (o nome do modelo está correto).

### 2. FLIPPED_AI_HANDOFF — caso 1/2
- **Pré:** garantir que `ai_settings.handoff_triggers` contenha algo como "cliente pede
  para falar com humano".
- **Ação:** enviar "Quero falar com um atendente humano."
- **Esperado:** a IA sinaliza handoff. Pode ou não mandar uma resposta-ponte ("já te
  conecto") antes de transferir.
- **Banco:** `conversations.handled_by` = `human`.
- **Log:** `outbound outcome=FLIPPED_AI_HANDOFF ... needs_human=true`

### 3. SKIPPED_NOT_AI (pré-condição) — depende do cenário 2
- **Ação:** logo após o handoff do cenário 2, enviar outra mensagem na MESMA conversa.
- **Esperado WhatsApp:** NENHUMA resposta da IA (a conversa é de humano agora).
- **Banco:** a inbound é persistida; nenhuma nova outbound; `handled_by` continua `human`.
- **Log:** `outbound outcome=SKIPPED_NOT_AI company_id=... conversation_id=...`

### 4. EVOLUTION_CONFIG_ERROR (token errado) — caso 8
- **Pré:** alterar o `evolution_token` do tenant no banco para um valor INVÁLIDO.
- **Ação:** enviar "Oi".
- **Esperado WhatsApp:** NENHUMA resposta chega (o envio falha com 401).
- **Banco:** a inbound persiste; a IA gera resposta mas NÃO há outbound; `handled_by`
  continua `ai` (sem flip — canal quebrado, humano falharia igual).
- **Log:** `outbound outcome=EVOLUTION_CONFIG_ERROR ... reason=evolution_fatal` em nível **ERROR** (deve ser alertável no monitoramento — ver RISKS.md).
- **Pós:** restaurar o token correto.

## Cenários PULADOS (exigem fault injection — fora do escopo manual)

Cobertos pelos testes de integração (`OutboundServiceIntegrationTest`), não reproduzíveis
sem derrubar/atrasar a API real no meio:
- **caso 4/5** — IA transient esgotada / fatal (FLIPPED_AI_EXHAUSTED).
- **caso 7** — envio Evolution transient esgotado (FLIPPED_EVOLUTION_EXHAUSTED).
- **caso 3** — contrato quebrado da IA (FLIPPED_AI_BAD_REPLY) — depende do modelo violar
  o schema, não forçável de forma confiável.
- **caso 9.1/9.2** — phone/credenciais ausentes (correção manual de dados concorrente).

## Verificação de correlação de logs (MDC)

Nos logs do processamento async, confirmar que cada linha `outbound outcome=...` está
correlacionada por `conversation_id`/`company_id` no MDC (mesma conversa = mesmos ids),
e que threads `outbound-N` distintas não vazam MDC entre si (cada outcome com os ids
corretos da sua conversa).
```
