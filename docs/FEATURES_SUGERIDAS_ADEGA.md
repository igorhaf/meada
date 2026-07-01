# Features Sugeridas — Adega/delivery de bebidas

> Backlog de features avançadas para o nicho **Adega/delivery de bebidas** (profile_id `adega`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Cardápio com modifiers** (Volume, Temperatura via `adega_menu_item_options`) e categorias hardcoded (vinhos/espumantes/cervejas/destilados/sem_alcool/acessorios).
- **Pedido por conversa livre** com a IA montando o carrinho e emitindo a tag `<pedido_adega>`; total recalculado no backend (descarta o da IA); snapshots de preço/nome/opção.
- **Trava +18 dura** (`age_confirmed` NOT NULL): sem confirmação de maioridade não há pedido no banco; selo "+18 confirmado" visível no painel.
- **Kanban com gate de aceite humano** (aguardando → em_preparo → saiu_entrega → entregue; recusado/cancelado), com notificações WhatsApp de texto fixo por status.
- **Configurações** de taxa de entrega (flat) + pedido mínimo. Cache de cardápio Caffeine TTL 60s.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Cupom de desconto + pedido mínimo por cupom.** O chassi de cupom já existe pronto no sushi (`sushi_coupons`: percent/fixed, min_order, max_uses, valid_until) e é clonável quase direto para `adega_coupons`. Vende porque cupom é a alavanca #1 de conversão em delivery de bebidas — "10% no primeiro pedido", "R$ 20 off acima de R$ 150" fecham carrinho que ia abandonar e elevam o ticket médio na direção do frete grátis. A IA só passa o código na tag; o backend valida (ativo, validade, mínimo, usos) e aplica, sem violar nenhuma trava. Esforço P, impacto imediato em receita.

**2. Fidelidade / cashback por contagem de pedidos.** Também já existe pronto no sushi (`sushi_loyalty_config`: enabled + threshold + reward), clonável para adega. Adega vive de recompra (cliente pede toda semana/quinzena); dar "a cada 5 pedidos, 15% no próximo" transforma cliente ocasional em recorrente e cria barreira de troca contra o concorrente da esquina. O backend conta pedidos entregues do contato antes de inserir e aplica o desconto automático — zero atrito na conversa. Retenção pura, esforço P/M.

**3. Reativação de cliente inativo (win-back) via campanha segmentada.** Base de contatos já existe (todo pedido tem contact). Um job que identifica quem não pede há X dias e dispara pelo WhatsApp uma oferta ("sentimos sua falta — 15% off essa semana") recupera receita que já estava perdida, com custo marginal zero (canal próprio). É o maior ROI de retenção possível: reativar um cliente adormecido é muito mais barato que adquirir um novo. Depende de scheduler + módulo de campanha (transversais), mas o retorno justifica priorizar a infra.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Cupom de desconto (percent/fixed, mínimo, validade, usos) | Alto | P | Converte carrinho, sobe ticket, alavanca de 1ª compra | Receita |
| 2 | Fidelidade/cashback por contagem de pedidos | Alto | P | Transforma ocasional em recorrente; barreira de troca | Retenção |
| 3 | Reativação de inativo (win-back automático) | Alto | M | Recupera receita perdida sem custo de mídia | Retenção |
| 4 | Pagamento online / Pix na conversa (gateway) | Alto | G | Reduz calote e pedido fantasma; fecha na hora | Receita |
| 5 | Upsell/cross-sell proativo da IA (harmonização + acessórios) | Alto | M | Sobe ticket sugerindo o que combina com o carrinho | IA |
| 6 | Controle de estoque por garrafa/SKU | Alto | M | Evita vender o que acabou; para de decepcionar cliente | Operação |
| 7 | Clube de assinatura de vinho (recorrência mensal) | Alto | G | Receita recorrente previsível; ticket garantido/mês | Receita |
| 8 | Combo/kit e "leve 3 pague 2" | Médio | P | Sobe volume por pedido; queima estoque parado | Receita |
| 9 | Pós-venda + NPS + pedido de avaliação | Médio | M | Mede satisfação, gera prova social, retém | Retenção |
| 10 | Campanha em massa segmentada (novidade/safra/happy hour) | Alto | M | Vende para a base inteira num clique | Marketing |
| 11 | Scheduler de auto-transição + lembrete de entrega | Médio | M | Menos trabalho manual no Kanban; menos pedido esquecido | Operação |
| 12 | Aniversário / data especial (oferta automática) | Médio | P | Toque pessoal que fecha venda de alto ticket | Retenção |
| 13 | Relatórios/dashboard (top rótulos, ticket, recorrência) | Médio | M | Dono decide o que comprar/promover com dado | Operação |
| 14 | Indicação com recompensa (member-get-member) | Médio | M | Aquisição orgânica dentro do WhatsApp | Marketing |
| 15 | Página pública/CMS da adega (vitrine + destaques) | Médio | P | Presença própria; link na bio; capta lead novo | Marketing |
| 16 | Foto do rótulo no cardápio e na conversa | Médio | M | Bebida vende no olho; sobe conversão | Marketing |

## Detalhamento das prioritárias

### 1. Cupom de desconto

- **Problema de negócio:** hoje não há alavanca nenhuma de conversão nem de recuperação de carrinho. Delivery de bebidas é comprado por impulso e por preço; sem cupom a adega não tem como incentivar a 1ª compra, o carrinho grande, nem responder a um concorrente que deu desconto.
- **Como funciona:** clonar o chassi do sushi para `adega_coupons` (kind percent 1..100 / fixed centavos, `min_order`, `max_uses`, `valid_until`, `active`, `uses`). Tela "Cupons" na sidebar Adega (CRUD). A IA, ao fechar, passa o código no campo `cupom` da tag `<pedido_adega>`; o `PedidoAdegaConfirmHandler`/`AdegaOrderService` **valida no backend** (ativo + validade + mínimo + usos) e aplica sobre o subtotal; cupom inválido **não aborta** o pedido (sai sem desconto). `uses` incrementa na aplicação. Respeita a trava +18 (o cálculo continua vindo só depois do `age_confirmed`). O `total_cents` da IA segue descartado.
- **Dependências:** nenhuma dura. Independente de gateway e de foto.
- **Métrica de sucesso:** taxa de conversão de conversa→pedido e ticket médio com/sem cupom; % de pedidos que atingem o pedido mínimo por causa do cupom.

### 2. Fidelidade / cashback por contagem de pedidos

- **Problema de negócio:** adega vive de recompra semanal/quinzenal, mas não há nada que segure o cliente — ele pede de quem estiver mais barato naquele dia. Sem programa de fidelidade a adega compete só em preço.
- **Como funciona:** clonar `sushi_loyalty_config` (1:1 por company: `enabled`, `threshold_orders`, `reward` percent/fixed) para `adega_loyalty_config`. Tela "Fidelidade" (toggle + limiar + recompensa). No `AdegaOrderService.create`, **antes de inserir**, contar os pedidos **entregues** (terminal não-cancelado) do contato; quando `count > 0 && count % threshold == 0`, aplicar o desconto automático e marcar `loyalty_applied=true` no pedido. Soma com cupom, clampado ao subtotal. A IA pode avisar o cliente que ele ganhou o benefício, mas **quem calcula é o backend**.
- **Dependências:** nenhuma dura.
- **Métrica de sucesso:** frequência de recompra (pedidos/contato/mês), % de clientes que atingem o limiar, retenção 30/60/90 dias.

### 3. Reativação de cliente inativo (win-back)

- **Problema de negócio:** clientes somem em silêncio. Cada cliente que parou de pedir é receita recorrente perdida que ninguém está tentando recuperar — e recuperar é muito mais barato que adquirir.
- **Como funciona:** um job agendado (scheduler transversal) roda diariamente, seleciona contatos com último pedido há mais de N dias (config por tenant) e **sem pedido em aberto**, e dispara via Evolution uma mensagem de win-back ("faz tempo que a gente não te vê — essa semana tem 15% off no seu vinho favorito"). Pode ancorar o cupom da feature #1 (código dedicado de win-back, rastreável). Painel: tela "Reativação" com a régua (dias de inatividade, texto, cupom vinculado) e o log de disparos. **Respeita a trava +18** (a mensagem não fecha pedido — só reengaja; o pedido em si passa pelo fluxo normal com `age_confirmed`).
- **Dependências:** scheduler/cron (transversal); reaproveita cupom (#1) e o módulo de campanha (#10); opt-out obrigatório por LGPD/WhatsApp.
- **Métrica de sucesso:** % de inativos reativados, receita recuperada por disparo, taxa de opt-out.

### 4. Pagamento online / Pix na conversa

- **Problema de negócio:** pedido fica só "combinado", sujeito a calote, cliente fantasma e pagamento na entrega que trava o caixa e o entregador. Cobrar antecipado reduz perda e garante o pedido.
- **Como funciona:** ao emitir a tag e nascer `aguardando`, o backend gera cobrança Pix/cartão pelo gateway e a IA envia o link/QR na conversa; ao confirmar o webhook do gateway, o pedido pode ir para `em_preparo` (ou marcar `paid=true` mantendo o gate humano). Painel mostra status de pagamento por pedido. **Não muda a trava +18** (pagamento só entra depois do `age_confirmed` e do cálculo de total no backend).
- **Dependências:** **gateway de pagamento (pendência global #50)** — bloqueada até destravar. Depois de pronta, também habilita sinal do clube (#7).
- **Métrica de sucesso:** % de pedidos pagos antecipadamente, queda de no-show/calote, tempo até confirmação.

### 5. Upsell/cross-sell proativo da IA (harmonização + acessórios)

- **Problema de negócio:** a IA hoje só monta o que o cliente pediu. Cada conversa é uma chance perdida de subir o ticket sugerindo o que combina — a margem de acessórios (taça, saca-rolha, gelo) e o segundo rótulo são pura receita incremental.
- **Como funciona:** a persona ADEGA (que já **pode sugerir harmonização entre itens do cardápio**) ganha instrução explícita de, ao fechar, oferecer **1 sugestão contextual do próprio cardápio** (um espumante que harmoniza, um acessório, "leva mais uma pra fechar o frete grátis" quando falta pouco pro mínimo/cupom). Continua **sem inventar rótulo/preço fora do cardápio** e **sem incentivar consumo excessivo** (respeita a trava: sugestão de conveniência, nunca "beba mais"). O cross-sell lê o cache de cardápio já existente; nenhuma tabela nova.
- **Dependências:** nenhuma dura. Ganha força com estoque (#6, não sugerir o que acabou) e cupom (#1, gatilho de "falta pouco").
- **Métrica de sucesso:** itens por pedido, ticket médio, % de conversas em que a sugestão é aceita.

## Dependências transversais

- **Gateway de pagamento (pendência global #50):** destrava #4 (Pix/cartão na conversa) e o **sinal/mensalidade do clube de assinatura (#7)**; sem ele, essas features ficam como "combinar pagamento fora do app".
- **Upload de foto/anexo (bloqueado hoje por `SERVICE_ROLE_KEY` ausente):** destrava #16 (foto do rótulo no cardápio e na conversa) e enriquece a página pública/CMS (#15) e as campanhas (#10) com imagem do produto.
- **Scheduler/cron:** destrava #3 (win-back), #11 (auto-transição + lembrete de entrega), #12 (aniversário) e a renovação do clube (#7). É a infra de maior efeito multiplicador — uma vez pronta, várias features de retenção/operação passam a existir "de graça".
- **Módulo de campanha em massa segmentada (#10):** é a base de disparo reaproveitada por #3 (win-back), #12 (aniversário) e #14 (indicação); construir o motor de segmentação + envio Evolution uma vez habilita todo o eixo de marketing outbound, sempre com opt-out por LGPD.
