# Features Sugeridas — Casa de festas/buffet

> Backlog de features avançadas para o nicho **Casa de festas/buffet** (profile_id `eventos`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Proposta order-based** com itens de orçamento (total materializado) + gate de aprovação em 2 fases via WhatsApp (a IA abre a proposta em `rascunho` e captura `aprovada`/`recusada` quando está `orcada`).
- **Cronograma ordenado do dia do evento** (`event_timeline_items`: horário + título + descrição), gerenciado no painel, fora do total.
- **Cerimonialistas** (catálogo simples, atribuição opcional à proposta, sem agenda).
- **Funil de status** (rascunho → orcada → aprovada → fechada → realizada + recusada/cancelada) com notificação automática ao cliente em orcada/aprovada/fechada/recusada.
- **Persona consultiva-organizadora** com travas: não fecha preço/contrato/desconto, não confirma data não confirmada, não inventa item/valor/estrutura.
- Configuração básica (nome do espaço/buffet + observações). SEM pagamento, SEM foto, SEM conflito de data, SEM catálogo de pacotes.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/depósito com link de pagamento (gateway #50).** A dor nº 1 de casa de festas é reter a data: sem sinal, o cliente "some" e o buffet perde datas nobres (sábado de dezembro). Assim que o gateway (#50) existir, a transição `aprovada → fechada` gera um link de sinal (ex.: 30% do total) enviado no WhatsApp; a proposta só vira `fechada` quando o sinal cai. Isso converte "aprovado" (frágil) em "contrato pago" (firme) e ancora todo o funil de receita do nicho. É o maior ROI possível: transforma intenção em caixa.

**2. Catálogo de pacotes pré-cadastrados (upsell estruturado).** Hoje o orçamento é 100% ad-hoc — a equipe digita item a item, o que é lento e inconsistente. Um catálogo de pacotes/itens (Pacote Prata/Ouro/Diamante + adicionais: hora extra, open bar, decoração premium) faz a equipe montar orçamento em segundos E deixa a IA sugerir upsell dentro do que existe (sem violar a trava — ela oferece o que está cadastrado, não inventa). Vende mais por ticket e padroniza a proposta. Esforço médio, retorno alto e recorrente.

**3. Aviso de "data já ocupada" no painel + captura de data confirmada.** A casa faz ~1 evento por data; hoje `event_date` é campo livre sem nenhuma checagem, o que gera overbooking acidental (duas festas no mesmo sábado). Um aviso informativo no painel quando já existe proposta `fechada`/`aprovada` na mesma data (não bloqueia — a equipe decide) evita o pior erro operacional do nicho sem esforço grande. Protege reputação e receita perdida por conflito.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/depósito com link de pagamento na transição `aprovada→fechada` | Alto | M | Trava a data com caixa real; converte aprovação frágil em contrato pago | Receita |
| 2 | Catálogo de pacotes/itens pré-cadastrados (Prata/Ouro/Diamante + adicionais) | Alto | M | Monta orçamento em segundos, padroniza, habilita upsell da IA | Receita |
| 3 | Aviso de data ocupada no painel (proposta fechada/aprovada na mesma data) | Alto | P | Evita overbooking acidental do sábado nobre | Operação |
| 4 | Parcelamento do saldo com boletos/PIX agendados até o evento | Alto | M | Fecha vendas maiores; fluxo de caixa previsível pro buffet | Receita |
| 5 | Scheduler de lembrete automático de pagamento (parcela/sinal a vencer) | Alto | M | Reduz inadimplência e cobrança manual da equipe | Operação |
| 6 | Auto-transição por data (proposta `fechada` vira `realizada` no dia seguinte ao evento) | Médio | P | Mantém o funil limpo sem trabalho manual; dispara pós-venda | Operação |
| 7 | Pós-venda: NPS + pedido de avaliação/depoimento após `realizada` | Alto | M | Prova social vende para os próximos noivos/aniversariantes | Marketing |
| 8 | Reativação de lead frio (proposta `orcada`/`rascunho` parada há X dias) via IA | Alto | M | Recupera orçamento esquecido = receita que já estava quase fechada | Retenção |
| 9 | Upsell proativo da IA dentro do catálogo (open bar, hora extra, DJ) | Alto | M | Aumenta ticket médio sem esforço humano; respeita a trava (só oferece o cadastrado) | IA |
| 10 | Contrato em PDF gerado da proposta + aceite registrado (e-sign leve) | Alto | M | Formaliza o "fechada" com documento; reduz disputa/quebra | Receita |
| 11 | Qualificação de lead pela IA (tipo/data/nº convidados/orçamento) antes de abrir proposta | Médio | P | Equipe recebe briefing pronto; prioriza leads quentes | IA |
| 12 | Campanha em massa segmentada (ex.: "datas livres de novembro", pacote sazonal) | Alto | M | Preenche datas ociosas; ativa base parada | Marketing |
| 13 | Lista de convidados/RSVP com link público por evento | Médio | G | Serviço a mais que retém o cliente e diferencia da concorrência | Retenção |
| 14 | Fornecedores/parceiros como catálogo (DJ, foto, decoração) com comissão registrada | Médio | M | Cross-sell de terceiros vira nova fonte de receita pro buffet | Receita |
| 15 | Página pública/CMS do espaço (galeria, pacotes, formulário de orçamento) | Médio | M | Capta lead 24/7 e alimenta a IA; vitrine do espaço | Marketing |
| 16 | Mood board/galeria de referências por proposta (foto liberada) | Médio | M | Alinha expectativa visual; fecha mais e reduz retrabalho | Operação |

## Detalhamento das prioritárias

### 1. Sinal/depósito com link de pagamento

- **Problema de negócio:** aprovação verbal não segura data. Casa de festas vive de datas escassas (fins de semana); sem sinal pago, cliente cancela em cima da hora e a data nobre é perdida sem compensação.
- **Como funciona:** ao mover a proposta de `aprovada → fechada`, o painel (ou a IA, ao capturar "quero fechar") gera um link de pagamento de sinal — percentual configurável do `total_cents` (ex.: 30%). O `OutboundService` envia o link pelo WhatsApp via Evolution. Um webhook do gateway confirma o pagamento e só então a proposta transita para `fechada` (mantendo a trava atual: a IA continua NÃO fechando preço, apenas dispara o link do valor já orçado pela equipe). Novo campo `event_proposals.deposit_cents` + `deposit_status` + tabela de pagamentos por proposta.
- **Dependências:** gateway de pagamento (#50). Sem ele, entregar só o registro manual de "sinal recebido" (flag + valor) como ponte.
- **Métrica de sucesso:** % de propostas `aprovada` que viram `fechada` com sinal pago; redução de cancelamento pós-aprovação.

### 2. Catálogo de pacotes/itens pré-cadastrados

- **Problema de negócio:** orçamento ad-hoc é lento, inconsistente e não escala; a IA não tem o que oferecer (não pode inventar itens/valores — trava).
- **Como funciona:** nova tabela `event_packages` (nome, descrição, preço, tipo: pacote/adicional) por company. No painel, montar orçamento passa a ser "escolher do catálogo" (ainda permitindo item avulso). O `EventosContextCache` injeta o catálogo na persona; a IA passa a poder DESCREVER pacotes e sugerir adicionais existentes (respeitando a trava — só o que está cadastrado, sem inventar valor). O item da proposta guarda snapshot do preço (padrão do chassi).
- **Dependências:** nenhuma externa. Combina com #9 (upsell da IA).
- **Métrica de sucesso:** tempo médio para montar orçamento; ticket médio da proposta; nº de adicionais por proposta.

### 3. Aviso de data ocupada no painel

- **Problema de negócio:** overbooking acidental — duas festas na mesma data — é o erro operacional mais caro do nicho (perda de reputação + reembolso).
- **Como funciona:** ao abrir/editar uma proposta com `event_date`, o backend consulta se já existe proposta `aprovada`/`fechada`/`realizada` na mesma data e company e retorna um aviso NÃO bloqueante (a casa pode ter dois salões). É consulta simples por `event_date` — nada de máquina de conflito por minuto (a data não é recurso disputado por slot). Opcionalmente, a IA passa a dizer com mais firmeza "essa data já está reservada, posso ver outra?" quando o contexto indicar ocupação.
- **Dependências:** nenhuma. A captura confiável de `event_date` na tag `<proposta_evento>` ajuda.
- **Métrica de sucesso:** nº de conflitos de data detectados/evitados; zero overbooking reportado.

### 4. Parcelamento do saldo até o evento

- **Problema de negócio:** o ticket de um evento é alto; exigir pagamento à vista trava vendas. Buffet precisa de fluxo de caixa previsível entre o fechamento e a festa.
- **Como funciona:** após o sinal (#1), o saldo é dividido em N parcelas com vencimentos até a data do evento (ex.: 3 boletos/PIX mensais). Nova tabela `event_installments` (proposta, valor, vencimento, status). O scheduler (#5) dispara link/lembrete a cada vencimento; webhook do gateway baixa a parcela. Painel mostra o extrato de pagamento da proposta.
- **Dependências:** gateway (#50), scheduler/cron. Depende de #1 (sinal) como âncora.
- **Métrica de sucesso:** % do saldo quitado antes do evento; inadimplência por proposta.

### 5. Scheduler de lembrete automático de pagamento

- **Problema de negócio:** cobrança manual é esquecida e desgasta a relação; parcela vencida vira perda.
- **Como funciona:** um job (cron) roda diariamente, varre `event_installments`/sinal a vencer em D-3/D0/D+3 e dispara mensagem pela IA/Evolution ("sua parcela do evento de [data] vence amanhã, aqui está o link"). Texto defensivo, sem pressão. Registra o envio para não duplicar. Também pode lembrar o cliente da data do evento em D-7 (reforço de comparecimento/organização).
- **Dependências:** scheduler/cron (infra transversal), gateway (#50) para o link; funciona parcialmente só com lembrete-texto sem link.
- **Métrica de sucesso:** taxa de pagamento no prazo; redução de parcelas em atraso.

## Dependências transversais

- **Gateway de pagamento (#50 — global):** destrava #1 (sinal), #4 (parcelamento), #5 (link nos lembretes) e o webhook de baixa. É a dependência de maior alavancagem — libera todo o eixo de RECEITA do nicho. Antes dele, entregar as pontes de registro manual (sinal/parcela marcados na mão) já organiza a operação.
- **Upload de foto/anexo (SERVICE_ROLE_KEY ausente):** destrava #16 (mood board/galeria por proposta) e a galeria da página pública (#15). Enquanto bloqueado, usar URL colada como paliativo.
- **Scheduler/cron (infra):** destrava #5 (lembretes), #6 (auto-transição realizada), #8 (reativação de lead frio) e os disparos temporais de #4. Um único mecanismo de agendamento serve os quatro.
- **Campanha em massa segmentada (infra de envio):** destrava #12 (datas ociosas/sazonal) e potencializa #7 (pós-venda) e #8 (reativação) — o mesmo motor de segmentação + Evolution atende marketing e retenção.
