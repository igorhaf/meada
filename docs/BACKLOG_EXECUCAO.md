# Backlog de Execução — Consolidado dos 33 nichos

> Consolidação executável das 528 sugestões (33 nichos × 16) num backlog deduplicado, com a
> ordem de ataque por ONDAS. Regra de ouro: o que é transversal (o mesmo em vários nichos) é
> feito UMA VEZ no núcleo; o específico é feito por nicho reusando a base. Gate entre ondas:
> `mvn -B clean test` verde + `next build` limpo.

## Números

- **Total de features levantadas:** 528 (33 nichos × 16 sugestões cada).
- **Transversais** (a mesma ideia se repete em vários nichos — feitas 1x no núcleo): **279** (~53%).
- **Específicas por nicho** (não-transversais): **249** (~47%).
- **Dependem do gateway de pagamento #50:** **65** (~12%). Bloqueadas por credencial externa.
- **Dependem de scheduler/cron:** **172** (~33%). É o maior destravador único do backlog.
- **Dependem de upload de foto/anexo** (bloqueado por `SERVICE_ROLE_KEY`): **43** (~8%). Onda tardia.

Leitura: um terço do backlog inteiro (172 features) fica DESTRAVADO por uma única peça de infra
(o scheduler). Por isso ele é a Onda 1. O gateway (#50) destrava 65 e depende de decisão do dono
(credencial), então o desenho entra cedo mas a chamada real fica pendente.

## Features TRANSVERSAIS (fazer 1x no núcleo, servem N nichos)

Agrupadas por ideia repetida. Ordenadas por nº de nichos que pedem (desc). "Depende de" indica
a peça de infra que a viabiliza.

| Feature transversal | Nº de nichos | Depende de | Onda | O que entrega |
|---|---|---|---|---|
| **Reativação de cliente/lead inativo (win-back)** | 24 | Scheduler | 3 | Régua de reengajamento por inatividade (N dias sem comprar/voltar), campanha automática pela IA |
| **Lembrete + confirmação anti-no-show (D-1/D-0, SIM/NÃO auto-muda status)** | 22 | Scheduler | 3 | Cron dispara lembrete; resposta do cliente muda o status do agendamento/pedido automaticamente |
| **Campanha em massa segmentada** | 20 | Scheduler | 3 | Envio segmentado (por frequência/objetivo/status/data) disparado ou agendado |
| **Cupom de desconto (percent/fixed, mínimo, validade, max usos)** | 20 | — (núcleo do sushi já tem) | 3 | Motor de cupom validado no backend, reusado por todos os nichos |
| **NPS / avaliação pós-atendimento (nota + pedido de review)** | 20 | Scheduler | 3 | Pesquisa pós-evento automática; coleta de review público |
| **Indicação com recompensa (member-get-member)** | 20 | — (parte com Gateway p/ cashback) | 5 | Link/código de indicação + crédito/desconto ao indicador |
| **Fidelidade / cashback / carimbo digital** | 17 | — (parte com Gateway p/ cashback real) | 3/5 | Pontos por contagem ou valor → recompensa; espelha o fidelidade do sushi |
| **Aniversário do cliente (mensagem + oferta automática)** | 15 | Scheduler | 3 | Gatilho por data de nascimento; cupom/mimo automático |
| **Scheduler de auto-transição de status** | 15 | Scheduler | 3 | Agendamento passado → realizada/falta; no-show automático; matrícula/pacote vencido → expirado |
| **Relatórios / dashboard operacional** | 15 | — | 5 | Faturamento, ocupação, no-show, top itens, funil — genérico + slots por nicho |
| **Página pública / CMS por tenant** | 20 | CMS (9.x, já existe) + Upload | 5 | Já entregue como feature flag; ligar por nicho + telas específicas |

Observação: reativação, lembrete anti-no-show, campanha, NPS, aniversário e auto-transição
(**~118 features somadas**) são todas gated pelo mesmo scheduler → caem juntas na Onda 3.

## Infra base (destrava o resto)

### Scheduler / cron — **ONDA 1** (maior alavanca única)

Destrava **172 features** ao todo. As transversais que ele libera de uma vez:

- Reativação de inativo (24 nichos)
- Lembrete + confirmação anti-no-show (22 nichos)
- Campanha em massa segmentada / régua (20 nichos)
- NPS pós-atendimento (20 nichos)
- Aniversário com oferta (15 nichos)
- Auto-transição de status (15 nichos)

E específicas que também dependem dele: régua de inadimplência (Academia, Escola, Cursos), recall
6m (Dental, Dermatologia), carteira de vacinas (Pet), lembrete de revisão por km/data (Oficina),
lembrete de próximo módulo anti-abandono (Cursos), confirmação de coleta/entrega D-1 (Lavanderia,
Floricultura, Padaria), follow-up de proposta orçada (Viagens, Eventos, Casamento), reposição por
ciclo (Suplementos, Ótica), recompra por crescimento (Moda Infantil), lembrete de prova/ajuste
(Ateliê), lembrete de checklist/parcela (Casamento, Eventos), alerta de estoque baixo com prazo
(Las, Suplementos, Papelaria, Moda Infantil), SLA de entrega de plano (Nutri).

Entrega da Onda 1: um agendador durável (job runner por company/tenant, idempotente, com janela de
frescor — respeitar a lição do incidente Baileys) + tabela de jobs/regras + um primeiro consumidor
genérico (lembrete + auto-transição) que os nichos herdam.

### Gateway de pagamento #50 — **ONDA 1 (desenho) / ONDA 4 (chamada real)**

Destrava **65 features**: sinal/pré-pagamento (quase todo nicho de agenda), cobrança recorrente
(Academia, Cursos, Escola), assinatura/clube (Sushi, Adega, Floricultura, Las, Padaria, Barbearia,
Suplementos, Estética), pacote pré-pago pago (Salão, Nutri, Dermatologia), aula/day-use avulsa
(Academia), reserva com sinal (Concessionária), pagamento online no delivery (Comida, Pizzaria,
Adega, Suplementos), honorários/parcelas (Legal, Eventos, Casamento, Viagens), gorjeta (Barbearia),
cashback real (Estética, Dermatologia, Casamento, Papelaria, Viagens, Suplementos).

**Depende de credencial externa (decisão do dono).** Por isso: o **desenho do ponto de integração**
(interface de PaymentGateway, entidade de cobrança/parcela, webhook de confirmação, status pago↔não
pago) entra na **Onda 1**, mas a **chamada real ao provedor fica PENDENTE** até a credencial existir.
Tudo que só depende do gateway sai na Onda 4.

### Upload de foto / anexo — **ONDA TARDIA** (bloqueado por `SERVICE_ROLE_KEY`)

Bloqueia **43 features**: foto de prato/rótulo/produto/quarto/bolo/sabor (Sushi, Adega, Lingerie,
Moda Infantil, Pousada, Padaria, Pizzaria, Las), antes/depois (Pet, Dermatologia), foto de avaria
(Oficina), croqui/render (Ateliê), mood board (Eventos, Casamento), galeria de seleção (Fotografia),
prova de arte por imagem / versões (Papelaria), anexo de documento/voucher/PDF (Legal, Viagens,
Cursos, Las), catálogo com foto no CMS. Só entra quando o Storage liberar a chave. Fica no fim.

## Ordem de execução por ondas

### Onda 1 — Infra (destrava o resto)
- **Scheduler/cron durável** (job runner idempotente por tenant + guard de frescor). Destrava 172.
- **Desenho do gateway #50** (interface PaymentGateway + entidade cobrança/parcela + webhook +
  estados pago/pendente). Chamada real PENDENTE de credencial.
- Gate: `mvn -B clean test` verde + `next build` limpo.

### Onda 2 — Piloto Academia (16 features) — valida o chassi assinatura + scheduler + gateway
As 16 da Academia e o status esperado depois da Onda 1:

| Feature | Status esperado |
|---|---|
| Reativação automática de aluno inativo | ✅ pronto (scheduler) |
| Confirmação de presença SIM/NÃO libera vaga | ✅ pronto (scheduler) |
| Painel de inadimplência + suspensão por atraso | ✅ pronto (scheduler; suspensão local) |
| Campanha em massa segmentada | ✅ pronto (scheduler) |
| Scheduler de auto-transição de matrícula | ✅ pronto (scheduler) |
| Aniversário do aluno | ✅ pronto (scheduler) |
| Lista de espera de aula lotada | ✅ pronto (lógica local) |
| Cupom de desconto na matrícula | ✅ pronto (motor de cupom) |
| Fidelidade por assiduidade (pontos) | ✅ pronto (contagem local) |
| Programa de indicação | ✅ pronto (desconto local; cashback real espera #50) |
| Upgrade/downgrade de plano proativo | ✅ pronto (lógica local) |
| Relatórios/dashboard (MRR, churn, ocupação) | ✅ pronto |
| Cobrança recorrente + lembrete de vencimento | ⏳ desenho pronto, cobrança real espera #50 |
| Aula avulsa / day-use pago | ⏳ espera #50 |
| Check-in / frequência por aula | ⏳ parcial (registro ok; QR/foto espera Upload) |
| Multi-unidade | 🔻 esforço G, fica pra fase própria |

Saldo do piloto: ~12/16 100% prontas sem o #50. Prova o padrão que os outros nichos de
assinatura (Cursos, Escola) e de agenda vão reusar.

### Onda 3 — Transversais que o scheduler destrava (aplica a TODOS os nichos de uma vez)
Faz no núcleo, liga por nicho:
- **Lembrete + confirmação anti-no-show** → nichos de agenda: Restaurante, Barbearia, Estética,
  Dental, Salão, Pousada, Pet, Nutri, Dermatologia, Fotografia, Ótica, Legal (audiência),
  Concessionária (test-drive), Escola (visita).
- **Auto-transição de status** → todos os de agenda + Sushi/Comida/Pizzaria/Adega (pedido),
  Eventos/Casamento/Ateliê/Viagens (proposta por data), Academia/Cursos/Escola (matrícula).
- **Reativação de inativo** → todos os 24 nichos que pedem.
- **Campanha em massa / régua** → 20 nichos.
- **NPS pós-atendimento** → 20 nichos.
- **Aniversário com oferta** → 15 nichos.
- **Cupom** (motor único) + **fidelidade/carimbo** (versão sem cashback real) → varejo e serviços.

### Onda 4 — Receita (pós-#50, quando a credencial existir)
Liga a chamada real do gateway desenhado na Onda 1:
- **Cobrança recorrente / assinatura / clube** → Academia, Cursos, Escola, Sushi, Adega,
  Floricultura, Las, Padaria, Barbearia, Suplementos, Estética, Dermatologia.
- **Sinal / pré-pagamento** → Restaurante, Barbearia, Salão, Pousada, Pet, Nutri, Dental,
  Dermatologia, Fotografia, Ótica, Ateliê, Lavanderia, Lingerie, Moda Infantil, Las, Papelaria,
  Concessionária (reserva), Eventos/Casamento/Viagens (depósito/parcela), Comida/Pizzaria/Adega
  (pagamento no fechamento).
- **Pacote/consulta/aula avulsa paga** → Academia (day-use), Salão/Nutri/Dermatologia (pacote),
  Legal (consultoria avulsa/pacote de horas).
- **Cashback real + gorjeta digital** → o que ficou pendente da Onda 3.

### Onda 5+ — Específicas por nicho (o que sobra), agrupadas por CHASSI
- **Chassi AGENDA (conflito por profissional/recurso):** multi-dentista (Dental), fila de encaixe/
  waitlist (Estética, Dermatologia, Salão, Nutri, Restaurante, Pousada, Cursos-coorte), bloqueio de
  datas/feriados (Restaurante, Casamento-calendário), reserva em grupo/mesas combinadas
  (Restaurante), garantia/assistência com prazo (Ótica), catálogo de procedimentos com preço/duração
  (Dental), triagem de urgência IA (Dermatologia), reagendamento pela IA (Nutri).
- **Chassi ORDER-BASED (pedido + itens + total):** combos/meio-a-meio (Sushi), combo do dia/preço
  por sabor/86 de sabor (Pizzaria), retirada balcão + taxa por zona + endereços salvos +
  agendamento de pedido (Comida/Pizzaria/Sushi/Adega), estoque por SKU/garrafa/limite do dia (Adega,
  Sushi), taxa por CEP + slots de entrega + anonimato do remetente + recompra 1-clique
  (Floricultura), pesagem/reprecificação + express + etiqueta QR + rastreio (Lavanderia), lead time
  por item (Padaria, Ótica-encomenda), aprovação item-a-item + histórico do veículo (Oficina).
- **Chassi ASSINATURA (recorrência):** upgrade/downgrade de plano (Academia), pré-requisito/trilha/
  quiz/certificado (Cursos), taxa de matrícula/rematrícula + cross-sell integral/transporte
  (Escola).
- **Chassi VAREJO com variantes (estoque):** alerta de volta ao estoque (Lingerie, Moda Infantil,
  Las-dye-lot), assistente de tamanho IA (Lingerie), devolução/troca com reentrada (Lingerie, Moda
  Infantil, Suplementos), reserva de estoque no carrinho (Lingerie), calculadora de novelos + kit de
  projeto (Las), FEFO/validade (Suplementos), frete por CEP (Moda Infantil).
- **Chassi PROPOSTA (order + aprovação 2 fases):** catálogo de pacotes/adicionais + upsell (Eventos,
  Casamento, Viagens, Dental-plano), parcelamento/contrato PDF e-sign (Eventos), aviso de data
  ocupada + fornecedores com comissão + RSVP (Eventos, Casamento), roteiro/itinerário + seguro
  casado (Viagens), trade-in + lista de desejos + simulação de financiamento + funil por vendedor
  (Concessionária), versões de prova de arte + orçamento ad-hoc (Papelaria), tabela de medidas +
  catálogo de materiais (Ateliê).
- **Integrações pesadas (esforço G, fase própria):** multi-unidade (11 nichos), iCal Booking/Airbnb
  (Pousada), simulação de financiamento (Concessionária), rastreio/rota com mapa (Floricultura,
  Lavanderia, Suplementos).

## Riscos e travas a respeitar

- **Travas de IA por nicho (NÃO afrouxar ao adicionar feature):**
  - Clínicas (Dental, Dermatologia) — IA NUNCA diagnostica/avalia lesão/recomenda conduta; guarda de
    sinal de alarme encaminha à consulta sem diagnosticar.
  - Nutri — IA NUNCA monta/calcula/ajusta plano nem dá caloria/macro; guarda de transtorno alimentar.
  - Estética/Salão — IA NUNCA indica procedimento não pedido nem opina sobre corpo/resultado.
  - Suplementos — trava de não-prescrição (nada de dosagem/posologia/uso terapêutico).
  - Adega — trava +18 (`age_confirmed` sem default; sem confirmação, aborta antes de criar pedido).
  - Concessionária — IA NUNCA fecha preço/desconto/financiamento nem muda status de estoque.
  - Ótica — IA registra receita como dado administrativo, NUNCA prescreve grau.
  - Legal — IA qualifica/agenda, NUNCA dá parecer jurídico.
  Toda feature nova de Receita/IA (upsell, cobrança, campanha) tem de respeitar a trava do nicho.
- **Multi-perfil (regra cravada):** feature de um nicho NUNCA pode quebrar/interferir em outro.
  Conflito → condicional explícita por `profile_id`, nunca generalizar à força. Ao clonar migration
  por `sed`, conferir que o CHECK de `companies.profile_id` mantém TODOS os perfis.
- **Incidente Baileys (RISKS.md):** o scheduler é o maior risco operacional novo — jobs que disparam
  mensagem outbound têm de herdar `EVOLUTION_DRY_RUN` em dev + guard de frescor por timestamp +
  idempotência por (tenant, alvo, janela). Webhook permanece OFF até religar consciente.
- **Gate entre ondas (obrigatório):** `mvn -B clean test` verde (contagem do Surefire, não grep) +
  `next build` limpo (Turbopack dev esconde import quebrado) + smoke. Nada avança de onda com o gate
  vermelho.
- **Pool de conexões (lição SM-K):** cada `*ServiceTest` novo é um ApplicationContext; manter o pool
  minúsculo em `application-dev.yml` (min-idle 0/max 2) pra não estourar o pooler Supabase quando o
  número de nichos × features crescer.
