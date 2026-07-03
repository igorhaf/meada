# Features Sugeridas — Loja de lãs/tricô

> Backlog de features avançadas para o nicho **Loja de lãs/tricô** (profile_id `las`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Catálogo com grade de variantes cor × dye lot** (lote de tingimento) — cada lote da mesma cor é um SKU próprio com estoque isolado (`UNIQUE(product_id, color, dye_lot)`).
- **Estoque com decremento transacional** — o pedido debita o estoque na criação (`UPDATE ... WHERE stock_qty >= qtd` → 409 `out_of_stock`, aborta o pedido inteiro se faltar unidade).
- **Regra "mesmo lote garantido"** (`same_lot_guaranteed`) — a IA agrupa por cor e exige lote único por cor quando o cliente pede (422 `mixed_dye_lots` aborta).
- **Pedido via WhatsApp com carrinho-na-conversa + tag `<pedido_las>`** — total recalculado no backend (descarta o da IA), snapshots de produto/cor/lote/preço no item.
- **Kanban com gate de aceite humano** (aguardando → separando → enviado → entregue + recusado/cancelado) e escolha entrega/retirada.
- **Categorias hardcoded** (las/linhas/kits/agulhas/acessorios/pelucia) e configuração de taxa de entrega + pedido mínimo.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Reserva de lote / lista de espera de dye lot (avise-me quando chegar).** O maior gerador de perda de venda numa loja de lã é justamente o dye lot: o cliente quer 8 novelos do lote X, só tem 5, e a venda evapora — ele vai comprar num lugar que tem o lote completo, porque misturar tom estraga o projeto. Uma feature que permite **registrar interesse num (produto, cor, lote-ou-cor) e disparar WhatsApp automático quando o estoque repõe** captura exatamente a intenção de compra mais quente do nicho. Vende porque converte a ruptura de estoque (hoje uma venda perdida) em venda futura garantida, e retém porque o cliente aprende que a loja "avisa quando chega". Esforço P: uma tabela `las_waitlist` + hook no update de estoque + notifier já existente.

**2. Cálculo de quantidade de novelos por projeto (calculadora de rendimento).** Hoje a IA explicitamente NÃO dimensiona o trabalho — mas essa é a dúvida nº 1 de quem tricota ("quantos novelos preciso pra um cachecol / um cobertor de solteiro?"). Uma calculadora simples por tipo de peça × gramatura/metragem do fio, alimentada por uma tabela de referência que o tenant edita, transforma a IA de "tirador de pedido" em **consultora que fecha o carrinho no tamanho certo**. Isso aumenta ticket (o cliente compra a quantidade completa de uma vez, no mesmo lote) E reduz a devolução por "faltou fio". É o upsell mais natural do nicho, sem violar nenhuma trava — a IA não inventa: lê da tabela do tenant e sempre apresenta como estimativa.

**3. Kit de projeto (bundle fio + agulha + receita) com preço fechado.** O chassi já tem categorias `kits` e `receitas` implícitas no catálogo; falta o produto composto. Um **kit "cachecol iniciante"** que amarra N novelos da mesma cor+lote + a agulha certa + o PDF da receita, vendido como um item só, é cross-sell puro: eleva o ticket médio e resolve a dor do iniciante que não sabe o que comprar junto. Alto valor de negócio, esforço M (produto tipo `kit` que referencia variantes-componente e debita o estoque de cada uma na venda).

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Reserva/lista de espera de dye lot (avise-me quando chegar) | Alto | P | Não perde a venda quando falta o lote; loja avisa a reposição | Retenção |
| 2 | Calculadora de novelos por projeto (rendimento por peça) | Alto | M | Compra a quantidade certa no mesmo lote, evita faltar fio | Receita |
| 3 | Kit de projeto (fio + agulha + receita, preço fechado) | Alto | M | Iniciante compra tudo junto sem adivinhar; ticket sobe | Receita |
| 4 | Sinal/pré-pagamento do pedido (reserva de lote paga) | Alto | M | Garante o lote reservado com sinal; menos desistência | Receita |
| 5 | Cupom e promoção (primeira compra, queima de lote antigo) | Alto | P | Desconto pra girar lote parado e converter novo cliente | Receita |
| 6 | Fidelidade por pontos / cashback em novelos | Alto | M | Cliente recorrente (projeto puxa projeto) volta pela recompensa | Retenção |
| 7 | Reativação de cliente inativo (campanha "novos lotes chegaram") | Alto | M | Traz de volta quem tricota sazonal; reaquece a base | Marketing |
| 8 | Alerta de estoque baixo + sugestão de reposição por lote | Médio | P | Loja repõe o lote antes de romper; menos venda perdida | Operação |
| 9 | Biblioteca de receitas/PDF com entrega read-only via IA | Alto | M | Entrega o gráfico da peça e puxa a lista de materiais | Marketing |
| 10 | Página pública / CMS da loja (catálogo de cores e lotes) | Médio | M | Vitrine própria com as cores disponíveis; capta lead | Marketing |
| 11 | Indicação com recompensa (traga uma amiga que tricota) | Médio | M | Vira o boca-a-boca do nicho em aquisição rastreável | Marketing |
| 12 | Assinatura "clube do novelo" (caixa mensal de fios) | Alto | G | Receita recorrente previsível; engaja o hobbista fiel | Receita |
| 13 | NPS / avaliação pós-entrega do fio (qualidade, tom do lote) | Médio | P | Feedback de tom/qualidade + prova social pro catálogo | Retenção |
| 14 | Relatórios de venda (giro por cor/lote, curva de estoque parado) | Médio | M | Enxerga qual lote encalha e qual cor gira; compra melhor | Operação |
| 15 | Foto do produto/amostra de cor real (quando liberar upload) | Alto | P | Cor de tela engana; foto real do lote reduz troca/insatisfação | Operação |
| 16 | Aniversário do cliente com cupom de fio | Médio | P | Toque afetivo + venda datada; barato e recorrente | Retenção |

## Detalhamento das prioritárias

### 1. Reserva / lista de espera de dye lot (avise-me quando chegar)

- **Problema de negócio:** o dye lot é a alma do nicho e também a maior fonte de venda perdida. Quando o cliente precisa de N novelos do lote X e só há M<N, a IA hoje simplesmente não oferece a variante esgotada e o cliente vai embora — porque projeto grande não aceita mistura de tom. Cada ruptura é uma venda quente perdida sem rastro.
- **Como funciona:** durante a conversa, se a IA não consegue montar a quantidade pedida no mesmo lote, ela oferece "quer que eu te avise quando chegar mais desse lote/dessa cor?". Nova tabela `las_waitlist` (company, contact, product_id, color, dye_lot nullable, qty_desejada, status). A IA emite uma tag namespace própria (`<lista_espera_las>`) que um handler grava — respeitando o chassi (só registra, não promete data). No update de estoque do catálogo (já existente), um hook checa a waitlist da variante/cor e dispara WhatsApp via Evolution ("chegaram novelos do lote X que você esperava"). A IA NÃO inventa prazo de reposição — só registra e o gatilho é o estoque real subir.
- **Dependências:** nenhuma bloqueante (usa notifier + estoque já existentes). Casa com a feature #8 (alerta de reposição pro tenant).
- **Métrica de sucesso:** % de itens de waitlist convertidos em pedido após a notificação; receita recuperada de ruptura de lote.

### 2. Calculadora de novelos por projeto (rendimento por peça)

- **Problema de negócio:** "quantos novelos eu preciso?" é a pergunta que precede quase toda compra e é onde o cliente ou compra a menos (e o projeto fica sem fio, com o lote já esgotado depois) ou desiste por insegurança. A IA hoje é proibida de dimensionar — mas isso deixa dinheiro na mesa.
- **Como funciona:** tabela `las_yield_reference` que o tenant edita (tipo de peça: cachecol / gorro / cobertor solteiro / casaco... × faixa de gramatura ou metragem do fio → novelos estimados). A IA, quando o cliente descreve a peça, consulta a referência do tenant (injetada no contexto via `LasMenuCache`) e sugere a quantidade SEMPRE como estimativa explícita ("pra um cachecol nesse fio, em média 4 novelos — confirma?"), nunca como garantia. Não há cálculo inventado: se não há referência cadastrada pra aquela peça, a IA diz que não tem a estimativa e sugere confirmar com a loja. Ao aceitar, ela monta o `<pedido_las>` já com a quantidade completa no mesmo lote (amarra com o `same_lot_guaranteed`).
- **Dependências:** nenhuma bloqueante. Sinergia forte com #1 (garante lote pra quantidade toda) e #3 (kit).
- **Métrica de sucesso:** ticket médio de pedidos que passaram pela calculadora vs. sem; taxa de recompra por "faltou fio" (deve cair).

### 3. Kit de projeto (fio + agulha + receita)

- **Problema de negócio:** o iniciante não sabe o que comprar junto e o experiente quer praticidade. Vender novelo avulso deixa o cross-sell (agulha, marcador, receita) na mesa. A categoria `kits` já existe no catálogo mas sem produto composto real.
- **Como funciona:** um produto tipo `kit` que referencia variantes-componente (N novelos de uma cor+lote + 1 agulha + 1 receita PDF) com preço fechado (com desconto vs. avulso). Na criação do pedido, o backend debita o estoque de CADA componente transacionalmente (mesma mecânica do `stock_qty >= qtd`, aplicada por componente; se qualquer um faltar, aborta). A IA oferece o kit quando o cliente descreve uma peça compatível ("quer o kit completo do cachecol, já com a agulha e o gráfico?"). Snapshot do preço e da composição no item do pedido.
- **Dependências:** integra melhor com #9 (receita entregue como material read-only). Foto do kit espera upload (#15).
- **Métrica de sucesso:** ticket médio de pedidos com kit; % de pedidos que incluem pelo menos um kit.

### 4. Sinal / pré-pagamento do pedido (reserva de lote paga)

- **Problema de negócio:** cliente pede pra "segurar" 10 novelos do lote X por dias e some — a loja imobiliza o lote e perde outras vendas, ou não segura e perde a venda. Um sinal resolve os dois lados.
- **Como funciona:** quando o pedido envolve reserva de lote crítico ou valor alto, a loja (no painel) pode exigir sinal; a IA envia um link de pagamento e o pedido só entra em "separando" após a confirmação do sinal. O valor pago abate no total. A IA nunca fecha o pagamento sozinha — só envia o link e confirma o recebimento reportado pelo gateway. O restante fica registrado como saldo a pagar na retirada/entrega.
- **Dependências:** **gateway de pagamento (pendência global #50, Stripe/Pix)** — bloqueante. Enquanto não existe, vira "sinal combinado fora do app" com marcação manual no painel.
- **Métrica de sucesso:** taxa de desistência de pedidos com lote reservado (deve cair); giro de lotes antes travados por reserva.

### 5. Cupom e promoção (primeira compra + queima de lote antigo)

- **Problema de negócio:** lotes antigos encalham (cor saiu de moda, dye lot descontinuado) e novos clientes não têm gatilho pra fechar a primeira compra. O nicho não tem hoje nenhum mecanismo de desconto.
- **Como funciona:** tabela `las_coupons` (código, tipo percent/fixo, mínimo, validade, usos, e — a especificidade do nicho — escopo opcional por variante/lote pra "queimar" um dye lot específico). A IA aplica o cupom informado na tag do pedido; o backend VALIDA (ativo, validade, mínimo, escopo) e recalcula o total — cupom inválido não aborta o pedido (sai sem o desconto), preservando o comportamento do chassi. O tenant cria no painel um cupom "LOTE-XYZ -20%" pra girar estoque parado.
- **Dependências:** nenhuma bloqueante. Combina com #7 (campanha) e #14 (relatório de lote parado que alimenta a decisão de qual cupom criar).
- **Métrica de sucesso:** giro dos lotes com cupom vs. sem; taxa de conversão de primeira compra com cupom de boas-vindas.

## Dependências transversais

- **Gateway de pagamento (pendência global #50 — Stripe/Pix):** destrava #4 (sinal/pré-pagamento), a parte paga de #12 (assinatura clube do novelo) e o pagamento online completo do pedido. Enquanto não existe, essas features rodam em modo "combinado fora do app" com marcação manual no painel.
- **Upload de foto/anexo (bloqueado hoje por SERVICE_ROLE_KEY ausente):** destrava #15 (foto real da amostra de cor/lote — crítico no nicho, cor de tela engana), foto do kit (#3) e imagem de capa da página pública (#10). A entrega de receita em PDF (#9) também depende de storage de anexo.
- **Scheduler / cron:** destrava #7 (campanha de reativação disparada por inatividade), #16 (cupom de aniversário na data), lembretes de reposição (#8) e o disparo automático da lista de espera (#1) se a reposição for por rotina em vez de por evento de estoque.
- **Motor de campanha em massa segmentada:** destrava #7 (reativação de inativo) e #11 (indicação), permitindo disparo por segmento (quem comprou determinada cor/lote, quem tem projeto em aberto) — base compartilhável com os demais nichos de varejo do monolito (lingerie, moda infantil), respeitando o isolamento por perfil.
