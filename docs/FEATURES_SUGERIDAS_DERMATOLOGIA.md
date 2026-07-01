# Features Sugeridas — Clínica dermatológica

> Backlog de features avançadas para o nicho **Clínica dermatológica** (profile_id `dermatologia`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Agenda por profissional** com conflito por `professional_id` (half-open, transacional), janela de horário (`opens_at`..`closes_at`) e `end_at` materializado. Consulta nasce via IA no WhatsApp (`<consulta_derma>`, 2 modos: paciente existente ou `new_patient`).
- **Pacientes como sub-entidade do contact** (catálogo próprio, snapshots patient_name/phone na consulta).
- **Tipos de atendimento como tabela** (`dermatologia_procedure_types`): cada tipo com sua `duration_minutes` e uma **nota de preparo** (`prep_instructions`) opcional; a consulta snapshota nome + duração.
- **Entrega READ-ONLY da nota de preparo** via `<entrega_preparo>{appointment_id}` (verbatim, com barreira de contato — não vaza preparo de outro paciente).
- **Status FEMININO com parity** (agendada→confirmada→realizada; cancelada/falta); notificação WhatsApp em confirmada (defensiva, sem conteúdo clínico) e cancelada.
- **TRAVA CLÍNICA forte:** a IA nunca diagnostica, nunca avalia lesão/foto, nunca prescreve; guarda de sinais de alarme encaminha à consulta com urgência sem nomear condição.
- **Base de conhecimento (RAG)** disponível sem gate (item "Conhecimento" do nav).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete + confirmação automática de consulta (scheduler).** Dermatologia é um dos nichos com maior taxa de no-show (procedimento eletivo, agenda longa entre marcação e data). Hoje não há scheduler algum: a consulta é confirmada uma vez e some do radar. Um cron que dispara "sua consulta é amanhã às 14h com a Dra. X, confirme com SIM" 24h antes — e envia a **nota de preparo automaticamente** quando o tipo de atendimento exige (jejum de ácido, suspender retinóico, chegar sem maquiagem) — recupera receita que hoje evapora em cadeira vazia e evita procedimento perdido por preparo mal feito. Esforço P (reusa o `EntregaPreparoHandler` e o notifier que já existem). É o maior ROI da lista.

**2. Reativação de paciente inativo / recall de retorno.** Dermatologia vive de recorrência: revisão de nevo anual, retorno pós-procedimento, renovação de botox/preenchimento a cada 4-6 meses, acompanhamento de acne. O paciente some e vai para o concorrente por falta de lembrete. Uma campanha segmentada ("faz 6 meses da sua última aplicação, quer reavaliar?") sobre a base de pacientes com última consulta > N meses reaquece agenda com quem já é cliente — o CAC é praticamente zero. Alto valor, esforço M.

**3. Sinal/pré-pagamento de procedimento para travar o no-show caro.** Consulta simples que fura dói pouco; um horário de laser/botox de 60 min que fura é prejuízo direto. Cobrar um sinal no ato do agendamento (abatido do valor final) muda o comportamento do paciente e protege os slots premium. Depende do gateway (#50), mas é a feature que mais converte agenda em caixa. Alto valor, esforço M (gated por #50).

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática (D-1) com envio do preparo | Alto | P | No-show e procedimento perdido por preparo mal feito | Operação |
| 2 | Recall de retorno / reativação de paciente inativo | Alto | M | Recorrência (revisão de nevo, renovação de botox) que se perde | Retenção |
| 3 | Sinal/pré-pagamento de procedimento (abatido) | Alto | M | No-show em slots caros de laser/aplicação | Receita |
| 4 | Pacote multi-sessão com saldo (laser, limpeza, peeling) | Alto | M | Venda antecipada de tratamento em série; fideliza | Receita |
| 5 | Auto-transição de status por scheduler (agendada→falta / →realizada) | Médio | P | Agenda "suja"; relatório de no-show impreciso | Operação |
| 6 | Dashboard clínico (ocupação, no-show, faturamento por tipo/profissional) | Alto | M | Dono não enxerga onde perde slot nem qual procedimento rende | Operação |
| 7 | Fila de espera / lista de encaixe para horário liberado | Alto | M | Cancelamento vira buraco na agenda em vez de encaixe | Operação |
| 8 | Pós-procedimento: orientação de cuidado + NPS automático | Médio | P | Insatisfação silenciosa e falta de review; reforça cuidado | Retenção |
| 9 | Programa de indicação (paciente traz paciente) com recompensa | Alto | M | Aquisição barata num nicho de ticket alto | Marketing |
| 10 | Cupom / campanha sazonal (mês da mulher, verão/protetor) | Médio | P | Ocupar baixa temporada; ativar base | Marketing |
| 11 | Fidelidade/cashback em procedimentos estéticos | Médio | M | Reter cliente estético que é sensível a preço | Receita |
| 12 | Triagem inteligente de urgência (IA prioriza sinal de alarme) | Alto | M | Lesão suspeita esperando semanas na fila comum | IA |
| 13 | Página pública/CMS da clínica (equipe, procedimentos, agendar) | Médio | M | Presença digital + captação de lead fora do WhatsApp | Marketing |
| 14 | Qualificação de lead novo pela IA (convênio? estética×clínica?) | Médio | M | Secretaria gasta tempo triando; lead frio entope agenda | IA |
| 15 | Agenda antes/depois de procedimento com foto (quando liberar) | Alto | G | Evidência de resultado que vende o próximo tratamento | Marketing |
| 16 | Multi-unidade (rede de clínicas com agenda por unidade) | Médio | G | Rede não consegue operar num tenant só | Operação |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática (D-1) com envio do preparo

- **Problema de negócio:** o no-show é a maior sangria do nicho. Um horário de procedimento que fura raramente é reocupado no mesmo dia, e o paciente que não fez o preparo (suspender ácido/retinóico, jejum, chegar sem maquiagem) chega e a sessão é perdida ou remarcada — dois slots queimados.
- **Como funciona:** um scheduler (cron) varre consultas `confirmada`/`agendada` com data em ~24h e dispara pelo `notifier` uma mensagem defensiva ("Sua consulta é amanhã às 14h com a Dra. X — responda SIM para confirmar"). Se o `procedure_type` tem `prep_instructions`, reaproveita o `EntregaPreparoHandler` (já existe, verbatim, com barreira de contato) e envia a nota junto. A resposta "SIM" do paciente é interpretada pela IA e muta a consulta para `confirmada`; silêncio pode alimentar a fila de espera (feature 7). **Respeita a trava:** texto administrativo, sem conteúdo clínico; o preparo é o que o médico já gravou, não gerado pela IA.
- **Dependências:** scheduler/cron (transversal). Nenhuma de foto ou pagamento.
- **Métrica de sucesso:** queda da taxa de `falta`; % de consultas confirmadas via lembrete; redução de remarcação por preparo incorreto.

### 2. Recall de retorno / reativação de paciente inativo

- **Problema de negócio:** dermatologia é recorrência pura — mapeamento de pintas anual, retorno de acne, renovação de toxina botulínica/preenchimento a cada poucos meses. Sem lembrete, o paciente esquece ou migra. É base quente parada.
- **Como funciona:** relatório/segmento sobre `dermatologia_patients` cruzado com a última consulta `realizada` (por tipo, se quiser diferenciar "renovação de botox 4m" de "revisão anual"). O tenant dispara uma campanha em massa segmentada pelo WhatsApp; a IA conduz a partir da resposta e agenda com `<consulta_derma>`. **Respeita a trava:** convite administrativo ("faz 6 meses da sua última avaliação, quer reagendar?"), sem sugerir conduta clínica.
- **Dependências:** infra de campanha em massa segmentada (transversal); scheduler para automatizar o gatilho por janela de tempo.
- **Métrica de sucesso:** consultas geradas por campanha de recall / base contatada; receita reativada de pacientes inativos > N meses.

### 3. Sinal/pré-pagamento de procedimento (abatido)

- **Problema de negócio:** slots de procedimento estético (laser, aplicação) são caros e longos; furá-los é prejuízo direto e o efeito psicológico do "grátis para desmarcar" incentiva o no-show.
- **Como funciona:** no fechamento do agendamento a IA informa o valor do sinal (do catálogo — nunca inventado pela IA) e gera um link de pagamento; a consulta só é confirmada com sinal pago, e o valor é abatido no atendimento. Política de reembolso/perda do sinal por antecedência de cancelamento fica no painel. **Respeita a trava:** o preço vem do cadastro do tipo de atendimento; a IA não negocia valor.
- **Dependências:** **gateway de pagamento #50** (bloqueia hoje). Exige adicionar `price_cents` ao `procedure_type` (hoje o tipo tem duração e preparo, não preço).
- **Métrica de sucesso:** taxa de no-show em consultas com sinal vs sem; % de agendamentos que pagam sinal; receita antecipada.

### 4. Pacote multi-sessão com saldo (laser, limpeza, peeling)

- **Problema de negócio:** tratamentos dermatológicos são em série (depilação a laser 6-10 sessões, limpeza de pele mensal, peeling em ciclos). Vender sessão avulsa perde a âncora de retenção e o caixa antecipado.
- **Como funciona:** entidade de pacote (espelho do chassi de saldo já provado no nicho **estética** — `aesthetic_packages`: total de sessões, saldo que decrementa transacionalmente no agendamento, devolução ao cancelar, status ativo/esgotado). Cada `<consulta_derma>` de um tipo coberto pode consumir uma sessão do pacote ativo do paciente. **Respeita a trava:** preço do pacote = nº de sessões × preço do tipo, do catálogo; a IA não monta preço.
- **Dependências:** idealmente casada com o gateway #50 (venda do pacote), mas o saldo funciona mesmo com pagamento presencial. Reaproveita padrão já existente no monólito (estética).
- **Métrica de sucesso:** ticket médio; % de pacientes em pacote ativo; sessões consumidas/vendidas; retenção ao longo do ciclo.

### 5. Auto-transição de status por scheduler

- **Problema de negócio:** a agenda fica "suja" — consultas passadas continuam `agendada`/`confirmada`, e o relatório de no-show e de faturamento fica impreciso porque ninguém fecha manualmente todos os status.
- **Como funciona:** cron que, após a janela da consulta + tolerância, transiciona `confirmada` sem check-in para `falta` (ou marca pendência de baixa) e oferece um fecho rápido de `realizada` no painel. Mantém a máquina de status atual (parity FEMININO) — só automatiza a transição temporal. **Respeita a trava:** puramente operacional, sem tocar conteúdo clínico. Cancelamento continua sendo ação humana/painel (nunca IA).
- **Dependências:** scheduler/cron (transversal).
- **Métrica de sucesso:** precisão do relatório de no-show; tempo de secretaria gasto em baixa manual; consistência da agenda.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava sinal/pré-pagamento (3), venda online de pacote (4), fidelidade/cashback com resgate (11). É o maior multiplicador de receita da lista; sem ele essas features ficam presas ao pagamento presencial.
- **Upload de foto/anexo (SERVICE_ROLE_KEY hoje ausente):** destrava a agenda de antes/depois com foto (15) e um futuro registro fotográfico de evolução do tratamento. Enquanto bloqueado, a trava clínica (IA não avalia foto) segue valendo — a liberação é só de armazenamento, não de avaliação.
- **Scheduler/cron:** destrava lembrete D-1 (1), o gatilho automático de recall (2), auto-transição de status (5) e follow-up pós-procedimento (8). É a peça de infra que sozinha viabiliza quatro features de retenção/operação.
- **Campanha em massa segmentada:** destrava recall/reativação (2), cupom sazonal (10) e indicação (9). Feita a infra de segmentar a base + disparar em lote, três features de marketing saem quase de graça.
