# Features Sugeridas — Pizzaria

> Backlog de features avançadas para o nicho **Pizzaria** (profile_id `pizzaria`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Cardápio com modifiers:** sabores (salgadas/doces), bordas, bebidas, sobremesas e combos, cada item com grupos de opção (Tamanho, Borda) e delta de preço (`pizzaria_menu_item_options`).
- **Pizza meio-a-meio (escapada estrutural):** N frações por pizza, preço pela **regra do maior valor** (`MAX(preço dos sabores) + Σ deltas`), tudo recalculado no backend (o total da IA é descartado).
- **Carrinho-na-conversa:** a IA monta o pedido em linguagem livre (inclusive meio-a-meio), confirma total + endereço e emite `<pedido_pizza>`.
- **Kanban com gate de aceite humano:** pedido nasce `aguardando`; a pizzaria ACEITA (→ `em_preparo`) ou RECUSA (→ `recusado`); fluxo `em_preparo → saiu_entrega → entregue`. A IA não aceita/recusa.
- **Notificações outbound automáticas por status** (texto fixo ao entrar em cada estado).
- **Config simples:** taxa de entrega flat + pedido mínimo por company.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Cupom / desconto promocional (código + regras).** É o motor de aquisição e de ticket médio que TODA pizzaria de delivery usa ("PIZZA20", "primeira compra grátis a borda", "combo de terça"). O chassi já recalcula o total no backend — basta a IA passar o código na tag `<pedido_pizza>`, o backend valida (ativo, validade, mínimo, usos) e aplica o desconto ANTES de fechar. Retorno imediato: cria motivo pra comprar hoje, reativa cliente parado e dá alavanca de margem controlada (percentual/fixo). Espelha o cupom já existente no perfil sushi, então o esforço é P.

**2. Fidelidade por contagem — "a cada 10 pizzas, 1 grátis".** Programa de selo/carimbo é a arma número 1 de RETENÇÃO em pizzaria de bairro: aumenta frequência de recompra sem custo de mídia. O backend conta os pedidos ENTREGUES do contato e, ao atingir o gatilho, aplica desconto automático (pizza grátis ou % ). Já existe pronto no sushi (`sushi_loyalty_config` + contagem por contato) — clonável quase 1:1. Alto valor, esforço P/M, e casa perfeitamente com o cardápio de recompra alta da pizza.

**3. Upsell/cross-sell proativo da IA (borda recheada + bebida + sobremesa).** A margem da pizzaria mora na borda recheada, no refri de 2L e na sobremesa — itens que o cliente ADICIONA quando alguém oferece. Hoje a IA só monta o que o cliente pede. Ensinando a persona a sugerir UMA vez, no momento da confirmação ("quer borda recheada por +R$10? levar um Guaraná 2L?"), sem ser insistente, o ticket médio sobe sem nenhuma tabela nova — só regra de contexto no `PizzariaMenuCache`/persona. Esforço P, retorno direto no ticket.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Cupom/desconto promocional (código, %, fixo, mínimo, validade, usos) | Alto | P | Motivo pra comprar hoje; recupera inativo; alavanca de margem | Receita |
| 2 | Fidelidade por contagem ("10ª pizza grátis") | Alto | P/M | Aumenta frequência de recompra sem mídia paga | Retenção |
| 3 | Upsell/cross-sell proativo da IA (borda/bebida/sobremesa) | Alto | P | Sobe ticket médio no momento da confirmação | IA |
| 4 | Pagamento online (Pix/cartão) na conversa | Alto | M | Reduz calote/troco; fecha o pedido sem atrito | Receita |
| 5 | Combo do dia / promoção por dia da semana | Alto | P/M | "Terça em dobro", esvazia horário fraco | Receita |
| 6 | Reativação de cliente inativo (campanha segmentada) | Alto | M | Traz de volta quem não pede há X dias | Retenção |
| 7 | Taxa de entrega por bairro/distância | Alto | M | Para de subsidiar entrega longa; margem correta | Operação |
| 8 | Avaliação/nota pós-entrega (NPS + estrelas) | Médio | P/M | Prova social + detecta pizzaria ruim antes do churn | Marketing |
| 9 | Horário de funcionamento + fila/aviso "fechado agora" | Alto | P | Não confirma pedido fora do horário; agenda pra depois | Operação |
| 10 | Pré-agendamento de pedido (pizza pras 20h) | Médio | M | Captura pedido de festa/jantar marcado | Receita |
| 11 | Preço por tamanho POR SABOR (tabela sabor×tamanho) | Médio | M | Precificação real (pizza P/M/G não custa o mesmo delta) | Receita |
| 12 | Múltiplos endereços salvos por cliente | Médio | P | Recompra em 1 toque (casa/trabalho); menos atrito | Retenção |
| 13 | Estoque/86 de sabor esgotado em tempo real | Médio | M | Evita vender o que acabou; some do cardápio da IA | Operação |
| 14 | Painel de relatórios (ticket médio, campeões, horário de pico) | Médio | M | Decisão de cardápio e escala baseada em dado | Operação |
| 15 | Programa de indicação (indique e ganhe desconto) | Médio | M | Aquisição viral por WhatsApp, custo quase zero | Marketing |
| 16 | Foto do sabor no cardápio (quando liberar upload) | Médio | P | Cardápio apetitoso; a IA descreve com imagem | Marketing |

## Detalhamento das prioritárias

### 1. Cupom / desconto promocional

- **Problema de negócio:** hoje não há nenhuma alavanca de conversão nem de reativação. Pizzaria depende de "gatilho de compra" (cupom da primeira compra, cupom de terça, cupom de recuperação). Sem isso, o cliente que abriu a conversa e achou caro simplesmente some.
- **Como funciona:** tabela `pizzaria_coupons` (código, kind percent 1..100 / fixed centavos, min_order, max_uses, valid_until, active, uses) — espelho de `sushi_coupons`. A persona ensina a IA a coletar/aceitar o código e passá-lo na tag `<pedido_pizza>` (`"cupom":"PIZZA20"`). O backend VALIDA (ativo + validade + mínimo + max_uses) e aplica sobre o total recalculado; cupom inválido NÃO aborta (o pedido sai sem o desconto — igual sushi). Tela nova `/dashboard/pizzaria-coupons`. **Não viola trava:** a IA não inventa desconto — só repassa um código que o backend valida.
- **Dependências:** nenhuma (usa o recálculo de total já existente). Combina com #6 (reativação manda o cupom).
- **Métrica de sucesso:** taxa de pedidos com cupom, ticket médio com/sem cupom, taxa de resgate por campanha.

### 2. Fidelidade por contagem

- **Problema de negócio:** aquisição é cara; a rentabilidade da pizzaria vem da FREQUÊNCIA. Cliente que pede toda sexta vale 50× o de uma compra. Falta um mecanismo que premie a recompra.
- **Como funciona:** `pizzaria_loyalty_config` (enabled + threshold_orders + reward percent/fixed), 1:1 com company — espelho de `sushi_loyalty_config`. Antes de inserir o pedido, o backend conta os pedidos ENTREGUES (terminal não-recusado/cancelado) do contato; quando `count>0 && count % threshold == 0`, aplica o desconto automático e marca `loyalty_applied=true`. A IA avisa na conversa ("essa é sua 10ª pizza — vem de graça 🎉"). Tela `/dashboard/pizzaria-loyalty`. Cupom + fidelidade SOMAM, clampados ao subtotal (regra já resolvida no sushi).
- **Dependências:** nenhuma; usa o histórico de pedidos entregues que já existe.
- **Métrica de sucesso:** frequência de recompra (pedidos/cliente/mês), % de clientes que atingem o gatilho, retenção 30/60/90 dias.

### 3. Upsell / cross-sell proativo da IA

- **Problema de negócio:** borda recheada, bebida 2L e sobremesa são os itens de MAIOR margem e os que o cliente esquece de pedir. Hoje a IA é passiva — só monta o que foi pedido.
- **Como funciona:** ajuste na persona `ProfilePromptContext.PIZZARIA` + no bloco de contexto do `PizzariaMenuCache`: instruir a IA a, UMA única vez e no momento da confirmação, oferecer 1-2 complementos coerentes ("quer adicionar borda recheada por +R$10? levar uma bebida gelada?"). Regra explícita de NÃO insistir e NÃO empurrar item caro sem pedido. Nenhuma tabela nova — o cardápio e os modifiers já existem. **Não viola trava:** a IA sugere itens do próprio cardápio, não inventa preço.
- **Dependências:** nenhuma. Fica ainda melhor com #16 (foto) e #5 (combo do dia).
- **Métrica de sucesso:** ticket médio, taxa de aceite do upsell, itens/pedido.

### 4. Pagamento online (Pix/cartão) na conversa

- **Problema de negócio:** "pagar na entrega" gera calote, problema de troco e pedido-fantasma. Cobrar Pix/cartão ANTES trava a receita, elimina o troco e reduz no-show de entrega.
- **Como funciona:** gateway (dependência #50 global — Stripe/Pix). Ao fechar o pedido, o backend gera cobrança (link Pix/checkout) e o pedido só entra em `em_preparo` após confirmação de pagamento (ou segue "pagar na entrega" como opção). A IA envia o link e confirma o recebimento; o webhook do gateway muta o status. Mantém o gate de aceite humano (a pizzaria ainda aceita/recusa). **Trava:** a IA nunca "confirma pagamento" por conta própria — quem confirma é o webhook.
- **Dependências:** **gateway #50** (bloqueante). Sinal/pré-pago para agendado (#10) reaproveita este fluxo.
- **Métrica de sucesso:** % de pedidos pré-pagos, queda de no-show/recusa por endereço, redução de perda por troco/calote.

### 5. Combo do dia / promoção por dia da semana

- **Problema de negócio:** segunda e terça são horários mortos; a pizzaria precisa de um motivo temático pra encher esses dias sem queimar margem no fim de semana cheio.
- **Como funciona:** `pizzaria_daily_deals` (day_of_week, título, regra de desconto ou combo montado, active). O `PizzariaMenuCache` injeta a promoção VIGENTE do dia no contexto da IA, que a apresenta ("hoje é terça: pizza grande G + refri por R$ X"). O backend valida e precifica no fechamento (nunca confia no total da IA). Reusa a máquina de desconto do #1. Tela na área de cardápio/config.
- **Dependências:** idealmente após #1 (motor de desconto). Independe de gateway.
- **Métrica de sucesso:** pedidos nos dias-alvo antes/depois, ticket médio do combo, canibalização vs. dias cheios.

## Dependências transversais

- **Gateway de pagamento (#50 global):** destrava #4 (pagamento na conversa), o **sinal/pré-pago** do pré-agendamento (#10) e o resgate financeiro de indicação (#15, se for cashback em vez de cupom). Enquanto não existir, cupom (#1) e fidelidade (#2) já entregam a alavanca comercial sem tocar em dinheiro real.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY):** destrava #16 (foto do sabor no cardápio) e uma futura "IA responde a foto do cliente" (ex.: cliente manda print de um sabor). Todo o resto do backlog independe de foto.
- **Scheduler/cron (auto-transição + campanha):** destrava #6 (reativação de inativo por janela de dias), o disparo automático da avaliação #8 pós-entrega, os lembretes de pré-agendamento (#10) e a auto-transição de status (pedido "saiu pra entrega" há X min). Hoje o fluxo é manual no Kanban; o cron transforma vários itens de "manual" em "automático".
- **Motor de campanha em massa segmentada:** é a base do #6 (reativação), do #15 (indicação) e de disparos do #1/#5 (cupom/combo). Um único módulo de "enviar mensagem para segmento" (por WhatsApp, respeitando opt-out) alimenta três features de marketing de uma vez.
- **Motor de desconto (do #1):** uma vez pronto, é reutilizado por #2 (fidelidade), #5 (combo do dia) e #15 (indicação) — construir uma vez, colher em quatro features.
