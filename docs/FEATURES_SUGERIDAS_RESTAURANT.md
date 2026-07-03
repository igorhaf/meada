# Features Sugeridas — Restaurante (reservas)

> Backlog de features avançadas para o nicho **Restaurante (reservas)** (profile_id `restaurant`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Reserva por mensagem livre + tag `<reserva>`:** a IA negocia dia/hora/mesa/nº de pessoas em texto natural e cria a reserva como `pendente`; o `OutboundService` remove a tag antes de enviar ao cliente.
- **Conflito de agenda transacional:** `ReservationRepository.findConflict` (janela half-open `end_at`) re-verificado dentro da transação → 409 `conflict_slot`; se o slot pedido estiver ocupado, a IA oferece alternativa (±30 min ou outra mesa).
- **Cadastro de mesas** (`/dashboard/tables`: capacidade + disponível/indisponível) e **config** (`restaurant_reservation_config`: duração padrão 2h, janela `opens_at`..`closes_at`, buffer fixo 0).
- **Máquina de status hardcoded** (`pendente → confirmada → realizada`; `pendente/confirmada → cancelada`; `confirmada → no_show`) com notificação automática só em **confirmada** e **cancelada**.
- **Reserva manual pelo painel** (sem WhatsApp, não notifica) + agenda por dia filtrável por status.
- **Cache de contexto TTL 15s** (agenda muda rápido), fuso fixo America/Sao_Paulo.

## 🏆 Top 3 quick wins (fazer primeiro)

1. **Lembrete + confirmação automática D-1 com resposta SIM/NÃO que muda o status.** Hoje a reserva nasce `pendente` e depende de alguém confirmar no painel; e não existe nenhum lembrete ("sua reserva é amanhã"). Um scheduler que dispara na véspera perguntando "confirma sua mesa amanhã às 20h? Responda SIM para confirmar ou NÃO para cancelar" — e faz a IA mover automaticamente para `confirmada`/`cancelada` — ataca diretamente o **no-show**, que é a maior perda de receita do restaurante (mesa vazia num sábado à noite = faturamento perdido irrecuperável). Esforço médio (precisa de cron), retorno altíssimo. Esta é a feature que mais vende o produto.

2. **Sinal/pré-pagamento para grupos grandes e datas de pico (anti no-show financeiro).** Cobrar um sinal (ex.: R$ 30/pessoa em reservas de 8+ lugares ou em datas especiais como Dia dos Namorados/Réveillon) elimina a reserva-fantasma que trava a mesa e nunca aparece. Muda o jogo de "torço para o cliente vir" para "o cliente tem skin in the game". Depende do gateway de pagamento (#50), mas destrava receita adiantada + reduz drasticamente furos em datas caras.

3. **Waitlist (lista de espera) quando não há mesa no horário pedido.** Hoje, se o slot está cheio, a IA só oferece ±30 min ou desiste — e o restaurante perde o cliente. Uma fila de espera por dia/horário que **avisa automaticamente pelo WhatsApp quando abre vaga** (por cancelamento ou no_show) recaptura reservas que iriam para o concorrente e enche mesas que ficariam ociosas. Alto valor, esforço P/M — reaproveita a máquina de status e a notificação já existentes.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação D-1 com SIM/NÃO que auto-muda status | Alto | M | Reduz no-show confirmando a reserva na véspera sem trabalho manual | Operação |
| 2 | Waitlist com aviso automático de vaga liberada | Alto | P | Recaptura cliente quando o horário lota; enche mesa de cancelamento | Operação |
| 3 | Scheduler de auto-transição de status (confirmada→realizada; no_show automático) | Alto | P | Agenda se atualiza sozinha; relatórios ficam confiáveis | Operação |
| 4 | Reativação de cliente inativo ("faz 60 dias que não janta conosco") | Alto | P | Traz de volta cliente que sumiu, com histórico de reservas já no banco | Retenção |
| 5 | Sinal/pré-pagamento para grupos e datas de pico | Alto | M | Elimina reserva-fantasma; receita adiantada em data cara | Receita |
| 6 | Campanha em massa segmentada (por frequência/data/nº de pessoas) | Alto | M | Enche noite fraca (terça) disparando oferta a quem só vem sábado | Marketing |
| 7 | Aniversário do cliente com oferta automática | Alto | P | Ocasião de alto ticket; mensagem no dia gera reserva comemorativa | Retenção |
| 8 | Reserva em grupo / mesas combinadas | Alto | M | Aceita festa de 12 juntando 3 mesas — hoje é impossível pela IA | Receita |
| 9 | Bloqueio de datas/feriados e horários especiais | Médio | P | Evita reserva em dia fechado ou evento privado; corrige a agenda | Operação |
| 10 | Pós-reserva NPS/avaliação com pedido de review público | Médio | P | Mede satisfação e capta review no Google que atrai novos clientes | Marketing |
| 11 | Textos de notificação personalizáveis por restaurante | Médio | P | Voz da marca nas mensagens; hoje são fixos | Operação |
| 12 | Página pública/CMS de reservas (link "reserve pelo WhatsApp") | Médio | M | Ponto de captação com identidade própria; QR na mesa/vitrine | Marketing |
| 13 | Política de no-show com registro e restrição de reincidente | Médio | P | Marca quem furou; alerta/bloqueia serial no-show em data cheia | Operação |
| 14 | Multi-unidade (reservar filial certa pela conversa) | Médio | G | Rede com 2+ casas atende tudo num número só, sem confundir agenda | Operação |
| 15 | Upsell proativo na confirmação (menu degustação/vinho/decoração) | Médio | M | Aumenta ticket médio oferecendo add-on no momento da confirmação | IA |
| 16 | Relatórios/dashboard (ocupação por dia/hora, taxa de no-show, mesas ociosas) | Médio | M | Dono decide horário de pico, staff e política com número, não achismo | Operação |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação D-1 com SIM/NÃO que auto-muda status

- **Problema de negócio:** a reserva nasce `pendente` e alguém precisa confirmar no painel; não há nenhum lembrete na véspera. Cliente esquece, mesa fica presa e vazia — no-show é a maior perda direta de receita do restaurante (mesa de sábado à noite não se recupera).
- **Como funciona:** um scheduler (cron) varre reservas `pendente`/`confirmada` para o dia seguinte e dispara via Evolution "Sua mesa está reservada para amanhã, 20h, 4 lugares. Confirma? Responda SIM ou NÃO." A resposta entra pelo webhook; a IA (dentro da trava — nunca inventa horário, só interpreta a intenção) mapeia SIM→`confirmada` e NÃO→`cancelada`, respeitando a máquina de status já existente. Cancelamento libera o slot (e pode disparar a Waitlist, feature #2). No painel, a agenda mostra o selo "confirmada pelo cliente".
- **Dependências:** scheduler/cron (transversal); reaproveita `ReservationNotifier`, a máquina de status e o webhook inbound. Não depende de gateway nem de foto.
- **Métrica de sucesso:** queda da taxa de no-show (reservas `no_show` / total confirmadas) e % de reservas confirmadas pelo cliente.

### 2. Waitlist com aviso automático de vaga liberada

- **Problema de negócio:** quando o horário pedido está cheio, a IA só oferece ±30 min; se não serve, o cliente vai ao concorrente e a mesa que vagar por cancelamento fica ociosa.
- **Como funciona:** nova entidade `restaurant_waitlist` (contato, dia, faixa de horário desejada, nº de pessoas, status aguardando/ofertado/convertido/expirado). Quando a IA não acha slot, oferece "quer entrar na lista de espera para 20h de sábado? Te aviso na hora se abrir." Ao cancelar/expirar uma reserva (inclusive via feature #1), o backend consulta a waitlist compatível (capacidade + janela) e dispara o convite pelo WhatsApp por ordem de chegada; a primeira resposta positiva vira reserva `pendente` (re-verificando conflito na transação). A IA nunca promete mesa — só oferece e captura o aceite.
- **Dependências:** scheduler leve para expirar entradas; reaproveita `findConflict` e o notifier. Sem gateway, sem foto.
- **Métrica de sucesso:** nº de reservas recuperadas via waitlist/mês e redução de mesas ociosas em horário de pico.

### 3. Scheduler de auto-transição de status

- **Problema de negócio:** hoje `confirmada → realizada` e `no_show` só acontecem se alguém clicar; a agenda fica suja e os relatórios (feature #16) ficam sem base confiável.
- **Como funciona:** cron periódico marca como `realizada` reservas `confirmada` cuja janela (`end_at`) já passou e não foram canceladas; opcionalmente marca `no_show` reservas `confirmada` que passaram do horário sem check-in (com uma tolerância configurável). Transições silenciosas (o baseline já não notifica realizada/no_show — coerente: "quem furou não recebe sermão"). Tudo respeitando a máquina de status hardcoded.
- **Dependências:** scheduler/cron. Nenhuma outra.
- **Métrica de sucesso:** % de reservas com status final correto sem toque manual; confiabilidade dos relatórios.

### 4. Reativação de cliente inativo

- **Problema de negócio:** o restaurante já tem o histórico de quem reservou, mas não faz nada com quem sumiu. Trazer um cliente de volta custa muito menos que conquistar um novo.
- **Como funciona:** job periódico identifica contatos com última reserva `realizada` há mais de N dias (config, ex.: 60) e dispara uma mensagem de reengajamento ("Faz um tempo que você não janta com a gente — que tal reservar uma mesa esse fim de semana?"). A conversa cai no fluxo normal da IA, que já sabe negociar reserva. Segmentável por nº de pessoas/frequência para calibrar a oferta.
- **Dependências:** scheduler/cron; opcionalmente amarra com campanha em massa (#6). Sem gateway, sem foto.
- **Métrica de sucesso:** taxa de reativação (contatos inativos que voltam a reservar após a mensagem).

### 5. Sinal/pré-pagamento para grupos e datas de pico

- **Problema de negócio:** reserva grande ou em data cara (Namorados, Réveillon) que fura destrói a noite — a mesa ficou bloqueada e ninguém pagou por ela.
- **Como funciona:** o tenant configura regras (ex.: sinal de R$ X/pessoa para 8+ lugares ou em datas marcadas). Ao confirmar a reserva, a IA envia um link de pagamento; a reserva só passa a `confirmada` após o sinal cair (webhook do gateway). O sinal vira crédito no consumo ou é retido em caso de no-show, conforme política do restaurante. A IA nunca cobra fora das regras nem inventa valor — só entrega o link gerado pelo backend.
- **Dependências:** **gateway de pagamento #50** (bloqueador). Reaproveita a máquina de status (fica `pendente` até o pagamento).
- **Métrica de sucesso:** redução de no-show em datas de pico e receita de sinal antecipada/mês.

## Dependências transversais

- **Gateway de pagamento (#50 — global):** destrava #5 (sinal/pré-pagamento). Também habilita futuras gorjeta antecipada, pacote de degustação pré-pago e cashback. Nada de receita transacional sai do papel sem ele.
- **Scheduler/cron (infra de plataforma):** destrava #1 (lembrete D-1), #2 (expirar waitlist), #3 (auto-transição), #4 (reativação), #7 (aniversário) e os jobs de campanha (#6). É a dependência de maior alavancagem — uma única infra libera metade do backlog de alto ROI.
- **Campanha em massa segmentada (motor de disparo):** base de #6, e reaproveitada por #4 (reativação) e #7 (aniversário). Precisa de segmentação por atributos do contato/histórico de reservas.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** não é crítico para reservas, mas destravaria a Página pública/CMS (#12) com fotos do salão e o cardápio ilustrado. Enquanto ausente, #12 nasce só com texto/URL colada.
- **Página pública/CMS (feature-flag existente):** #12 se pendura na infra de feature flags + CMS já presentes na plataforma; vira ponto de captação para #6/#10 (link de review) e QR de reserva.
