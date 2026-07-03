# Features Sugeridas — Ótica

> Backlog de features avançadas para o nicho **Ótica** (profile_id `otica`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Fluxo A — agenda de exame:** optometristas + agenda com conflito por profissional (paralelismo), status agendado→confirmado→realizado + cancelado/falta, notificação de confirmação/cancelamento via WhatsApp.
- **Fluxo B — encomenda de óculos:** pedido com armação + lentes (modifiers tipo/tratamento), total recalculado no backend, gate de aceite humano (aguardando→em_montagem/recusado), Kanban de pedidos.
- **Lead time de montagem:** item sob encomenda define `ready_date = hoje + MAX(lead)`; violação → 422.
- **Receita administrativa:** campos de grau (esf/cil/eixo OD/OE + DP) registrados VERBATIM, sem interpretação; `prescription_pending` quando faltam dados.
- **Trava de comportamento:** a IA nunca prescreve grau, nunca diagnostica, nunca aceita/recusa pedido, nunca inventa item/preço.
- **Catálogo:** armações/lentes/acessórios com opções e made_to_order/lead; retirada na loja (sem taxa de entrega).

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete + confirmação automática de exame (anti-no-show).** Ótica vive de agenda de exame cheia; cadeira de optometrista vazia é receita perdida na hora e óculos não vendido depois (o exame é o funil de entrada da venda de óculos). Um scheduler que dispara "seu exame é amanhã às 14h, confirma?" pelo WhatsApp e move o status conforme a resposta reduz o buraco de faltas com esforço P. Cada exame recuperado é potencialmente uma venda de armação+lente — o item de maior ticket da loja.

**2. Aviso de "óculos pronto para retirada" + cobrança de retirada parada.** Hoje o status `pronto` notifica uma vez, mas óculos montado que não é retirado é capital imobilizado e cliente que esfria. Um follow-up automático ("seu óculos está pronto há 3 dias, te esperamos!") mais um lembrete de sinal/saldo pendente acelera a retirada, libera a bancada e fecha o caixa. Esforço P, retorno direto em fluxo de caixa.

**3. Recompra programada de lentes de contato / troca de grau (reativação por ciclo).** Quem usa lente de contato descartável recompra a cada 30/90 dias; quem tem óculos troca o grau a cada ~12-18 meses. A ótica esquece o cliente e ele compra no concorrente. Registrar a data da última compra/exame e disparar "está na hora de repor suas lentes / revisar seu grau" transforma cliente inativo em receita recorrente previsível. É o maior alavancador de LTV do nicho, com esforço M.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática de exame | Alto | P | Reduz no-show da cadeira do optometrista (funil de venda) | Operação |
| 2 | Follow-up de "óculos pronto" não retirado | Alto | P | Acelera retirada, libera bancada, fecha caixa | Retenção |
| 3 | Recompra programada (lentes de contato / troca de grau) | Alto | M | Reativa cliente por ciclo → receita recorrente | Retenção |
| 4 | Sinal/pagamento antecipado do óculos sob encomenda | Alto | M | Garante compromisso, financia a montagem, reduz calote | Receita |
| 5 | No-show do exame com política (reagendar/bloquear) | Alto | P | Protege a agenda de quem falta repetidamente | Operação |
| 6 | Upsell de tratamentos de lente na conversa (antirreflexo, filtro azul, transitions) | Alto | M | Aumenta ticket médio do pedido sem violar trava | Receita |
| 7 | Pós-venda + NPS após retirada | Médio | P | Mede satisfação, gera prova social, detecta ajuste de armação | Retenção |
| 8 | Campanha em massa segmentada (grau vencendo, sem compra há X meses) | Alto | M | Traz cliente inativo de volta em lote | Marketing |
| 9 | Programa de indicação ("indique e ganhe desconto no óculos") | Médio | M | Aquisição barata via boca a boca | Marketing |
| 10 | Fidelidade/cashback por compra (armação + lente = pontos) | Médio | M | Prende o cliente à loja na próxima troca | Receita |
| 11 | Garantia/assistência de armação com prazo rastreado | Médio | M | Diferencia da concorrência, gera retorno à loja | Retenção |
| 12 | Aniversário do cliente com cupom de armação | Médio | P | Gatilho de recompra emocional e datado | Retenção |
| 13 | Relatório/dashboard (taxa de no-show, exame→venda, ticket médio, lead time real) | Médio | M | Dá visão de gargalo e conversão pro dono | Operação |
| 14 | Multi-unidade (várias lojas, agenda e estoque por unidade) | Médio | G | Escala a rede sob um só tenant | Operação |
| 15 | Entrega do óculos com taxa + rastreio de status | Médio | M | Amplia alcance além de quem retira na loja | Receita |
| 16 | Resposta a foto da receita/armação quando upload liberar | Alto | M | Registra receita e identifica armação por imagem | IA |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática de exame

- **Problema de negócio:** a cadeira do optometrista é o funil de entrada da loja — quem faz exame é candidato a comprar óculos. Falta silenciosa deixa slot ocioso e perde a venda subsequente.
- **Como funciona:** um scheduler (cron) varre exames com status `confirmado`/`agendado` para D-1 e D-0 e dispara mensagem pela Evolution ("seu exame é amanhã às 14h com Dra. X, responda SIM para confirmar"). A resposta do cliente é interpretada pela IA e mapeada para transição de status existente (confirma → mantém; "não posso" → oferece reagendar dentro dos slots livres). NÃO viola trava: a IA apenas confirma/reagenda horário, nunca fala de grau/conduta. Backend: nova coluna de controle de lembrete enviado (idempotência) + endpoint de opt do tenant nas Configurações.
- **Dependências:** scheduler/cron (transversal). Nenhuma outra.
- **Métrica de sucesso:** queda da taxa de `falta` nos exames; % de exames confirmados via resposta automática.

### 2. Follow-up de "óculos pronto" não retirado

- **Problema de negócio:** óculos montado e não retirado é capital parado, bancada ocupada e risco de cliente desistir. Hoje o status `pronto` avisa uma vez e para.
- **Como funciona:** o scheduler acompanha pedidos em `pronto` há N dias sem passar a `retirado` e dispara follow-ups escalonados ("seu óculos está pronto, quando passa buscar?" → depois "ainda te esperamos"). Se houver saldo a pagar (ver feature #4), inclui o valor pendente. A IA responde dúvidas de horário/localização e confirma intenção de retirada; NÃO aceita/recusa nada — só o tenant move o status. Painel: coluna "dias parado" no Kanban de pedidos.
- **Dependências:** scheduler/cron. Integra com sinal/pagamento (#4) para exibir saldo.
- **Métrica de sucesso:** tempo médio de pedido em `pronto` até `retirado`; % de pedidos prontos retirados em ≤7 dias.

### 3. Recompra programada (lentes de contato / troca de grau)

- **Problema de negócio:** lente de contato descartável recompra por ciclo (30/90 dias) e grau de óculos costuma trocar em ~12-18 meses. Sem lembrete, o cliente recompra no concorrente — perda pura de LTV.
- **Como funciona:** ao registrar a última compra/exame, o backend materializa uma "próxima recompra estimada" (ciclo da lente ou janela de revisão de grau). Um scheduler dispara "está na hora de repor suas lentes / revisar seu grau — quer agendar exame ou já deixar seu pedido?" A IA conduz para AGENDAR EXAME (Fluxo A) ou abrir ENCOMENDA (Fluxo B) — ambos já existem. NÃO prescreve nem sugere grau; só lembra do ciclo e encaminha ao exame. Painel: lista de "recompras previstas" filtrável por mês.
- **Dependências:** scheduler/cron. Opcionalmente campanha em massa (#8) para disparo em lote.
- **Métrica de sucesso:** receita recorrente atribuída ao lembrete; taxa de recompra dentro da janela.

### 4. Sinal/pagamento antecipado do óculos sob encomenda

- **Problema de negócio:** óculos sob encomenda envolve custo de montagem antes da retirada; sem sinal, a loja banca o risco de o cliente sumir e a lente já foi cortada no grau dele (não revende).
- **Como funciona:** ao aceitar o pedido (gate humano em `em_montagem`), o tenant define sinal (ex.: 50%). O sistema gera cobrança via gateway e envia o link pelo WhatsApp; o pagamento confirmado marca `sinal_pago` e libera/registra a montagem. Saldo restante é cobrado na retirada (feature #2 exibe o pendente). A IA envia o link e confirma recebimento, NUNCA fecha preço (o total já é recalculado pelo backend) nem processa manualmente. Respeita a trava: preço vem do catálogo.
- **Dependências:** gateway de pagamento (pendência #50, transversal). Bloqueada até #50 existir.
- **Métrica de sucesso:** % de pedidos com sinal pago; queda de pedidos abandonados na montagem.

### 5. No-show do exame com política

- **Problema de negócio:** cliente que falta repetidamente entope a agenda e desloca quem compraria. Hoje `falta` é só um status terminal sem consequência.
- **Como funciona:** ao marcar `falta`, o backend contabiliza faltas por contato. Regra configurável no painel: após N faltas, o próximo agendamento pede confirmação obrigatória antecipada ou sinal simbólico (integra com #4). A IA, ao reagendar um cliente com histórico de falta, comunica a política ("para garantir seu horário, confirme com antecedência"); NÃO julga nem bloqueia por conta própria — só aplica a regra do tenant. Combina com o lembrete (#1).
- **Dependências:** scheduler (#1) para o lembrete reforçado; opcional gateway (#4) se exigir sinal.
- **Métrica de sucesso:** redução de faltas reincidentes; ocupação real da agenda.

## Dependências transversais

- **Gateway de pagamento (pendência #50):** destrava sinal/pagamento antecipado do óculos (#4), saldo na retirada (#2), fidelidade/cashback com resgate real (#10), entrega com taxa paga online (#15) e sinal por política de no-show (#5).
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY):** destrava resposta a foto da receita/armação (#16), foto de armação no catálogo, e comprovação visual de garantia/ajuste (#11).
- **Scheduler/cron:** destrava lembrete/confirmação de exame (#1), follow-up de óculos pronto (#2), recompra programada (#3), aniversário (#12) e a régua de no-show (#5). É a dependência de MAIOR alavancagem — habilita sozinha os três quick wins.
- **Campanha em massa segmentada:** destrava disparo em lote de recompra (#3), reativação de inativos (#8) e cupom de aniversário (#12) para audiências filtradas por grau vencendo / sem compra há X meses.
