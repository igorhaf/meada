# Features Sugeridas — Oficina mecânica

> Backlog de features avançadas para o nicho **Oficina mecânica** (profile_id `oficina`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Ordem de serviço (OS) order-based** com itens (peça / mão de obra), `total_cents` materializado e trava de itens (`order_locked`) a partir de em_execucao.
- **Fluxo de status** `aberta → orcada → aprovada → em_execucao → concluida → entregue` (+ recusada/cancelada), com notificações automáticas ao cliente ao orçar, aprovar, concluir e entregar.
- **Gate de aprovação em 2 fases pela IA:** a IA ABRE a OS a partir da queixa (tag `<ordem_servico>`, 2 modos: veículo existente ou cadastra veículo novo) e, num turno posterior, CAPTURA a aprovação/recusa (tag `<aprovacao_os>`) — sem diagnosticar nem inventar preço.
- **Cadastro de mecânicos** (sem agenda) e de **veículos** (placa única por company, sub-entidade do cliente/contato), com arquivar × excluir (409 se em uso).
- **Config de horário informativo** (a oficina trabalha por OS, não por slot).
- **Trava de comportamento da IA:** nunca diagnostica defeito, nunca monta orçamento/inventa preço de peça, nunca promete prazo fora da OS — sintoma vira avaliação presencial.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Catálogo de peças/serviços com preço pré-cadastrado (auto-orçamento assistido).** Hoje o mecânico digita cada item na mão em toda OS — repetitivo e lento, e a IA fica proibida de orçar porque não há tabela de preços confiável. Um catálogo (`oficina_catalog_items`: nome, tipo peça/mão-de-obra, preço padrão) transforma o lançamento de itens num autocomplete e, sobretudo, **destrava a IA para pré-preencher o orçamento com serviços tabelados** (troca de óleo, alinhamento, revisão) sem violar a trava — porque o preço vem do catálogo do próprio tenant, não é invenção da IA. Acelera o "orçada" (etapa que trava a receita), reduz erro de digitação e é a fundação de pacotes/combos. Esforço P/M, ROI altíssimo.

**2. Lembrete de revisão periódica / retorno programado (reativação por km ou data).** A oficina mecânica vive de recorrência: troca de óleo a cada X km/meses, revisão, alinhamento. Hoje nada traz o cliente de volta — ele some até o carro falhar. Um scheduler que dispara no WhatsApp "Faz 6 meses da sua última troca de óleo no [placa/modelo], hora de agendar a revisão?" é a alavanca de retenção nº 1 do nicho, com CAC zero (reusa o contato). Gera fluxo previsível de OS e é o maior gerador de receita recorrente sem depender de gateway. Esforço M.

**3. Pagamento/sinal online (Pix/cartão) na aprovação da OS.** O gate de aprovação já existe — falta capturar dinheiro nele. Cobrar um sinal (ex.: 30%) no momento em que o cliente aprova reduz drasticamente o "aprovou e sumiu" (peça comprada, box parado), e cobrar o saldo na entrega elimina inadimplência e agiliza a retirada. É o eixo de maior impacto direto em receita/fluxo de caixa. Depende do gateway global (#50), mas é a feature que mais justifica priorizar #50 para este nicho.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Catálogo de peças/serviços com preço tabelado | Alto | P/M | Acelera orçamento, destrava IA pré-preencher itens tabelados | Receita |
| 2 | Lembrete de revisão periódica (km/data) via scheduler | Alto | M | Traz cliente de volta na hora da manutenção; receita recorrente | Retenção |
| 3 | Sinal/pagamento online na aprovação + saldo na entrega | Alto | M | Reduz aprovação-fantasma e inadimplência; caixa antecipado | Receita |
| 4 | Pacotes/combos de manutenção (revisão completa, kit troca) | Alto | P/M | Ticket médio maior; venda de conjunto em vez de item solto | Receita |
| 5 | Follow-up pós-entrega + NPS ("como ficou o carro?") | Alto | P | Retém, gera avaliação, detecta retrabalho antes de virar reclamação | Retenção |
| 6 | Reativação de veículo inativo (não volta há N meses) | Alto | M | Campanha automática para carteira parada; receita "adormecida" | Marketing |
| 7 | Upsell proativo da IA (itens complementares tabelados) | Alto | M | IA sugere "aproveitar e trocar filtro/pastilha" no orçamento | IA |
| 8 | Aprovação item-a-item pelo cliente no WhatsApp | Médio | M | Cliente aprova só parte do orçamento; recupera OS que seria recusada inteira | Receita |
| 9 | Agenda de entrada/box por slot (opcional por tenant) | Médio | M | Organiza o fluxo do dia; evita superlotação do box | Operação |
| 10 | Lembrete de OS pronta/parada aguardando aprovação | Médio | P | Cobra a decisão do cliente; libera box e destrava receita | Operação |
| 11 | Programa de fidelidade / cashback por serviço | Médio | M | Recompensa quem volta; diferencia da oficina da esquina | Retenção |
| 12 | Foto da avaria / laudo visual na OS (quando liberar upload) | Alto | M | Justifica o orçamento, gera confiança, reduz contestação | Operação |
| 13 | Relatórios/dashboard (faturamento, top serviços, ticket, funil OS) | Médio | M | Dono enxerga gargalo (OS parada em orçada), decide melhor | Operação |
| 14 | Indicação com bônus ("indique um amigo, ganhe desconto") | Médio | P/M | Aquisição orgânica barata via base existente | Marketing |
| 15 | Histórico do veículo / carteira de manutenção do cliente | Médio | P/M | Fideliza (a oficina "conhece" o carro); embasa próximo serviço | Retenção |
| 16 | Cupom de desconto (primeira visita, época morta) | Médio | P | Enche agenda em período fraco; capta cliente novo | Marketing |

## Detalhamento das prioritárias

### 1. Catálogo de peças/serviços com preço tabelado

- **Problema de negócio:** o mecânico redigita os mesmos itens em toda OS e a IA fica impedida de orçar (trava: não inventa preço). Resultado: orçamento lento e a etapa `aberta → orcada` (que libera a receita) fica represada esperando alguém digitar no painel.
- **Como funciona:** nova tabela `oficina_catalog_items` (company, nome, tipo `peca|mao_de_obra`, preço padrão, ativo). No painel o editor de itens da OS vira autocomplete que puxa preço do catálogo (ainda editável linha a linha). A IA ganha, no `OficinaContextCache`, a lista de serviços tabelados; para serviços de preço fixo do próprio tenant (ex.: troca de óleo R$120), a IA pode PRÉ-PREENCHER o item na abertura da OS via a tag `<ordem_servico>` — **sem violar a trava**, porque o valor vem do catálogo do tenant, não de invenção da IA. Serviço que exige diagnóstico continua sem preço (aberta, mecânico orça).
- **Dependências:** nenhuma externa. Migration nova + campo opcional na tag de abertura. Não quebra outros perfis (tabela e rota `/api/oficina/**` isoladas).
- **Métrica de sucesso:** tempo médio `aberta → orcada` e % de OS orçadas no mesmo dia.

### 2. Lembrete de revisão periódica (km/data)

- **Problema de negócio:** manutenção é recorrente por natureza (óleo, revisão, filtros), mas nada reengaja o cliente — ele volta só quando quebra, muitas vezes em outra oficina.
- **Como funciona:** ao entregar uma OS, registrar o "próximo retorno sugerido" (data e/ou km, derivado do tipo de serviço — ex.: óleo +6 meses / +10.000 km). Um scheduler diário varre veículos cujo retorno venceu e dispara mensagem outbound via Evolution ("Passaram 6 meses da última troca de óleo do seu [modelo/placa]. Quer que eu agende a revisão?"). A resposta entra no fluxo normal da IA (abre nova OS). Config por tenant liga/desliga e define os intervalos padrão.
- **Dependências:** scheduler/cron (transversal). Reusa o vínculo veículo↔contato já existente. Sem gateway.
- **Métrica de sucesso:** taxa de retorno (OS geradas por lembrete / lembretes enviados) e receita atribuída ao canal.

### 3. Sinal/pagamento online na aprovação + saldo na entrega

- **Problema de negócio:** o cliente aprova a OS, o mecânico compra a peça e o cliente some — box parado, prejuízo. E na entrega há atrito/inadimplência para receber.
- **Como funciona:** no gate de aprovação (a OS vira `aprovada`), o backend gera um link de pagamento do sinal (% configurável do total) e a IA o envia no WhatsApp; o pagamento confirmado registra `signal_paid` na OS. Na `entrega`, gera o link do saldo. Estados de pagamento (pendente/sinal_pago/quitado) visíveis no painel. A IA nunca fecha preço/desconto (trava mantida) — só entrega o link do valor já orçado.
- **Dependências:** **gateway #50** (Stripe/Pix) — bloqueador direto. Enquanto não existe, entregar a versão "registro manual de pagamento" no painel (baixa de sinal/saldo) como ponte.
- **Métrica de sucesso:** % de OS aprovadas com sinal pago; queda no tempo `aprovada → em_execucao` e na inadimplência na entrega.

### 4. Pacotes/combos de manutenção

- **Problema de negócio:** o cliente compra "só o que quebrou"; o ticket médio fica baixo e serviços preventivos rentáveis não são vendidos.
- **Como funciona:** com o catálogo (feature 1), definir pacotes (ex.: "Revisão completa 20.000 km" = óleo + filtros + alinhamento + balanceamento, com preço fechado). Na abertura ou orçamento, mecânico/IA aplica o pacote como um bloco de itens (snapshot no `os_items`, total materializado como já é). A IA oferece o pacote quando a queixa casa ("já que vai fazer a revisão, temos o pacote 20 mil km por X"). Preço sempre do catálogo — trava respeitada.
- **Dependências:** feature 1 (catálogo). Sem gateway.
- **Métrica de sucesso:** ticket médio por OS e % de OS com pacote aplicado.

### 5. Follow-up pós-entrega + NPS

- **Problema de negócio:** entregou o carro e o relacionamento morre; retrabalho vira reclamação pública em vez de conserto discreto, e não se captura prova social.
- **Como funciona:** scheduler dispara D+2/D+3 após a `entrega` uma mensagem: "Como o [modelo] se comportou após o serviço? (nota 0–10)". Nota baixa abre alerta interno no painel (chance de retrabalho/recall) e a IA acolhe sem prometer nada técnico; nota alta convida a avaliar (semente da feature de review/marketing). Respostas viram um painel de NPS por período.
- **Dependências:** scheduler/cron. Sem gateway. Trava: a IA não interpreta o defeito relatado no follow-up, só registra e encaminha.
- **Métrica de sucesso:** NPS médio, % de detratores recuperados (viram nova OS/ajuste) e volume de avaliações positivas coletadas.

## Dependências transversais

- **Gateway de pagamento (#50, global):** destrava sinal/saldo na aprovação (feature 3), cobrança de pacotes (4) e cupom/fidelidade com valor real (11, 16). Enquanto não existe, todas essas rodam em modo "registro manual de pagamento" no painel como ponte.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** destrava foto da avaria/laudo visual na OS (feature 12) e reforça a justificativa do orçamento (feeds 3 e 8, aumentando aprovação). Sem ele, a OS segue só texto.
- **Scheduler/cron:** destrava lembrete de revisão (2), reativação de inativo (6), lembrete de OS parada (10) e follow-up/NPS (5). É a dependência que habilita todo o eixo de RETENÇÃO — priorizar junto ao catálogo.
- **Campanha em massa segmentada:** destrava reativação de inativo (6), cupom de época morta (16) e indicação (14) em escala; reusa a base de contatos/veículos por filtro (última visita, tipo de serviço, modelo).
