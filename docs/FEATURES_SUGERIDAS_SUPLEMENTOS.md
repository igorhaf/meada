# Features Sugeridas — Loja de suplementos

> Backlog de features avançadas para o nicho **Loja de suplementos** (profile_id `suplementos`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Catálogo com variantes (sabor × peso) + estoque:** produto → N variantes (`sup_variants`) com preço/SKU/`stock_quantity`/`expiry_date`; a variante é o SKU real que o pedido referencia.
- **Decremento transacional de estoque:** `UPDATE ... stock_quantity >= qtd` na criação do pedido; 0 linhas → `409 out_of_stock` e rollback total (sem pedido parcial).
- **Pedido order-based com gate de aceite humano:** nasce `aguardando`; a loja aceita → `em_preparo` ou recusa → `recusado`; Kanban de status (aguardando→em_preparo→saiu_entrega→entregue + recusado/cancelado).
- **Só entrega (com taxa + pedido mínimo):** `delivery_address` obrigatório (422 `address_required`); total materializado no backend (descarta o da IA).
- **TRAVA DE NÃO-PRESCRIÇÃO (o coração):** a IA nunca dá dosagem, conduta por objetivo/sintoma, opinião de saúde ou interação; só mostra catálogo, tira dúvida de PRODUTO e monta pedido. Vive na persona e no bloco de instruções do `SuplementosMenuCache`.
- **Categorias fixas** (proteinas/aminoacidos/vitaminas/pre_treino/emagrecedores/acessorios) e base de conhecimento (RAG) padrão.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Assinatura de recompra automática (clube de suplementos).** Whey e creatina acabam em ~30 dias e o cliente recompra sempre o mesmo item — é o padrão de consumo mais previsível do varejo esportivo. Uma assinatura mensal (o cliente confirma "quero receber todo dia 5") transforma uma venda avulsa em receita recorrente (MRR) e reduz drasticamente a perda pro concorrente do lado, que também vende whey. O chassi de assinatura JÁ existe no monolito (academia/cursos/escola) — clonar é esforço P/M e o retorno é o maior de todos: cada assinante vale N pedidos futuros garantidos.

**2. Reativação de cliente inativo por ciclo de reposição.** A loja sabe quando o cliente comprou um pote de 900g (dura ~1 mês); no dia projetado do fim do estoque dele, dispara automaticamente pelo WhatsApp "seu whey deve estar acabando, quer repor?". É o gatilho de recompra mais rentável que existe — pega o cliente exatamente no momento de decisão, sem custo de mídia, e reverte o churn silencioso (o cliente que sumiu porque esqueceu). Só depende de um scheduler + data do último pedido; esforço P e valor altíssimo.

**3. Cupom de primeira compra + frete grátis acima de X.** A objeção nº1 do varejo de suplementos é a taxa de entrega num ticket que já é caro. Um cupom de boas-vindas (aplicado pela IA na conversa, validado pelo backend) e uma regra de "frete grátis acima de R$ X" aumentam a taxa de conversão da primeira conversa E elevam o ticket médio (o cliente adiciona mais um item pra bater o piso do frete grátis). Esforço P/M, mexe direto nos dois números que mais importam: conversão e ticket.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Assinatura de recompra mensal (clube) | Alto | M | Receita recorrente garantida em whey/creatina; retém contra o concorrente | Receita |
| 2 | Reativação por ciclo de reposição (whey acabando) | Alto | P | Recupera cliente que sumiu; gatilho de recompra no momento certo | Retenção |
| 3 | Cupom de 1ª compra + frete grátis acima de X | Alto | P | Vence a objeção do frete; sobe conversão e ticket médio | Receita |
| 4 | Pagamento online (Pix/cartão) via gateway | Alto | M | Fecha a venda na hora; menos desistência no "vou ver e te chamo" | Receita |
| 5 | Fidelidade por pontos/cashback | Alto | M | Cliente volta pra queimar pontos; aumenta frequência de compra | Retenção |
| 6 | Retirada na loja (pickup) sem taxa | Alto | P | Remove o frete pra quem passa na loja; converte quem trava na taxa | Operação |
| 7 | Combo/kit com preço fechado (bundle) | Alto | M | Cross-sell de whey+creatina+coqueteleira; sobe ticket | Receita |
| 8 | Cross-sell/upsell proativo da IA (dentro da trava) | Alto | M | "Quem leva whey costuma levar coqueteleira" — sem virar conselho | IA |
| 9 | Devolução de estoque ao cancelar/recusar | Médio | P | Evita ruptura fantasma; estoque bate com a realidade | Operação |
| 10 | Alerta de estoque baixo + validade próxima (FEFO) | Médio | M | Não vende o que não tem; queima lote perto do vencimento | Operação |
| 11 | Campanha em massa segmentada (por categoria comprada) | Alto | M | Anúncio de reposição/lançamento pro público certo, sem mídia paga | Marketing |
| 12 | Programa de indicação (indique e ganhe cashback) | Médio | M | Aquisição orgânica barata; público fitness indica muito | Marketing |
| 13 | Página pública/CMS do catálogo (link compartilhável) | Médio | P | Vitrine linkável no Instagram/bio; capta lead pro WhatsApp | Marketing |
| 14 | Rastreio de entrega + confirmação de recebimento | Médio | M | Reduz "cadê meu pedido?"; melhora NPS pós-venda | Operação |
| 15 | NPS/avaliação pós-entrega + review de produto | Médio | P | Prova social que fecha a próxima venda; detecta insatisfeito | Marketing |
| 16 | Relatório de vendas (top produtos, ruptura, recorrência) | Médio | M | Dono decide o que repor/promover com dado, não achismo | Operação |

## Detalhamento das prioritárias

### 1. Assinatura de recompra mensal (clube)

- **Problema de negócio:** o consumo de whey/creatina é mensal e repetitivo, mas hoje toda venda é avulsa — o cliente decide de novo a cada mês e pode ir pro concorrente. Zero previsibilidade de receita.
- **Como funciona:** nova entidade `sup_subscriptions` (contact + variante + qtd + dia do mês + status ativa/pausada/cancelada), espelhando o chassi de assinatura já existente (academia/cursos). Um scheduler diário gera o pedido recorrente no dia programado (nasce `aguardando`, entra no mesmo Kanban/gate de aceite — sem furar o fluxo humano). A IA pode oferecer "quer receber todo mês?" ao fechar um pedido (dentro da trava: é logística de recompra do MESMO item, não recomendação de saúde). Tela nova "Assinaturas" na sidebar. Preço e frete recalculados no backend a cada ciclo (snapshot no pedido, como já é hoje).
- **Dependências:** scheduler/cron (transversal); idealmente pagamento #50 pra cobrar o ciclo, mas funciona em v1 com pagamento na entrega.
- **Métrica de sucesso:** % de pedidos originados de assinatura; MRR do clube; churn mensal de assinantes.

### 2. Reativação por ciclo de reposição

- **Problema de negócio:** o maior vazamento de receita é o cliente que comprou uma vez e "esqueceu de voltar" — some sem reclamar. Sem gatilho, a loja só reage quando ele volta (se voltar).
- **Como funciona:** ao entregar um pedido, o backend registra `next_reorder_date = data_entrega + dias_estimados` (config simples por categoria, ex.: proteínas 30 dias, pré-treino 30, vitaminas 60; sem inventar dosagem — é só janela logística). Um scheduler diário varre quem chegou na data e dispara um outbound automático via Evolution: "seu [produto] deve estar acabando, quer que eu já monte a reposição?". Se o cliente responde, a IA remonta o carrinho a partir do histórico. Respeita a trava: fala de RECOMPRA do mesmo item, nunca de dose.
- **Dependências:** scheduler/cron; histórico de pedidos por contato (já existe).
- **Métrica de sucesso:** taxa de resposta ao lembrete; pedidos recuperados/mês; redução do intervalo médio entre compras.

### 3. Cupom de primeira compra + frete grátis acima de X

- **Problema de negócio:** a taxa de entrega sobre um ticket caro derruba a primeira conversão, e não há incentivo pra elevar o ticket.
- **Como funciona:** tabela `sup_coupons` (código, tipo percentual/fixo, mínimo, validade, usos máx, primeira-compra-only). A IA aceita o código na conversa e o passa na tag; o backend VALIDA e aplica no total materializado (nunca a IA calcula o desconto — mesma disciplina do total). Regra de "frete grátis acima de R$ X" fica na config de entrega e o backend zera a taxa quando o subtotal bate o piso. Tela "Cupons" + campo na config de entrega. A IA pode avisar "faltam R$ 20 pro frete grátis" (é fato de pedido, não conselho de saúde).
- **Dependências:** nenhuma bloqueante; casa bem com pagamento #50 quando existir.
- **Métrica de sucesso:** conversão da 1ª conversa; ticket médio antes/depois; taxa de resgate do cupom.

### 4. Pagamento online (Pix/cartão) via gateway

- **Problema de negócio:** hoje o pedido fecha sem cobrança — o "vou ver e te chamo" e o calote na entrega corroem a margem. Sem pagamento na hora, a venda esfria.
- **Como funciona:** ao confirmar o pedido, o backend gera cobrança (Pix copia-e-cola/QR ou link de cartão) via o gateway da pendência global #50; a IA envia o link pelo WhatsApp e o webhook do gateway marca o pedido como pago, liberando o aceite. A trava é preservada — a IA só encaminha cobrança, não negocia preço. Selo "pago" no card do Kanban.
- **Dependências:** **gateway #50** (bloqueante — feature depende inteiramente dele).
- **Métrica de sucesso:** % de pedidos pré-pagos; queda de cancelamento na entrega; tempo até o pagamento.

### 5. Fidelidade por pontos/cashback

- **Problema de negócio:** varejo de suplemento compete por preço; sem programa de recompensa, o cliente é 100% oportunista e vai onde estiver mais barato.
- **Como funciona:** config de fidelidade (X pontos por R$ gasto; regra de resgate percent/fixo), espelhando o modelo de fidelidade já existente no sushi. O backend acumula pontos ao entregar o pedido e a IA informa saldo/aplica resgate na conversa (backend valida e desconta do total materializado). Tela "Fidelidade". Sem violar a trava — pontos são financeiros, não de saúde.
- **Dependências:** nenhuma bloqueante; combina com pagamento #50 e cupom (#3) reusando a mesma pipeline de desconto.
- **Métrica de sucesso:** frequência de compra dos participantes vs não-participantes; % de pontos resgatados; retenção 90 dias.

## Dependências transversais

- **Gateway de pagamento (pendência #50):** destrava #4 (pagamento online) por completo e potencializa #1 (cobrança automática do ciclo de assinatura), #3 e #5 (checkout com desconto/pontos aplicados). É a maior alavanca de receita bloqueada.
- **Upload de foto/anexo (SERVICE_ROLE_KEY ausente):** destrava foto de produto no catálogo e na vitrine pública (#13) — hoje tudo é texto; imagem eleva conversão no varejo. Nenhuma feature do topo depende dela, mas #13 e reviews com foto (#15) ganham muito quando existir.
- **Scheduler/cron:** destrava #1 (geração do pedido recorrente), #2 (lembrete de reposição), #10 (alerta de validade/estoque) e o disparo agendado de #11 (campanha). É a infra que habilita toda a automação de retenção — alto retorno por uma peça só.
- **Campanha em massa segmentada:** uma vez feita (#11), vira o canal de distribuição de #12 (indicação), #15 (pedido de review) e anúncios de #7 (combos) e lançamentos — reaproveita a mesma engine de envio segmentado para vários eixos.
