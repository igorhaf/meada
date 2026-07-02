# Perfil Ateliê (costura sob medida · arte · design) — camada 8.14

Guia operacional do tenant **atelie** (`profile_id='atelie'`). UM perfil serve os TRÊS tipos de
negócio — **ateliê de costura sob medida**, **estúdio de arte** e **estúdio de design** — porque os
três compartilham o mesmo chassi: peça/obra **sob encomenda personalizada** → briefing → orçamento →
aprovação → execução com **provas/ajustes**. O tipo é um CAMPO da proposta (`project_type`), não três
perfis.

É o **19º perfil vertical** (20º contando o generic). CLONA o chassi do **EventosBot** (camada 8.2 —
proposta order-based + itens de orçamento com total materializado + gate de aprovação em 2 fases via
tag que muta o estado de um artefato existente) e inaugura a **sub-entidade de ETAPAS DE PROVA/AJUSTE**
(`atelie_fittings`).

## Telas (sidebar "Ateliê")

| Tela | Rota | O que faz |
|------|------|-----------|
| Artesãos | `/dashboard/atelie-artisans` | CRUD de artesãos/responsáveis (catálogo simples, sem agenda; desativar preferido a excluir). |
| Propostas | `/dashboard/atelie-proposals` | Lista por status + badge de tipo + alertas ("Entrega atrasada", "Sinal pendente"); detalhe com DOIS editores (ORÇAMENTO + PROVAS/AJUSTES) e o registro do SINAL. |
| Configurações | `/dashboard/atelie-settings` | Nome do ateliê + notas + toggle do lembrete de prova/ajuste (sem horário). |

## UM perfil, três tipos (`project_type`)

`project_type` é **hardcoded com parity** (`AtelieProjectType` ↔ `atelie-project-type.ts`,
`AtelieProjectTypeParityTest`): **costura** "Costura sob medida", **arte** "Arte", **design** "Design".
É um campo da proposta (CHECK em `atelie_proposals.project_type`, default `'costura'`), NÃO um perfil
separado. Para arte/design a mesma estrutura de provas serve como marcos de aprovação visual ("esboço
aprovado", "arte final", "entrega"), mas o vocabulário-base é de provas de costura.

## ESCAPADA — Etapas de prova/ajuste (`atelie_fittings`)

Uma peça sob medida tem **PROVAS MARCADAS** ao longo da produção (1ª prova → 2ª prova → ajuste final →
entrega). Cada prova é uma linha de `atelie_fittings`:

- **title** + **description** + **due_date** (NULLABLE — previsão da prova, campo livre, sem conflito).
- **status BINÁRIO** (`pendente`/`realizada`) — uma prova ou aconteceu ou não. Transição **LIVRE**
  entre os 2 (a equipe pode reabrir uma prova já realizada). Ao entrar em `realizada` seta
  `completed_at`; ao voltar a `pendente` zera `completed_at`. Status inválido → 400
  `invalid_fitting_status`.
- **position** (ORDEM explícita inteira) — lida ordenada por `position asc`. O reorder re-materializa
  `position` sequencial 0..N na mesma transação a partir da lista de ids ordenada.

A prova **NÃO entra no total** (≠ itens de orçamento). É gerenciada **SÓ no painel** — não há tag de
IA pra provas (igual o cronograma do eventos). Trava junto com os itens de orçamento a partir de
`fechada` (`itemsLocked` → 409 `proposal_locked`).

> **Diferença pro projetos/eventos:** os marcos de cronograma do eventos eram horários de um dia
> (`start_time`); as etapas de execução do projetos têm 3 estados (pendente/em_andamento/concluida).
> Aqui o status é **BINÁRIO** (pendente/realizada) e o vocabulário é de **prova de costura/ajuste**.

## Funil da proposta (idêntico ao EventosBot)

`AtelieProposalStatus` ↔ `atelie-proposal-status.ts` (`AtelieProposalStatusParityTest`):

```
rascunho → orcada → aprovada → fechada → realizada
   │         │         │          │
   └ cancelada (de qualquer não-terminal) · orcada → recusada
```

- `rascunho` = proposta aberta sem orçamento; `orcada` = aguardando aprovação; `aprovada` = cliente
  aceitou; `fechada` = "contrato" fechado; `realizada` = peça entregue.
- Ir pra `orcada` exige `total_cents > 0` → 400 `empty_budget`.
- **GATE DE SINAL (onda backlog #2):** com sinal REGISTRADO (`deposit_cents > 0`) e não pago,
  `aprovada → fechada` → 409 `deposit_required`. Sem sinal registrado, o fechamento segue livre.
- Transição inválida → 409 `invalid_status_transition`.
- **`itemsLocked` a partir de `fechada`** congela os itens de orçamento **E as provas**.
- Notificam (texto defensivo): **orcada** (com total + tipo de peça), **aprovada**, **fechada**,
  **recusada**. rascunho/realizada/cancelada silenciosos.

## Onda de features do backlog (docs/FEATURES_SUGERIDAS_ATELIE.md #1/#2/#12)

### #1 — Lembrete automático de prova/ajuste (`AtelieFittingReminderJob`)

Cron diário (`atelie.fitting-reminder-cron`, default 9h) varre as provas **pendentes** com
`due_date` = **AMANHÃ** (America/Sao_Paulo), de proposta viva (não-terminal), e envia mensagem
outbound **FIXA e defensiva** pelo canal da proposta (via `AtelieProposalNotifier` — **não passa pela
IA**, respeitando a trava de prazo/medida). Idempotência por **(prova, data)**: `reminded_due_date`
guarda qual due_date já foi lembrado — **remarcar a prova rearma o lembrete** (espelho do
`overdue_notified_month` da academia, mig 72). Sem canal (proposta manual) → marca sem envio.
Toggle por tenant: `atelie_config.fitting_reminder_enabled` (default LIGADO; ausência de linha =
ligado), editável em Configurações. `EVOLUTION_DRY_RUN` honrado em dev (lição Baileys).

### #2 — Sinal/entrada com gate no fechamento (manual até o gateway #50)

A proposta ganha `deposit_cents` (valor combinado) + `deposit_paid` (+ `deposit_paid_at`). A equipe
registra o sinal no detalhe da proposta (PATCH `/api/atelie/proposals/{id}/deposit`) e marca
"recebido" ao confirmar o Pix à mão. Regras: valor negativo ou "pago" sem valor > 0 → 400
`invalid_deposit`; sinal congela junto com os sub-itens a partir de `fechada` (409 `proposal_locked`);
**com sinal registrado e não pago, `aprovada→fechada` → 409 `deposit_required`**. A IA **não toca em
pagamento** (persona reforçada: nunca informa valor/Pix/forma nem confirma recebimento — "a equipe
combina o sinal"). O pagamento online real destrava com o gateway #50.

### #12 — Prazo de entrega prometido + alerta de atraso no painel

O prazo prometido já era o `estimated_date`; o ALERTA é derivado no painel (sem coluna nova):
proposta viva com `estimated_date` < hoje ganha badge **"Entrega atrasada"** na lista e destaque em
vermelho no detalhe (helper `isDeliveryOverdue` em `atelie-types.ts`, comparação yyyy-MM-dd no fuso
local).

## O que a IA faz

- Abre uma **proposta** a partir do briefing (tipo de peça/obra, ocasião, medidas/dimensões
  aproximadas, referência descrita em texto).
- **Captura a aprovação/recusa** quando a proposta já está **orçada** (gate de 2 fases).

## O que a IA NÃO faz

- **NUNCA fecha contrato, preço ou desconto** — quem orça e fecha é a equipe no painel.
- **NUNCA confirma um PRAZO ou MEDIDA/DIMENSÃO** que a equipe não cravou — "vou confirmar prazo e
  medidas com a equipe na primeira prova/avaliação".
- **NUNCA inventa** material, tecido, técnica, acabamento, valor, item de orçamento ou serviço.
- **NUNCA promete resultado estético**, durabilidade ou caimento que dependa de prova presencial.
- **NUNCA gerencia as provas/ajustes pela conversa** — as provas são marcadas e transicionadas pela
  equipe no painel. A IA só abre a proposta e captura a aprovação.
- **NUNCA fala de PAGAMENTO/SINAL** — não informa valor de sinal, chave Pix ou forma de pagamento e
  não confirma recebimento; quem combina o sinal é a equipe, diretamente.

## Tags

**`<proposta_atelie>`** (ABRE a proposta em rascunho — UM modo só; resolve o contato da conversa; total
0, sem itens, sem provas):

```json
{ "project_type": "costura|arte|design", "occasion": "texto|null",
  "estimated_date": "YYYY-MM-DD|null", "briefing": "texto", "artisan_id": "UUID|null", "notes": "texto" }
```
- `project_type` ausente/inválido → `'costura'`. `artisan_id` inválido → ignora o artesão mas abre.

**`<aprovacao_atelie>`** (MUTA o estado — só se a proposta está `'orcada'`):

```json
{ "proposal_id": "UUID", "decisao": "aprovada|recusada" }
```

Ambas têm namespace próprio, distinto de `<proposta_evento>`/`<aprovacao_proposta>` e de TODAS as
outras. O `OutboundService` remove a tag antes de enviar ao cliente.

## O que NÃO existe nesta fase

- **Conflito de agenda/data** (não há agenda — datas das provas são previsões livres).
- **Catálogo de tecidos/materiais/técnicas** pré-cadastrados (orçamento ad-hoc — a equipe digita os
  itens).
- **Tabela de medidas estruturada** do cliente (ombro/busto/cintura como colunas — o briefing é texto
  livre).
- **Contrato e-sign** (o "contrato" é o estado `fechada`); **pagamento ONLINE do sinal/parcelas**
  (Stripe #50 — o REGISTRO manual do sinal já existe, onda backlog #2).
- **Foto/anexo** de referência/croqui/render/arte (bloqueador SERVICE_ROLE_KEY).
- **Confirmação automática da prova pelo cliente** (responder SIM/remarcar muda estado — backlog #6;
  o lembrete de véspera já existe, onda backlog #1); **multi-artesão com agenda/conflito** (catálogo
  simples, atribuição opcional); **reativação de inativo/campanha** (motor de campanha transversal,
  Onda 3).

## Notas técnicas

- Migration `58_atelie.sql` (5 tabelas: artisans, config, proposals, proposal_items, fittings). A
  CHECK de `companies.profile_id` ACRESCENTA `'atelie'` preservando os 19 perfis anteriores.
- Migration `81_atelie_lembrete_sinal.sql` (onda backlog #1/#2/#12): `fitting_reminder_enabled` na
  config, `reminded_due_date` + índice parcial nas fittings, `deposit_cents`/`deposit_paid`/
  `deposit_paid_at` na proposta.
- `atelie_proposals`/`atelie_proposal_items`/`atelie_fittings`: INSERT pelo backend (service_role);
  tenant SELECT/UPDATE.
- `total_cents`/`line_total_cents` MATERIALIZADOS no INSERT/UPDATE (não generated). `estimated_date`/
  `due_date` campos livres (sem conflito de agenda).
- Cliente NÃO é entidade do core — continua o contact; snapshots `customer_name`/`phone` na proposta.
- Contexto da IA via `AtelieContextCache` (Caffeine TTL 20s, keyed por (companyId, contactId) — traz
  artesãos + propostas do contato em aberto; **NÃO injeta as provas**), invalidado em toda mutação.
- Guard `/api/atelie/**` → 403 `forbidden_wrong_profile`. Paleta `orquidea`.
- Tenant de teste: `igorhaf25` (Ateliê Modelo).
