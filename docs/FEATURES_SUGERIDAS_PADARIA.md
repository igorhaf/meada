# Features Sugeridas — Padaria & confeitaria

> Backlog de features avançadas para o nicho **Padaria & confeitaria** (profile_id `padaria`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Cardápio pronta-entrega × sob-encomenda com lead time** — cada item tem `made_to_order` + `lead_time_days`; a data do pedido é condicional (obrigatória só com encomenda) e valida `hoje + MAX(lead_time)` no fuso America/Sao_Paulo (422 `lead_time_violation`).
- **Pedido por conversa livre + tag `<encomenda_padaria>`** — a IA monta o carrinho relendo o histórico e emite a tag na confirmação; o backend recalcula o total (descarta o da IA) e materializa snapshots de item/opção.
- **Modifiers + personalização do bolo** — opções planas Sabor/Recheio/Tamanho (`padaria_menu_item_options` com `price_delta`) e `cake_message` (placa) por item do pedido.
- **Retirada × entrega** — `fulfillment` com taxa e endereço obrigatório na entrega (422 `address_required`); funil de status diverge no fim.
- **Kanban com gate de aceite humano** — `PadariaOrderStatus` (aguardando→em_preparo→pronto→{retirado | saiu_entrega→entregue}); aguardando não notifica; a IA não aceita/recusa.
- **Categorias hardcoded** (paes/salgados/doces_balcao/bolos_encomenda/tortas/bebidas) + config de taxa/mínimo/lead default.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/entrada obrigatório em bolo sob encomenda (anti-furo de encomenda).** O maior prejuízo de uma confeitaria não é o pedido pequeno — é o bolo de encomenda que o cliente encomenda, a padaria compra insumo e produz, e o cliente não busca. Exigir um sinal (ex.: 30% ou valor fixo) no ato da encomenda transforma "reserva verbal" em compromisso financeiro. Reduz no-show de encomenda a quase zero, financia a compra de insumo antecipada, e o restante fica pra retirada. É a feature que mais protege margem no nicho — depende do gateway (#50), mas o fluxo de exigir sinal antes de mover `aguardando→em_preparo` pode nascer com registro manual (a padaria confirma "recebi o Pix") e ganhar o gateway depois.

**2. Assinatura de pães/café da manhã recorrente (receita previsível).** Padaria de bairro vive de recorrência: o cliente que compra pão todo dia. Uma assinatura semanal/mensal (ex.: "6 pães + 1 café toda manhã", ou "cesta de fim de semana") gera MRR, fideliza e antecipa produção. Clona o chassi de assinatura da Academia (7.7): plano recorrente, status ativa/suspensa/cancelada, entregas materializadas. Transforma venda avulsa imprevisível em base recorrente — o ativo de maior valor de um SaaS de padaria. Alto valor, esforço M (chassi já existe no monólito).

**3. Reativação de cliente inativo + lembrete de recompra sazonal (IA proativa).** A padaria tem um dado ouro que não usa: quem comprou bolo de aniversário ano passado. Uma campanha automática ("Faz X meses que você não pede — que tal um bolo pro fim de semana?") e o gatilho sazonal (Dia das Mães, Páscoa, festa junina, Natal — datas em que confeitaria fatura o ano) trazem de volta o cliente adormecido sem custo de mídia. Reengaja a base pelo canal que já converte (WhatsApp). Depende de campanha em massa segmentada (transversal), mas o ROI é altíssimo porque reusa contato já existente.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/entrada em encomenda (bloqueia aceite sem sinal) | Alto | M | Elimina furo de bolo de encomenda; financia insumo antecipado | Receita |
| 2 | Assinatura de pães / café da manhã recorrente | Alto | M | MRR previsível, fideliza cliente diário, antecipa produção | Receita |
| 3 | Reativação de inativo + gatilho sazonal (Páscoa/Natal/Mães) | Alto | M | Traz de volta cliente adormecido nas datas que mais faturam | Retenção |
| 4 | Fidelidade "compre 10, ganhe 1" (cartão digital de pães/cafés) | Alto | M | Retém o freguês diário; incentiva recompra sem desconto direto | Retenção |
| 5 | Cupom de desconto (primeira encomenda, aniversário do cliente) | Alto | P | Converte quem hesita; brinde de aniversário gera pedido | Receita |
| 6 | Upsell/cross-sell proativo da IA (vela, refri, cartão, docinhos) | Alto | P | Aumenta ticket médio de todo pedido com sugestão no fecho | IA |
| 7 | Lembrete automático de retirada/entrega (D-1 e no dia) | Alto | P | Reduz encomenda esquecida/não retirada; confirma presença | Operação |
| 8 | Scheduler de auto-transição + alerta de encomenda do dia | Médio | P | Painel de produção do dia; nada de encomenda perdida na cozinha | Operação |
| 9 | Encomenda de bolo artístico ad-hoc com orçamento e aprovação | Alto | M | Fecha bolo temático/personalizado (maior margem) via proposta | Receita |
| 10 | Pesquisa de satisfação pós-entrega (NPS + nota do bolo) | Médio | P | Mede qualidade, capta elogio pra review e reclamação a tempo | Retenção |
| 11 | Encomenda para data comemorativa com catálogo sazonal | Alto | M | Vitrine de Páscoa/Natal/festa junina com pré-venda antecipada | Marketing |
| 12 | Página pública / CMS da padaria (cardápio + encomendas online) | Médio | M | Vitrine própria com link na bio; capta pedido fora do horário | Marketing |
| 13 | Foto do bolo na conversa e no catálogo (quando liberar upload) | Alto | M | Vender bolo pela imagem; referência de tema pelo cliente | IA/Marketing |
| 14 | Controle de estoque/produção com limite de encomendas por dia | Médio | M | Impede vender mais bolo do que a cozinha aguenta no dia | Operação |
| 15 | Indicação com recompensa ("indique e ganhe um pão de queijo") | Médio | P | Crescimento orgânico via boca a boca incentivado | Marketing |
| 16 | Multi-unidade (filial/ponto) com cardápio e agenda por loja | Médio | G | Rede de padarias com estoque e encomenda por unidade | Operação |

## Detalhamento das prioritárias

### 1. Sinal/entrada em encomenda

- **Problema de negócio:** o bolo de encomenda produzido e não retirado é prejuízo puro (insumo + mão de obra + oportunidade). Sem compromisso financeiro, a "reserva" é só uma promessa; o cliente cancela ou some sem custo.
- **Como funciona:** na config do tenant, um toggle "exigir sinal em encomenda" + percentual/valor. Quando o pedido tem item `made_to_order`, o backend calcula o sinal sobre o total e o gate de aceite (`aguardando→em_preparo`) fica BLOQUEADO até o sinal ser confirmado (novo campo `deposit_cents` + `deposit_paid` no pedido). A IA informa o valor do sinal e as instruções ("para reservar seu bolo, um sinal de R$X é necessário") — respeitando a trava (a IA NÃO confirma pagamento, quem confirma é a padaria no painel, ou o gateway quando existir). No painel, o card mostra "sinal pendente/recebido" e só libera o aceite com sinal recebido.
- **Dependências:** confirmação manual de sinal já destrava a v1; o Pix/cartão automático depende do gateway (#50). Nada de foto.
- **Métrica de sucesso:** % de encomendas com sinal, queda no nº de encomendas não retiradas, valor recuperado em insumo.

### 2. Assinatura de pães / café da manhã recorrente

- **Problema de negócio:** a padaria não captura o valor do cliente diário — cada compra é avulsa, imprevisível e sem vínculo. Falta receita recorrente, o ativo mais valioso de qualquer negócio.
- **Como funciona:** clona o chassi de assinatura da Academia (7.7). O tenant cadastra planos ("Cesta manhã: 6 pães + 1 café, seg-sex", "Fim de semana"), com preço mensal/semanal e regra de entrega/retirada. A IA oferece e matricula via tag própria (`<assinatura_padaria>`), status `ativa/suspensa/cancelada`, anti-dupla por contato. O scheduler materializa as entregas do período (kanban de "assinaturas do dia" pra cozinha). Pagamento manual na v1 (registro mensal, UNIQUE por mês → sem duplicar), Stripe quando existir.
- **Dependências:** chassi de assinatura já existe (academia/escola/cursos); pagamento real depende de #50; scheduler (transversal) para materializar entregas.
- **Métrica de sucesso:** nº de assinaturas ativas, MRR gerado, churn mensal, % de faturamento vindo de recorrência.

### 3. Reativação de inativo + gatilho sazonal

- **Problema de negócio:** a base de contatos que já pediu é subutilizada; datas comemorativas (que respondem por grande parte do faturamento de confeitaria) passam sem uma ação ativa sobre quem já é cliente.
- **Como funciona:** um job identifica contatos sem pedido há N dias (configurável) e contatos que pediram bolo em datas específicas no ano anterior. Dispara mensagem segmentada pelo WhatsApp ("Faz tempo! Bora um bolo?" / "Páscoa chegando — reserve seu ovo/colomba"). O tenant escolhe a audiência e edita o texto no painel de campanhas; a IA continua o atendimento normal se o cliente responder. Respeita a trava (não inventa produto/preço — usa o cardápio ativo).
- **Dependências:** campanha em massa segmentada (transversal); scheduler/cron para o gatilho sazonal e o corte de inatividade.
- **Métrica de sucesso:** taxa de resposta da campanha, pedidos reativados, receita atribuída por data comemorativa.

### 4. Fidelidade "compre 10, ganhe 1"

- **Problema de negócio:** o freguês diário não tem incentivo pra concentrar as compras nesta padaria em vez da concorrente da esquina; falta um mecanismo de retenção que não corroa margem com desconto.
- **Como funciona:** cartão digital por contato — a cada N pedidos (ou N itens de uma categoria, ex.: pães/cafés) o cliente ganha 1 grátis. O backend conta os pedidos entregues do contato (espelho da fidelidade por contagem do Sushi, migration 69) e aplica o brinde automático no próximo pedido, com `loyalty_applied`. A IA informa o progresso ("faltam 2 pedidos pro seu pão de queijo grátis") lendo do contexto. Config por tenant: threshold + recompensa (item/percentual).
- **Dependências:** nenhuma dura — a mecânica de contagem já existe no monólito (Sushi). Não depende de gateway nem foto.
- **Métrica de sucesso:** frequência de recompra dos inscritos, ticket recorrente, % de clientes com cartão ativo.

### 5. Cupom de desconto

- **Problema de negócio:** não há alavanca para converter o cliente indeciso, premiar aniversariante ou impulsionar dia fraco de venda.
- **Como funciona:** clona o chassi de cupom do Sushi (migration 69): CRUD de cupons (percentual/fixo, mínimo de pedido, validade, uso máximo). A IA aceita o `cupom` na tag `<encomenda_padaria>`; o backend VALIDA (ativo/validade/mínimo/uso) e aplica; cupom inválido não aborta o pedido (sai sem desconto). Cupom de aniversário do cliente pode ser disparado pela feature #3.
- **Dependências:** chassi de cupom pronto (Sushi); combina com campanha (#3) e fidelidade (#4). Sem gateway/foto.
- **Métrica de sucesso:** taxa de resgate, incremento de pedidos em dia fraco, ticket médio com/sem cupom.

## Dependências transversais

- **Gateway de pagamento (#50 — Stripe/Pix):** destrava a v2 do **sinal de encomenda (#1)** (cobrança automática do sinal em vez de confirmação manual), o pagamento recorrente da **assinatura (#2)**, e o pré-pagamento de **encomenda sazonal (#11)**. Até lá, todas funcionam com confirmação manual de pagamento no painel.
- **Upload de foto/anexo (bloqueado hoje por SERVICE_ROLE_KEY):** destrava **foto do bolo (#13)** — no catálogo (vender pela imagem) e na conversa (cliente manda a referência de tema; e, no futuro, a IA responde a foto). Também enriquece a **página pública/CMS (#12)** e a **encomenda de bolo artístico (#9)**.
- **Scheduler/cron:** destrava a materialização de entregas da **assinatura (#2)**, o **lembrete automático de retirada/entrega (#7)**, a **auto-transição/alerta de produção (#8)**, o **gatilho sazonal e corte de inatividade (#3)** e o **NPS pós-entrega (#10)**.
- **Campanha em massa segmentada:** destrava **reativação de inativo (#3)**, **indicação (#15)** e a distribuição de **cupom (#5)** por audiência. É a base de todo o eixo de marketing outbound do nicho.
