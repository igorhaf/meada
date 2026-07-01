# Features Sugeridas — Nutrição

> Backlog de features avançadas para o nicho **Nutrição** (profile_id `nutri`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Agenda com conflito por profissional** (half-open, transacional): consulta primeira/retorno/avaliação, notificação de confirmar/cancelar. Status `agendado → confirmado → realizado/cancelado/falta`.
- **Pacientes como sub-entidade do contato** (um contato/responsável pode ter N pacientes), com objetivo e restrições em texto livre administrativo (sem número).
- **Planos alimentares** escritos pelo nutricionista (markdown livre), 1 ativo por paciente, versionamento por arquivamento automático.
- **Entrega read-only do plano ativo pela IA** (`<entrega_plano>`): envia o `body` VERBATIM, com barreira de contato (só do próprio contato da conversa). A IA NUNCA monta/edita/calcula plano.
- **Trava de segurança clínica + guarda de transtorno alimentar** na persona e no schema: a IA só agenda e entrega o plano gravado.
- **2 tags** (`<consulta_nutri>` agenda com 2 modos paciente existente/novo; `<entrega_plano>` entrega) + `NutriContextCache` (não injeta o body do plano).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete + confirmação automática de consulta (anti-no-show).** Consultório de nutrição vive de retorno — a consulta faltada é receita perdida e horário morto na agenda. Um scheduler que dispara no WhatsApp "sua consulta é amanhã às 14h, confirma?" (D-1 e D-0) com resposta que muta `agendado → confirmado` reduz falta diretamente e reaproveita o slot vago se o paciente cancelar com antecedência. É o item de maior ROI porque ataca a maior fonte de perda operacional do nicho, roda sobre a agenda que já existe e não toca a trava clínica (é logística, não nutrição).

**2. Régua de retorno / reengajamento de paciente inativo.** Emagrecimento e mudança de hábito só funcionam com continuidade; o paciente que "sumiu" depois da 1ª consulta é a maior evasão de receita recorrente. Uma régua que detecta paciente sem consulta futura há X dias e dispara "faz um tempo que não te vejo, bora retomar o acompanhamento?" traz de volta receita que já estava perdida, com custo marginal zero. Vende retenção pura e se apoia só na tabela de consultas já existente.

**3. Pacote de consultas / acompanhamento pré-pago (assinatura de acompanhamento).** Hoje cada consulta é avulsa; o nutricionista ganha muito mais vendendo o *acompanhamento* (ex.: pacote 4 retornos ou plano mensal). Um pacote com saldo de sessões que decrementa a cada consulta realizada (chassi já provado no perfil estética) trava o paciente no tratamento longo, aumenta o ticket médio e o lifetime value. É o maior salto de receita e o chassi de saldo transacional já existe no monolito para clonar.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática de consulta (D-1/D-0) | Alto | M | Reduz no-show, reaproveita slot vago | Operação |
| 2 | Régua de reengajamento de paciente inativo | Alto | M | Traz de volta receita recorrente perdida | Retenção |
| 3 | Pacote de consultas pré-pago (saldo de sessões) | Alto | M | Aumenta ticket e lifetime value, trava o tratamento longo | Receita |
| 4 | Sinal/pré-pagamento da consulta (anti-furo) | Alto | M | Garante compromisso, reduz falta, adianta caixa | Receita |
| 5 | Scheduler de auto-transição de status | Médio | P | Consulta passada vira "realizada"; libera régua/NPS | Operação |
| 6 | NPS + pós-consulta automático | Alto | P | Feedback, prova social, detecta insatisfação cedo | Retenção |
| 7 | Reagendamento pela IA (paciente remarca sozinho) | Alto | M | Evita cancelamento seco, mantém o paciente na agenda | Operação |
| 8 | Lista de espera / encaixe de horário | Médio | M | Preenche cancelamentos de última hora, zero slot ocioso | Operação |
| 9 | Fila de entrega de plano com prazo (SLA do plano) | Médio | M | Paciente não fica sem plano; puxa o retorno | Retenção |
| 10 | Campanha em massa segmentada (por objetivo/status) | Alto | M | Vende retorno/pacote para audiência filtrada | Marketing |
| 11 | Indicação com recompensa (member-get-member) | Alto | M | Aquisição barata via boca-a-boca do paciente | Marketing |
| 12 | Aniversário / datas do paciente (mimo automático) | Médio | P | Vínculo afetivo, gatilho de reengajamento | Retenção |
| 13 | Página pública / CMS do nutricionista | Médio | M | Captação de lead, vitrine, prova social (CMS já existe) | Marketing |
| 14 | Cupom de desconto (1ª consulta / retorno) | Médio | P | Converte lead frio, reativa inativo | Receita |
| 15 | Multi-unidade / multi-consultório | Médio | G | Escala para clínicas com mais de um endereço | Operação |
| 16 | Qualificação de lead pela IA (triagem de objetivo) | Médio | M | Chega na agenda paciente pré-qualificado | IA |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática de consulta (D-1/D-0)

- **Problema de negócio:** falta em consulta de nutrição é a maior perda operacional — o horário morre e a receita some. O nicho hoje só notifica *quando* alguém muda o status manualmente; não há disparo proativo.
- **Como funciona:** um scheduler (cron interno) varre `nutri_appointments` com consulta em D-1 e no dia; o `NutriAppointmentNotifier` (que já envia via Evolution) dispara "sua consulta com [profissional] é [data/hora]. Confirma? Responda SIM". A resposta do paciente entra pelo webhook; a IA (dentro da trava — é logística, não nutrição) muta `agendado → confirmado` ou, se pedir, aciona o fluxo de cancelamento/reagendamento. Painel: toggle de lembrete e janela (D-1/D-0/ambos) em `/dashboard/nutri-settings`.
- **Dependências:** scheduler/cron (transversal, ainda não existe). Nenhuma dependência de foto ou gateway. Não toca a trava clínica.
- **Métrica de sucesso:** taxa de `falta` sobre confirmadas; % de consultas confirmadas via lembrete; slots recuperados por cancelamento antecipado.

### 2. Régua de reengajamento de paciente inativo

- **Problema de negócio:** o paciente que fez a 1ª consulta e sumiu é a maior evasão de receita recorrente do consultório. Sem gatilho, ninguém o traz de volta.
- **Como funciona:** régua diária (cron) detecta paciente ativo (não arquivado) sem consulta futura e com última realizada há > X dias (configurável por objetivo — emagrecimento talvez 21d, manutenção 45d). Dispara mensagem acolhedora de retomada com CTA de agendar; se responder, cai no fluxo de agendamento normal. Cadência limitada (ex.: 1 toque, depois silêncio para não spammar). Painel: liga/desliga + janela em dias + texto editável.
- **Dependências:** scheduler/cron. Se o pacote (#3) existir, a régua pode oferecer o pacote em vez de consulta avulsa. Não depende de foto/gateway. Persona respeita a trava (não fala de dieta, só convida a retomar o acompanhamento).
- **Métrica de sucesso:** % de inativos reativados (consulta agendada em 14d após o toque); receita recuperada.

### 3. Pacote de consultas pré-pago (saldo de sessões)

- **Problema de negócio:** vender consulta avulsa deixa dinheiro na mesa — o resultado nutricional (e a receita) vive no *acompanhamento* de meses. Sem pacote, o paciente decide retornar toda vez do zero.
- **Como funciona:** clona o chassi de saldo pré-pago já provado no perfil estética (`aesthetic_packages`): `nutri_packages` (total de sessões, `sessions_remaining` materializado, status ativo/esgotado). Ao marcar uma consulta como *realizada* (ou ao agendar, a decidir), decrementa o saldo na MESMA transação (`UPDATE ... WHERE sessions_remaining > 0`, fecha corrida). Cancelar devolve a sessão. A IA pode informar o saldo e agendar consumindo o pacote (nunca inventa preço — `total = sessões × preço do catálogo`, a IA não carrega valor na tag). Pacote nasce `pendente`; só o tenant ativa (a ativação vem junto do pagamento, ver #4). Painel: tela de Pacotes com saldo por paciente.
- **Dependências:** para cobrar de verdade, gateway (#50, global). Sem ele, o pacote funciona como controle de saldo (pagamento externo, o tenant ativa manualmente). Não depende de foto. Trava intacta (é logística de sessões, não plano alimentar).
- **Métrica de sucesso:** % de pacientes em pacote vs avulso; ticket médio; nº de sessões/paciente (retenção).

### 4. Sinal / pré-pagamento da consulta

- **Problema de negócio:** consulta sem compromisso financeiro é a que mais fura. Um sinal (mesmo pequeno) filtra o paciente sério e adianta caixa.
- **Como funciona:** ao confirmar pela IA, gera-se um link de pagamento do sinal; a consulta só passa a `confirmado` quando o sinal é pago (webhook do gateway). Abatido no valor total no dia. Painel: valor do sinal por tipo de consulta em `/dashboard/nutri-settings` + coluna de status de pagamento na agenda.
- **Dependências:** gateway de pagamento (#50, global) — bloqueante para a cobrança real; até lá, "sinal confirmado manualmente" pelo tenant. Não depende de foto. A IA envia o link mas não fecha preço/condição além do configurado (respeita a trava de não inventar valor).
- **Métrica de sucesso:** no-show de consultas com sinal vs sem; antecipação de caixa; % de confirmações que pagam o sinal.

### 5. Scheduler de auto-transição de status

- **Problema de negócio:** hoje a consulta passada não vira "realizada" sozinha — depende do nutricionista marcar. Isso trava relatórios, NPS, régua e consumo de pacote, e polui a agenda com estados velhos.
- **Como funciona:** cron periódico transiciona consultas `confirmado` cuja hora já passou para `realizada` (ou marca candidata a `falta` conforme política do tenant), respeitando as transições hardcoded (`NutriAppointmentStatus`). É a peça que destrava #2, #3, #6 (todas dependem de "realizada" confiável). Painel: política de auto-transição (auto-realizada após N horas / exigir confirmação manual).
- **Dependências:** scheduler/cron (transversal). Nenhuma de foto/gateway. Não toca a trava.
- **Métrica de sucesso:** % de consultas com status atualizado sem ação manual; consistência dos relatórios de realizadas.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava a cobrança real de #3 (pacote), #4 (sinal) e #14 (cupom com valor). Enquanto não existir, essas features rodam em modo "pagamento externo / ativação manual pelo tenant" — o valor de negócio (controle de saldo, filtro de compromisso) já aparece; só a captura do dinheiro fica manual.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** relevante para evoluções futuras do nicho (bioimpedância, antropometria com foto, envio de exame) que estão fora do escopo desta cartela por segurança clínica *e* pelo bloqueador técnico. Nenhuma feature deste backlog depende de foto — são todas texto/logística, de propósito, para não ficarem bloqueadas.
- **Scheduler / cron interno:** peça mais destravante do backlog. Habilita #1 (lembrete), #2 (reengajamento), #5 (auto-transição), #6 (NPS pós-consulta), #9 (SLA do plano) e #12 (aniversário). É o investimento de infra de maior alavancagem: um scheduler entrega meia dúzia de features de retenção/operação.
- **Campanha em massa segmentada (#10):** ao existir, vira o motor de entrega de #11 (indicação), #12 (aniversário) e #14 (cupom) para audiências filtradas por objetivo/status, transformando o CMS/base de pacientes em canal de marketing ativo.
