# Features Sugeridas — Clínica de estética

> Backlog de features avançadas para o nicho **Clínica de estética** (profile_id `estetica`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Agenda por profissional** (conflito por `professional_id`, half-open, re-verificado na transação; paralelismo entre profissionais) + **ficha/evolução textual por sessão** (área tratada, parâmetros do aparelho, observações — administrativo, sem foto).
- **Pacote multi-sessão com saldo que decrementa** (`aesthetic_packages`): compra → pendente → ativo → esgotado/expirado/cancelado; consumo abate 1 sessão na transação; cancelar agendamento **devolve** a sessão.
- **Catálogo de procedimentos** com duração + **preço por sessão** (base do total do pacote); a IA nunca inventa preço.
- **IA no WhatsApp**: identifica cliente pelo telefone, agenda sessões (consome saldo do pacote ativo ou avulso), **captura intenção de compra** de pacote (nasce pendente; a clínica confirma pagamento).
- **Trava estética**: a IA não indica/recomenda procedimento, não opina sobre o corpo, não promete resultado, não confirma pagamento, não discute contraindicação.
- **2 tags** (`<agendamento_estetica>`, `<compra_pacote>`) + status hardcoded com parity (agendamento e pacote), notificações de confirmado/cancelado/ativo.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Confirmação de sessão com resposta SIM/NÃO que muda o status automaticamente (anti no-show).** Estética vive de agenda cheia por profissional: cada falta é uma cadeira ociosa que não volta e, quando o cliente usa pacote, ainda gera atrito de saldo/reagendamento. Um lembrete automático 24h antes ("Sua sessão de [procedimento] é amanhã às [hora] com [profissional]. Confirma? Responda SIM ou NÃO") em que **SIM** move `agendado→confirmado` e **NÃO** aciona o cancelamento (liberando a sessão do pacote automaticamente) reduz falta de forma direta e mensurável. É esforço P (scheduler + parser de resposta), reusa toda a máquina de status e notificação já existente, e ataca a maior perda operacional do nicho.

**2. Pagamento/sinal do pacote no ato (destrava o funil de receita).** Hoje o pacote nasce **pendente** e depende da clínica confirmar pagamento no painel — há um vão entre a IA capturar a intenção e o dinheiro entrar, e muita compra "esfria" nesse intervalo. Um link de pagamento (ou sinal) enviado pela IA na captura da compra, que ao ser pago **ativa o pacote automaticamente** (pendente→ativo) e libera o agendamento que consome saldo, transforma intenção em receita no mesmo minuto. Depende do gateway #50, mas é o maior multiplicador de faturamento do nicho porque o pacote é o produto de maior ticket.

**3. Reativação automática de cliente inativo com pacote esgotado/vencido.** Quem terminou um pacote (esgotado) ou tem um pacote perto de expirar é o lead mais quente que existe: já comprou, já conhece o resultado, já confia na clínica. Uma régua que dispara mensagem segmentada ("Faz X dias da sua última sessão de [procedimento] — quer renovar o pacote?") recupera receita recorrente com custo quase zero. Alto valor, esforço P/M, reusa o histórico de pacotes/agendamentos que já está no banco.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Confirmação de sessão SIM/NÃO que muda status automático (anti no-show) | Alto | P | Reduz falta e cadeira ociosa; libera saldo do pacote no cancelamento | Operação |
| 2 | Lembrete automático de sessão 24h/2h antes (scheduler) | Alto | P | Cliente não esquece; agenda previsível por profissional | Retenção |
| 3 | Reativação de inativo / pacote esgotado ou a vencer (régua) | Alto | P | Recupera cliente que já comprou; renova pacote | Retenção |
| 4 | Auto-transição de status por scheduler (sessão passada → realizada; pacote vencido → expirado) | Alto | P | Painel sempre fiel; saldo/expiração corretos sem trabalho manual | Operação |
| 5 | Pagamento/sinal do pacote no ato + ativação automática | Alto | M | Converte intenção em receita na hora; menos compra esfriada | Receita |
| 6 | Assinatura/recorrência de pacote (renovação mensal automática) | Alto | M | Receita recorrente previsível; retém quem faz procedimento contínuo (ex.: depilação a laser) | Receita |
| 7 | Pesquisa de satisfação / NPS pós-sessão automática | Alto | P | Mede resultado percebido; identifica risco de churn cedo | Retenção |
| 8 | Programa de fidelidade / cashback por sessão realizada | Alto | M | Incentiva volta e compra de novo pacote; diferencial vs concorrência | Receita |
| 9 | Indicação com recompensa (link/código do cliente) | Alto | M | Aquisição barata via cliente satisfeito; vale por sessão convertida | Marketing |
| 10 | Campanha em massa segmentada (por procedimento, pacote esgotado, inativo) | Alto | M | Vende pacote/sessão pra base já existente; ocupa horário vago | Marketing |
| 11 | Lista de espera / preenchimento de horário cancelado | Médio | M | Cancelou? Oferece a vaga a quem esperava — cadeira não fica vazia | Operação |
| 12 | Estoque de insumos/descartáveis com baixa por procedimento | Médio | M | Não falta produto no meio da sessão; controla custo por atendimento | Operação |
| 13 | Comissão por profissional sobre sessão realizada | Médio | M | Fecha o financeiro da equipe; base pra pagar sem planilha | Operação |
| 14 | Cupom de desconto (primeira sessão / pacote) validado no backend | Médio | P | Ferramenta de aquisição e reativação; a IA aplica sem inventar valor | Receita |
| 15 | Lembrete de aniversário com oferta (voucher de procedimento) | Médio | P | Toque de relacionamento que vende; alto engajamento no nicho | Marketing |
| 16 | Página pública / CMS da clínica com catálogo e agendamento | Médio | G | Vitrine própria; capta lead fora do WhatsApp | Marketing |

## Detalhamento das prioritárias

### 1. Confirmação de sessão SIM/NÃO com mudança de status automática

- **Problema de negócio:** a falta (no-show) é a maior perda do nicho — cada sessão vazia é receita perdida numa agenda por profissional que não escala. Com pacote, a falta ainda gera atrito de saldo e reagendamento manual.
- **Como funciona:** um scheduler (cron) varre agendamentos `agendado`/`confirmado` das próximas 24h e dispara via Evolution "Sua sessão de [procedimento] é amanhã às [hora] com [profissional]. Confirma? SIM ou NÃO". A resposta cai no inbound; um handler dedicado (fora da geração livre da IA, pra não depender de interpretação) casa **SIM** → transição `agendado→confirmado` (notificação já existente), **NÃO** → `cancelado` (que já **devolve a sessão ao pacote** pela mecânica atual). Respeita a trava: é confirmação operacional, a IA não opina nem recomenda nada.
- **Dependências:** scheduler/cron (transversal); nada de gateway nem foto.
- **Métrica de sucesso:** taxa de no-show (queda) e % de sessões confirmadas por resposta.

### 2. Lembrete automático de sessão (scheduler)

- **Problema de negócio:** cliente esquece o horário; a clínica descobre pela ausência. Sem lembrete, a agenda por profissional fica imprevisível.
- **Como funciona:** o mesmo scheduler do item 1, com janela configurável (24h e/ou 2h antes) por tenant nas Configurações. Mensagem informativa (sem pedir ação, quando não for o fluxo SIM/NÃO). Reusa `EsteticaAppointment` + notifier existentes.
- **Dependências:** scheduler/cron.
- **Métrica de sucesso:** no-show e reagendamentos de última hora (queda).

### 3. Reativação de inativo / pacote esgotado ou a vencer

- **Problema de negócio:** clientes que terminaram um pacote (esgotado) ou têm pacote perto de `expirado` param de comprar por inércia — receita recorrente evaporando com o lead mais quente possível.
- **Como funciona:** uma régua (cron diário) segmenta contatos por (a) pacote `esgotado` há N dias, (b) pacote `ativo` a vencer em N dias, (c) sem agendamento há N dias. Dispara mensagem de renovação. Ao responder, a IA cai no fluxo já existente de **captura de compra de pacote** (`<compra_pacote>`) — sem inventar preço nem recomendar procedimento (trava intacta). Textos configuráveis no painel.
- **Dependências:** scheduler/cron; idealmente encadeia com o item 5 (pagamento na hora) pra fechar no mesmo turno.
- **Métrica de sucesso:** pacotes renovados por cliente reativado; receita de recompra.

### 4. Auto-transição de status por scheduler

- **Problema de negócio:** hoje `esgotado` é automático, mas `realizado` (sessão que já passou) e `expirado` (pacote vencido) dependem de ação manual — o painel diverge da realidade e o saldo/vencimento fica errado.
- **Como funciona:** cron que (a) move sessões `confirmado` com horário no passado para `realizado` (respeitando janela de tolerância configurável), e (b) move pacotes `ativo` com data de validade vencida para `expirado`. Tudo dentro das transições já válidas da máquina de status; sem tocar nas travas.
- **Dependências:** scheduler/cron; requer um campo de validade no pacote (o estado `expirado` já existe — falta a data que o dispara).
- **Métrica de sucesso:** % de agendamentos/pacotes em estado correto sem intervenção manual.

### 5. Pagamento/sinal do pacote no ato + ativação automática

- **Problema de negócio:** o pacote — produto de maior ticket — nasce `pendente` e espera a clínica confirmar pagamento. Esse vão perde vendas que "esfriam".
- **Como funciona:** na captura de compra (`<compra_pacote>`), o backend gera um link de pagamento (ou sinal parcial) com o **total calculado do catálogo** (a IA não inventa valor — trava respeitada). O webhook do gateway, ao confirmar o pagamento, muda `pendente→ativo` automaticamente (a mesma transição que hoje é manual) e notifica o cliente. Sinal parcial pode ativar com saldo devedor sinalizado no painel.
- **Dependências:** **gateway de pagamento #50** (bloqueador). Destrava também os itens 6, 8 e 14.
- **Métrica de sucesso:** tempo entre captura e ativação (queda); % de pacotes capturados que viram pagos.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava #5 (pagamento/sinal do pacote), #6 (assinatura/recorrência), #8 (cashback com resgate financeiro) e #14 (cupom com valor real). É o maior multiplicador de receita do nicho — o pacote é o produto principal e hoje o dinheiro entra fora do fluxo.
- **Scheduler/cron:** destrava #1, #2, #3, #4, #7 e #15. Vários dos maiores quick wins dependem só disso (nenhum gateway) — é o investimento de infra de menor custo e maior retorno imediato pro nicho.
- **Campanha em massa / disparo segmentado:** base compartilhada de #3, #10 e #15 (fila de envio + segmentação por procedimento/pacote/inatividade + throttling do Evolution).
- **Upload de foto/anexo (bloqueado hoje por SERVICE_ROLE_KEY ausente):** pré-requisito de qualquer feature de **antes/depois** e de ficha clínica com imagem. Enquanto não liberado, a ficha segue textual e nenhuma sugestão acima depende dele. Ao destravar, abre a evolução visual da sessão (com o cuidado de LGPD/dado sensível já sinalizado no nicho) — item de fase futura, fora deste backlog priorizado.
