# Perfil Adega (delivery de bebidas) — camada 8.9

Guia operacional do tenant **adega** (`profile_id='adega'`). Adega / delivery de bebidas (vinhos,
espumantes, cervejas, destilados, sem-álcool, acessórios): a equipe gerencia o cardápio (itens +
modifiers de Volume/Temperatura), a IA atende clientes pelo WhatsApp e monta o pedido na conversa
**confirmando a maioridade**, e a loja acompanha o fluxo num Kanban com **gate de aceite**.

É o **18º perfil vertical** (19º contando o generic). Clona o chassi do **ComidaBot** (camada 8.4 —
cardápio + carrinho-na-conversa + tag de pedido + recálculo de total no backend + snapshot de
preço/nome + modifiers + taxa de entrega/pedido mínimo + Kanban + gate de aceite humano) e adiciona
**uma escapada estrutural nova**: a **trava de faixa etária (+18)** na venda de álcool.

## Telas (sidebar "Adega")

| Tela | Rota | O que faz |
|------|------|-----------|
| Catálogo | `/dashboard/adega-menu` | CRUD de itens (nome, descrição, preço, categoria, disponível) **e** dos modifiers de cada item (Volume, Temperatura — grupos com delta de preço). |
| Pedidos | `/dashboard/adega-orders` | Kanban do pedido + **gate de aceite** (aceitar/recusar) + histórico. Cada pedido mostra o **selo "+18 confirmado"** (compliance). |
| Cupons | `/dashboard/adega-coupons` | CRUD de cupons de desconto (percent/fixed + mínimo + validade + máx. de usos). |
| Fidelidade | `/dashboard/adega-loyalty` | Toggle + limiar + recompensa da fidelidade por contagem de pedidos entregues. |
| Configurações | `/dashboard/adega-settings` | Taxa de entrega (flat) + pedido mínimo. |

## ESCAPADA — Trava de faixa etária (+18, venda de álcool)

A venda de bebida alcoólica é **proibida para menores de 18 anos**. O pedido carrega um flag
**`age_confirmed` (boolean NOT NULL)** em `adega_orders`. A regra é **dura no backend**, em duas
camadas:

- **Na IA / handler:** a IA confirma a maioridade na conversa e só então emite a tag com
  `"age_confirmed":true`. Se a tag chega **sem o flag ou com `false`**, o `PedidoAdegaConfirmHandler`
  **aborta SEM criar pedido** — não há pedido "menor de idade" no banco.
- **No service:** `AdegaOrderService.create(...)` recebe `ageConfirmed` e, se for `false`, lança
  **`AgeNotConfirmedException` → 422 `age_not_confirmed`** ANTES de qualquer cálculo de total.

O flag é **persistido** (`adega_orders.age_confirmed`, NOT NULL, sem default — só `true` entra) para
**auditoria/compliance**, e fica **visível no painel** como o selo "+18 confirmado". A confirmação é
**DECLARATÓRIA** pela IA nesta SM (verificação documental real é processo da loja na entrega). A
trava vale **mesmo para carrinho 100% sem-álcool** nesta SM (simplicidade; refinar é fase futura).

> É a regra que justifica adega ser **perfil próprio**, e não um preset do comida: há uma
> **pré-condição dura de compliance** no backend antes de qualquer cálculo.

## Herda do chassi comida

- **Gate de aceite (ação humana, não da IA):** o pedido nasce **aguardando** (a IA já confirmou o
  RECEBIMENTO); a loja **ACEITA** (→ `em_preparo`) ou **RECUSA** (→ `recusado`, terminal, com motivo
  opcional) no painel. `aguardando` **NÃO notifica** (evita mensagem duplicada).
- **Modifiers (Volume, Temperatura):** grupos de opção por item em `adega_menu_item_options`, com
  snapshot em `adega_order_item_options` no pedido. `unit_price = base + Σ deltas`, recalculado no
  backend (o `total_cents` da tag é **descartado**). **Sem regra do maior valor** (isso é do
  pizzaria — adega não tem meio-a-meio).

```
aguardando ──aceitar──→ em_preparo ──→ saiu_entrega ──→ entregue
    │                        │                │
    └──recusar──→ recusado   └──→ cancelado ←─┘
```

Transição inválida → 409 `invalid_status_transition`. Terminais: `entregue`, `recusado`, `cancelado`.

### Notificações outbound (texto fixo ao ENTRAR no status)

| Status | Notifica? | Texto |
|--------|-----------|-------|
| `aguardando` | **Não** (silencioso) | — (a IA já confirmou o recebimento na conversa) |
| `em_preparo` | Sim | "Seu pedido foi aceito! Já estamos separando suas bebidas. 🍷" |
| `saiu_entrega` | Sim | "Seu pedido saiu pra entrega. Já já chega aí! Beba com moderação." |
| `entregue` | Sim | "Pedido entregue. Aproveite e beba com moderação — obrigado pela preferência!" |
| `recusado` | Sim | Texto defensivo + motivo opcional. |
| `cancelado` | Sim | "Seu pedido foi cancelado. Se quiser refazer, é só me chamar." |

## Cupons e Fidelidade (backlog #1/#2 — clone do chassi sushi)

Migration `80_adega_coupons_loyalty.sql`. Espelho exato do motor do sushi (mig 69):

- **Cupom** (`adega_coupons`): kind `percent` (1..100) / `fixed` (centavos), `min_order_cents`,
  `max_uses`, `valid_until`, `active`, `uses`. Código **único por adega (case-insensitive,
  lower(code))**. A IA só passa o **código** no campo `cupom` da tag; o backend **valida**
  (ativo + validade + mínimo + usos) e aplica sobre o subtotal. **Cupom inválido NÃO aborta** o
  pedido — sai sem o desconto. `uses` incrementa na criação (mesma transação).
- **Fidelidade** (`adega_loyalty_config`, 1:1): `enabled` + `threshold_orders` + reward
  (percent/fixed). O backend conta os pedidos **`entregue`** do contato **ANTES** de inserir o novo;
  quando `count > 0 && count % threshold == 0` → desconto automático + `loyalty_applied=true`.
- **Desconto no pedido** (`adega_orders.discount_cents` + `coupon_id`/`coupon_code_snapshot` +
  `loyalty_applied`): cupom + fidelidade **SOMAM, clampados ao subtotal**.
  `total = subtotal − discount + delivery_fee`.
- **A trava +18 não muda:** o desconto só é calculado DEPOIS do `age_confirmed` (a pré-condição
  continua vindo antes de qualquer cálculo).

## O que a IA faz

- Monta o pedido em **linguagem livre** na conversa, com os modifiers (Volume, Temperatura).
- **Confirma SEMPRE a MAIORIDADE** antes de fechar; pode sugerir **harmonização** entre o que já está
  no cardápio.
- Ao fechar, **pode oferecer UMA sugestão de conveniência** do próprio cardápio (harmonização,
  acessório, ou completar o pedido mínimo) — no máximo uma vez, sem insistir, **nunca** como
  incentivo a beber mais.
- **Registra o código do cupom** no campo `cupom` da tag quando o cliente informar — quem valida e
  calcula é o backend (a IA nunca inventa desconto).
- **Confirma SEMPRE** com o valor total e o endereço antes de fechar; inclui **"Beba com moderação"**.
- Emite a tag `<pedido_adega>` com `age_confirmed:true`; o pedido nasce **aguardando**.
- Avisa o cliente que o pedido vai para **confirmação da loja**.

## O que a IA NÃO faz

- **NUNCA vende para menor de idade** — sem maioridade confirmada, não fecha o pedido (e o backend
  recusa criar sem o flag).
- **NUNCA incentiva consumo excessivo**, NUNCA sugere "beber mais", NUNCA minimiza riscos do álcool.
- **Não aceita nem recusa** pedido (no sentido de produção) — isso é a loja no painel (gate de
  aceite). A IA só confirma o **recebimento**.
- **Não inventa** rótulo, marca, safra, volume, opção ou preço fora do cardápio.
- **Não define o total** — recalculado no backend; o `total_cents` da tag é **descartado**.

## Tag `<pedido_adega>`

Formato JSON em texto livre (NÃO é tool calling do Gemini — texto livre + regex). O `OutboundService`
remove a tag antes de enviar a mensagem ao cliente.

```json
{
  "age_confirmed": true,
  "items": [
    { "item_id": "UUID", "qtd": 1, "options": ["UUID_VOLUME", "UUID_TEMPERATURA"] }
  ],
  "endereco": "Rua X, 10",
  "cupom": "OFF10",
  "total_cents": 0
}
```

- **`age_confirmed:true` é OBRIGATÓRIO** — sem ele o pedido NÃO é criado (trava +18).
- `options` é **opcional** por item (lista de UUIDs dos modifiers escolhidos).
- `cupom` é **opcional** — só o código; o backend valida e aplica (inválido → pedido sem desconto).
- `total_cents` é **ignorado** pelo backend.

## O que NÃO existe nesta fase

- **Clube de assinatura de vinho** (recorrência — o padrão de assinatura é do academia).
- **Curadoria/scoring de safra**, integração com sommelier/API de rótulos.
- **Controle de estoque por garrafa** (cupom e fidelidade JÁ existem — ver seção acima).
- **Integração com iFood / Zé Delivery**, rastreio de entregador em mapa, ETA dinâmico.
- **Validação documental de idade** (RG/foto/biometria) — a confirmação é **declaratória** pela IA;
  verificação real na entrega é processo da loja.
- **Dispensa da trava +18 para carrinho 100% sem-álcool** — nesta SM TODO pedido passa pela trava.
- **Pagamento online** (Stripe é #50), **foto** do rótulo (bloqueador `SERVICE_ROLE_KEY`),
  **scheduler** de auto-transição de status.

## Notas técnicas

- Migration `53_adega.sql` (6 tabelas: config, menu_items, menu_item_options, orders, order_items,
  order_item_options). A CHECK de `companies.profile_id` ACRESCENTA `'adega'` preservando os 17
  perfis anteriores.
- Migration `80_adega_coupons_loyalty.sql` (cupom + fidelidade + colunas de desconto em
  `adega_orders`, clone do sushi mig 69; seed idempotente de `adega_loyalty_config`).
- `adega_orders.age_confirmed` é **boolean NOT NULL sem default** — o banco é a defesa final da
  trava (o backend só insere `true`).
- Categorias **hardcoded** (vinhos / espumantes / cervejas / destilados / sem_alcool / acessorios) em
  sync `AdegaCategory.java` ↔ `adega-categories.ts` (`AdegaCategoryParityTest`).
- Status **hardcoded** `AdegaOrderStatus` (Java) ↔ `adega-order-status.ts`
  (`AdegaOrderStatusParityTest`).
- Guard de perfil: `/api/adega/**` → 403 `forbidden_wrong_profile` para tenant de outro perfil.
- `unit_price`/`subtotal`/`total` MATERIALIZADOS no INSERT (não generated). Snapshots de item/opção.
- Cache de cardápio Caffeine TTL 60s, invalidado em toda mutação do cardápio.
- Paleta `beringela`.
- Tenant de teste: `igorhaf20` (Adega Modelo).
