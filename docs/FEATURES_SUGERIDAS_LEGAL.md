# Features Sugeridas — Legal (advocacia)

> Backlog de features avançadas para o nicho **Legal (advocacia)** (profile_id `legal`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Clientes** (`legal_clients`): catálogo próprio, desacoplado do contato de WhatsApp, com vínculo automático pelo telefone (badge "vinculado"); CPF/CNPJ, e-mail, telefone, notas.
- **Processos** (`legal_cases`): CNJ validado por módulo 97, título/vara/fórum/matéria, filtros por status.
- **Status do processo** (ativo/suspenso/arquivado/encerrado) com **notificação automática** ao cliente vinculado em suspensão/arquivamento/encerramento (texto fixo defensivo).
- **Andamentos** (`legal_case_updates`): timeline manual (título/descrição/data); a IA usa os últimos andamentos para responder "como está meu processo?".
- **IA de atendimento:** identifica o cliente pelo telefone, resume andamentos, **nunca dá opinião jurídica** (trava do nicho), pede identificação a telefone desconhecido sem expor dados.
- **Não tem hoje:** audiências/prazos com cálculo, alertas/lembretes, partes formais, recursos/custas, documentos/anexos (bloqueador de Storage), pagamento, personalização de textos.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Agenda de prazos e audiências com lembrete automático.** É o maior gerador de valor do escritório: prazo perdido é dano ao cliente e risco de responsabilidade civil do advogado. Hoje tudo é manual e sem alerta. Ligando cada prazo/audiência a um processo e disparando lembrete pelo WhatsApp (para o cliente comparecer e, num painel interno, para o advogado), o produto passa de "consulta de status" para "não deixo você perder prazo" — o argumento de venda mais forte e o que mais retém, porque o escritório para de conseguir operar sem ele. Esforço médio (clona o chassi de agenda + scheduler novo).

**2. Cobrança de honorários (sinal + parcelas) com lembrete de vencimento.** Advocacia vive de honorários contratuais e recorrentes (mensais/êxito). Registrar o combinado por processo/cliente, gerar parcelas e lembrar o cliente do vencimento pelo WhatsApp reduz inadimplência diretamente — receita que já é do escritório mas escorre por falta de cobrança organizada. Começa como controle manual (registro + lembrete) e destrava cobrança real quando o gateway (#50) existir. Esforço P/M, retorno de receita imediato.

**3. Reativação/pós-atendimento e captação de avaliação (Google/indicação).** O escritório vive de reputação e recomendação. Ao encerrar um processo, disparar automaticamente uma mensagem de agradecimento pedindo avaliação no Google e oferecendo indicação gera novos clientes a custo quase zero. É esforço P (clona notificação de status já existente + trigger no encerramento) com efeito de marketing composto.

## Backlog priorizado (12+ features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Agenda de prazos e audiências vinculados ao processo + lembrete automático (WhatsApp) | Alto | M | Escritório para de perder prazo/audiência; cliente é lembrado de comparecer | Operação |
| 2 | Honorários: contrato + parcelas + lembrete de vencimento | Alto | M | Reduz inadimplência de honorários; organiza a receita recorrente | Receita |
| 3 | Pós-encerramento: agradecimento + pedido de avaliação (Google) + indicação | Alto | P | Gera novos clientes por reputação/boca a boca a custo zero | Marketing |
| 4 | Consulta/agendamento de reunião com o advogado pela IA (tag `<consulta_juridica>`) | Alto | M | Cliente marca reunião 24/7 sem telefonema; menos ligação, mais conversão | Receita |
| 5 | Scheduler de auto-transição + lembrete de prazo em D-3/D-1 | Alto | M | Alerta antecipado do prazo; nada depende de alguém "lembrar" | Operação |
| 6 | Reativação de cliente/processo inativo (sem andamento há N dias) | Alto | P | Recupera cliente esquecido e mostra que o processo está sendo cuidado | Retenção |
| 7 | Qualificação de lead pela IA (novo contato sem processo) | Alto | M | Filtra e cadastra potencial cliente antes de ocupar o advogado | IA |
| 8 | Modelos de notificação personalizáveis por escritório | Médio | P | Voz/marca própria nas mensagens; deixa de ser texto genérico | Marketing |
| 9 | Partes do processo (autor/réu/terceiros/parte contrária) | Médio | P | Organização real do caso; base para conflito de interesse | Operação |
| 10 | Dashboard do escritório (processos por status/matéria, prazos da semana, inadimplência) | Médio | M | Visão de gestão: o que vence, o que rende, o que travou | Operação |
| 11 | Consultoria avulsa paga / pacote de horas (assinatura de assessoria) | Alto | M | Receita recorrente de PJ (assessoria mensal) e de consulta pontual | Receita |
| 12 | Entrega read-only de documento do processo pela IA (petição/andamento oficial) | Médio | M | Cliente recebe o documento certo sem expor o acervo; depende de Storage | IA |
| 13 | Campanha em massa segmentada (por matéria/status/inatividade) | Médio | M | Comunica mudança de lei/oportunidade ao público certo | Marketing |
| 14 | Confirmação de audiência com resposta do cliente (confirma/remarca) | Médio | P | Reduz falta em audiência e retrabalho de reagendamento | Operação |
| 15 | NPS/pesquisa de satisfação pós-etapa | Médio | P | Mede satisfação e antecipa cliente insatisfeito antes da perda | Retenção |
| 16 | Indicação com recompensa (advogado parceiro / desconto em honorário) | Médio | P | Transforma cliente satisfeito em canal de aquisição | Marketing |

## Detalhamento das prioritárias

### 1. Agenda de prazos e audiências com lembrete automático

- **Problema de negócio:** perder prazo processual é o pior evento possível num escritório — dano ao cliente, risco de ação de responsabilidade contra o advogado e perda de confiança. Hoje os andamentos são manuais e não há alerta nenhum; o escritório depende da memória de alguém.
- **Como funciona:** nova entidade `legal_deadlines` (FK para `legal_cases`) com tipo (prazo/audiência), título, `due_date`, `due_time` opcional e status (pendente/cumprido/perdido). Um **scheduler** (cron backend, novo — hoje o nicho não tem) varre prazos com vencimento em D-3/D-1/D-0 e dispara: (a) lembrete ao cliente vinculado via `LegalCaseNotifier` (reaproveita o canal Evolution já usado nas notificações de status) e (b) destaque no painel do advogado. A IA, quando o cliente pergunta "quando é minha audiência?", lê os prazos pendentes do contexto (`LegalCaseContextCache`) e responde a data — **sem interpretar o mérito** (respeita a trava: informa data/local, nunca aconselha juridicamente). Painel: aba "Prazos" no detalhe do processo + visão consolidada "Prazos da semana".
- **Dependências:** scheduler/cron (transversal, novo no nicho); Evolution (já existe).
- **Métrica de sucesso:** % de prazos com lembrete entregue antes do vencimento; nº de prazos marcados "perdido" tendendo a zero.

### 2. Cobrança de honorários (sinal + parcelas) com lembrete de vencimento

- **Problema de negócio:** honorários contratuais e mensais são a receita do escritório, mas a cobrança costuma ser desorganizada — parcela vence e ninguém avisa, gerando inadimplência que é dinheiro já ganho escorrendo.
- **Como funciona:** `legal_fee_agreements` (por cliente/processo: valor total, tipo — à vista/parcelado/mensal/êxito) + `legal_fee_installments` (parcelas com `due_date`, `amount_cents`, status pago/pendente/atrasado). O scheduler lembra o cliente pelo WhatsApp em D-3 e no vencimento (texto personalizável — ver feature 8). Registro de pagamento **manual** pelo advogado nesta fase (marca a parcela como paga); painel mostra total em aberto e inadimplência. A IA pode informar "sua próxima parcela vence em DD/MM" quando perguntado, **sem negociar valor/desconto** (trava: não fecha condição financeira, só informa o combinado).
- **Dependências:** cobrança real (Pix/cartão) depende do **gateway #50** — até lá é controle + lembrete manual; scheduler.
- **Métrica de sucesso:** taxa de inadimplência de honorários; % de parcelas pagas até o vencimento após ativar lembrete.

### 3. Pós-encerramento: agradecimento + avaliação + indicação

- **Problema de negócio:** o escritório cresce por reputação e indicação, mas raramente pede avaliação no momento certo — logo após entregar resultado, quando o cliente está mais satisfeito.
- **Como funciona:** trigger no momento em que o processo muda para **encerrado** (evento já existente que hoje só dispara a notificação de status). Além do texto atual, encadeia uma mensagem de agradecimento com link de avaliação (Google Meu Negócio) e um convite de indicação. Configurável por escritório (texto + link) — reaproveita o `LegalCaseNotifier`. Nada disso viola a trava (é comunicação de relacionamento, não conselho jurídico).
- **Dependências:** modelos de notificação personalizáveis (feature 8) tornam melhor, mas não é bloqueante; link de avaliação é campo de config.
- **Métrica de sucesso:** nº de avaliações no Google atribuíveis ao disparo; nº de leads que citam "indicação".

### 4. Agendamento de reunião com o advogado pela IA

- **Problema de negócio:** muito lead e muito cliente ativo quer "conversar com o advogado" e isso hoje vira ligação/manual. Perde-se conversão fora do horário comercial.
- **Como funciona:** introduz o **primeiro fluxo de agenda do nicho** — `legal_appointments` (data/hora, tipo: presencial/online/telefone, advogado, notas), com conflito por advogado (clona o chassi de conflito por `professional_id` do dental/salon: janela half-open, re-verificada na transação, `end_at` materializado no INSERT). A IA emite a tag `<consulta_juridica>{"date","start_time","type","notes"}` — espelho da tag `<consulta>` do dental, adaptada; `OutboundService` ganha `maybeProcessConsultaJuridica`. A IA **negocia horário mas não dá parecer** (trava intacta). Notifica confirmação/cancelamento; painel "Agenda" no grupo Escritório.
- **Dependências:** scheduler ajuda (lembrete da reunião) mas o núcleo já entrega valor sem ele.
- **Métrica de sucesso:** nº de reuniões agendadas pela IA/mês; taxa de comparecimento; leads convertidos em cliente.

### 5. Scheduler de auto-transição + lembrete de prazo antecipado (D-3/D-1)

- **Problema de negócio:** sem cron, tudo é reativo — ninguém é avisado antes, e processos "esquecem" de mudar de estado. É a infra que multiplica o valor das features 1, 2 e 4.
- **Como funciona:** job agendado (novo no nicho) que roda diariamente e: (a) dispara lembretes de prazo/parcela/reunião em D-3 e D-1; (b) opcionalmente sinaliza processos sem andamento há N dias (alimenta a feature 6). Idempotente (marca "lembrete enviado" para não duplicar). É o mesmo padrão de scheduler que vários nichos listam como pendência.
- **Dependências:** nenhuma além do próprio cron; habilita 1, 2, 4, 6, 14.
- **Métrica de sucesso:** % de eventos com lembrete antecipado entregue; queda de eventos vencidos sem aviso.

## Dependências transversais

- **Gateway de pagamento (#50):** destrava cobrança real de honorários (feature 2), consultoria avulsa paga e assinatura de assessoria (feature 11). Até lá essas features entram como registro + lembrete manual, ainda gerando valor.
- **Upload de documento/anexo (bloqueador de Storage / SERVICE_ROLE_KEY):** destrava a entrega read-only de documentos do processo pela IA (feature 12) e o anexo de petições no processo. Enquanto não liberar, o produto continua só com texto.
- **Scheduler/cron (novo no nicho):** é a fundação de lembretes e auto-transição — habilita as features 1, 2, 4, 5, 6, 14 (todas as que dependem de "avisar no momento certo").
- **Campanha em massa segmentada:** infra de disparo em lote (por matéria/status/inatividade) reaproveitada pela reativação (feature 6), avaliação/indicação (features 3/16) e comunicação de mudança de lei (feature 13).
