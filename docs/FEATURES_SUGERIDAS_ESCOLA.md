# Features Sugeridas — Escola/educação infantil

> Backlog de features avançadas para o nicho **Escola/educação infantil** (profile_id `escola`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Turmas** (série/ano + turno + capacity/vagas + mensalidade + ano letivo) com controle transacional de vaga (`class_full`).
- **Alunos como sub-entidade do responsável** (1 responsável → N filhos) e **matrícula = assinatura** (ativa ⇄ suspensa → cancelada), com anti-dupla por (aluno, turma).
- **Mensalidade MANUAL** (`escola_payments`, 1 registro por mês, UNIQUE por referência) — sem cobrança automática, sem inadimplência.
- **Visita agendada** da família (dia + período, leve, sem slot fino) como entidade própria com status.
- **IA acolhedora** que identifica o responsável, mostra turmas com vaga, agenda visita e registra interesse de matrícula — com travas fortes: nunca promete vaga, nunca inventa/negocia mensalidade/desconto/bolsa, nunca dá parecer pedagógico.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lista de espera automática por turma (waitlist).** Hoje, turma cheia = a IA diz "aviso quando abrir" e o lead evapora sem rastro. Com a lista de espera persistida, cada interesse em turma lotada vira uma posição numerada; quando uma matrícula é cancelada e libera vaga, a secretaria vê "3 famílias esperando" e a IA pode disparar "abriu uma vaga na turma X, quer garantir?". É retenção de demanda pura: a escola já pagou (marketing/indicação) pra atrair aquela família — perdê-la por falta de vaga momentânea é jogar dinheiro fora. Esforço P (uma tabela + status na captura da tag) e não viola a trava (continua "pré-reservar pra secretaria confirmar").

**2. Lembrete e confirmação automática de visita (reduz no-show).** Visita agendada é o topo do funil de matrícula — família que visita converte muito mais. Mas visita sem lembrete tem no-show alto (pais esquecem, imprevisto com criança). Um lembrete WhatsApp automático D-1 e no dia, com botão "confirmo / preciso remarcar", enche a agenda de visitas efetivas e libera horário de quem furaria. É o clássico redutor de perda operacional: mais visitas realizadas = mais matrículas fechadas, sem custo de mídia adicional.

**3. Cobrança de mensalidade com link de pagamento (Pix/cartão).** A mensalidade recorrente é a receita-âncora da escola, e hoje ela é 100% manual (a secretaria digita cada pagamento). Ligar o gateway (#50) pra emitir Pix/boleto e conciliar automaticamente elimina trabalho de secretaria, acelera o caixa e — o que mais importa — reduz inadimplência com lembrete de vencimento + link de pagamento no próprio WhatsApp. É a feature que mais move receita; depende do gateway global, mas o valor justifica priorizar o desbloqueio.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lista de espera por turma (waitlist) com aviso de vaga | Alto | P | Não perde a família quando a turma está cheia; reengaja quando abre vaga | Retenção |
| 2 | Lembrete + confirmação automática de visita (D-1 e no dia) | Alto | P | Reduz no-show de visita, enche a agenda de visitas efetivas → mais matrículas | Operação |
| 3 | Cobrança de mensalidade com link Pix/cartão + conciliação | Alto | G | Recebe recorrente automático, reduz inadimplência, tira trabalho da secretaria | Receita |
| 4 | Régua de inadimplência (lembrete de vencimento + atrasado) | Alto | M | Cobra sozinho quem atrasou, recupera mensalidade sem constrangimento presencial | Receita |
| 5 | Taxa de matrícula / rematrícula anual com cobrança | Alto | M | Fatura o pico de janeiro (taxa + material), não só a mensalidade mensal | Receita |
| 6 | Campanha de rematrícula (fim de ano) segmentada por turma | Alto | M | Retém a base pro próximo ano letivo antes que migrem pra concorrência | Marketing |
| 7 | Indicação com desconto (aluno indica aluno) | Alto | M | Cresce por boca-a-boca de pai, o canal mais barato e confiável de escola | Marketing |
| 8 | Reativação de lead frio (visitou/interessou e sumiu) | Alto | M | Recupera famílias que quase matricularam; converte funil parado | Retenção |
| 9 | Página pública da escola (CMS) por tenant | Médio | M | Vitrine com turmas/valores/depoimentos que a IA referencia; capta lead | Marketing |
| 10 | Scheduler de auto-transição de visita/matrícula | Médio | P | Visita passada vira "realizada" sozinha; relatórios ficam corretos | Operação |
| 11 | Dashboard de gestão (ocupação por turma, receita prevista, funil) | Médio | M | A diretoria enxerga vagas, MRR e conversão sem planilha | Operação |
| 12 | Multi-unidade (rede de escolas/franquia) | Médio | G | Rede vê e opera todas as unidades num login só | Operação |
| 13 | Cross-sell de serviços extras (integral, transporte, material) | Médio | M | Aumenta ticket por aluno com add-ons já embutidos na conversa | Receita |
| 14 | NPS / pesquisa de satisfação pós-visita e trimestral | Médio | P | Mede risco de evasão cedo, gera depoimento pra marketing | Retenção |
| 15 | Aniversário do aluno (parabéns automático + mimo) | Médio | P | Encanta a família, reduz evasão com toque humano barato | Retenção |
| 16 | Qualificação de lead pela IA (idade da criança → turma sugerida) | Médio | M | A IA já triAGE e encaminha o interesse certo; secretaria fecha mais rápido | IA |

## Detalhamento das prioritárias

### 1. Lista de espera por turma (waitlist)

- **Problema de negócio:** turma cheia hoje é um beco sem saída — a IA responde "aviso quando abrir" e não há registro nenhum. A escola perde o contato de uma família quente e não tem como reengajar quando uma matrícula é cancelada (o que acontece o tempo todo em educação infantil). Demanda reprimida vira churn de funil.
- **Como funciona:** nova tabela `escola_waitlist` (company_id, class_id, student_id/new_student snapshot, contact_id, position, status `aguardando/convertida/desistiu/expirada`, created_at). Quando a IA emite `<matricula_escola>` numa turma sem vaga (`class_full`), em vez de abortar, o handler enfileira na waitlist e a IA responde dentro da trava ("registrei seu interesse na lista de espera da turma X, posição N — a secretaria avisa assim que abrir vaga"). Posição DERIVADA por query (espelho da fila do barbearia — sem coluna que precise reordenar). Ao cancelar uma matrícula (libera vaga), o painel destaca a fila e um botão dispara notificação Evolution pro 1º da lista ("abriu vaga, quer garantir?"). Tela nova em `/dashboard/escola-waitlist`.
- **Dependências:** nenhuma bloqueante — usa Evolution (já existe) e o chassi de status/parity. Casa perfeitamente com o cancelamento de matrícula que já existe.
- **Métrica de sucesso:** % de vagas liberadas preenchidas por alguém da lista de espera; nº de matrículas originadas de waitlist / mês.

### 2. Lembrete + confirmação automática de visita

- **Problema de negócio:** a visita é o momento de maior conversão do funil (família que pisa na escola matricula muito mais), mas visita marcada com dias de antecedência tem no-show alto — pais esquecem ou têm imprevisto com a criança. Cada horário de visita furado é um slot da coordenação desperdiçado e um lead que esfria.
- **Como funciona:** scheduler/cron diário varre `escola_visits` com status `agendada`; dispara via Evolution um lembrete D-1 e outro na manhã do dia, com CTA de confirmação ("confirmo" / "preciso remarcar"). Resposta do responsável cai no webhook inbound já existente; a IA (dentro da trava — não negocia nada) apenas confirma ou reabre o agendamento de nova data. Painel mostra "confirmada / pendente" na lista de visitas. Integra com a feature #10 (auto-transição) pra marcar `realizada`/`falta`.
- **Dependências:** scheduler/cron (transversal — ainda não existe no projeto). Sem foto, sem gateway.
- **Métrica de sucesso:** taxa de comparecimento de visitas (realizadas / agendadas) antes vs. depois; nº de remarcações capturadas em vez de no-show.

### 3. Cobrança de mensalidade com link Pix/cartão + conciliação

- **Problema de negócio:** a mensalidade é a receita-âncora e recorrente da escola, mas o registro é 100% manual — a secretaria lança cada pagamento à mão, o caixa demora, e não há cobrança proativa. Inadimplência silenciosa corrói a margem.
- **Como funciona:** ao gerar a mensalidade do mês (ou em lote pra turma inteira), o backend emite uma cobrança Pix/cartão via gateway (#50) e envia o link pelo WhatsApp ("mensalidade de março da Maria — R$ X, pague aqui"). O webhook do gateway concilia automaticamente e grava em `escola_payments` (respeitando o UNIQUE por mês já existente), atualizando o status sem digitação. A IA NÃO define valor nem negocia — só entrega o link do valor já cadastrado (trava preservada). Tela de mensalidades ganha "emitir cobrança" e status pago/pendente/atrasado.
- **Dependências:** gateway de pagamento (#50, pendência global). Sem isso, entra a versão manual + lembrete (feature #4) como ponte.
- **Métrica de sucesso:** % da mensalidade recebida via link automático; redução do prazo médio de recebimento; queda da inadimplência.

### 4. Régua de inadimplência (lembrete de vencimento + atraso)

- **Problema de negócio:** hoje não há cálculo de inadimplência nem cobrança de atrasados — mensalidade vencida só aparece se a secretaria for olhar. Escola pequena costuma "esquecer" de cobrar e acumula perda que raramente recupera.
- **Como funciona:** scheduler diário compara os meses decorridos desde o `start_date` da matrícula com os `escola_payments` registrados (o cálculo de "meses em aberto" já é a base do summary atual). Dispara régua via Evolution: aviso amigável 3 dias antes do vencimento, lembrete no dia, e cobrança gentil no atraso — sempre com o valor JÁ cadastrado (a IA/mensagem nunca inventa multa/juros; se a escola cobra multa, é campo administrativo do tenant, não decisão da IA). Painel `/dashboard/escola-enrollments` ganha selo "em atraso" por matrícula.
- **Dependências:** scheduler/cron (transversal). Potencializa com #3 (link de pagamento no mesmo lembrete).
- **Métrica de sucesso:** taxa de recuperação de mensalidades atrasadas; dias médios de atraso antes vs. depois.

### 5. Taxa de matrícula / rematrícula anual com cobrança

- **Problema de negócio:** o pico financeiro da escola é a virada do ano letivo (taxa de matrícula + material + rematrícula), e hoje o produto só modela a mensalidade recorrente — todo esse faturamento sazonal fica de fora do sistema.
- **Como funciona:** um tipo de cobrança `taxa` associado à matrícula (avulsa, não mensal), emitida no ato da matrícula/rematrícula, com valor cadastrado pelo tenant. A IA pode informar o valor da taxa já cadastrada quando o responsável registra interesse (sem negociar). Com gateway (#50), vira link Pix; sem, é registro manual (espelho de `escola_payments`). Relatório separa receita recorrente (mensalidade) de receita de matrícula (taxa).
- **Dependências:** gateway (#50) pra cobrança automática; funciona manual sem ele.
- **Métrica de sucesso:** receita de taxa de matrícula capturada no sistema por safra letiva; ticket médio da matrícula (mensalidade + taxa).

## Dependências transversais

- **Gateway de pagamento (#50, pendência global):** destrava #3 (cobrança de mensalidade), #4 na versão com link, #5 (taxa de matrícula) e #13 (add-ons pagos) e #7 (crédito de indicação como desconto real). É o maior multiplicador de receita — desbloqueá-lo libera metade do backlog de RECEITA de uma vez.
- **Scheduler / cron (ainda não existe no projeto):** destrava #2 (lembrete de visita), #4 (régua de inadimplência), #6 (campanha de rematrícula temporizada), #8 (reativação de lead frio), #10 (auto-transição de visita/matrícula), #14 (NPS trimestral) e #15 (aniversário). É o segundo maior multiplicador — praticamente todo o eixo de RETENÇÃO/OPERAÇÃO automatizada depende dele.
- **Campanha em massa segmentada (infra de disparo):** destrava #6 (rematrícula), #7 (indicação), #8 (reativação) e #14 (NPS) — envio para audiência filtrada (por turma, por status, por ano letivo). Reaproveitável entre nichos.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** NÃO é dependência de nada no top do backlog (deliberado — foto de criança é sensível/LGPD e fica para fase futura com cripto at-rest). Só destravaria itens de baixa prioridade (foto no cadastro do aluno, material didático em PDF), que ficam de fora por decisão de risco.
- **CMS/feature flags (camada 9.x, já existe a infra):** #9 (página pública da escola) só precisa ligar a flag `cms` pro nicho escola e montar os blocos específicos — a fundação já está pronta.
