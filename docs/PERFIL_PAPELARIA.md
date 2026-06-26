# Perfil Papelaria (Papelaria · Convites personalizados) — camada 8.15

Guia operacional do tenant **papelaria** (`profile_id='papelaria'`). É encomenda GRÁFICA personalizada:
convites, save the date, cartões, adesivos, embalagens. A IA atende clientes pelo WhatsApp, monta o
pedido na conversa (com tiragem + personalização), coleta a data respeitando o lead time, e a papelaria
acompanha num Kanban com gate de aceite + fluxo de prova de arte. Tom prestativo-criativo.

É o **32º perfil vertical** (31 + generic). CLONA o chassi do Padaria (8.15→8.8, pedido com lead time +
made_to_order + gate de aceite + modifiers + fulfillment) e inaugura a **prova de arte**.

## Telas (sidebar "Papelaria")

| Tela | Rota | O que faz |
|------|------|-----------|
| Catálogo | `/dashboard/papelaria-catalog` | CRUD de itens + opções (Papel/Acabamento/Cor/Tamanho) + made_to_order + lead + specs. |
| Pedidos | `/dashboard/papelaria-orders` | Kanban com gate de aceite + FLUXO DE PROVA DE ARTE + tiragem/personalização. |
| Configurações | `/dashboard/papelaria-settings` | Taxa de entrega + pedido mínimo + lead time default. |

## ESCAPADA — Prova de arte (aprovação do layout pelo cliente dentro do pedido)

A papelaria é encomenda gráfica: antes de imprimir, a equipe faz a ARTE (o layout do convite) e precisa
do OK do cliente. O pedido ganha um estado EXTRA no funil: **`arte_aprovacao`** (entre `aceito` e
`em_producao`).

- Depois do gate de aceite (`aguardando → aceito`), a papelaria PRODUZ a arte e SOBE no painel (cola a
  `art_url`) — o pedido vai pra `arte_aprovacao` (a IA NÃO sobe arte, é ação humana).
- Campo `art_approved boolean`. A transição `arte_aprovacao → em_producao` SÓ é permitida com
  `art_approved=true` → senão **409 `art_not_approved`**.
- Quem aprova: o cliente, via tag `<aprovacao_arte>` na conversa (a IA captura), OU a papelaria no painel.
  Aprovar materializa `art_approved=true` e move pra `em_producao`. A IA **NUNCA aprova a arte pelo
  cliente** — só REGISTRA a aprovação que o cliente DECLARAR, e só quando o pedido está em
  `arte_aprovacao`; `<aprovacao_arte>` num pedido fora desse estado é no-op.
- Pedido SÓ pronta-entrega pode pular `arte_aprovacao` (`aceito → em_producao` direto). `art_not_approved`
  só barra `arte_aprovacao → em_producao`.

Espelho conceitual: `<pedido_papelaria>` CRIA o pedido / `<aprovacao_arte>` MUTA o estado de um pedido
existente (como AberturaOs/AprovacaoOs do oficina), mas a aprovação é da ARTE, não de orçamento.

## Tiragem + personalização + lead + fulfillment (herdados do padaria)

- **TIRAGEM:** `order_item.quantity` é a tiragem (50/100/200); line total = `unit_price × quantity`. Tiragem
  alta escala o total. `unit_price = base + Σ deltas` (Papel/Acabamento/Cor/Tamanho).
- **custom_text:** texto personalizado por item (snapshot — ex.: "Casamento Ana & Bruno · 12/12").
- **Lead time:** item `made_to_order` → `pickup_or_delivery_date` obrigatória e ≥ `hoje + MAX(leads)`;
  antes disso → 422 `lead_time_violation`. Pronta-entrega → data opcional.
- **fulfillment** retirada/entrega (entrega exige endereço → 422 `address_required` + taxa).

## Status (gate de aceite + prova de arte + funil divergente)

`PapelariaOrderStatus` ↔ TS (parity, 10 estados): `aguardando → aceito → arte_aprovacao → em_producao →
pronto → {retirado | saiu_entrega → entregue}`; `recusado`/`cancelado` terminais. Gate de aceite humano
(`aguardando → aceito`); aguardando NÃO notifica. Notifica aceito/arte_aprovacao/em_producao/pronto/
saiu_entrega/entregue/recusado. Total materializado.

## Tags (namespaces próprios, distintas de TODAS as outras)

- `<pedido_papelaria>{"fulfillment","pickup_or_delivery_date","delivery_period","delivery_address","items":[{"catalog_item_id","options":[{"option_id"}],"custom_text","quantity"}],"notes"}`
- `<aprovacao_arte>{"order_id":"UUID|null"}`

## Categorias

Hardcoded (`PapelariaCategory` ↔ `papelaria-categories.ts`, parity): convites, save_the_date, cartoes,
papelaria, adesivos, embalagens. `PapelariaFulfillment` + `PapelariaPeriod` também têm parity.

## O que NÃO existe nesta fase

Upload da arte como arquivo/imagem (a "arte subida" é link colado — `art_url`; bloqueador
SERVICE_ROLE_KEY); versões/revisões da prova de arte; convite artístico sob orçamento ad-hoc; e-sign/
contrato; assinatura recorrente; combo/cupom; pagamento real (Stripe #50); gráfica externa; estoque.

## Notas técnicas

- Migration `59_papelaria.sql` (6 tabelas). A CHECK ACRESCENTA `'papelaria'` preservando os 31 perfis.
  Entra por ÚLTIMO no `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK tem os 32).
- Base de conhecimento (RAG): disponível como em todo perfil. Cache `PapelariaCatalogCache` TTL 60s
  (ignora conversationId; ensina as 2 tags).
- Guard `/api/papelaria/**` → 403 `forbidden_wrong_profile`. Paleta `lavanda`. Tenant: `igorhaf26`.
