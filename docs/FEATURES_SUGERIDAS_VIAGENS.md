# Features Sugeridas — Agência de viagens

> Backlog de features avançadas para o nicho **Agência de viagens** (profile_id `viagens`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

## O que o nicho já tem (baseline)

- **Proposta/cotação order-based** (`travel_proposals` + `travel_proposal_items` por categoria aéreo/hospedagem/traslado/passeio/outro) com total materializado e funil `rascunho → orcada → aprovada → fechada → realizada` (+ recusada/cancelada).
- **Aprovação em 2 fases pela IA** via tags `<proposta_viagem>` (abre rascunho a partir do briefing) e `<aprovacao_viagem>` (captura aprovada/recusa quando já orçada). A IA NUNCA emite passagem, confirma preço/voo/hotel nem fecha contrato.
- **Roteiro/itinerário multi-dia** (`travel_itinerary_days`, uma linha por dia, descritivo, ordenado por data — gerenciado só no painel).
- **Consultores** (catálogo simples, sem agenda/conflito) e **Configurações** (nome da agência + notas).
- **Notificações WhatsApp** de mudança de status (orcada/aprovada/fechada/recusada, texto defensivo).
- **Base de conhecimento (RAG)** por tenant, como em todo perfil.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Sinal/entrada para travar a venda (link de pagamento na proposta aprovada).** Hoje a proposta "aprovada" é só um estado — nada garante que o cliente não some pra pensar e nunca fechar. Cobrar um sinal (ex.: 20% ou valor fixo) no momento do aceite é o que transforma interesse em receita real: reduz drasticamente a desistência entre "aprovada" e "fechada", que é onde a agência mais perde dinheiro. A IA envia o link, o pagamento confirmado move a proposta pra 'fechada' automaticamente. É o maior ROI possível porque ataca diretamente a conversão da venda que já está quase fechada. Depende do gateway (#50).

**2. Lembretes automáticos da viagem + pós-venda/NPS (scheduler).** Uma agência vive de recompra e indicação, e ambas nascem de o cliente lembrar que a agência existe. Um scheduler que dispara "faltam 7 dias — checklist de bagagem/documentos", "boa viagem hoje" e, no retorno, "como foi? nos avalie/indique" cria pontos de contato de altíssimo valor percebido com custo quase zero. O pós-viagem é o melhor momento comercial que existe (cliente feliz, recém-chegado) e hoje é totalmente desperdiçado.

**3. Catálogo de pacotes/destinos pré-montados (com upsell na cotação).** A cotação é 100% ad-hoc hoje — cada proposta é montada do zero, o que é lento e não permite vender "prateleira". Um catálogo de pacotes/destinos com preço-base permite a IA APRESENTAR opções ("temos Buenos Aires 5 noites a partir de X"), acelerar a montagem da proposta e, principalmente, fazer upsell/cross-sell estruturado (seguro, passeio extra, upgrade de hotel). Vende mais por proposta e reduz o tempo do consultor por venda — dobra a alavanca de receita e produtividade ao mesmo tempo.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Sinal/entrada com link de pagamento na proposta aprovada | Alto | M | Trava a venda: reduz sumiço entre aprovada e fechada; entra dinheiro no aceite | Receita |
| 2 | Scheduler de lembretes de viagem + pós-venda/NPS | Alto | M | Recompra e indicação; presença no momento certo (pré e pós-viagem) | Retenção |
| 3 | Catálogo de pacotes/destinos pré-montados | Alto | G | Vende prateleira, acelera cotação, base para upsell estruturado | Receita |
| 4 | Upsell/cross-sell proativo pela IA (seguro, passeio, upgrade) | Alto | M | Aumenta ticket médio por proposta sem esforço do consultor | Receita |
| 5 | Reativação de cliente inativo (campanha segmentada) | Alto | M | Recupera quem viajou e não voltou; enche baixa temporada | Marketing |
| 6 | Parcelamento e política de pagamento na proposta | Alto | M | Fecha vendas de ticket alto que travam à vista | Receita |
| 7 | Seguro-viagem como item destacado + venda casada | Alto | P | Margem alta, quase todo cliente aceita; hoje nem é oferecido | Receita |
| 8 | Follow-up automático de proposta orçada não respondida | Alto | P | Resgata cotação parada; a IA cutuca com gentileza em D+2/D+5 | Retenção |
| 9 | Indicação com recompensa (traga um amigo) | Médio | M | CAC baixíssimo; viajante satisfeito é o melhor vendedor | Marketing |
| 10 | Avaliação/review pós-viagem + prova social no site | Médio | M | Confiança fecha venda; alimenta CMS/marketing | Marketing |
| 11 | Cupom/desconto sazonal (early bird, feirão) | Médio | P | Cria urgência, antecipa venda, enche datas fracas | Receita |
| 12 | Programa de pontos/cashback por viagem fechada | Médio | G | Fideliza; incentiva próxima viagem com a mesma agência | Retenção |
| 13 | Anexo de voucher/roteiro em PDF pela IA | Alto | M | Entrega o "produto" no WhatsApp; profissionaliza o pós-fechamento | Operação |
| 14 | Página pública/CMS da agência com pacotes em destaque | Médio | M | Captação de lead orgânico; vitrine dos pacotes do catálogo | Marketing |
| 15 | Dashboard de conversão do funil + relatórios de venda | Médio | M | Enxerga onde perde venda (orcada→aprovada→fechada) e por consultor | Operação |
| 16 | Qualificação automática de lead pela IA (orçamento/perfil) | Médio | P | Consultor prioriza quem tem fit; menos tempo em curioso | IA |

## Detalhamento das prioritárias

### 1. Sinal/entrada com link de pagamento na proposta aprovada

- **Problema de negócio:** o gargalo de receita não é conseguir o "sim" — é converter o "sim" em dinheiro. Entre 'aprovada' e 'fechada' o cliente esfria, compara preço, some. Sem um compromisso financeiro, a agência trabalhou de graça na cotação.
- **Como funciona:** ao mover pra 'aprovada' (ou logo após a `<aprovacao_viagem>` com decisao=aprovada), o backend gera um pedido de sinal (percentual configurável na tela de Configurações ou valor fixo por proposta). A IA envia o link de pagamento pelo WhatsApp — **sem fechar preço nem negociar** (respeita a trava: a IA só transmite o valor já cravado pela equipe). Webhook do gateway confirma o pagamento → o backend registra `deposit_paid` na proposta e move automaticamente pra 'fechada' (ou marca sinal pago dentro de aprovada, conforme a regra da agência). Painel: coluna "sinal" na lista de propostas + selo "sinal pago".
- **Dependências:** gateway de pagamento (**pendência #50**, global). Sem #50, entrega-se só a estrutura de valor-de-sinal e um registro manual "sinal recebido" pelo painel como ponte.
- **Métrica de sucesso:** taxa de conversão aprovada→fechada; % de propostas aprovadas com sinal pago em até 48h.

### 2. Scheduler de lembretes de viagem + pós-venda/NPS

- **Problema de negócio:** a agência só fala com o cliente quando ele procura. Perde os dois momentos de ouro: o pré-viagem (valor percebido, chance de upsell de passeio de última hora) e o pós-viagem (recompra e indicação com o cliente eufórico).
- **Como funciona:** um cron diário varre `travel_proposals` fechadas com `start_date`/`end_date` e dispara mensagens WhatsApp por gatilho: D-7 ("faltam 7 dias — confira documentos/bagagem"), D-1/D0 ("boa viagem!"), D+2 após o `end_date` ("como foi? conte pra gente / nos avalie"). Tudo texto defensivo, sem prometer nada. Integra com a feature 10 (review) e 9 (indicação) no gatilho de retorno. Painel: tela de "Automações" com liga/desliga por gatilho e log de disparos (espelho do padrão de outros perfis).
- **Dependências:** scheduler/cron (transversal — ainda não existe no nicho). Reusa `ViagensNotifier`/Evolution já presentes.
- **Métrica de sucesso:** taxa de resposta ao NPS; nº de indicações e recompras originadas do gatilho de retorno.

### 3. Catálogo de pacotes/destinos pré-montados

- **Problema de negócio:** cotação 100% do zero é lenta e não escala — o consultor é o gargalo. E sem "prateleira" a agência não consegue divulgar ofertas nem fazer upsell padronizado.
- **Como funciona:** nova entidade `travel_packages` (destino, nº noites, roteiro-modelo, preço-base, categoria, ativo) gerenciada em nova tela "Pacotes". A IA passa a APRESENTAR pacotes na conversa ("temos Bariloche 6 noites a partir de X — quer que eu monte pra suas datas?") e, ao abrir a `<proposta_viagem>`, pode referenciar um `package_id` que pré-preenche itens e itinerário — a equipe ajusta e orça no painel. **A IA nunca confirma preço final nem disponibilidade** (mantém a trava: o preço-base é ponto de partida, não orçamento fechado). Alimenta a feature 14 (site) e a 4 (upsell).
- **Dependências:** nenhuma bloqueante; foto do pacote depende de **upload liberado (SERVICE_ROLE_KEY)** — nasce com imagem por URL colada, igual ao CMS.
- **Métrica de sucesso:** % de propostas originadas de pacote; tempo médio de montagem de proposta; ticket médio.

### 4. Upsell/cross-sell proativo pela IA (seguro, passeio, upgrade)

- **Problema de negócio:** cada proposta sai "pelada" — só o essencial que o cliente pediu. Seguro-viagem, passeios extras e upgrade de hotel são margem alta que simplesmente não é ofertada.
- **Como funciona:** o `ViagensContextCache` injeta uma lista de "adicionais sugeríveis" (cadastrados pela agência, com preço-base — ex.: seguro, transfer premium, city tour). A IA, ao montar o briefing, OFERECE esses itens de forma consultiva ("recomenda incluir seguro-viagem? é exigido em vários destinos"). Se o cliente topa, entram como itens da proposta (categoria existente `passeio`/`outro` ou nova `seguro`), a equipe confirma o preço no painel. A IA sugere, **não fecha valor** (trava preservada).
- **Dependências:** idealmente após a feature 3 (catálogo) e 7 (seguro). Sem #50 funciona (é montagem de proposta, não cobrança).
- **Métrica de sucesso:** nº médio de itens por proposta; % de propostas com pelo menos 1 upsell; ticket médio.

### 5. Reativação de cliente inativo (campanha segmentada)

- **Problema de negócio:** quem viajou há 12+ meses e não voltou é receita adormecida. Sem campanha, a agência espera passivamente — e o cliente vai pro concorrente que lembrou dele primeiro.
- **Como funciona:** segmentação por contatos com última proposta fechada há X meses (ou por destino/estilo de viagem do histórico) + envio em massa WhatsApp de oferta dirigida ("faz 1 ano da sua viagem a Lisboa — que tal Porto agora?"). Tela de "Campanhas": criar público (filtros), escolher template, disparar, ver métricas de abertura/resposta. Reusa a base de contatos e o `ViagensNotifier`.
- **Dependências:** infra de campanha em massa (transversal — a ser criada) + scheduler para agendamento. Respeitar opt-out/limites do WhatsApp.
- **Métrica de sucesso:** taxa de resposta da campanha; nº de propostas reabertas por inativo reativado; receita atribuída.

## Dependências transversais

- **Gateway de pagamento (pendência #50, global):** destrava #1 (sinal), #6 (parcelamento), #7 (venda casada de seguro com cobrança), #11 (cupom com valor cobrado), #12 (cashback resgatável). Enquanto não existir, todas essas podem entregar a parte estrutural (registro de valor, política, marcação manual de "pago") como ponte, sem a cobrança automática.
- **Upload de foto/anexo (bloqueado hoje — SERVICE_ROLE_KEY ausente):** destrava a imagem em #3 (pacotes) e #14 (site), e o **anexo de voucher/roteiro em PDF** (#13). Ponte: tudo por URL colada (padrão já usado no CMS) até o upload ser liberado.
- **Scheduler/cron (não existe no nicho):** destrava #2 (lembretes/pós-venda), #5 e #8 (follow-up/reativação agendados), #11 (early bird com janela). É a infra que mais multiplica valor porque converte features passivas em proativas.
- **Campanha em massa segmentada (a criar):** destrava #5 (reativação), #9 (indicação em escala), #11 (divulgação de cupom sazonal). Compartilhável com os demais perfis do monolito (não pode quebrar outro nicho — feature de plataforma, gateada por perfil).
