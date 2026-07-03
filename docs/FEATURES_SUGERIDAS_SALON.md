# Features Sugeridas — Salão de beleza

> Backlog de features avançadas para o nicho **Salão de beleza** (profile_id `salon`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Agenda por profissional** com conflito por `professional_id` (paralelismo entre profissionais), slot de 15min, duração por serviço, janela `opens_at`..`closes_at`.
- **Catálogo de serviços** (`salon_offerings`: nome, categoria livre, duração, preço opcional) e **profissionais** (`salon_professionals`), ambos com ativo/inativo.
- **IA que agenda por conversa livre** via tag `<agendamento>`, resolve o contato, oferece serviços + profissionais disponíveis nos próximos 7 dias, com persona acolhedora (nunca recomenda serviço não pedido, nunca opina sobre aparência, sem promessa de resultado).
- **Status hardcoded** `agendado → confirmado → realizado`; `→ cancelado`; `confirmado → falta`. Só confirmado e cancelado notificam o cliente.
- **Snapshots no agendamento** (nome do profissional/serviço, preço, duração) — mudar catálogo depois não altera agendamentos passados.
- **Cliente NÃO é entidade** — histórico vem do `contact` do WhatsApp + agendamentos dele.

## 🏆 Top 3 quick wins (fazer primeiro)

1. **Lembrete e confirmação automática de agendamento (D-1 / D-0).** Salão vive de agenda cheia, e a maior sangria de receita é o **no-show**: cadeira vazia = dinheiro perdido que não volta. Um scheduler que dispara "Seu horário com a Ana é amanhã às 15h, confirma? Responda SIM ou REMARCAR" corta faltas de forma brutal — é o feature de maior ROI do nicho e usa o canal WhatsApp que já está montado. Baixo esforço (cron + template + reaproveita o notifier). Retém cliente porque abre janela de remarcação em vez de perda seca.

2. **Sinal/pré-pagamento no agendamento (gateway #50).** No-show também se combate com **compromisso financeiro**: cobrar um sinal (ex.: 30% ou valor fixo) no ato de marcar reduz falta e traz caixa antecipado. Só emite receita real quando o gateway global #50 existir, mas o registro de "sinal pendente/pago" e a política ("horários acima de R$X pedem sinal") já podem entrar. Vende porque protege a agenda dos serviços caros (progressiva, coloração) que hoje são os mais furados.

3. **Reativação de cliente inativo (win-back automático).** O salão já tem o histórico de cada contato via agendamentos. Detectar quem não volta há N dias (ex.: 45 dias sem agendamento) e disparar campanha "Sentimos sua falta 💇 que tal agendar seu retorno?" recupera receita adormecida a custo quase zero. É o clássico gerador de receita incremental de recorrência — quem corta cabelo tem ciclo previsível, e lembrar no momento certo trava a cliente antes que ela vá pro concorrente.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática (D-1/D-0) via WhatsApp | Alto | P | Reduz no-show; abre janela de remarcação em vez de perda seca | Operação |
| 2 | Sinal/pré-pagamento no agendamento | Alto | M | Compromisso financeiro corta falta em serviço caro; caixa antecipado | Receita |
| 3 | Reativação de cliente inativo (win-back) | Alto | P | Recupera receita adormecida do ciclo de retorno (corte/coloração) | Retenção |
| 4 | Programa de fidelidade por pontos/carimbo digital | Alto | M | "5 cortes = 1 grátis" retém e aumenta frequência | Retenção |
| 5 | Pacote de sessões pré-pago com saldo (ex.: 10 escovas) | Alto | M | Vende à frente, fideliza, garante recorrência do serviço | Receita |
| 6 | Upsell/cross-sell proativo da IA (respeitando a trava) | Alto | P | Sugere combos JÁ oferecidos ("corte + hidratação?") só quando faz sentido | IA |
| 7 | Auto-transição de status + no-show com política | Médio | P | Marca realizado/falta sozinho; aplica regra de reincidente | Operação |
| 8 | Cadastro formal de cliente (perfil + preferências) | Alto | M | Histórico rico, aniversário, alergia a produto, cor favorita | Retenção |
| 9 | Mensagem de aniversário com brinde/desconto | Médio | P | Toque afetivo que traz cliente e gera agendamento datado | Retenção |
| 10 | Pós-atendimento: NPS + pedido de avaliação | Médio | P | Coleta reputação, capta insatisfação antes do boca-a-boca ruim | Marketing |
| 11 | Cupom/desconto (percentual/fixo, validade, uso único) | Médio | M | Campanha de baixa temporada, primeira visita, indicação | Receita |
| 12 | Indicação (member-get-member) com recompensa | Médio | M | Aquisição orgânica barata; cliente vira canal de venda | Marketing |
| 13 | Fila de encaixe/lista de espera para horário lotado | Médio | M | Preenche cancelamento de última hora; não perde demanda | Operação |
| 14 | Comissão por profissional + fechamento | Médio | M | Cálculo automático da comissão; menos atrito com a equipe | Operação |
| 15 | Relatórios/dashboard (receita, top serviço, ocupação, no-show) | Médio | M | Decisão de preço/escala com dado, não achismo | Operação |
| 16 | Página pública/CMS do salão com "agende pelo WhatsApp" | Médio | M | Vitrine + captação; link único de agendamento | Marketing |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática (D-1/D-0)

- **Problema de negócio:** cadeira vazia por falta não avisada é receita que não se recupera; a maior parte das faltas é esquecimento, não desistência.
- **Como funciona:** um scheduler (cron) varre `salon_appointments` com status `agendado`/`confirmado` cujo `start_at` cai em D-1 e D-0 e ainda não receberam o lembrete; dispara pelo `SalonAppointmentNotifier`/Evolution um texto configurável ("Oi {nome}, seu horário com {profissional} é amanhã às {hora}. Confirma? Responda SIM ou REMARCAR"). A resposta SIM cai no fluxo inbound normal e a IA move para `confirmado`; REMARCAR abre negociação de novo horário via a tag `<agendamento>` já existente. Uma coluna `reminder_sent_at` evita duplo envio. Painel: toggle liga/desliga + janela de antecedência em `/dashboard/salon-settings`.
- **Dependências:** scheduler/cron (transversal); nenhuma trava violada (é comunicação operacional, não estética).
- **Métrica de sucesso:** queda na taxa de `falta` sobre agendados; % de lembretes respondidos com SIM.

### 2. Sinal/pré-pagamento no agendamento

- **Problema de negócio:** serviços longos e caros (progressiva, coloração, mechas) são os mais furados; sem custo de falta, a cliente não sente compromisso.
- **Como funciona:** política por serviço/valor em `salon_offerings` (ex.: `requires_deposit`, `deposit_cents` ou percentual). Ao confirmar via IA, o agendamento nasce com `deposit_status='pendente'` e a IA envia o link de pagamento; ao pagar, webhook do gateway marca `pago` e a IA confirma. A IA NUNCA inventa valor — usa só o cadastrado (respeita a trava de não improvisar preço). Painel: coluna de sinal na agenda + relatório de sinais recebidos.
- **Dependências:** **gateway de pagamento #50** (bloqueador de receita real); enquanto isso, o registro de "sinal exigido/pendente" já funciona como sinalização.
- **Métrica de sucesso:** taxa de falta nos serviços com sinal vs. sem; caixa antecipado no mês.

### 3. Reativação de cliente inativo (win-back)

- **Problema de negócio:** cliente de salão tem ciclo previsível (corte ~30-45d); se ninguém lembra, ela experimenta o concorrente e não volta.
- **Como funciona:** query que agrupa contatos por último `salon_appointments.start_at` e seleciona quem passou do limiar (configurável, ex.: 45 dias) sem novo agendamento; campanha em massa segmentada dispara uma mensagem calorosa com CTA de agendar (opcionalmente com cupom da feature #11). A cliente responde e cai no fluxo de agendamento da IA. Painel: `/dashboard/salon-campaigns` com o público estimado e o texto.
- **Dependências:** infra de campanha em massa (transversal); combina com cupom (#11) para virar oferta.
- **Métrica de sucesso:** % de inativos que reagendam após a campanha; receita recuperada.

### 4. Programa de fidelidade por pontos/carimbo digital

- **Problema de negócio:** salão compete por frequência; sem incentivo, a cliente não tem motivo pra concentrar todos os serviços num só lugar.
- **Como funciona:** `salon_loyalty_config` (1:1 com company: pontos por real ou carimbo por visita, meta, recompensa percentual/fixa/serviço grátis). A cada agendamento `realizado`, o backend credita pontos/carimbos ao contato; ao atingir a meta, a IA avisa e aplica o benefício no próximo agendamento. A IA só comunica o saldo — não recomenda serviço (respeita a trava). Painel: tela de fidelidade + saldo por cliente.
- **Dependências:** idealmente cadastro de cliente (#8) para saldo persistente robusto; funciona já sobre o `contact`.
- **Métrica de sucesso:** frequência média de retorno; % de clientes com programa ativo.

### 5. Pacote de sessões pré-pago com saldo

- **Problema de negócio:** receita concentrada em visita avulsa é volátil; pacote antecipa caixa e amarra a cliente por N sessões.
- **Como funciona:** espelha o chassi de pacote da Estética (`aesthetic_packages`) — `salon_packages` (serviço, total de sessões, preço, saldo `sessions_remaining` materializado). A cliente compra (nasce `pendente`, ativa só após pagamento/tenant); cada agendamento pode consumir 1 sessão do saldo, decrementado transacionalmente (UPDATE condicional `sessions_remaining > 0` para fechar corrida). Cancelar agendamento que consumiu devolve a sessão. A IA agenda consumindo o pacote certo; NUNCA inventa preço. Painel: tela de pacotes com saldo.
- **Dependências:** **gateway #50** para cobrança real do pacote; o consumo/saldo já roda sem gateway (tenant marca como pago).
- **Métrica de sucesso:** ticket médio antecipado; taxa de recompra de pacote.

## Dependências transversais

- **Gateway de pagamento #50 (global):** destrava receita real de **sinal (#2)**, **pacote pré-pago (#5)** e cobrança de **cupom com valor mínimo pago (#11)**. Sem ele, essas features rodam em modo "registro/pendente" (o tenant confirma pagamento manualmente).
- **Scheduler/cron:** destrava **lembrete/confirmação (#1)**, **auto-transição + no-show (#7)** e **aniversário (#9)**. É a peça que falta para qualquer disparo temporal automático.
- **Infra de campanha em massa segmentada:** destrava **reativação (#3)**, **cupom em campanha (#11)** e **indicação (#12)** — todas dependem de selecionar um público e disparar via Evolution.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** destrava **portfólio de trabalhos** no CMS (#16), **foto de referência** que a cliente manda ("quero esse corte") e, no futuro, **IA respondendo a foto** — todas ficam bloqueadas até o Storage ser liberado.
- **Cadastro formal de cliente (#8):** fortalece **fidelidade (#4)**, **aniversário (#9)** e **relatórios (#15)**, dando base persistente de preferências e histórico além do `contact` do WhatsApp.
