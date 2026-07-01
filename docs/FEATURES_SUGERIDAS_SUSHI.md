# Features Sugeridas — Sushi (restaurante delivery)

> Backlog de features avançadas para o nicho **Sushi (restaurante delivery)** (profile_id `sushi`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Cardápio dinâmico** com categorias gerenciáveis pelo tenant, itens com preço/disponibilidade e cache invalidado na hora (a IA vê a mudança imediatamente).
- **Pedido por conversa livre** — a IA monta o carrinho relendo o histórico e emite a tag `<pedido>`; o backend RECALCULA o total (descarta o valor chutado pela IA) e faz snapshot de preço/nome no item.
- **Kanban de pedidos** com status dinâmicos por tenant (recebido → preparo → saiu_pra_entrega → entregue, + cancelado) e **notificação WhatsApp automática por transição** (texto configurável ligável/desligável por status).
- **Cupom de desconto** (percentual/fixo, mínimo, validade, limite de usos) validado no backend, e **fidelidade por contagem** (a cada N pedidos entregues → desconto automático).
- **Retirada × entrega** (taxa + endereço obrigatório na entrega) e **agendamento** por dia+período (agora/manhã/tarde/noite).
- Trava da persona: a IA fecha o pedido só com endereço, nunca inventa total, remove a tag antes de enviar ao cliente.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Pagamento antecipado / sinal via Pix na confirmação do pedido.** Delivery de sushi tem ticket alto (R$ 80–150) e sofre com "pedido fantasma" (cliente some antes da entrega) e chargeback de dinheiro na entrega. Cobrar o pedido (ou um sinal) por Pix no momento da confirmação — a IA fecha o carrinho, gera o QR/link Pix e o pedido só entra em "preparo" após a baixa — elimina calote, melhora o fluxo de caixa e reduz troco/maquininha na porta. É o eixo de maior ROI direto: converte cada pedido confirmado em receita garantida. Depende do gateway (#50); é o gargalo que mais trava valor no nicho.

**2. Upsell/cross-sell proativo da IA (bebida, temaki extra, sobremesa) antes de fechar.** O maior alavancador de ticket em delivery é a sugestão certa na hora certa. Ensinar a IA a oferecer, ANTES de fechar, um combo complementar do próprio cardápio ("quer adicionar um refrigerante ou uma sobremesa?" / "por +R$18 vira combo com 8 peças a mais") aumenta o ticket médio 10–25% sem nenhum custo de mídia. Roda 100% sobre o chassi atual (cardápio + tag `<pedido>`), sem gateway nem foto — puro ganho de receita com esforço P. Respeita a trava: só sugere itens que EXISTEM e estão disponíveis no cardápio.

**3. Reativação automática de cliente inativo ("saudade do sushi").** O restaurante já tem, no histórico de pedidos, o telefone e a data do último pedido de cada cliente. Um scheduler que dispara uma mensagem WhatsApp para quem não pede há N dias (ex.: 21) — opcionalmente com um cupom de retorno gerado na hora — traz de volta receita que já estava perdida, com custo marginal zero. É a feira mais barata do funil (cliente já conhece a casa). Esforço M (precisa do scheduler/cron e de uma tela de campanha), retorno alto e recorrente.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Pagamento/sinal via Pix na confirmação | Alto | M | Elimina calote e "pedido fantasma"; garante receita e fluxo de caixa | Receita |
| 2 | Upsell/cross-sell proativo da IA (bebida/sobremesa/combo) | Alto | P | Sobe ticket médio 10–25% sem custo de mídia | IA |
| 3 | Reativação de inativo com cupom de retorno (scheduler) | Alto | M | Recupera cliente parado; receita de custo marginal zero | Retenção |
| 4 | Combos/kits e "meio a meio" no cardápio | Alto | M | Vende ticket maior; formato nativo do sushi (combinado) | Receita |
| 5 | Programa de pontos/cashback por valor gasto | Alto | M | Fideliza; incentiva o próximo pedido (crédito acumulado) | Retenção |
| 6 | Campanha em massa segmentada (promoção do dia / rodízio) | Alto | M | Ocupa a cozinha em dia parado; empurra promoção sazonal | Marketing |
| 7 | Confirmação/aviso de tempo de entrega + status "atrasado" | Médio | P | Reduz ligação "cadê meu pedido"; melhora percepção | Operação |
| 8 | NPS/avaliação pós-entrega automática | Médio | P | Mede satisfação, coleta review, sinaliza problema cedo | Marketing |
| 9 | Cupom de aniversário automático | Médio | P | Toque pessoal que traz pedido em data com alta intenção | Retenção |
| 10 | Estoque/limite de itens do dia (rodízio de peixe) | Médio | M | Evita vender o que acabou; a IA respeita o esgotado | Operação |
| 11 | Assinatura "clube do sushi" (N pedidos/mês com desconto) | Alto | G | Receita recorrente previsível; trava o cliente na casa | Receita |
| 12 | Horário de funcionamento + fechado/pausar pedidos | Médio | P | Não aceita pedido fora do expediente; evita frustração | Operação |
| 13 | Área de entrega por bairro/CEP com taxa variável | Médio | M | Taxa justa por distância; recusa fora da área na hora | Operação |
| 14 | Programa de indicação ("indique e ganhe") | Médio | M | Aquisição barata via boca a boca do próprio cliente | Marketing |
| 15 | Relatórios/dashboard de vendas (ticket, top itens, horário-pico) | Médio | M | Dono decide cardápio/promoção com dado, não achismo | Operação |
| 16 | Foto do prato no cardápio (quando Storage liberar) | Médio | M | Foto de sushi vende sozinha; sobe conversão do cardápio | Marketing |

## Detalhamento das prioritárias

### 1. Pagamento/sinal via Pix na confirmação do pedido

- **Problema de negócio:** delivery de sushi tem ticket alto e é o nicho onde o "pedido fantasma" e o calote na entrega mais doem. Dinheiro/maquininha na porta gera troco, insegurança do entregador e chargeback.
- **Como funciona:** a IA monta o carrinho como hoje e, na confirmação, em vez de mandar direto pro Kanban, o backend gera uma cobrança Pix (QR + copia-e-cola) via gateway e envia ao cliente pelo WhatsApp. O pedido nasce num status novo `aguardando_pagamento` (não notifica cozinha); ao receber o webhook de baixa, transiciona para o `is_initial` do tenant (recebido). Configurável por tenant: cobrança total ou sinal (%). A IA NUNCA confirma pagamento por conta própria — só o webhook do gateway libera. Snapshot de valor pago no pedido, selo "pago" no card do Kanban.
- **Dependências:** gateway de pagamento (#50, pendência global). Sem ele, fica na prateleira.
- **Métrica de sucesso:** % de pedidos pré-pagos, queda na taxa de cancelamento pós-confirmação, redução de troco/dinheiro na entrega.

### 2. Upsell/cross-sell proativo da IA

- **Problema de negócio:** o ticket médio fica abaixo do potencial porque o cliente pede só o que veio buscar; ninguém oferece a bebida ou a sobremesa.
- **Como funciona:** a persona SUSHI ganha uma instrução de, ANTES de emitir a tag `<pedido>`, oferecer 1 (no máximo 2) itens complementares tirados do PRÓPRIO cardápio disponível — priorizando categorias sub-representadas no carrinho (sem bebida → sugere bebida; sem sobremesa → sugere sobremesa; ou o combo maior por poucos reais a mais). O contexto injetado (SushiMenuCache) marca quais categorias já estão no carrinho para a IA sugerir o que falta. Regra dura: só itens que existem e estão `disponível=true`; sem insistência (uma oferta, respeita o "não"). Nenhuma mudança de schema — vive na persona + no bloco de cardápio.
- **Dependências:** nenhuma (roda no chassi atual). Opcional: campo `upsell_habilitado` na config do tenant.
- **Métrica de sucesso:** ticket médio, % de pedidos com item adicionado após sugestão, itens/pedido.

### 3. Reativação automática de cliente inativo

- **Problema de negócio:** cliente que pediu 2–3 vezes e sumiu é receita perdida que já conhece a casa — o CAC de trazê-lo de volta é quase zero, mas hoje ninguém o chama.
- **Como funciona:** um scheduler diário varre os contatos com pedidos entregues cujo último pedido é anterior a N dias (configurável, ex.: 21). Para cada um, dispara uma mensagem WhatsApp de reengajamento ("faz tempo que você não pede… tá com saudade do temaki?"), opcionalmente com um cupom de retorno GERADO na hora (reaproveita `sushi_coupons`, com validade curta e limite de 1 uso pro contato). Respeita janela de opt-out e não repete o mesmo contato dentro de M dias. Painel: tela de "Reativação" com regra (dias de inatividade, texto, cupom on/off) e log de disparos.
- **Dependências:** scheduler/cron (transversal); reusa cupom e o notifier Evolution que já existe. Cuidar do incidente de webhook/re-sync (não disparar em massa sem guard de frescor).
- **Métrica de sucesso:** taxa de retorno dos reativados, receita atribuída ao cupom de retorno, custo por cliente reativado (≈0).

### 4. Combos/kits e "meio a meio" no cardápio

- **Problema de negócio:** combinado é o formato NATIVO do sushi e o que puxa ticket alto; hoje o item é unitário e o cliente monta na mão, perdendo a venda de conjunto.
- **Como funciona:** o item de cardápio ganha um tipo `combo` com uma composição (lista de itens/quantidades) e um preço próprio (ou desconto sobre a soma). Para "meio a meio", o item aceita 2 componentes e o preço segue a regra do maior valor (espelho do que o nicho pizzaria já resolveu — reaproveitar o padrão). A IA passa a oferecer e montar combos pela tag `<pedido>`; o backend recalcula o total pela regra do combo (mantendo o snapshot). Painel: editor de combo no `/dashboard/sushi-menu`.
- **Dependências:** nenhuma externa; é evolução do modelo de item + recálculo de total.
- **Métrica de sucesso:** % de pedidos com combo, ticket médio de pedidos com combo vs avulso.

### 5. Programa de pontos/cashback por valor gasto

- **Problema de negócio:** a fidelidade atual é por CONTAGEM de pedidos (a cada N, um desconto) — não recompensa quem gasta mais e não cria "saldo" que puxa o próximo pedido.
- **Como funciona:** cada pedido entregue credita pontos/cashback proporcional ao valor (ex.: 5% em crédito). O saldo por contato é persistido; a IA informa o saldo e permite abatê-lo no próximo pedido (o backend valida e desconta como um cupom interno, clampando ao subtotal — mesma mecânica de desconto já existente). Convive com a fidelidade por contagem (o tenant escolhe qual usar). Painel: tela de configuração da regra (percentual, validade do crédito) e visão de saldo por cliente.
- **Dependências:** nenhuma externa; reusa a soma/clamp de desconto do pedido. Casa bem com #1 (crédito vira desconto no Pix).
- **Métrica de sucesso:** frequência de recompra, % de pedidos que resgatam crédito, LTV do cliente.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava #1 (Pix/sinal na confirmação), viabiliza cobrança real do "clube do sushi" (#11) e o resgate de cashback como pagamento (#5). É o único bloqueador de maior impacto de receita.
- **Upload de foto/anexo (SERVICE_ROLE_KEY):** destrava #16 (foto do prato no cardápio). Enquanto bloqueado, o cardápio segue só-texto; a IA não recebe/analisa imagem do cliente.
- **Scheduler/cron:** destrava #3 (reativação), #8 (NPS pós-entrega), #9 (cupom de aniversário) e o status "atrasado" de #7 — todas dependem de disparo temporal automático, hoje inexistente no nicho.
- **Infra de campanha em massa segmentada:** destrava #6 (promoção do dia), #14 (indicação) e potencializa #3/#9; precisa de segmentação de contatos + envio em lote com opt-out e guard de frescor (lição do incidente de re-sync do Baileys — nunca disparar em massa sem controle).
