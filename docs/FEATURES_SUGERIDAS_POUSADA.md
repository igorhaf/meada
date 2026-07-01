# Features Sugeridas — Pousada (hospedagem)

> Backlog de features avançadas para o nicho **Pousada (hospedagem)** (profile_id `pousada`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Quartos** (`pousada_rooms`): catálogo com capacidade + diária fixa + descrição, ativo/inativo, proteção de exclusão com reserva.
- **Reservas por intervalo de dias** (`pousada_reservations`): conflito overlap half-open por quarto, `nights`/`total` materializados (diária × noites), snapshots de nome/diária/capacidade, validação `check_out > check_in`, `check_in >= hoje`, `guests <= capacity`.
- **Máquina de status** (6 estados): reservado → confirmado → checked_in → checked_out; cancelado; no_show. Notifica confirmado e cancelado via WhatsApp.
- **IA de reserva por conversa livre** + tag `<reserva_pousada>`: mostra quartos por nº de pessoas/datas, calcula total, reserva; contexto com disponibilidade de 30 dias e histórico do contato.
- **Configurações:** horário de check-in/check-out + política de cancelamento (texto livre repassado pela IA).
- **Reserva manual pelo painel** (sem WhatsApp, não notifica) + lista mensal filtrável por status/quarto.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/pré-pagamento da diária no ato da reserva (garantia contra no-show).** Hoje a reserva nasce sem nenhum comprometimento financeiro — o hóspede some e o quarto fica bloqueado à toa. Cobrar um sinal (ex.: 1 diária ou 30% via Pix) transforma "interesse" em "reserva firme": derruba no-show, melhora fluxo de caixa e cria a base para toda a monetização do nicho. É o maior alavancador de receita e o mais pedido em hospedagem pequena; depende do gateway #50, mas o restante (config de % do sinal, estado `aguardando_sinal`, link na notificação) pode ser preparado antes.

**2. Lembrete automático de check-in D-1 + confirmação de presença.** Um scheduler que, na véspera do check-in, dispara pelo WhatsApp "sua estadia começa amanhã, check-in a partir das Xh — confirma que vem?" reduz no-show, antecipa cancelamentos (liberando o quarto para revenda) e melhora a experiência sem esforço humano. Esforço P (reusa Evolution + config de horário já existentes) e retorno alto e imediato; é o quick win operacional mais barato.

**3. Tarifa sazonal / por data (alta temporada, fim de semana, feriado).** A diária fixa por quarto deixa dinheiro na mesa: réveillon e carnaval valem o triplo de uma terça de baixa. Uma tabela de regras de preço por período (com fallback pra diária base) faz a IA cotar o valor certo automaticamente e aumenta a receita por reserva sem nenhuma venda extra. É pura margem, específico do nicho, e destrava upsell de temporada.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/pré-pagamento via Pix no ato da reserva | Alto | M | Reserva vira compromisso firme; derruba no-show e antecipa caixa | Receita |
| 2 | Lembrete de check-in D-1 + confirmação de presença | Alto | P | Menos no-show; libera quarto pra revenda com antecedência | Operação |
| 3 | Tarifa sazonal/por data (feriado, fim de semana, alta) | Alto | M | Cobra o valor certo em pico; mais receita por reserva | Receita |
| 4 | Auto-transição de status por scheduler (no-show, check-in/out) | Alto | P | Agenda se atualiza sozinha; painel confiável sem trabalho manual | Operação |
| 5 | Serviços/extras cross-sell (café, passeio, late check-out) | Alto | M | Ticket médio maior; IA oferece extras na conversa | Receita |
| 6 | Estadia mínima por período (min nights) | Médio | P | Bloqueia reserva de 1 noite em feriadão; protege ocupação | Operação |
| 7 | Reativação de hóspede inativo (campanha "volte") | Alto | M | Traz de volta quem já hospedou; receita de base existente | Retenção |
| 8 | Pós-estadia: NPS + pedido de avaliação | Médio | P | Mede satisfação, gera prova social, recupera insatisfeito | Retenção |
| 9 | Cupom de desconto (código na conversa) | Médio | P | Fecha reserva em baixa temporada; alavanca campanhas | Receita |
| 10 | Dashboard de ocupação e receita (taxa, RevPAR, no-show) | Alto | M | Enxerga o negócio; decide preço e temporada com dado | Operação |
| 11 | Página pública/CMS da pousada com quartos e reserva | Médio | M | Vitrine própria; capta reserva fora do WhatsApp | Marketing |
| 12 | Indicação de hóspede (traga um amigo, desconto mútuo) | Médio | P | Cresce base a custo zero de mídia | Marketing |
| 13 | Programa de fidelidade por estadias/noites | Médio | M | Recorrência; hóspede volta pra somar noite/benefício | Retenção |
| 14 | Fila de espera para datas lotadas | Médio | P | Não perde a demanda quando lota; revende cancelamento | Operação |
| 15 | Foto do quarto na conversa e na cotação (quando liberar) | Alto | P | Converte mais; hóspede vê o quarto antes de fechar | IA |
| 16 | Integração de calendário (iCal) com Booking/Airbnb | Alto | G | Evita overbooking entre canais; centraliza a agenda | Integração |

## Detalhamento das prioritárias

### 1. Sinal/pré-pagamento via Pix no ato da reserva

- **Problema de negócio:** a reserva não custa nada pro hóspede, então o no-show é livre e o quarto fica bloqueado sem receita. Falta comprometimento financeiro — o maior ralo de dinheiro da pousada pequena.
- **Como funciona:** a config ganha `deposit_type` (nenhum/percentual/1ª diária) + `deposit_percent`. Ao criar a reserva, o total já é conhecido; o backend calcula o valor do sinal e a reserva nasce em novo estado `aguardando_sinal` (antes de `reservado`). A IA, na confirmação, envia o link de pagamento e explica a política; o webhook do gateway confirma o Pix e o handler promove a reserva para `reservado`/`confirmado`, disparando a notificação. Se não pagar em N horas, um scheduler expira e libera o quarto. A IA NUNCA "confirma pagamento" por conta própria — só o webhook do gateway muda o estado (respeita o padrão de gate humano/externo do projeto).
- **Dependências:** gateway de pagamento (pendência #50) + scheduler/cron (feature #4). A modelagem de estado e a UI de política podem ir antes do gateway.
- **Métrica de sucesso:** % de reservas com sinal pago, queda na taxa de no-show, receita antecipada/mês.

### 2. Lembrete de check-in D-1 + confirmação de presença

- **Problema de negócio:** hóspede esquece ou desiste na última hora sem avisar; a pousada só descobre no dia, sem tempo de revender o quarto.
- **Como funciona:** um job diário (America/Sao_Paulo, mesmo fuso já usado nas validações) varre as reservas com `check_in_date = amanhã` em estado `reservado`/`confirmado` e dispara pelo Evolution uma mensagem com quarto, horário de check-in e a pergunta "confirma sua chegada?". A resposta livre do hóspede cai no fluxo normal da IA, que pode confirmar (`confirmado`) ou registrar um cancelamento (liberando o quarto). Reusa o `PousadaContextCache` e o notifier existentes; só acrescenta o scheduler e um flag `checkin_reminder_sent` pra idempotência.
- **Dependências:** scheduler/cron (feature #4). Nenhuma outra.
- **Métrica de sucesso:** taxa de confirmação pós-lembrete, no-show antes/depois, cancelamentos antecipados (>24h) recuperados.

### 3. Tarifa sazonal / por data

- **Problema de negócio:** diária fixa por quarto não captura o valor de feriados, fins de semana e alta temporada — a pousada cobra barato quando poderia cobrar caro, e às vezes caro demais na baixa.
- **Como funciona:** nova tabela `pousada_rate_rules` (por company: intervalo de datas ou dia-da-semana, quarto opcional, multiplicador ou diária absoluta, prioridade). No cálculo do total, o backend resolve a diária de CADA noite pela regra vigente (fallback = `nightly_rate_cents` do quarto) e soma — o `total_cents` deixa de ser `diária × noites` fixo e passa a somar noite a noite. Os snapshots continuam materializados na reserva. A IA recebe no contexto a diária vigente para as datas pedidas e cota certo; NUNCA inventa preço fora das regras (mantém a trava "não promete o que não está cadastrado", agora aplicada a preço). Painel: tela de regras de tarifa.
- **Dependências:** nenhuma dura; só refatorar o cálculo de total pra iterar por noite.
- **Métrica de sucesso:** diária média (ADR) em períodos de pico, receita por reserva antes/depois.

### 4. Auto-transição de status por scheduler

- **Problema de negócio:** nada muda de status sozinho — uma reserva vencida fica "confirmado" pra sempre, o painel mente sobre a ocupação real e o no-show nunca é marcado.
- **Como funciona:** job diário que aplica transições seguras respeitando a máquina hardcoded: reserva `confirmado`/`reservado` cujo `check_in_date` passou sem check-in vira `no_show`; `checked_in` cujo `check_out_date` passou vira `checked_out`. Cada transição segue as regras de notificação já existentes (no_show/checked_out são silenciosos hoje — mantém). É a base operacional que destrava #1 (expirar sinal), #2 (varredura) e #14 (revender quarto liberado).
- **Dependências:** infra de scheduler/cron (transversal). Respeita a `PousadaReservationStatus` sem inventar estado.
- **Métrica de sucesso:** % de reservas em status coerente com a data, redução de intervenção manual de status.

### 5. Serviços/extras cross-sell (café, passeio, late check-out)

- **Problema de negócio:** a receita é só diária × noites; a pousada tem café da manhã, passeios, transfer e late check-out que hoje não são vendidos pelo canal.
- **Como funciona:** catálogo `pousada_extras` (nome, preço, por-noite ou por-estadia, ativo). A IA, ao fechar ou após confirmar a reserva, oferece os extras cadastrados ("quer incluir café da manhã por R$X/dia?") e, no aceite, uma tag `<extra_pousada>` ou campo na tag de reserva adiciona os itens; o total é recalculado no backend (descarta valor da IA, igual ao chassi de pedido dos outros nichos). Snapshots dos extras na reserva. A IA só oferece o que está no catálogo (mantém a trava anti-promessa).
- **Dependências:** nenhuma pra registrar; se o extra for pago à parte, casa com o gateway #50.
- **Métrica de sucesso:** % de reservas com extra, receita incremental de extras/mês, ticket médio.

## Dependências transversais

- **Gateway de pagamento (pendência #50 — global):** destrava #1 (sinal/pré-pagamento), a cobrança dos extras pagos (#5), o pré-pago de fidelidade (#13) e o resgate de cupom com estorno (#9). Sem ele, essas features ficam no modo "registro de intenção" (a pousada cobra fora do app).
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** destrava #15 (foto do quarto na conversa e na cotação) e enriquece a página pública #11. Enquanto não liberar, ambas usam URL colada/placeholder.
- **Scheduler/cron (infra transversal):** é a espinha de #2 (lembrete D-1), #4 (auto-transição), #7 (reativação de inativo), #8 (NPS pós-estadia) e a expiração de sinal do #1. Implementar uma vez habilita todas.
- **Campanha em massa segmentada (infra transversal):** destrava #7 (reativação), #12 (indicação) e as campanhas de baixa temporada com cupom (#9) — reusa a base de contatos/histórico já disponível no `PousadaContextCache`.
- **CMS/feature-flag (camada 9.x já existente):** #11 (página pública da pousada) pode pendurar no chassi de CMS por tenant, gateado por feature flag por nicho — reaproveita infra pronta em vez de construir do zero.
