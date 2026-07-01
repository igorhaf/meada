# Features Sugeridas — Academia/studio fitness

> Backlog de features avançadas para o nicho **Academia/studio fitness** (profile_id `academia`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Planos mensais** (nome + valor + descrição, ativo/inativo) que a IA oferece.
- **Aulas semanais recorrentes** com dia/hora/duração/**capacidade** e vaga em tempo real; matrícula ocupa N vagas em N aulas (junction), conflito por capacity validado transacionalmente.
- **Matrícula = assinatura** (`ativa ⇄ suspensa`; ambas → `cancelada`), anti-dupla matrícula por contato, snapshots de plano/aula, notificação de ativa/cancelada via WhatsApp.
- **Pagamento manual** mês a mês (UNIQUE por mês de referência), com último mês pago + meses em aberto; sem cobrança automática nem cálculo de inadimplência.
- **IA acolhedora-motivadora** que matricula via tag `<matricula>`; trava clínica: NUNCA prescreve treino/dieta/avaliação física, não julga, sem promessa de resultado corporal.
- **Configuração** de horário de funcionamento. Aluno não é entidade (histórico via contato do WhatsApp).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Cobrança recorrente automática com lembrete de vencimento (Pix/cartão).** Hoje o pagamento é 100% manual e a inadimplência é invisível até alguém conferir a planilha mental. Numa academia a receita é assinatura pura — cada mensalidade que "some" no esquecimento é receita perdida direta. Um cron que, no dia do vencimento, gera a cobrança e a IA manda o link (Pix/cartão) com lembrete D-3/D0/D+3, mais um painel de inadimplentes, transforma retenção de receita em automático. É o maior ROI do nicho porque ataca a maior fonte de perda (mensalidade não paga = churn silencioso) e depende só de destravar o gateway #50.

**2. Reativação automática de aluno inativo (win-back).** Academia vive de frequência; aluno que parou de aparecer 2-3 semanas é candidato a cancelar e sumir. Uma campanha automática que detecta matrícula ativa sem check-in recente (ou sem renovação/pagamento) e dispara pela IA uma mensagem calorosa de volta ("senti sua falta na aula de funcional, quer remarcar?") recupera receita que já estava saindo pela porta. É esforço médio, valor altíssimo: cada aluno reativado é uma mensalidade salva sem custo de aquisição.

**3. Check-in / frequência do aluno (base de dados de engajamento).** Hoje o sistema não sabe quem realmente aparece — só quem está matriculado. Registrar presença (a IA confirma "cheguei", ou toggle no painel, ou QR na recepção quando foto/leitor liberar) cria o dado que destrava reativação, relatório de frequência por aula, e a conversa proativa da IA. É o alicerce barato de retenção: sem saber quem some, não dá pra reter ninguém.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Cobrança recorrente + lembrete de vencimento (Pix/cartão) via IA | Alto | M | Mensalidade paga sozinha; para de perder receita por esquecimento | Receita |
| 2 | Painel de inadimplência + suspensão automática por atraso | Alto | P | Enxerga quem deve; corta acesso de quem não pagou sem trabalho manual | Receita |
| 3 | Reativação automática de aluno inativo (win-back pela IA) | Alto | M | Recupera aluno prestes a sumir = mensalidade salva sem custo de aquisição | Retenção |
| 4 | Check-in / registro de frequência por aula | Alto | M | Sabe quem aparece; alicerce de reativação e relatório de engajamento | Operação |
| 5 | Confirmação de presença na aula (SIM/NÃO que libera a vaga) | Alto | P | Reduz cadeira vazia; libera vaga de quem não vai pra lista de espera | Operação |
| 6 | Lista de espera automática de aula lotada | Alto | P | Aula cheia vira oportunidade: chama o próximo quando abre vaga | Retenção |
| 7 | Upgrade/downgrade de plano proativo pela IA | Alto | M | Aluno que lota o limite do plano é convidado a subir = ticket maior | Receita |
| 8 | Aula avulsa / day-use pago (drop-in) | Alto | M | Monetiza quem não quer assinar ainda; funil de conversão pra matrícula | Receita |
| 9 | Programa de indicação (traga um amigo → desconto) | Alto | M | Aluno vira canal de aquisição; desconto só quando o amigo matricula | Marketing |
| 10 | Cupom de desconto na matrícula (1º mês, campanha) | Médio | P | Fecha matrícula parada com gatilho de oferta; a IA aplica na conversa | Receita |
| 11 | Campanha em massa segmentada (inativos, aniversariantes, plano X) | Alto | M | Enche turma vazia e reengaja por WhatsApp sem disparo manual | Marketing |
| 12 | Fidelidade por assiduidade (pontos por check-in → recompensa) | Médio | M | Premia quem frequenta = hábito = renovação; retém pelo streak | Retenção |
| 13 | Scheduler de auto-transição de matrícula (fim de vigência, no-pay) | Médio | P | Matrícula muda de estado sozinha (suspende/cancela) sem gestão manual | Operação |
| 14 | Aniversário do aluno (mensagem + brinde/desconto automático) | Médio | P | Toque de relacionamento que a IA dispara sozinha; barato e retém | Retenção |
| 15 | Relatórios/dashboard (MRR, churn, ocupação por aula, faturamento) | Médio | M | Dono enxerga saúde do negócio: receita recorrente, aula ociosa, evasão | Operação |
| 16 | Multi-unidade (aluno escolhe/troca de studio) | Médio | G | Rede com 2+ endereços gerencia tudo num tenant; escala o produto | Operação |

## Detalhamento das prioritárias

### 1. Cobrança recorrente + lembrete de vencimento (Pix/cartão) via IA

- **Problema de negócio:** a mensalidade hoje é registrada à mão mês a mês. Quem não é cobrado ativamente atrasa, e atraso vira churn silencioso — a maior sangria de receita de uma academia por assinatura.
- **Como funciona:** um scheduler (cron) percorre as matrículas `ativa` e, no dia do vencimento (derivado de `start_date` + mês corrente, já existe o cálculo de meses em aberto), gera a cobrança no gateway (#50) e a IA envia o link de pagamento (Pix copia-e-cola / cartão) pelo WhatsApp, com lembretes D-3 / D0 / D+3. O webhook do gateway confirma o pagamento e grava automaticamente em `academia_payments` (respeitando o UNIQUE por mês de referência) — eliminando o registro manual. A IA só entrega link e confirma recebimento; não negocia valor nem parcela (respeita a trava de não inventar preço).
- **Dependências:** gateway de pagamento (#50) + scheduler/cron. Reusa o `academia_payments` e o cálculo de meses em aberto já existentes.
- **Métrica de sucesso:** taxa de inadimplência (mensalidades vencidas não pagas) cai; % de pagamento até o vencimento sobe.

### 2. Painel de inadimplência + suspensão automática por atraso

- **Problema de negócio:** o dono não tem uma lista de quem está devendo; hoje precisa cruzar pagamentos na cabeça. E aluno inadimplente continua ocupando vaga e usando a academia.
- **Como funciona:** uma tela `/dashboard/academia-inadimplencia` lista matrículas ativas com meses em aberto (derivado do que já existe), ordenada por dias de atraso e valor devido. Uma regra opcional configurável (ex.: "suspender após 15 dias de atraso") deixa o scheduler mover a matrícula `ativa → suspensa` automaticamente — reusando a máquina de status existente (suspensa MANTÉM a vaga, coerente com a regra cravada) e notificando o aluno via WhatsApp com o link de regularização.
- **Dependências:** scheduler/cron (para a suspensão automática); a lista pura funciona já hoje. Se casada com o gateway #50, a suspensão pode ser disparada pelo não-pagamento confirmado.
- **Métrica de sucesso:** dias médios de atraso caem; receita recuperada de inadimplentes que regularizaram.

### 3. Reativação automática de aluno inativo (win-back pela IA)

- **Problema de negócio:** aluno que para de frequentar cancela em 30-60 dias. Sem detecção, ele sai sem que ninguém tente segurar — perda de uma assinatura recorrente.
- **Como funciona:** um scheduler detecta matrícula `ativa` com sinal de inatividade (sem check-in recente — depende da feature #4 — ou sem pagamento do mês vigente) e dispara pela IA uma mensagem calorosa-motivadora ("senti sua falta no funcional de terça, quer que eu reserve sua vaga essa semana?"), sempre dentro da trava (sem promessa de resultado, sem prescrição). Pode acoplar um incentivo (cupom da #10 ou aula avulsa cortesia). O painel mostra a fila de reativação e o resultado (respondeu / voltou / cancelou).
- **Dependências:** scheduler/cron; qualidade máxima com a #4 (frequência). Cupom (#10) e campanha (#11) potencializam.
- **Métrica de sucesso:** taxa de churn mensal cai; nº de matrículas reativadas por mês.

### 4. Check-in / registro de frequência por aula

- **Problema de negócio:** o sistema sabe quem está matriculado, mas não quem realmente aparece. Sem esse dado, não há como reter, priorizar reativação, nem mostrar ocupação real.
- **Como funciona:** uma tabela de check-ins (matrícula + aula + data/hora). Três vias de registro: (a) o aluno avisa pela IA ("cheguei"/"vou hoje") e a IA confirma a presença na aula do dia; (b) toggle na recepção pelo painel; (c) QR/scanner na entrada quando upload/leitor for liberado. Alimenta relatório de frequência por aula e por aluno, e serve de gatilho para reativação (#3) e fidelidade por assiduidade (#12).
- **Dependências:** nenhuma dura para as vias (a)/(b); a via QR depende do bloqueio de foto/anexo (SERVICE_ROLE_KEY) ser resolvido. Scheduler ajuda a consolidar frequência semanal.
- **Métrica de sucesso:** % de aulas com presença registrada; frequência média por aluno (proxy de retenção).

### 5. Confirmação de presença na aula (SIM/NÃO que libera a vaga)

- **Problema de negócio:** aula com vagas limitadas perde valor quando o aluno reserva e não vai — cadeira vazia que poderia ser de quem está na lista de espera. Numa aula coletiva com capacity apertado, isso é ocupação (e satisfação) desperdiçada.
- **Como funciona:** na véspera, a IA/scheduler pergunta ao aluno da aula do dia seguinte se ele confirma presença. Resposta livre interpretada pela IA (SIM → mantém; NÃO → libera a vaga transacionalmente e dispara a lista de espera da #6). Tudo dentro do chassi de capacity já existente; a liberação reusa a lógica de contagem de vaga por aula.
- **Dependências:** scheduler/cron; casa naturalmente com a lista de espera (#6). Sem dependência de gateway.
- **Métrica de sucesso:** taxa de ocupação efetiva por aula sobe; nº de vagas recuperadas e reofertadas.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava #1 (cobrança recorrente), #2 (suspensão por não-pagamento confirmado), #8 (aula avulsa paga), e reforça #10 (cupom com pagamento) e #9 (indicação com crédito). É a dependência de maior alavancagem de receita.
- **Scheduler / cron (auto-transição, lembrete, disparo):** destrava #1, #2, #3, #5, #13, #14 e a consolidação de #4/#12. Sem ele, essas features viram ação manual e perdem a maior parte do valor.
- **Campanha em massa segmentada (#11):** é ela própria uma feature, mas também vira infraestrutura para #3 (win-back) e #14 (aniversário) rodarem em lote por segmento.
- **Upload de foto/anexo (SERVICE_ROLE_KEY ausente):** destrava a via QR/scanner do check-in (#4) e futura ficha do aluno com foto. As vias por IA e por painel não dependem disso — dá pra entregar #4 sem esperar o upload.
- **Base de frequência (#4):** é pré-requisito de qualidade para #3 (reativação por inatividade) e #12 (fidelidade por assiduidade) — sem saber quem aparece, esses dois operam só por pagamento, com sinal mais fraco.
