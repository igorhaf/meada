# Features Sugeridas — Lavanderia

> Backlog de features avançadas para o nicho **Lavanderia** (profile_id `lavanderia`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Pedido agendado por coleta+entrega** com catálogo de serviços (preço por peça + `turnaround_days` + cuidado) e opções/modifiers, montado pela IA na conversa.
- **DUAS datas ligadas por turnaround:** `delivery_date` materializada = `collect_date + MAX(turnaround_days dos itens)`; entrega antes disso → 422 `turnaround_violation`.
- **Gate de aceite humano:** pedido nasce `aguardando`; a lavanderia aceita (→`coletado`) ou recusa no painel — a IA nunca aceita/recusa.
- **Funil de status com notificação WhatsApp** por etapa (`coletado`/`pronto`/`saiu_entrega`/`entregue`/`recusado`) via Evolution.
- **Kanban de pedidos** + config (taxa de entrega, pedido mínimo, turnaround default) + base de conhecimento (RAG) por tenant.
- **Trava da IA:** nunca inventa serviço/preço, nunca promete remover mancha/garantir resultado, sempre coleta+entrega (sem balcão).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Assinatura mensal / plano de lavagem recorrente (Receita, esforço M).** É o maior alavanca de receita PREVISÍVEL do nicho: cliente que lava roupa toda semana vira MRR em vez de transação avulsa. Um plano ("4 coletas/mês", "franquia de X peças/mês") transforma o cliente ocasional em fixo, reduz drasticamente a evasão e estabiliza o caixa da lavanderia. O chassi de assinatura já existe maduro no monólito (academia/escola/cursos), então é reuso de padrão — não invenção do zero. Vende porque o dono da lavanderia enxerga faturamento recorrente garantido; retém porque quem tem plano não vai na concorrência.

**2. Serviço EXPRESS / 24h com sobretaxa (Receita, esforço P).** Está explicitamente listado como "NÃO existe nesta fase" e é dinheiro fácil: quem precisa da roupa pra amanhã paga mais. Basta um flag `express` no pedido (ou por item) que encurta o turnaround e soma uma sobretaxa configurável ao total materializado. É P porque reaproveita o cálculo de `delivery_date` e o recálculo de total que já existem — só inverte a regra do turnaround e adiciona um delta de preço. Margem alta, esforço mínimo, diferencia da concorrência.

**3. Reativação automática de cliente inativo (Retenção, esforço M).** Lavanderia tem cadência natural (a roupa suja volta a acumular). Um scheduler que detecta quem não faz pedido há N dias e dispara uma mensagem WhatsApp ("faz X semanas que não cuidamos das suas roupas — quer agendar uma coleta?") recupera receita que ia embora em silêncio. É o típico "dinheiro na mesa": custo quase zero por mensagem, alto retorno. Depende do scheduler transversal, que também destrava outras features.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Assinatura mensal / plano de lavagem recorrente | Alto | M | Receita recorrente previsível + trava o cliente fixo | Receita |
| 2 | Serviço EXPRESS / 24h com sobretaxa | Alto | P | Vende urgência com margem alta; turnaround curto pago | Receita |
| 3 | Reativação automática de inativo (WhatsApp) | Alto | M | Recupera cliente que sumiu na cadência natural da roupa | Retenção |
| 4 | Pagamento/sinal online (Pix/cartão) | Alto | M | Fecha venda na conversa, reduz calote/no-show na coleta | Receita |
| 5 | Fidelidade por selos ("a cada 10 lavagens, 1 grátis") | Alto | M | Incentiva repetição; retém contra a lavanderia da esquina | Retenção |
| 6 | Cupom de desconto (primeira coleta / campanha) | Alto | P | Converte novato e recupera carrinho abandonado | Receita |
| 7 | Confirmação de coleta D-1 + lembrete "estar em casa" | Alto | P | Corta coleta furada (motoboy que vai e não tem ninguém) | Operação |
| 8 | Pesagem real + reprecificação (edredom/pesados por kg) | Alto | M | Cobra o justo em peça pesada; para de perder margem | Receita |
| 9 | Rastreio/status por link + posição do motoboy | Médio | M | Reduz "cadê minha roupa?"; melhora percepção de serviço | Operação |
| 10 | Etiqueta/QR por peça (controle de extravio) | Médio | M | Evita perda de peça e disputa; diferencial de confiança | Operação |
| 11 | Campanha em massa segmentada (WhatsApp) | Alto | M | Promoção sazonal (edredom no inverno) pra base inteira | Marketing |
| 12 | NPS / avaliação pós-entrega | Médio | P | Mede satisfação, capta review, sinaliza cliente em risco | Retenção |
| 13 | Programa de indicação com bônus | Médio | M | Aquisição barata: cliente traz cliente por crédito | Marketing |
| 14 | Scheduler de auto-transição + lembrete de peça pronta | Médio | P | Peça "pronta" há dias sem retirar → cobra o cliente | Operação |
| 15 | Cross-sell/upsell proativo da IA (impermeabilização, passar) | Médio | P | Aumenta ticket médio sem esforço humano | IA |
| 16 | Multi-unidade (filiais/pontos de coleta) | Médio | G | Escala a operação; roteia pedido pra unidade certa | Operação |

## Detalhamento das prioritárias

### 1. Assinatura mensal / plano de lavagem recorrente

- **Problema de negócio:** a lavanderia vive de transações avulsas e imprevisíveis; não há vínculo que segure o cliente nem receita garantida no fim do mês.
- **Como funciona:** o tenant cadastra planos (`lavanderia_plans`: nome, ciclo mensal, franquia — nº de coletas ou de peças/mês, preço). A IA oferece o plano na conversa quando percebe recorrência ("você lava toda semana; nosso plano X sai mais em conta") e emite uma tag `<assinatura_lavanderia>` que abre a assinatura em `pendente` (gate humano igual ao pedido — a IA nunca ativa cobrança). Ativada, cada pedido dentro da franquia debita o saldo do ciclo (contador materializado na transação, espelho do saldo de pacote da estética). Status por parity (`ativa⇄suspensa; →cancelada`), notificação de boas-vindas/renovação. Painel: tela "Planos" + coluna de saldo no pedido.
- **Dependências:** cobrança automática real depende do gateway (#50); sem ele, funciona como controle de franquia + cobrança manual registrada (mesmo modelo de `academia_payments`).
- **Métrica de sucesso:** % de clientes com plano ativo; MRR do nicho; queda na taxa de churn mensal.

### 2. Serviço EXPRESS / 24h com sobretaxa

- **Problema de negócio:** cliente com pressa hoje ouve "só fica pronto em 3 dias" e vai embora; a lavanderia deixa de capturar quem pagaria mais por velocidade.
- **Como funciona:** flag `express` por pedido (ou por item) com `express_surcharge` configurável (percentual ou valor fixo por peça) e um `express_turnaround_days` reduzido. Quando o cliente pede urgência, a IA confirma a sobretaxa e emite a tag com `express:true`; o backend recalcula `delivery_date` com o turnaround curto e soma a sobretaxa ao total materializado (o cálculo de total e data já existem — só muda a regra). A trava permanece: a IA informa a sobretaxa, nunca a inventa fora da config.
- **Dependências:** nenhuma externa; reusa `delivery_date` materializada e recálculo de total.
- **Métrica de sucesso:** % de pedidos express; incremento de ticket médio; receita adicional de sobretaxa.

### 3. Reativação automática de cliente inativo

- **Problema de negócio:** cliente que fazia coleta toda semana some e ninguém percebe; a receita evapora silenciosamente.
- **Como funciona:** um job agendado (cron/scheduler) roda diário, calcula o intervalo médio entre pedidos por contato e detecta quem passou de um limiar (ex.: 1,5× o intervalo típico, ou N dias sem pedido). Dispara mensagem WhatsApp personalizada via Evolution ("faz 3 semanas que não cuidamos das suas roupas — quer agendar uma coleta? 🧺"), respeitando janela de horário e opt-out. Opcionalmente anexa um cupom (feature #6) para incentivar o retorno.
- **Dependências:** scheduler/cron transversal; integra com cupom (#6) e campanha (#11) se existirem.
- **Métrica de sucesso:** taxa de reativação (respostas → pedidos) por campanha; receita recuperada; queda de clientes classificados como inativos.

### 4. Pagamento/sinal online (Pix/cartão)

- **Problema de negócio:** hoje o pagamento é fora do app; há calote e coleta furada (motoboy vai, cliente some). Cobrar (ou pegar sinal) na hora de fechar reduz perda e antecipa caixa.
- **Como funciona:** ao confirmar o pedido, a IA envia um link de pagamento (Pix/cartão) do gateway; o webhook do gateway marca o pedido como pago/sinal-pago e libera a coleta. Configurável por tenant: pagamento total antecipado, sinal parcial, ou pagamento na entrega. O gate de aceite humano continua — o pagamento apenas o precede/complementa. A IA nunca confirma pagamento sem retorno do gateway.
- **Dependências:** gateway de pagamento (#50) — bloqueante direto.
- **Métrica de sucesso:** % de pedidos com pré-pagamento; redução de coleta furada e calote; dias de antecipação de caixa.

### 5. Fidelidade por selos ("a cada 10 lavagens, 1 grátis")

- **Problema de negócio:** lavanderia é commodity de bairro; sem vínculo, o cliente troca por qualquer promoção da concorrência.
- **Como funciona:** contador de fidelidade por contato (`lavanderia_loyalty`: pedidos concluídos válidos → selos). Configurável: X pedidos entregues geram Y de recompensa (percentual, valor fixo ou serviço grátis). Ao atingir o gatilho, o desconto é aplicado automaticamente no próximo pedido e a IA comunica ("essa é sua 10ª lavagem — a próxima peça sai por nossa conta 🎁"). Espelha a fidelidade por contagem já existente no sushi/comida. Painel mostra o saldo de selos por cliente.
- **Dependências:** nenhuma externa; conta sobre pedidos `entregue` (terminal não-cancelado).
- **Métrica de sucesso:** frequência de pedidos por cliente fidelizado; % de recompensas resgatadas; retenção a 90 dias.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava pagamento/sinal online (#4), cobrança automática da assinatura (#1, que sem ele fica em cobrança manual) e resgate monetário do programa de indicação (#13). É o bloqueador de maior impacto em receita direta.
- **Scheduler/cron transversal:** destrava reativação de inativo (#3), confirmação de coleta D-1 e lembretes (#7, #14) e renovação/aviso de assinatura (#1). Uma vez existente, vira infra reutilizável por vários nichos.
- **Campanha em massa segmentada (#11):** destrava reativação em lote, promoção sazonal e distribuição de cupom (#6) para a base. Depende de infra de disparo + segmentação por comportamento.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava referência de mancha na coleta, laudo antes/depois de peça delicada e comprovante fotográfico do estado da peça — todos hoje impossíveis. Nenhuma feature deste backlog depende dela como bloqueante primário, mas ela habilita uma camada de confiança/laudo no futuro.
