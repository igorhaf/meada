# Features Sugeridas — Moda infantil

> Backlog de features avançadas para o nicho **Moda infantil** (profile_id `moda_infantil`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Catálogo com grade de variantes** (faixa etária × cor, preço por variante, `stock_qty`) e decremento transacional de estoque na criação do pedido (`out_of_stock` fecha a corrida da última unidade).
- **Sugestão idade→tamanho** pela IA (`suggestForAgeMonths`) — a IA converte "6 meses" em "6-9m" e confirma com o cliente.
- **Pedido via WhatsApp** com carrinho montado na conversa (tag `<pedido_moda_infantil>`), total recalculado no backend (descarta o da IA), entrega (com endereço) ou retirada.
- **Kanban com gate de aceite humano** (aguardando→separando→enviado→entregue + recusado/cancelado); aguardando não notifica.
- **Devolução de estoque ao cancelar/recusar** (restock idempotente via `stock_returned`).
- **Configuração de taxa de entrega + pedido mínimo.** Base de conhecimento (RAG) por tenant.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Cupom de desconto + promoção sazonal.** É a alavanca de receita mais barata e mais pedida no varejo de roupa infantil, que vive de datas (volta às aulas, Dia das Crianças, Natal, liquidação de coleção). Hoje NÃO existe cupom. Reusa o motor de `sushi_coupons` (percent/fixed, min_order, max_uses, validade): a IA aceita o código na conversa, o backend valida e aplica no total já materializado, cupom inválido não aborta o pedido (sai sem o desconto). Vende porque cria urgência ("cupom VOLTAAULAS10 vale só até domingo") e recupera carrinho parado — retorno direto em faturamento com esforço P.

**2. Recompra por crescimento / reativação do "trocou de tamanho".** É o superpoder EXCLUSIVO deste nicho: criança cresce, a roupa deixa de servir em ~3-6 meses — logo TODO cliente é um cliente recorrente latente. Um scheduler varre pedidos entregues, estima a idade da criança (a partir da faixa comprada + data do pedido) e dispara no WhatsApp "o [nome] já deve estar no tamanho 2a, chegou a coleção nova" com sugestão da próxima faixa. Transforma venda única em assinatura implícita de guarda-roupa. Retenção altíssima, esforço M (precisa de campanha em massa + scheduler).

**3. Lista de espera / avise-me quando voltar (variante esgotada).** Hoje a IA simplesmente não oferece variante esgotada e a venda MORRE ali. Registrar o interesse (contato + variante) e, quando o estoque daquela faixa×cor voltar (via o próprio restock ou reposição no painel), disparar "voltou o body 6-9m rosa que você queria" recupera uma venda que já estava perdida. Esforço P/M, e é receita que hoje escoa pelo ralo toda vez que falta um tamanho.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Cupom de desconto + promoção sazonal | Alto | P | Cria urgência em datas (volta às aulas, Dia das Crianças) e fecha carrinho parado | Receita |
| 2 | Recompra por crescimento (avise que trocou de tamanho) | Alto | M | Recompra recorrente natural do nicho: criança cresce, roupa não serve | Retenção |
| 3 | Lista de espera "avise-me quando voltar" | Alto | P | Recupera venda perdida por variante esgotada | Receita |
| 4 | Pagamento online / sinal (Pix + cartão) | Alto | G | Fecha a venda na hora, reduz desistência e no-show de retirada | Receita |
| 5 | Cross-sell / kit "look completo" pela IA | Alto | M | Aumenta ticket sugerindo meia+body+calçado da mesma faixa | Receita |
| 6 | Fidelidade por compras (cashback/pontos) | Alto | M | Faz o cliente voltar na SUA loja e não na concorrente | Retenção |
| 7 | Frete por CEP / faixa de bairro | Médio | M | Cobra frete justo, viabiliza entrega além do raio fixo | Operação |
| 8 | Foto de produto no catálogo e na conversa | Alto | M | Roupa se vende pelo visual; sem foto a conversão despenca | Marketing |
| 9 | Campanha em massa segmentada (coleção nova, liquidação) | Alto | M | Reativa base inteira com um disparo por faixa etária/última compra | Marketing |
| 10 | Aniversário da criança (cupom presente) | Médio | P | Gatilho emocional que gera venda e fideliza a mãe | Retenção |
| 11 | Alerta de estoque baixo / reposição no painel | Médio | P | Evita ruptura de tamanho campeão de venda | Operação |
| 12 | Troca/devolução com fluxo próprio (voucher) | Médio | M | Troca de tamanho é rotina; hoje é cancelar+refazer na unha | Operação |
| 13 | Dashboard de vendas (top variante, faixa, receita/dia) | Médio | M | Mostra qual faixa/cor gira, o que repor, sazonalidade | Operação |
| 14 | Página pública / vitrine (CMS já existe na plataforma) | Médio | P | Loja tem link próprio pra divulgar no Instagram/bio | Marketing |
| 15 | NPS / avaliação pós-entrega | Médio | P | Mede satisfação e gera prova social (review) | Marketing |
| 16 | Indicação com recompensa (indique uma mãe) | Médio | M | Crescimento orgânico via boca-a-boca de grupos de mães | Marketing |

## Detalhamento das prioritárias

### 1. Cupom de desconto + promoção sazonal
- **Problema de negócio:** roupa infantil vende por evento de calendário e por queima de coleção. Sem cupom, o tenant não tem como criar urgência nem recuperar um cliente que hesitou. É a feature de receita mais requisitada e a mais barata.
- **Como funciona:** clonar o motor de `sushi_coupons` (tabela `moda_infantil_coupons`: kind percent(1..100)/fixed(centavos), min_order, max_uses, valid_until, active, uses). A IA aceita o código na conversa e o passa na tag `<pedido_moda_infantil>` (campo `cupom`); o backend VALIDA (ativo + validade + mínimo + max_uses) e aplica sobre o total já materializado — **cupom inválido NÃO aborta** o pedido (sai sem desconto, espelho exato do sushi). `uses` incrementa na criação. Painel: tela "Cupons" (CRUD). Trava respeitada: a IA só APLICA cupom existente, NUNCA cria/inventa desconto.
- **Dependências:** nenhuma bloqueante. Independe de gateway.
- **Métrica de sucesso:** % de pedidos com cupom, ticket médio em campanha vs. fora, receita incremental por campanha.

### 2. Recompra por crescimento (reativação "trocou de tamanho")
- **Problema de negócio:** a criança cresce e a roupa para de servir em poucos meses — o cliente É recorrente por natureza, mas o tenant não captura essa recompra e ela vaza pra concorrência. É a maior alavanca de LTV do nicho.
- **Como funciona:** scheduler diário (cron) varre pedidos `entregue`, estima a faixa atual da criança a partir da faixa comprada + tempo decorrido (o enum `KidsSize` já ordena as faixas), e agenda um disparo WhatsApp via Evolution: "Oi! O bebê já deve estar no tamanho [próxima faixa]. Chegou coleção nova nesse tamanho — quer ver?". Reusa o motor de campanha em massa (#9). Backend: coluna de controle pra não redisparar; painel mostra "recompras sugeridas" com opt-out por contato (respeitar consentimento). A IA só continua a conversa se o cliente responder.
- **Dependências:** scheduler/cron (transversal) + campanha em massa (#9). Consentimento de contato (LGPD).
- **Métrica de sucesso:** taxa de resposta ao disparo, % de recompra em 30/60 dias, receita reativada.

### 3. Lista de espera "avise-me quando voltar"
- **Problema de negócio:** quando a faixa×cor desejada está esgotada, a IA não oferece e a venda morre sem deixar rastro. Numa loja de roupa infantil faltar um tamanho específico é rotina — essa é receita escoando toda semana.
- **Como funciona:** tabela `moda_infantil_waitlist` (company, contact, variant_id, created_at, notified). Quando a IA detecta interesse numa variante com `stock_qty=0`, oferece registrar o interesse. Ao repor estoque (restock por cancelamento OU edição no painel que sobe `stock_qty` de 0→N), o backend enfileira um disparo "voltou o [produto] [faixa] [cor] que você queria". Painel: aba "Lista de espera" por produto mostra demanda reprimida (também orienta a REPOSIÇÃO — o tenant vê o que falta e vende). Trava: a IA registra interesse, não promete data de reposição.
- **Dependências:** scheduler/cron pra o disparo (ou disparo imediato no evento de reposição) + Evolution (já existe).
- **Métrica de sucesso:** nº de itens em espera, taxa de conversão do "voltou" em pedido, demanda reprimida por variante (insight de compra).

### 4. Pagamento online / sinal (Pix + cartão)
- **Problema de negócio:** hoje o pedido nasce sem pagamento; entre a conversa e a retirada/entrega o cliente desiste, e retirada sem sinal gera no-show. Cobrar na hora fecha a venda e derruba a desistência.
- **Como funciona:** ao confirmar o pedido, o backend gera link de pagamento (Pix/cartão) via gateway; webhook do gateway marca o pedido como pago e só então libera pra "separando". Opção de sinal parcial pra encomenda. Painel mostra status de pagamento por pedido. A IA envia o link mas NUNCA processa cartão nem confirma pagamento por conta própria (só o webhook confirma).
- **Dependências:** **gateway de pagamento — pendência global #50** (bloqueante). Destrava também sinal, fidelidade-cashback com saldo real e assinatura.
- **Métrica de sucesso:** % de pedidos pagos antecipado, queda de no-show de retirada, tempo médio conversa→pagamento.

### 5. Cross-sell / kit "look completo" pela IA
- **Problema de negócio:** o ticket médio é baixo porque o cliente compra uma peça só. Roupa infantil é comprada em conjunto (body + calça + meia + calçado da mesma faixa), e a IA hoje não puxa isso.
- **Como funciona:** a IA, ao montar o carrinho, sugere itens complementares da MESMA faixa etária disponível em estoque (ex.: "quer levar a meia 6-9m que combina?"). Backend: relação leve de itens sugeridos por categoria (bebe→acessorios/calcados) ou por curadoria do tenant ("kits" já é categoria). Injeta no contexto do cache do catálogo os complementos com estoque. Trava respeitada: só sugere item que EXISTE e tem estoque, nunca inventa produto/preço, sem apelo agressivo.
- **Dependências:** nenhuma bloqueante (usa o catálogo e o cache já existentes). Melhora muito com foto (#8) liberada.
- **Métrica de sucesso:** ticket médio, itens por pedido, % de pedidos com item sugerido aceito.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava pagamento/sinal (#4), fidelidade com cashback de saldo real (#6), e viabiliza assinatura/clube futuro. É o maior desbloqueio de receita.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava foto de produto no catálogo e na conversa (#8) e enriquece cross-sell (#5) e a vitrine/CMS (#14). Roupa se vende pelo visual — é alto impacto assim que liberar.
- **Scheduler/cron:** destrava recompra por crescimento (#2), lista de espera com disparo (#3), aniversário (#10), alerta de estoque baixo (#11) e NPS pós-entrega (#15). Praticamente toda a coluna de RETENÇÃO/OPERAÇÃO depende dele.
- **Campanha em massa segmentada (#9):** vira infraestrutura de disparo reusada por recompra (#2), aniversário (#10), lista de espera (#3) e coleção nova. Construir uma vez, alimentar várias features.
- **CMS/feature flag (já existe na plataforma, camada 9.x):** basta LIGAR a flag CMS pro nicho pra destravar a vitrine pública (#14) sem código novo — quick win de marketing quase de graça.
