# Perfil Fotografia (estúdio · cinema · audiovisual) — camada 8.16

Guia operacional do tenant **fotografia** (`profile_id='fotografia'`). A IA atende clientes pelo
WhatsApp, AGENDA sessões/coberturas escolhendo um **pacote** do catálogo + um **fotógrafo**, e
ENTREGA, read-only, o **link do material** que o estúdio gravou na sessão. Tom criativo, atencioso e
organizado — sem prometer resultado estético.

É o **25º perfil vertical** (24 + generic). CLONA o chassi de **agenda por profissional** do
**DentalBot/DermatologiaBot** (conflito por profissional + end_at materializado) + um **catálogo de
pacotes** (espelho leve do `aesthetic_procedures` do EsteticaBot, SEM saldo multi-sessão), e inaugura
a **entrega de LINK read-only com prazo materializado**.

## Telas (sidebar "Fotografia")

| Tela | Rota | O que faz |
|------|------|-----------|
| Fotógrafos | `/dashboard/fotografia-professionals` | CRUD (o conflito de agenda da sessão é por profissional). |
| Pacotes | `/dashboard/fotografia-packages` | Catálogo: nome, categoria, duração, valor, **prazo de entrega** (dias). |
| Agenda | `/dashboard/fotografia-appointments` | Sessões por status + escrever o **link do material** + ver o prazo de entrega. |
| Configurações | `/dashboard/fotografia-settings` | Horário + slot (sem duração — vem do pacote). |

## Cliente = contato (sem sub-entidade)

Diferente do Dermatologia (que tem `dermatologia_patients` sub-entidade do contato), aqui o **cliente
é o próprio contato** do WhatsApp (espelho salon/estetica). A sessão **snapshota**
`customer_name`/`customer_phone` do contato no momento — não há tabela de pacientes nem modo
"new_patient" na tag.

## Catálogo de pacotes (espelho leve estetica)

`fotografia_packages`: nome + categoria (texto livre) + `duration_minutes` + `price_cents` +
`delivery_days` + active. A sessão referencia `package_id` e **snapshota** name+price+duration+
delivery_days — alterar o pacote depois NÃO altera sessões já criadas. A **duração da sessão vem do
pacote** (snapshot), não da config. Excluir pacote com sessão → 409 `package_in_use`.

## Agenda (chassi dental/dermatologia)

Conflito **POR `professional_id`** (half-open `NOT (end_at <= newStart OR start_at >= newEnd)`,
re-verificado dentro da transação) → 409 `conflict_slot`; mesmo horário + fotógrafo DIFERENTE → OK
(paralelismo). `end_at` MATERIALIZADO no INSERT (start_at + duration_minutes, em Java — timestamptz +
interval não é IMMUTABLE). Janela `opens_at`..`closes_at` → 400 `outside_hours`.

**Status** `FotografiaAppointmentStatus` ↔ TS (parity, FEMININO):
`agendada → confirmada → realizada → entregue`; `agendada/confirmada → cancelada`; `confirmada →
falta`. A diferença vs Dermatologia (que para em `realizada`): aqui há um estado **`entregue`** (o
material foi entregue). Notifica **confirmada** (com pacote+fotógrafo+data/hora, defensivo) e
**cancelada**; agendada/realizada/entregue/falta silenciosos.

## ESCAPADA — Entrega de LINK read-only + prazo materializado

A sessão tem duas colunas próprias:
- **`delivery_due_date`** (date) — MATERIALIZADA no INSERT = `date(start_at) + delivery_days`. É o
  prazo prometido de entrega; aparece em cada card da Agenda ("entrega até …").
- **`delivery_link`** (text, nullable) — a URL da galeria/material. O estúdio grava DEPOIS da sessão,
  via PATCH `/api/fotografia/sessions/{id}` (campo Material na tela de Agenda). Vazio = nada a entregar.

`<entrega_material>{session_id}` → o `EntregaMaterialHandler` busca a sessão e envia o `delivery_link`
**VERBATIM** via `notifier.sendText` (NÃO passa pela IA — pra não ser reescrito/encurtado), com
**BARREIRA DE CONTATO**: só entrega se o contato da sessão == contato da conversa (impede vazar o link
de outro cliente). Sem link / contato diferente / sessão inexistente → não entrega. Espelho EXATO da
entrega de plano do NutriBot / preparo do DermatologiaBot, mas o link mora **na própria sessão** (não
numa nota de catálogo).

## Tags

**`<sessao_foto>`** (AGENDA — 1 modo, sem new_patient):
```json
{ "professional_id", "package_id", "date":"YYYY-MM-DD", "start_time":"HH:MM", "notes" }
```
**`<entrega_material>`**: `{ "session_id":"UUID" }`.

Ambas têm namespace próprio, distinto de TODAS as outras. A IA DESCARTA qualquer preço que tente
emitir — o backend snapshota o valor do pacote no catálogo.

## O que a IA faz / NÃO faz

- **FAZ:** agenda sessão (pacote + fotógrafo + data/hora), confirma com o prazo de entrega, entrega o
  link quando ele já está na sessão do próprio cliente.
- **NÃO FAZ:** inventar pacote/valor/prazo/fotógrafo fora do catálogo; negociar preço/desconto;
  prometer resultado estético ("vai ficar perfeito"); garantir entrega antes do prazo do pacote;
  inventar link; aceitar/recusar sessão (transição de status é ação humana no painel).

## O que NÃO existe nesta fase

- Upload de foto/material (o material é entregue por **link colado**, não há armazenamento —
  bloqueador SERVICE_ROLE_KEY); saldo multi-sessão / pacote de N sessões (estetica cobre); seleção de
  fotos / galeria com curadoria; contrato/assinatura; sinal/pagamento (Stripe #50); segunda câmera /
  equipe por sessão; orçamento ad-hoc com itens (eventos cobre); scheduler de auto-transição/lembrete
  de prazo de entrega.

## Notas técnicas

- Migration `60_fotografia.sql` (4 tabelas: professionals, packages, config,
  session_appointments). A CHECK ACRESCENTA `'fotografia'` preservando os 24 perfis. Entra por ÚLTIMO
  no `SCRIPTS` do `AbstractIntegrationTest` (sua CHECK tem os 25).
- Base de conhecimento (RAG): disponível como em todo perfil (item "Conhecimento" do nav +
  `{{knowledge}}` do PromptBuilder, sem gate de perfil).
- Guard `/api/fotografia/**` → 403 `forbidden_wrong_profile`. Paleta `carvao`. Tenant: `igorhaf27`.
