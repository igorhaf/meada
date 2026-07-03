# Features Sugeridas — Papelaria/gráfica/convites

> Backlog de features avançadas para o nicho **Papelaria/gráfica/convites** (profile_id `papelaria`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Pedido gráfico personalizado** por WhatsApp: a IA monta o pedido na conversa com **tiragem** (`quantity` = 50/100/200), **modifiers** (Papel/Acabamento/Cor/Tamanho via `options` com `price_delta`) e **`custom_text`** por item (texto da placa/convite, snapshot). Total materializado no backend (descarta o da IA).
- **Prova de arte** dentro do pedido: estado `arte_aprovacao` no funil; a papelaria cola a `art_url` (link), o cliente aprova via tag `<aprovacao_arte>` (ou no painel), e `arte_aprovacao → em_producao` só passa com `art_approved=true` (senão 409 `art_not_approved`).
- **Lead time + made_to_order:** item sob encomenda exige `pickup_or_delivery_date ≥ hoje + MAX(leads)` (senão 422 `lead_time_violation`); pronta-entrega dispensa data.
- **Funil de 10 estados** (`PapelariaOrderStatus`, parity) com **gate de aceite humano** (aguardando→aceito; a IA não aceita/recusa) e notificações WhatsApp por transição; **Kanban** de pedidos no painel.
- **fulfillment retirada × entrega** (entrega exige endereço → 422 `address_required` + taxa), config de taxa/pedido mínimo/lead default.
- **Categorias hardcoded** (convites, save_the_date, cartões, papelaria, adesivos, embalagens) + RAG por tenant.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/entrada obrigatório na aprovação da arte (50% pra produzir).** Gráfica personalizada tem o pior calote do varejo: a arte é feita, o cliente aprova, some, e a peça impressa não serve pra mais ninguém (é "Casamento Ana & Bruno"). Cobrar um sinal no momento da aprovação da arte trava esse prejuízo e antecipa caixa. Encaixa direto no gate que já existe (`arte_aprovacao → em_producao` só com pagamento confirmado). É o maior ROI do nicho — protege 100% dos pedidos personalizados e vende a própria produção. Depende do gateway #50, mas o campo `deposit_cents`/`deposit_paid` e a lógica de bloqueio podem ir hoje (marcado "pago" manual pelo tenant) e plugam no Pix depois.

**2. Cálculo de preço por faixa de tiragem (tabela de quantidade).** Hoje `line_total = unit_price × quantity` é LINEAR — 500 convites custam 10× 50 convites, o que é irreal em gráfica (o setup dilui na tiragem). Isso faz a IA passar preço absurdo e perder venda de tiragem alta, justo a mais lucrativa. Uma tabela de faixas por item (1–49 / 50–99 / 100–299 / 300+ com `unit_price` decrescente) faz a IA fechar tiragem grande e o ticket subir. Esforço P (nova tabela + lookup no cálculo do total), retorno imediato no ticket médio.

**3. Reativação de datas comemorativas (a IA volta a puxar o cliente).** Convite/papelaria é venda por EVENTO: quem comprou save the date compra convite, depois lembrancinha, depois o "1 ano". Um scheduler que dispara campanha segmentada por data (Dia das Mães, Natal corporativo, formatura, "seu casamento foi há 1 ano → aniversário de bodas?") reativa cliente inativo com custo zero de mídia. Reaproveita o histórico de pedidos que já existe; é o motor de recompra do nicho.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/entrada obrigatório pra liberar produção da arte | Alto | M | Fim do calote em peça personalizada; caixa antecipado | Receita |
| 2 | Preço por faixa de tiragem (tabela de quantidade) | Alto | P | IA fecha tiragem alta sem preço linear absurdo | Receita |
| 3 | Reativação por data comemorativa (scheduler + campanha) | Alto | M | Recompra por evento (save the date→convite→bodas) | Retenção |
| 4 | Versões/revisões da prova de arte (v1, v2, v3) | Alto | M | Ciclo real de ajuste do layout sem perder histórico | Operação |
| 5 | Upsell/cross-sell proativo da IA (kit convite completo) | Alto | M | Sobe ticket: convite + save the date + tag + menu | Receita/IA |
| 6 | Cupom de desconto (primeira compra / tiragem grande) | Alto | P | Fecha indeciso e incentiva volume | Receita |
| 7 | Aprovação de arte por imagem (quando foto liberar) | Alto | M | Cliente vê o layout no WhatsApp e aprova na hora | Operação/IA |
| 8 | Confirmação/lembrete de retirada e prazo de entrega | Médio | P | Reduz "esqueci de buscar" e no-show de retirada | Operação |
| 9 | Pós-venda + NPS + pedido de foto do evento | Médio | P | Prova social e reabre conversa pra recompra | Marketing |
| 10 | Indicação com recompensa (noiva indica noiva) | Alto | M | Aquisição barata no nicho mais viral (casamento) | Marketing |
| 11 | Orçamento ad-hoc pra convite artístico sob medida | Alto | G | Captura o job premium fora do catálogo | Receita |
| 12 | Estoque de insumos (papel/envelope) com alerta | Médio | M | Evita prometer prazo sem material | Operação |
| 13 | Combo/kit pré-montado (papelaria de festa) | Médio | P | Ticket maior com 1 clique; menos fricção | Receita |
| 14 | Relatórios: faturamento, tiragem média, arte reprovada | Médio | M | Dono enxerga o que vende e onde perde | Operação |
| 15 | Fidelidade por evento (cashback pra próximo pedido) | Médio | M | Amarra o cliente ao longo dos eventos da vida | Retenção |
| 16 | Multi-unidade / franquia (loja física + estúdio) | Médio | G | Escala pra rede de gráficas | Operação |

## Detalhamento das prioritárias

### 1. Sinal/entrada obrigatório pra liberar produção da arte

- **Problema de negócio:** peça personalizada aprovada e não paga = prejuízo total (não revende). É a maior sangria do nicho. Sem sinal, a gráfica financia a produção do cliente e absorve o calote.
- **Como funciona:**
  - *Backend:* `papelaria_orders` ganha `deposit_cents` (default = X% do total, configurável) + `deposit_paid boolean` + `deposit_paid_at`. A transição `arte_aprovacao → em_producao` passa a exigir `deposit_paid=true` (nova trava, espelho do `art_not_approved`) → senão 409 `deposit_required`. Enquanto o gateway #50 não existe, o tenant marca "sinal recebido" no painel (Pix/dinheiro fora do app); quando o #50 entrar, a IA envia o link de pagamento e o webhook seta `deposit_paid`.
  - *IA:* ao capturar a `<aprovacao_arte>`, a persona informa o valor do sinal e como pagar. **NÃO** confirma pagamento sozinha (respeita a trava: a IA não fecha preço nem confirma caixa) — só registra a intenção; a confirmação é humana ou via webhook do gateway.
  - *Painel:* card do pedido mostra selo "Sinal pago / pendente"; botão "Registrar sinal".
- **Dependências:** cobrança automática exige gateway #50; a trava e o campo funcionam hoje em modo manual.
- **Métrica de sucesso:** % de pedidos personalizados com sinal antes da produção; queda de peças produzidas e não retiradas.

### 2. Preço por faixa de tiragem (tabela de quantidade)

- **Problema de negócio:** o preço linear (`unit_price × quantity`) faz a IA cobrar 300 convites como 6× 50 convites. Em gráfica o custo de setup dilui na tiragem — o preço/unidade CAI. Sem isso a loja perde a venda grande (a mais lucrativa) ou tem que fugir do app pra orçar.
- **Como funciona:**
  - *Backend:* nova tabela `papelaria_item_tiers (item_id, min_qty, unit_price_cents)`; no cálculo do `line_total`, em vez de `unit_price` fixo, faz lookup da faixa por `quantity` (maior `min_qty ≤ quantity`) e usa esse `unit_price` como base (+ Σ deltas de modifiers). Sem faixas cadastradas → cai no `unit_price` atual (compat).
  - *IA:* o cache do catálogo injeta a tabela de faixas; a IA apresenta "50 un = R$ X, 100 un = R$ Y (mais barato por unidade)" — estimula subir a tiragem.
  - *Painel:* editor de faixas por item na tela de Catálogo.
- **Dependências:** nenhuma (só schema + cálculo). Não viola trava — preço vem do catálogo, IA não inventa.
- **Métrica de sucesso:** tiragem média por pedido; ticket médio; taxa de pedidos que sobem de faixa.

### 3. Reativação por data comemorativa (scheduler + campanha)

- **Problema de negócio:** papelaria é venda por evento e o cliente some entre eventos. Sem reativação, cada venda é uma aquisição nova. O histórico de quem comprou save the date/convite é uma mina de recompra ociosa.
- **Como funciona:**
  - *Backend:* scheduler (cron) diário que segmenta contatos por histórico (comprou `save_the_date` há N dias → oferecer convite; comprou convite de casamento há ~1 ano → bodas; datas do calendário: Mães/Natal corporativo/formatura). Dispara mensagem outbound via Evolution (campanha em massa, respeitando opt-out).
  - *IA:* mensagem de reabertura calorosa ("chegou a hora dos convites?") com CTA; se o cliente responder, cai no fluxo normal de pedido.
  - *Painel:* tela de Campanhas — audiências pré-montadas + agendamento + preview + log de envio/resposta.
- **Dependências:** infra de campanha em massa (transversal); scheduler/cron. Sem foto/gateway.
- **Métrica de sucesso:** taxa de resposta da campanha; receita atribuída a reativação; recompra por cliente/ano.

### 4. Versões/revisões da prova de arte (v1, v2, v3)

- **Problema de negócio:** arte de convite quase nunca é aprovada na 1ª — o cliente pede ajuste de cor, texto, layout. Hoje há UMA `art_url`; a cada revisão o histórico se perde e não dá pra cobrar por excesso de revisões.
- **Como funciona:**
  - *Backend:* nova tabela `papelaria_art_versions (order_id, version_no, art_url, note, created_at, approved boolean)`. Cada nova arte incrementa a versão; `art_approved` do pedido reflete a versão aprovada. Config opcional `free_revisions` (nº incluso) → acima disso o painel sinaliza cobrança de revisão extra (upsell).
  - *IA:* a `<aprovacao_arte>` aprova a versão CORRENTE (a última enviada); a IA informa qual versão está aprovando.
  - *Painel:* timeline de versões no card do pedido (link + nota + status).
- **Dependências:** upload real de imagem (SERVICE_ROLE_KEY) melhora muito, mas funciona hoje com link colado por versão.
- **Métrica de sucesso:** nº médio de revisões até aprovar; receita de revisão extra; tempo em `arte_aprovacao`.

### 5. Upsell/cross-sell proativo da IA (kit convite completo)

- **Problema de negócio:** quem compra convite geralmente precisa de save the date, tags de lembrancinha, menu de mesa, papelaria do dia — mas só pede o que lembrou. A IA passiva deixa dinheiro na mesa.
- **Como funciona:**
  - *Backend:* mapa de sugestões por categoria (`convites → {save_the_date, tags, menu}`), simples e configurável; nada que invente preço (tudo do catálogo).
  - *IA:* após montar o convite e ANTES de fechar, a persona oferece 1–2 itens complementares do catálogo ("quer incluir as tags das lembrancinhas no mesmo tema?"). Respeita a trava: sugere só item existente, não força, não inventa valor.
  - *Painel:* toggle de "sugestões complementares" e ranking dos combos que mais convertem.
- **Dependências:** nenhuma (usa catálogo atual). Não viola trava.
- **Métrica de sucesso:** itens por pedido; taxa de aceite da sugestão; ticket médio.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava a cobrança automática do **sinal (#1)**, do **cupom (#6)** no checkout, do **combo/kit (#13)**, do **fidelidade/cashback (#15)** e do fechamento do **orçamento ad-hoc (#11)**. Até lá, todas essas features funcionam em modo "marcar pago manual" — o schema e a trava já podem ir agora, plugando o webhook depois.
- **Upload de foto/anexo (SERVICE_ROLE_KEY):** destrava **aprovação de arte por imagem (#7)** de ponta a ponta, enriquece as **versões da prova de arte (#4)** (miniaturas em vez de link) e a **foto do evento no pós-venda (#9)** (prova social real). Hoje tudo isso opera com link colado.
- **Scheduler/cron:** habilita **reativação por data comemorativa (#3)**, **lembrete de retirada/prazo (#8)**, **pós-venda/NPS (#9)** e **alerta de estoque (#12)**. É a peça de infra que converte features "manuais" em automáticas.
- **Campanha em massa segmentada:** base para **reativação (#3)** e **indicação (#10)**; uma vez pronta, qualquer audiência por histórico de pedido vira disparo com opt-out.
