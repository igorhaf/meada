# Features Sugeridas — Estúdio fotográfico

> Backlog de features avançadas para o nicho **Estúdio fotográfico** (profile_id `fotografia`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Agenda por fotógrafo** com conflito por `professional_id` (half-open, re-verificado na transação), `end_at` materializado, janela `opens_at`..`closes_at` → paralelismo entre fotógrafos.
- **Catálogo de pacotes** (`fotografia_packages`: nome, categoria livre, duração, preço, `delivery_days`, active); a sessão snapshota name+price+duration+delivery_days.
- **IA agenda sessão** via `<sessao_foto>` (pacote + fotógrafo + data/hora) — DESCARTA qualquer preço que a IA tente emitir (preço vem do catálogo).
- **Entrega de LINK read-only** via `<entrega_material>{session_id}` — envia `delivery_link` VERBATIM com barreira de contato; `delivery_due_date` materializada = `date(start_at) + delivery_days`.
- **Status** `agendada → confirmada → realizada → entregue` (+ cancelada/falta); notifica confirmada (com pacote+fotógrafo+data/hora) e cancelada.
- **Trava:** IA nunca inventa pacote/valor/prazo/fotógrafo, nunca negocia preço/desconto, nunca promete resultado estético, nunca garante entrega antes do prazo, nunca aceita/recusa sessão.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/pré-pagamento para travar a data (reserva com sinal).** Estúdio vive de agenda escassa e de no-show que come faturamento: um casamento ou ensaio bloqueia um fotógrafo por horas, e quando o cliente some sem sinal a data morre. Cobrar um sinal (ex.: 30% do pacote) para CONFIRMAR a sessão transforma o "confirmada" numa reserva de fato paga, elimina o furador oportunista e antecipa caixa. É o maior gerador de receita e o maior redutor de perda do nicho — depende do gateway #50, mas é o primeiro alvo assim que ele existir.

**2. Lembrete + confirmação automática D-2/D-1 (scheduler).** Sessão fotográfica exige preparo do cliente (roupa, locação, horário de luz) e do fotógrafo; falta significa deslocamento e equipe parados sem receita. Um cron que dispara "sua sessão é amanhã às 15h com o fotógrafo X, confirma?" e move `agendada → confirmada` na resposta (ou marca risco na falta de resposta) derruba no-show sem trabalho humano. Esforço P (o notifier e os status já existem), retorno imediato em ocupação de agenda.

**3. Cobrança automática da entrega no prazo + pós-venda/upsell de fotos extras.** O momento de maior disposição de compra é quando o cliente RECEBE as fotos e se emociona. Hoje a entrega é passiva (só quando alguém dispara a tag). Um scheduler que, no `delivery_due_date`, lembra o estúdio (ou entrega automática se o link já está gravado) e, logo após a entrega, oferece "fotos extras / álbum impresso / arquivo em alta" gera receita incremental num cliente já quente. Amarra retenção (entrega no prazo prometido = reputação) com upsell.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/pré-pagamento para confirmar a sessão | Alto | M | Trava a data com dinheiro, elimina furador, antecipa caixa | Receita |
| 2 | Lembrete + confirmação automática D-2/D-1 | Alto | P | Derruba no-show de sessão, ocupa a agenda | Operação |
| 3 | Entrega no prazo automatizada + pós-venda de fotos extras | Alto | M | Reputação de entrega + upsell no momento quente | Receita |
| 4 | Política de no-show/cancelamento com retenção do sinal | Alto | P | Compensa data perdida, desincentiva furo de última hora | Operação |
| 5 | Upsell proativo de pacote/add-on pela IA no agendamento | Alto | M | Sobe ticket médio (hora extra, 2º fotógrafo, making-of) | IA |
| 6 | Assinatura/plano de ensaios recorrentes (gestante, newborn, família) | Alto | M | Receita recorrente + cliente cativo por meses | Receita |
| 7 | Reativação de cliente inativo (ensaio anual, "faz 1 ano") | Alto | M | Traz de volta cliente que já comprou, custo zero de aquisição | Retenção |
| 8 | Cupom/voucher e gift card de ensaio | Médio | P | Presente de Natal/aniversário vira nova venda e novo contato | Receita |
| 9 | Programa de indicação com desconto/cashback | Médio | M | Cliente satisfeito traz clientes (boca a boca virou canal) | Marketing |
| 10 | NPS/avaliação pós-entrega + coleta de depoimento | Médio | P | Prova social para fechar próximos + detecta insatisfação | Marketing |
| 11 | Dashboard de faturamento/ocupação por fotógrafo e pacote | Médio | M | Dono vê o que rende, qual pacote/fotógrafo puxa receita | Operação |
| 12 | Campanha em massa segmentada (data comemorativa, promo de baixa) | Médio | M | Enche agenda em período fraco disparando pra base | Marketing |
| 13 | Aniversário do cliente/do filho → ensaio comemorativo | Médio | P | Gatilho emocional recorrente que vende ensaio | Retenção |
| 14 | Bloqueio/indisponibilidade e agenda de equipe por sessão | Médio | M | Evita agendar em folga/viagem; casa fotógrafo+assistente | Operação |
| 15 | Galeria com seleção de fotos pelo cliente (quando foto liberar) | Médio | G | Cliente escolhe as do álbum → upsell de fotos além do pacote | Receita |
| 16 | Contrato/autorização de uso de imagem com aceite registrado | Médio | M | Formaliza a venda, protege juridicamente, profissionaliza | Operação |

## Detalhamento das prioritárias

### 1. Sinal/pré-pagamento para confirmar a sessão

- **Problema de negócio:** a sessão bloqueia um fotógrafo por horas; sem compromisso financeiro, o cliente fura e a data — recurso escasso e não-reestocável — se perde. Faltam caixa antecipado e filtro de cliente sério.
- **Como funciona:** ao emitir `<sessao_foto>` a IA agenda em `agendada` como hoje; um novo passo gera uma cobrança de sinal (percentual configurável do preço snapshotado do pacote) e envia o link de pagamento. Só quando o gateway confirma o pagamento o backend move a sessão para `confirmada` e dispara a notificação existente. A **trava é respeitada**: a IA NÃO negocia valor nem confirma pagamento — ela apenas informa o link; a confirmação vem do webhook do gateway. Painel: coluna de status de sinal (aguardando/pago/estornado) na Agenda e valor do sinal por pacote nas Configurações.
- **Dependências:** gateway de pagamento (#50, global). Sem ele, entregar só a MODELAGEM (colunas `deposit_cents`, `deposit_status`, `deposit_paid_at`) + registro manual de sinal pago pelo tenant como ponte.
- **Métrica de sucesso:** % de sessões confirmadas com sinal pago; queda na taxa de no-show; caixa antecipado/mês.

### 2. Lembrete + confirmação automática D-2/D-1

- **Problema de negócio:** falta de sessão significa fotógrafo e equipe deslocados/parados sem receita, e o cliente muitas vezes esquece ou não se preparou.
- **Como funciona:** um scheduler (cron) varre sessões `agendada`/`confirmada` cujo `start_at` cai em D-2 e D-1 e dispara pelo Evolution um lembrete com pacote+fotógrafo+data/hora+prazo de entrega, pedindo confirmação. Resposta afirmativa move `agendada → confirmada` (reaproveita a transição e a notificação já existentes); silêncio marca a sessão como "risco" para follow-up humano. Respeita a trava (texto informativo/defensivo, sem promessa estética).
- **Dependências:** infra de scheduler/cron (transversal — destrava #3, #4, #7, #13 também). Evolution ativo (hoje webhook OFF em local — ver RISKS.md).
- **Métrica de sucesso:** taxa de no-show antes/depois; % de confirmações via lembrete automático.

### 3. Entrega no prazo automatizada + pós-venda de fotos extras

- **Problema de negócio:** entrega atrasada arranha a reputação (é o produto final), e o pico de disposição de compra do cliente — quando ele vê as fotos — passa sem oferta.
- **Como funciona:** o scheduler observa `delivery_due_date`; se o `delivery_link` já está gravado, dispara a entrega automática (reusa o `EntregaMaterialHandler`, mantendo a barreira de contato); se o link ainda não está, alerta o estúdio de que o prazo venceu. Logo após a entrega, a IA oferece add-ons do catálogo (fotos avulsas em alta, álbum impresso, arquivo estendido) — SEM inventar preço (valores do catálogo) e SEM prometer resultado. A venda extra vira uma nova sessão/pedido ou um add-on cobrado (amarra com #15 e com gateway).
- **Dependências:** scheduler/cron; para cobrar o extra, gateway #50; para "escolher as fotos", upload de foto liberado (#15).
- **Métrica de sucesso:** % de entregas dentro do prazo; receita de add-ons por sessão entregue; conversão da oferta pós-entrega.

### 4. Política de no-show/cancelamento com retenção do sinal

- **Problema de negócio:** cancelar em cima da hora queima uma data que poderia ter sido vendida; sem regra clara, o estúdio absorve o prejuízo.
- **Como funciona:** Configurações define janela de cancelamento (ex.: grátis até 72h antes; após isso retém o sinal) e a IA COMUNICA a política ao agendar e ao cliente pedir cancelamento — sem decidir sozinha reembolso (a transição de status e a retenção do sinal são ação humana/registro no painel, respeitando "IA não aceita/recusa/cancela"). O backend registra o motivo e se o sinal foi retido; alimenta o dashboard (#11).
- **Dependências:** sinal (#1) e gateway #50 para reter/estornar de fato; scheduler ajuda a marcar `falta` automaticamente.
- **Métrica de sucesso:** receita recuperada por retenção de sinal; queda de cancelamentos tardios.

### 5. Upsell proativo de pacote/add-on pela IA no agendamento

- **Problema de negócio:** o ticket médio fica preso no pacote base; oportunidades naturais (hora extra, 2º fotógrafo, making-of, troca de look) não são oferecidas.
- **Como funciona:** ao montar a sessão, o contexto (`FotografiaContextCache`) injeta add-ons/pacotes superiores marcados como "sugeríveis"; a IA oferece de forma consultiva ("quer incluir o making-of?") usando SEMPRE nome+preço do catálogo, sem inventar valor nem pressionar. Se o cliente aceita, entra no pacote/add-on da tag. Mantém a trava (nada de desconto negociado nem promessa estética).
- **Dependências:** modelagem de add-ons no catálogo (colunas/tabela auxiliar); nenhuma dependência externa dura.
- **Métrica de sucesso:** ticket médio por sessão; taxa de aceite de upsell.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava #1 (sinal), a cobrança real do pós-venda em #3, a retenção/estorno de #4, o gift card pago de #8 e o cashback de #9. Sem ele, essas features entregam só a MODELAGEM + registro manual de pagamento pelo tenant.
- **Scheduler/cron (infra transversal, hoje inexistente no nicho):** destrava #2 (lembrete D-2/D-1), #3 (entrega no prazo), #4 (marcar `falta`), #7 (reativação por tempo), #13 (aniversário) e as auto-transições de status. É o multiplicador de maior alcance depois do gateway.
- **Upload de foto/material (bloqueado por SERVICE_ROLE_KEY):** destrava #15 (galeria com seleção pelo cliente) e enriquece #3 (fotos extras selecionáveis). Enquanto bloqueado, o material continua por link colado (como hoje) e a seleção fica pendente.
- **Campanha em massa segmentada (infra de disparo em lote):** destrava #12 diretamente e potencializa #7, #10 e #13 (permite disparar reativação/NPS/aniversário para segmentos, não 1 a 1).
- **Motor de add-ons no catálogo:** pré-requisito leve de #5 (upsell) e da parte de venda extra de #3; é modelagem interna, sem dependência externa.
