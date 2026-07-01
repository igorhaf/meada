# Features Sugeridas — Floricultura

> Backlog de features avançadas para o nicho **Floricultura** (profile_id `floricultura`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Catálogo com categorias + modifiers:** itens em 6 categorias hardcoded (buquês, arranjos, cestas, plantas, coroas, acessórios), com opções/adicionais por grupo ("Tamanho", "Adicionais") que somam ao preço base (`FloriculturaCatalogOption`, price_delta).
- **Pedido montado na conversa pela IA (carrinho relido do histórico) + tag `<pedido_flor>`:** a IA interpreta o pedido em texto livre; o backend recalcula o total a partir do catálogo (descarta o total que a IA mandou) e faz snapshot de preço/nome/opções no item.
- **Presente AGENDADO para outra pessoa (a escapada do nicho):** o pedido carrega `delivery_date` (dia) + `delivery_period` (manhã 8–12h / tarde 13–18h), `recipient_name` (quem recebe) e `card_message` (cartãozinho, nullable). É a natureza "flor é presente" já modelada.
- **Gate de aceite humano + Kanban de status:** o pedido nasce `aguardando`; a loja aceita (→ `em_preparo`) ou recusa (→ `recusado` com motivo). Fluxo `aguardando → em_preparo → saiu_entrega → entregue` (+ recusado/cancelado), cada transição dispara notificação WhatsApp fixa (a IA não aceita/recusa).
- **Config de delivery:** taxa de entrega + pedido mínimo em centavos por tenant.

**Bloqueadores globais herdados:** SEM pagamento real (Stripe/Pix = pendência #50), SEM foto de produto (SERVICE_ROLE_KEY ausente), SEM cupom/fidelidade, SEM scheduler/cron de auto-transição ou lembrete, SEM campanha em massa, SEM avaliação/NPS, SEM CMS/página pública ativa para o nicho.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/pagamento antecipado do buquê (link Pix) — trava o pedido e mata o no-show da data.**
Flor é presente com DATA marcada (aniversário, Dia das Mães, Dia dos Namorados) — se o cliente some, a loja montou o arranjo perecível à toa. Cobrar um sinal (ou o valor cheio) via link de pagamento no momento do aceite ancora o compromisso e adianta o caixa nas datas de pico, quando o volume explode. É o maior gerador de receita/proteção de margem do nicho. Depende do gateway #50; enquanto não existe, dá pra entregar como "confirmação de pagamento manual" (a loja marca `pago` no card) para já capturar o dado no fluxo.

**2. Lembrete de datas comemorativas do cliente (aniversário/namorados/mães) com sugestão proativa.**
Floricultura vive de recorrência sazonal e de "eu esqueci a data". Se a loja captura as datas importantes do cliente (aniversário da esposa, da mãe) e a IA dispara, dias antes, "o aniversário da sua mãe é sexta — quer que eu monte o mesmo arranjo do ano passado?", a loja reativa venda sem esforço e vira "a florista que lembra por mim". Retenção altíssima e recompra recorrente. Depende de scheduler/cron + campanha; é o que mais aumenta LTV.

**3. Reprise/recompra de 1 clique ("mandar de novo pra ela") + histórico de destinatários.**
Como o pedido já guarda `recipient_name`, `card_message` e os itens, a IA consegue oferecer "quer repetir o buquê que você mandou pra Ana no mês passado, pro mesmo endereço?". Reduz a fricção da recompra a segundos e explora o fato de que o comprador manda flor para as MESMAS pessoas repetidamente. Esforço baixo (dado já existe), receita incremental imediata.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/pagamento antecipado via link (Pix/cartão) no aceite | Alto | M | Trava o pedido perecível de data marcada; adianta caixa nos picos; mata no-show | Receita |
| 2 | Lembrete de datas comemorativas do cliente (aniversário/mães/namorados) + oferta proativa da IA | Alto | M | Reativa venda sazonal sem esforço; "a florista que lembra por mim" | Retenção |
| 3 | Recompra de 1 clique ("mandar de novo pra ela") com histórico de destinatários | Alto | P | Elimina fricção da recompra recorrente ao mesmo destinatário | Receita |
| 4 | Upsell/cross-sell proativo da IA (cartão especial, chocolate, vaso, urso, +1 tamanho) | Alto | P | Aumenta ticket médio em cada pedido, no momento da compra | Receita |
| 5 | Taxa de entrega por bairro/CEP + raio de cobertura | Alto | M | Cobra frete justo por distância e recusa endereço fora do raio antes de aceitar | Receita/Operação |
| 6 | Slots de entrega com capacidade por período (limite de entregas por manhã/tarde) | Alto | M | Evita vender mais entregas do que a equipe consegue rodar em datas de pico | Operação |
| 7 | Cupom de desconto + primeira compra / data comemorativa | Médio | P | Converte indeciso e cria gancho de campanha sazonal | Receita/Marketing |
| 8 | Fidelidade/cashback ("a cada 5 buquês, 1 arranjo de brinde") | Médio | M | Retém o comprador recorrente de flor; incentiva concentrar compras na loja | Retenção |
| 9 | Confirmação D-1 da entrega + lembrete automático ao comprador | Alto | M | Confirma endereço/horário antes de sair; reduz entrega furada e retrabalho | Operação/Retenção |
| 10 | Pós-venda: foto da entrega feita + NPS + pedido de avaliação | Médio | M | Prova de entrega ao comprador (presente à distância) e reputação | Marketing/Retenção |
| 11 | Assinatura/clube de flores (arranjo recorrente semanal/quinzenal) | Alto | G | Receita previsível (MRR) e cliente cativo; diferencial forte no nicho | Receita/Retenção |
| 12 | Reativação de cliente inativo (não compra há X dias) via campanha segmentada | Médio | M | Traz de volta quem comprou uma vez e sumiu, com oferta sazonal | Retenção/Marketing |
| 13 | Anonimato do remetente / "presente surpresa" (esconder quem enviou no cartão) | Médio | P | Atende declaração/segredo — caso de uso clássico e emocional da flor | Operação |
| 14 | Vitrine/CMS público por tenant (catálogo com foto e link do WhatsApp) | Médio | M | Loja ganha página de venda própria; IA converte o clique em pedido | Marketing |
| 15 | Estoque/disponibilidade por item com corte automático em data de pico | Médio | M | Evita vender rosa vermelha esgotada no Dia dos Namorados | Operação |
| 16 | Rastreio/rota da entrega (link de status + integração mapa) | Médio | G | Comprador acompanha o presente a caminho; reduz "já chegou?" | Operação/Integração |

## Detalhamento das prioritárias

### 1. Sinal/pagamento antecipado via link no aceite

- **Problema de negócio:** o arranjo é perecível e feito para uma DATA. Cliente que some após pedir gera prejuízo direto (flor montada, entrega reservada). Nas datas de pico o risco multiplica. Sem pagamento, a loja trabalha na confiança e absorve o furo.
- **Como funciona:** ao aceitar o pedido (transição `aguardando → em_preparo`), o painel gera um link de pagamento (sinal % configurável ou valor cheio); a IA envia o link ao comprador via Evolution ("pra confirmar a entrega no dia X, é só concluir o pagamento aqui: ..."). O webhook do gateway marca o pedido como `pago` e só então libera o `em_preparo` definitivo (ou mantém em `aguardando_pagamento`, novo estado intermediário). O total já é recalculado no backend (chassi atual), então o link usa o valor materializado — a IA nunca inventa preço. A loja vê o selo "pago/sinal pago" no card do Kanban.
- **Dependências:** gateway de pagamento (pendência global #50). Enquanto não existe, entregar a versão "confirmação manual" (campo `paid` + selo no card, a loja marca à mão) para já modelar o dado e a coluna no funil.
- **Métrica de sucesso:** % de pedidos com sinal pago; queda na taxa de recusa/cancelamento após aceite; receita adiantada em datas de pico.

### 2. Lembrete de datas comemorativas do cliente

- **Problema de negócio:** boa parte da venda de flor é movida por data (aniversário de alguém, Dia das Mães/Namorados) e por esquecimento. Quem não é lembrado, não compra. A loja perde a recompra previsível.
- **Como funciona:** a IA/painel captura datas importantes do contato (aniversário da mãe, da parceira, aniversário de namoro) e o destinatário associado (reusa `recipient_name`). Um scheduler diário varre datas a X dias e dispara, via campanha WhatsApp, uma oferta proativa: "sexta é aniversário da sua mãe — quer repetir o arranjo do ano passado ou montar um novo?". A IA já entra na conversa com o histórico do destinatário carregado (contexto), pronta para fechar. Respeita a trava do nicho (não inventa item/preço; só oferece o que está no catálogo).
- **Dependências:** scheduler/cron + infra de campanha em massa segmentada. Sem foto não bloqueia (texto basta).
- **Métrica de sucesso:** taxa de conversão dos lembretes; nº de recompras originadas por lembrete; LTV do cliente que ativou datas.

### 3. Recompra de 1 clique / histórico de destinatários

- **Problema de negócio:** o mesmo comprador manda flor repetidamente para as MESMAS pessoas (mãe, parceira, cliente corporativo). Refazer o pedido do zero a cada vez é fricção que derruba a recompra.
- **Como funciona:** como `recipient_name`, `card_message`, endereço e itens já ficam no pedido, a IA passa a oferecer "quer repetir o buquê que mandou pra Ana em maio, pro mesmo endereço?". Basta o comprador confirmar e ajustar a nova data/cartão — a tag `<pedido_flor>` é remontada a partir do pedido anterior. No painel, um botão "duplicar pedido" faz o mesmo para atendimento manual.
- **Dependências:** nenhuma além do dado já existente (leitura do histórico de pedidos do contato no contexto da IA).
- **Métrica de sucesso:** % de pedidos originados de recompra; tempo médio para fechar um pedido repetido; frequência de compra por cliente.

### 4. Upsell/cross-sell proativo da IA

- **Problema de negócio:** o ticket médio fica no valor do arranjo cru. A loja tem margem em adicionais (cartão especial, chocolate, vaso, pelúcia, subir um tamanho) que raramente são oferecidos no balcão digital.
- **Como funciona:** ao fechar o carrinho, a IA sugere 1–2 adicionais RELEVANTES do próprio catálogo/modifiers ("quer incluir um cartão escrito à mão por +R$X ou uma caixa de bombons?"). É oferta baseada no catálogo (modifiers/itens já existentes), nunca invenção de item/preço — respeita a trava. Configurável: o tenant marca quais itens/opções são "sugeríveis" e a IA prioriza.
- **Dependências:** nenhuma dura (usa catálogo + modifiers atuais); só ajuste de persona/contexto da IA.
- **Métrica de sucesso:** ticket médio antes/depois; taxa de aceite da sugestão; receita incremental por adicionais.

### 5. Taxa de entrega por bairro/CEP + raio de cobertura

- **Problema de negócio:** hoje a taxa de entrega é única para todo mundo. Entrega perto subsidia a longe (perda de margem) e a loja aceita endereços fora do raio que não consegue rodar no dia — gerando entrega furada e cliente frustrado.
- **Como funciona:** a config de delivery ganha uma tabela de zonas (bairro ou faixa de CEP → taxa) + raio máximo. Ao montar o pedido, a IA/backend resolve a taxa pela zona do endereço; endereço fora do raio → a IA avisa e oferece retirada, em vez de aceitar às cegas. O total é recalculado no backend com a taxa da zona (chassi de recálculo já existe).
- **Dependências:** opcional integração com mapa/CEP (ViaCEP para bairro; distância exige mapa). Versão simples por bairro/CEP não depende de terceiros.
- **Métrica de sucesso:** margem por entrega; queda de entregas fora de área; nº de conversões para retirada em vez de recusa.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava #1 (sinal/pagamento no aceite), #7 (cupom cobrando de fato), #8 (cashback com saldo real), #11 (assinatura/clube com cobrança recorrente). Enquanto não existe, todas essas rodam em modo "confirmação manual" (loja marca pago à mão) — capturam o dado e o funil, sem a cobrança automática.
- **Upload de foto/anexo (SERVICE_ROLE_KEY):** destrava #10 (foto da entrega feita como prova ao comprador), a foto real de produto no catálogo e no CMS público (#14), e o "presente surpresa" com foto. Hoje tudo isso vive só de texto/link colado.
- **Scheduler/cron:** destrava #2 (lembrete de datas comemorativas), #9 (confirmação D-1), #12 (reativação de inativo) e a auto-transição de status. É a peça que converte features passivas em automação proativa — provavelmente o maior multiplicador de retenção do nicho.
- **Campanha em massa segmentada:** destrava #2, #7, #12 no volume (disparo para uma audiência filtrada: compradores de uma data, inativos há X dias, clientes de coroa/luto vs. datas felizes — segmentação sensível ao contexto emocional da flor).
- **Contexto/persona da IA:** #3 (recompra), #4 (upsell) e #13 (anonimato) são majoritariamente evolução do prompt + leitura do histórico já persistido — as de menor esforço e retorno rápido, sem depender de infra global.
