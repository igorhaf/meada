# Perfil Concessionária (loja de carros) — camada 8.17

Guia operacional do tenant **concessionaria** (`profile_id='concessionaria'`). Loja de carros /
revenda: a IA atende clientes via WhatsApp e faz **AS TRÊS COISAS** de uma concessionária — mostra o
ESTOQUE, agenda TEST-DRIVE e registra LEAD de compra. É um **híbrido triplo** deliberado.

É o **21º perfil vertical** (22º contando generic). Combina três moldes: catálogo de **ESTOQUE** com
ciclo de vida próprio + **agenda** de test-drive (clona dental, conflito por VENDEDOR) + **lead/funil**
(clona oficina/eventos, sem itens).

## Telas (sidebar "Concessionária")

| Tela | Rota | O que faz |
|------|------|-----------|
| Estoque | `/dashboard/concessionaria-vehicles` | CRUD do estoque + ciclo de status (disponível→reservado→vendido); foto via link. |
| Vendedores | `/dashboard/concessionaria-salespeople` | CRUD de vendedores (o conflito de test-drive é por vendedor). |
| Test-drives | `/dashboard/concessionaria-testdrives` | Agenda por status (agendado→confirmado→realizado/cancelado/no_show). |
| Leads | `/dashboard/concessionaria-leads` | Funil (novo→em_negociacao→fechado/perdido); atribuir vendedor. |
| Configurações | `/dashboard/concessionaria-settings` | Nome da loja + janela/duração do test-drive. |

## ESCAPADA — o veículo é ITEM DE ESTOQUE com identidade e status próprios

O **veículo** (`concessionaria_vehicles`) é a entidade central — **estoque da LOJA** (FK company), NÃO
sub-entidade de um cliente (≠ `os_vehicles` da oficina, que é do cliente). É o primeiro perfil em que o
"produto" do nicho é um **item de estoque com identidade única e status próprio** (≠ catálogo
reabastecível de comida/floricultura, onde o item é um TIPO, não uma unidade física).

**Ciclo de estoque** (`VehicleStatus` ↔ `concessionaria-vehicle-status.ts`, parity):

```
disponivel → reservado, vendido
reservado  → disponivel, vendido
vendido    → (terminal)
```

O veículo **VENDIDO sai da disponibilidade** — não entra na vitrine da IA, não aceita test-drive/lead.
Mudança de status é **AÇÃO HUMANA** no painel (a IA não toca o estoque). A IA opera sobre o veículo por
**TRÊS lentes** (vitrine, agenda, lead) sem nunca alterá-lo.

## FLUXO 2 — Test-drive (clona DENTAL, conflito por VENDEDOR)

`concessionaria_test_drives` referencia `vehicle_id` + `salesperson_id`. O conflito é **por
salesperson_id** (não por veículo): 2 clientes podem test-driveiar o MESMO modelo em horários distintos;
o que não pode é o mesmo VENDEDOR em dois test-drives sobrepostos. `findConflict` (janela half-open, só
status bloqueantes agendado/confirmado) é **re-verificado DENTRO da transação** → choque = 409
`conflict_slot`. **MESMO horário com vendedor DIFERENTE → OK** (paralelismo). `end_at` MATERIALIZADO no
INSERT (start_at + duration; não generated). **Só de veículo 'disponivel'** → senão 422
`vehicle_not_available`.

Status `TestDriveStatus` ↔ TS (parity): agendado→confirmado→realizado; cancelado/no_show. Notifica
**confirmado** (com veículo+vendedor+data/hora) e **cancelado**; demais silenciosos.

## FLUXO 3 — Lead (clona OFICINA/EVENTOS, funil sem itens)

`concessionaria_leads` é um registro de **INTERESSE** em UM veículo (NÃO order-com-itens-e-total).
Condição `payment_condition` (avista|financiado, FLAG declarativa). `LeadStatus` ↔ TS (parity):
novo→em_negociacao→fechado/perdido. A IA cria o lead em **'novo'** e NÃO move; a equipe trabalha o funil.
**Preço = SNAPSHOT do catálogo** (`vehicle_price_cents`) — a IA NUNCA carrega preço na tag; o backend
sempre usa o preço do catálogo. **Só de veículo 'disponivel'** → 422 `vehicle_not_available`.

## A TRAVA (o coração da SM)

A IA SÓ: mostra estoque disponível, agenda test-drive, registra lead. **NUNCA** fecha preço/desconto/
financiamento, **NUNCA** aprova crédito, **NUNCA** simula parcela/juros/score, **NUNCA** inventa
veículo/preço/condição fora do catálogo, **NUNCA** promete entrega/disponibilidade não confirmada,
**NUNCA** muda o status de estoque do veículo nem o status do lead (ações humanas do painel). O preço é
SEMPRE o do catálogo.

## Tags

**`<testdrive_carro>`** (agenda): `{"vehicle_id","salesperson_id","date":"YYYY-MM-DD","start_time":"HH:MM","notes"}`.

**`<lead_carro>`** (registra interesse): `{"vehicle_id","payment_condition":"avista|financiado","notes"}` —
**sem preço** (o backend usa o do catálogo).

Ambas têm namespace próprio, distinto de TODAS as outras. O `OutboundService` remove a tag antes de
enviar.

## O que NÃO existe nesta fase

- **Upload de foto** (a foto do veículo é LINK colado — `photo_url`; bloqueador SERVICE_ROLE_KEY).
- **Financiamento real / simulação de parcela / score / aprovação de crédito** (proibido por trava;
  condição é só uma flag declarativa).
- **FIPE / avaliação de usado / trade-in**; **reserva com sinal/pagamento** (Stripe #50; 'reservado' é
  mudança de status manual); **documentação/emplacamento/transferência**; **VIN/chassi formal**;
  **notificação automática de fechamento do lead**; **multi-loja/pátio**.

## Notas técnicas

- Migration `61_concessionaria.sql` (5 tabelas: config, salespeople, vehicles, test_drives, leads). A
  CHECK de `companies.profile_id` ACRESCENTA `'concessionaria'` preservando os 21 perfis anteriores.
  **Lição de ordenação (atelie/casamento):** 61 entra por ÚLTIMO no `SCRIPTS` do
  `AbstractIntegrationTest` (sua CHECK tem os 22 perfis — a que reescreve a CHECK por último precisa ter
  a lista completa).
- `test_drives`/`leads`: INSERT pelo backend (service_role) — IA via handler OU POST manual; tenant
  SELECT/UPDATE. `vehicles`/`salespeople`/`config`: CRUD do tenant.
- Snapshots: marca/modelo/ano no test-drive; marca/modelo/ano/preço no lead. Alterar/vender o veículo
  depois NÃO altera test-drives/leads passados.
- Contexto da IA via `ConcessionariaContextCache` (Caffeine TTL 30s, keyed por (companyId, contactId) —
  vitrine + vendedores + slots livres por vendedor; NÃO injeta reservado/vendido), invalidado em toda
  mutação.
- Guard `/api/concessionaria/**` → 403 `forbidden_wrong_profile`. Paleta `meia-noite`.
- Tenant de teste: `igorhaf28` (Concessionária Modelo).
