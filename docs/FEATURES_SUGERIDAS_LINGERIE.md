# Features Sugeridas — Moda íntima/lingerie

> Backlog de features avançadas para o nicho **Moda íntima/lingerie** (profile_id `lingerie`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Catálogo com grade de variantes** (produto → variantes tamanho×cor, cada uma com preço e `stock_qty`), a variante é o SKU real que o pedido referencia.
- **Decremento transacional de estoque** com trava anti-corrida (`WHERE stock_qty >= qtd` → 409 `out_of_stock`, rollback do pedido inteiro).
- **Pedido pela IA no WhatsApp** montado pela variante exata (tag `<pedido_lingerie>`), com total recalculado no backend (descarta o da IA) e snapshot de produto/variante/preço no item.
- **Kanban com gate de aceite humano** (`aguardando → separando → enviado → entregue`; recusado/cancelado); a IA nunca aceita/recusa.
- **Fulfillment entrega (com endereço) × retirada** + configuração de taxa flat de entrega e pedido mínimo.
- **Persona discreta e respeitosa** (sem apelo sensual, sem comentar o corpo; nunca oferece variante esgotada nem inventa produto/tamanho/cor/preço).
- **Base de conhecimento (RAG)** disponível como em todo perfil.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Alerta de reposição + reserva de estoque na conversa ("me avisa quando voltar").** Hoje quando a variante está esgotada a IA simplesmente não oferece e o cliente evapora — venda perdida silenciosa. Um cadastro de "aviso de volta ao estoque" (contato + variante) que, ao repor o `stock_qty`, dispara automaticamente uma mensagem WhatsApp "seu P preto voltou!" recupera demanda já qualificada (a pessoa QUERIA aquele SKU exato) sem custo de mídia. É esforço P (uma tabela + hook no update de estoque + Evolution), reusa o gate de notificação que já existe, e converte alto porque é intenção comprovada. Retenção + receita direta.

**2. Sugestão de tamanho guiada (assistente de caimento).** Lingerie tem devolução/desistência altíssima por dúvida de numeração — é o maior atrito de conversão do nicho. A IA passa a fazer 2-3 perguntas objetivas (medida do busto/quadril ou "que tamanho você usa em X marca") e mapeia para a grade PP–XGG usando uma tabela de correspondência por produto/categoria cadastrada pelo tenant, respeitando a trava (só ORIENTA numeração, nunca comenta o corpo). Reduz devolução, aumenta ticket fechado e diferencia da concorrência que só manda tabela de medidas em imagem. Esforço M, altíssimo valor.

**3. Cross-sell "completa o conjunto" na hora do pedido.** Quando o cliente coloca uma calcinha/sutiã no carrinho, a IA proativamente oferece a peça-par (mesma coleção/cor) ou um item complementar (meia, modelador) marcado como "combina com" no catálogo. Cross-sell é o alavancador de ticket médio mais barato que existe e o varejo íntimo vive de conjuntos. Reusa o carrinho-na-conversa que já existe; só precisa de um campo de relação entre produtos + instrução de persona. Esforço P/M, receita incremental em cada pedido.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Alerta de volta ao estoque ("me avisa quando voltar") | Alto | P | Recupera venda perdida por variante esgotada; demanda já qualificada | Retenção |
| 2 | Assistente de tamanho/caimento guiado por IA | Alto | M | Corta devolução e trava de conversão por dúvida de numeração | IA |
| 3 | Cross-sell "completa o conjunto" na conversa | Alto | P/M | Sobe ticket médio com a peça-par/complementar | Receita |
| 4 | Cupom / promoção (percentual, fixo, primeira compra) | Alto | P/M | Fecha carrinho hesitante e ativa campanha sazonal (Dia dos Namorados) | Receita |
| 5 | Reativação de cliente inativo (win-back automático) | Alto | M | Traz de volta quem comprou e sumiu, com oferta segmentada | Retenção |
| 6 | Pagamento/sinal online (Pix/cartão) | Alto | G | Confirma pedido antes de separar; reduz calote e reserva de estoque fantasma | Receita |
| 7 | Fidelidade/cashback por compra | Alto | M | Recompra recorrente; lingerie é consumo repetido | Retenção |
| 8 | Foto do produto no catálogo e na conversa | Alto | M | Venda de moda é visual; sem foto a conversão despenca | Marketing |
| 9 | Devolução/troca com reentrada de estoque | Médio | M | Fecha o ciclo pós-venda e devolve a variante à grade | Operação |
| 10 | Campanha em massa segmentada (lançamento/liquidação) | Alto | M | Dispara coleção nova pra base filtrada por histórico | Marketing |
| 11 | Kit/combo com preço fechado | Médio | P/M | "Conjunto + meia" com desconto empacotado eleva ticket | Receita |
| 12 | Aniversário / data especial (cupom automático) | Médio | P | Toque pessoal que gera compra recorrente e fideliza | Retenção |
| 13 | Pós-venda + NPS + pedido de avaliação | Médio | P | Coleta review social e mede satisfação; feedback de caimento | Marketing |
| 14 | Página pública/CMS da loja (catálogo vitrine) | Médio | M | Vitrine linkável no bio do Instagram; captura fora do WhatsApp | Marketing |
| 15 | Relatórios de vendas (top SKU, tamanho, ruptura) | Médio | P/M | Decisão de compra/reposição por dado, não por achismo | Operação |
| 16 | Reserva de estoque temporária no carrinho | Médio | M | Segura a última peça enquanto o cliente decide, sem overselling | Operação |

## Detalhamento das prioritárias

### 1. Alerta de volta ao estoque ("me avisa quando voltar")

- **Problema de negócio:** toda vez que a IA responde "essa variante está esgotada" a venda morre sem rastro. Não há como recuperar a intenção — o cliente vai para a concorrência. Em varejo íntimo com grade fina (PP–XGG × várias cores), ruptura de SKU específico é diária.
- **Como funciona:** nova tabela `lingerie_stock_alerts (company_id, contact_id, variant_id, notified_at)`. Quando a IA detecta que o cliente quer uma variante com `stock_qty = 0`, em vez de só recusar, oferece cadastrar o aviso (tag ou fluxo de persona). No painel de Catálogo, quando o tenant edita a grade e a variante volta a ter `stock_qty > 0`, um hook no update dispara o `LingerieStockNotifier` (reusa o `Notifier` do Evolution) para todos os contatos com alerta pendente daquela variante e marca `notified_at`. Idempotente por `notified_at IS NULL`. Respeita a trava (só avisa disponibilidade, sem apelo).
- **Dependências:** nenhuma bloqueante — usa o canal de notificação já existente. Fica ainda melhor com foto liberada (mandar a imagem no aviso).
- **Métrica de sucesso:** % de alertas que viram pedido em até 48h da notificação; receita recuperada/mês.

### 2. Assistente de tamanho/caimento guiado por IA

- **Problema de negócio:** dúvida de numeração é a principal razão de carrinho abandonado e de devolução em moda íntima. Mandar "veja a tabela de medidas" em imagem tem baixíssima conversão.
- **Como funciona:** o tenant cadastra, por produto ou categoria, uma tabela de correspondência (`lingerie_size_guides`: medida → tamanho da grade, ou equivalência com marcas comuns). A persona ganha um sub-fluxo: a IA faz 2-3 perguntas objetivas (busto/quadril em cm, ou "que numeração você usa hoje") e mapeia para PP–XGG do catálogo, sugerindo a variante exata (`variant_id`). Trava reforçada: a IA ORIENTA numeração a partir de dados fornecidos pelo cliente; NUNCA opina sobre o corpo, formato ou estética. Contexto injetado via cache do perfil (TTL curto). Se houver dúvida, sugere a menor faixa segura e explica a política de troca.
- **Como funciona (backend):** o guia é lido no `LingerieMenuCache`/contexto da IA; nenhuma tabela nova de pedido — só de referência.
- **Dependências:** nenhuma bloqueante. Sinergia forte com a feature #9 (troca) — reduz o custo da troca ao acertar o tamanho na origem.
- **Métrica de sucesso:** taxa de devolução por tamanho errado; conversão de conversas que pediram ajuda de numeração.

### 3. Cross-sell "completa o conjunto" na conversa

- **Problema de negócio:** o cliente pede uma peça e fecha; o par/complemento que elevaria o ticket nunca é oferecido. Conjunto é a lógica natural do nicho.
- **Como funciona:** campo de relação no catálogo (`lingerie_product_relations`: produto A "combina com"/"é par de" produto B). Quando o carrinho-na-conversa recebe um item, a persona checa se há relação cadastrada e oferece o complementar UMA vez, de forma leve ("quer levar a calcinha do mesmo conjunto?"). O backend continua recalculando o total; a variante do complementar respeita estoque e grade normalmente. Persona mantém tom discreto, sem insistência.
- **Como funciona (painel):** editor de relações na tela de Catálogo (selecionar produtos relacionados).
- **Dependências:** nenhuma bloqueante. Combina com #11 (kit/combo) para oferta empacotada com desconto.
- **Métrica de sucesso:** ticket médio antes/depois; % de pedidos com 2+ itens; taxa de aceite da sugestão.

### 4. Cupom / promoção (percentual, fixo, primeira compra)

- **Problema de negócio:** não há alavanca de fechamento para carrinho hesitante nem munição para datas comerciais fortíssimas do nicho (Dia dos Namorados, Black Friday, Dia da Mulher). Hoje o preço é rígido.
- **Como funciona:** tabela `lingerie_coupons` (código, tipo percentual/fixo, valor, mínimo, validade, limite de uso, escopo primeira-compra). A IA recebe o código do cliente na conversa, VALIDA no backend (ativo + validade + mínimo + limite) e aplica; cupom inválido NÃO aborta o pedido (sai sem desconto). O total materializado passa a subtrair `discount_cents` clampado ao subtotal — espelho exato do que o perfil sushi já faz, reduzindo risco. A IA nunca inventa cupom nem valor.
- **Dependências:** nenhuma bloqueante; independe de gateway.
- **Métrica de sucesso:** taxa de resgate; incremento de conversão em campanhas com cupom vs. sem.

### 5. Reativação de cliente inativo (win-back automático)

- **Problema de negócio:** a base de quem já comprou é o ativo mais barato de reativar, e hoje ninguém fala com quem sumiu. Lingerie tem ciclo de recompra previsível.
- **Como funciona:** um scheduler (cron diário) varre contatos com último pedido há mais de N dias (configurável) e sem pedido recente, e dispara uma campanha de win-back segmentada via Evolution ("faz tempo! separamos novidades no seu tamanho"), opcionalmente com cupom (#4). Segmentação por histórico de tamanho/categoria comprada torna a mensagem relevante. Respeita opt-out e frequência máxima para não virar spam.
- **Dependências:** scheduler/cron transversal (ver seção final); casa com #4 (cupom) e #10 (campanha em massa) que compartilham o motor de disparo segmentado.
- **Métrica de sucesso:** taxa de reativação (inativos que voltam a comprar); receita atribuída ao win-back.

## Dependências transversais

- **Gateway de pagamento (#50 global):** destrava a #6 (pagamento/sinal online). Enquanto não existir, o pedido segue confirmado por aceite humano e pagamento fora do app. Também potencializa #11 (kit/combo pago) e reserva de estoque com sinal.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava a #8 (foto de produto) e enriquece #1 (foto no aviso de volta ao estoque), #10 (campanha com imagem da coleção) e #14 (vitrine CMS com fotos). É a dependência de maior impacto de conversão para o nicho — moda vende pelo visual.
- **Scheduler/cron transversal:** destrava #5 (win-back), #12 (aniversário/cupom automático), #13 (pós-venda/NPS temporizado) e #16 (expiração de reserva de estoque no carrinho). Um único motor de agendamento habilita quatro features de retenção/operação de uma vez.
- **Motor de campanha em massa segmentada:** base compartilhada de #5, #10 e #13 — filtro por histórico (tamanho, categoria, recência) + disparo Evolution com opt-out e limite de frequência. Construir uma vez, reusar em todas as automações de marketing/retenção.
