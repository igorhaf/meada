# Perfil Lavanderia (lavagem com coleta e entrega agendadas) — camada 8.10

Guia operacional do tenant **lavanderia** (`profile_id='lavanderia'`). A IA atende clientes pelo
WhatsApp, conhece o catálogo de SERVIÇOS de lavagem, monta o pedido na conversa (quantidade de peças
por serviço), coleta a DATA de COLETA + período + endereço, confirma com o total E a DATA DE ENTREGA
prometida, e avisa que o pedido vai para confirmação da lavanderia (gate de aceite humano).

É o **22º perfil vertical**. CLONA o chassi do **FloriculturaBot** (pedido agendado por dia+período +
gate de aceite + catálogo + modifiers) e inaugura **DUAS DATAS ligadas por um TURNAROUND**.

## Telas (sidebar "Lavanderia")

| Tela | Rota | O que faz |
|------|------|-----------|
| Serviços | `/dashboard/lavanderia-services` | CRUD de serviços (preço por peça + turnaround_days + cuidado) + opções (modifiers). |
| Pedidos | `/dashboard/lavanderia-orders` | Kanban por status + gate de aceite; detalhe mostra as DUAS datas (coleta + entrega), período, endereço. |
| Configurações | `/dashboard/lavanderia-settings` | Taxa de entrega + pedido mínimo + turnaround default. |

## ESCAPADA — DUAS datas ligadas por um prazo de TURNAROUND

O pedido tem **COLETA** e **ENTREGA**, e elas NÃO são independentes — estão acopladas por um PRAZO:

- Cada serviço tem **`turnaround_days`** (prazo de processamento; ex.: lavar+passar=1, lavagem a seco=3,
  edredom=2).
- `collect_date` é obrigatória e **>= hoje** (fuso America/Sao_Paulo).
- **`delivery_date` é MATERIALIZADA** no INSERT: `collect_date + MAX(turnaround_days entre TODOS os
  itens)`. **MAX, não soma** — o processamento é paralelo, vale o serviço mais lento.
- Se a tag pede uma entrega **< collect + MAX(turnaround)** → **422 `turnaround_violation`**, e a resposta
  traz a **primeira data possível** (= collect + MAX). Se a tag omite delivery_date, o backend calcula e
  materializa.

`delivery_date` é materializada em Java (date + interval não é IMMUTABLE — lição end_at). period
(manhã/tarde) é o da coleta; a entrega herda. **SEMPRE coleta+entrega** (`delivery_address` obrigatório;
sem retirada de balcão — diferença pra padaria).

## Funil de status

`LavanderiaOrderStatus` ↔ TS (parity):

```
aguardando → coletado → em_processo → pronto → saiu_entrega → entregue
    │                                                  │
    └ recusado          cancelado (de qualquer não-terminal) ←┘
```

aceite = **aguardando→coletado** (gate humano — receber as peças na coleta). Notifica coletado
("recebemos suas peças"), pronto ("suas peças estão prontas"), saiu_entrega, entregue, recusado
(defensivo). aguardando silencioso.

## O que a IA NÃO faz

- **NUNCA inventa** serviço, peça, adicional ou preço fora do catálogo.
- **NUNCA aceita/recusa** o pedido — é a lavanderia no painel (gate). A IA só confirma o recebimento.
- **NUNCA promete remover mancha**, garantir resultado ou recuperar peça danificada — "a equipe avalia a
  peça na coleta, sem garantia de remoção total".
- **NUNCA promete entrega antes do prazo** (coleta + MAX(turnaround)). O total é recalculado pelo sistema.

## Tag `<pedido_lavanderia>`

```json
{ "collect_date": "YYYY-MM-DD", "period": "manha|tarde", "delivery_address": "...",
  "delivery_date": "YYYY-MM-DD|null",
  "items": [{ "service_id": "UUID", "options": ["UUID"], "qty": N }], "notes": "...|null" }
```
- `delivery_date` opcional — o backend materializa/valida. `total_cents` (se enviado) é descartado.

## O que NÃO existe nesta fase

- Foto/referência de mancha (bloqueador SERVICE_ROLE_KEY); garantia/laudo de remoção; **serviço EXPRESS/
  24h** com sobretaxa; **pesagem real** com reprecificação; etiqueta/QR por peça; assinatura recorrente;
  combo/cupom/fidelidade; pagamento real (Stripe #50); rastreio/motoboy; slot por horário fino.

## Notas técnicas

- Migration `54_lavanderia.sql` (6 tabelas). A CHECK de `companies.profile_id` ACRESCENTA `'lavanderia'`
  preservando os 22 perfis. **Lição (atelie/casamento):** 54 entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 23 — a que reescreve por último precisa ter a lista
  completa).
- `subtotal`/`total`/`unit_price` materializados; `delivery_date` materializada (MAX em Java).
  `turnaround_snapshot` por item. Snapshots de nome/preço.
- Base de conhecimento (RAG): disponível como em todo perfil — o item "Conhecimento" do nav e a injeção
  `{{knowledge}}` do PromptBuilder valem pra lavanderia automaticamente (sem gate de feature).
- Guard `/api/lavanderia/**` → 403 `forbidden_wrong_profile`. Paleta `oceano`. Tenant: `igorhaf21`.
