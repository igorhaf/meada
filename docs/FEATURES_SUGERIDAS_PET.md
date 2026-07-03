# Features Sugeridas — Pet shop/veterinária

> Backlog de features avançadas para o nicho **Pet shop/veterinária** (profile_id `pet`), priorizado por VALOR DE NEGÓCIO. Objetivo: engordar a cartela de features com o que mais agrega receita/retenção no menor tempo. Baseado no estado REAL do nicho (o que já existe NÃO é repetido aqui).

> **Modo de execução (cravado):** ao implementar qualquer feature deste backlog, faça **tudo em prosa**,
> de forma contínua e autônoma, **sem perguntar nada ao programador**, sem pausas para confirmação e
> **sem usar o widget de perguntas** (AskUserQuestion). Não interrompa o fluxo pedindo aval intermediário:
> decida com base no estado real do código/banco e nas convenções das skills, implemente, e só pare em
> ponto de bifurcação arquitetural genuína ou no gate de teste. Reporte o progresso em prosa corrida.

## O que o nicho já tem (baseline)

- **Agenda por profissional** com conflito por `professional_id` (paralelismo entre profissionais) e janela de horário configurável.
- **Serviços** (banho, tosa, consulta…) com categoria, duração, preço opcional e **restrição de espécie** (só cães/gatos/outros), reforçada no backend (`species_mismatch`).
- **Animais como sub-entidade do tutor** (contato do WhatsApp): um tutor tem N animais; a IA cadastra o pet novo no mesmo turno do agendamento (tag `<agendamento_pet>` com modo `new_animal`).
- **IA que agenda** (persona carinhosa) identificando o tutor pelo telefone, oferecendo os animais já cadastrados e os slots livres — **sem diagnóstico, sem prescrição, sem recomendação de tratamento** (trava clínica cravada).
- **Status de agendamento** com notificação automática de confirmação e cancelamento ao tutor.
- **Snapshots** no agendamento (tutor/animal/profissional/serviço/preço) — editar o catálogo depois não altera o histórico.

## 🏆 Top 3 quick wins (fazer primeiro)

**1. Lembrete e confirmação automática de agendamento (D-1 / D-0).** O nicho HOJE não tem scheduler de lembrete — e no-show em banho/tosa/consulta é a maior sangria de receita e de agenda do pet shop. Um cron que dispara "amanhã o Thor tem banho às 14h, confirma?" pelo WhatsApp e captura "sim/não" reduz falta e reaproveita o horário liberado. É esforço P (reusa o notifier da Evolution + os status existentes) e ataca direto o eixo de perda. Vende porque o dono SENTE o furo da agenda toda semana.

**2. Carteira de vacinas + vermífugo com lembrete de retorno.** É o motivo #1 de o tutor voltar ao pet shop — e a IA hoje não sabe quando a próxima dose vence. Cadastrar as datas (V8/V10, antirrábica, vermífugo) e disparar "a vacina do Thor vence em 15 dias, quer agendar?" transforma um evento pontual em recorrência previsível. Puxa RETENÇÃO e RECEITA sem violar a trava: é lembrete administrativo de retorno, não conduta clínica. Esforço M (tabela nova + scheduler).

**3. Pacote/assinatura de banho recorrente (leva 4, paga 3 / plano mensal).** O banho é o serviço de maior frequência e menor ticket; empacotá-lo (crédito de N banhos ou assinatura mensal) trava o tutor no seu pet shop em vez do concorrente da esquina e antecipa caixa. O chassi de saldo já existe no projeto (estética/academia) — dá pra clonar. Esforço M; RECEITA recorrente é o maior multiplicador de LTV do nicho.

## Backlog priorizado (16 features)

| # | Feature | Valor de negócio | Esforço | O que resolve pro cliente | Eixo |
|---|---------|------------------|---------|---------------------------|------|
| 1 | Lembrete + confirmação automática (D-1/D-0) via WhatsApp | Alto | P | Corta no-show de banho/tosa/consulta e reaproveita horário liberado | Operação |
| 2 | Carteira de vacinas/vermífugo com lembrete de retorno | Alto | M | Traz o tutor de volta na hora certa; vira receita recorrente previsível | Retenção |
| 3 | Pacote/assinatura de banho recorrente (crédito ou mensal) | Alto | M | Trava o tutor, antecipa caixa, aumenta frequência do serviço-âncora | Receita |
| 4 | Sinal/pré-pagamento do agendamento (Pix/cartão) | Alto | M | Reduz no-show com skin-in-the-game e adianta receita | Receita |
| 5 | Reativação de tutor inativo (pet sem visita há X meses) | Alto | M | Recupera cliente perdido com campanha segmentada ("saudade do Thor?") | Retenção |
| 6 | Cross-sell/upsell proativo da IA (tosa+hidratação, taxi-dog) | Alto | M | Aumenta ticket médio oferecendo add-on compatível na conversa | Receita |
| 7 | Fidelidade por carimbo/pontos (a cada N banhos, 1 grátis) | Alto | M | Estimula recompra e diferencia do concorrente | Retenção |
| 8 | Scheduler de auto-transição + no-show com política | Médio | P | Marca "falta" sozinho e aplica regra (perde sinal / bloqueia) | Operação |
| 9 | Lembrete de aniversário do pet (mimo/desconto) | Médio | P | Toque emocional que gera visita e encanta o tutor | Marketing |
| 10 | NPS/avaliação pós-atendimento pelo WhatsApp | Médio | P | Mede satisfação, coleta review, identifica cliente em risco | Marketing |
| 11 | Campanha em massa segmentada (por espécie/serviço/inativo) | Alto | M | Enche agenda ociosa e promove serviço parado por segmento | Marketing |
| 12 | Indicação com recompensa (traga um amigo, ganhe banho) | Médio | M | Aquisição barata via boca-a-boca dos tutores fiéis | Marketing |
| 13 | Relatório/dashboard operacional (receita, no-show, top serviço) | Médio | M | Dá visão de negócio ao dono pra decidir preço e agenda | Operação |
| 14 | Cupom de desconto aplicável no agendamento | Médio | P | Ferramenta de promoção pontual e reativação | Receita |
| 15 | Estoque leve de produtos (ração/antipulgas) + aviso de reposição | Médio | M | Abre canal de venda de produto e evita ruptura | Receita |
| 16 | Cardápio de fotos do serviço (antes/depois da tosa) na página pública | Médio | M | Prova social visual que converte novo tutor (depende de foto liberada) | Marketing |

## Detalhamento das prioritárias

### 1. Lembrete + confirmação automática (D-1/D-0)

- **Problema de negócio:** o pet shop perde receita e horário toda vez que um tutor esquece do banho/consulta. Sem lembrete, a falta só é descoberta na hora — e o slot já não dá pra revender.
- **Como funciona:** um cron (scheduler novo, o nicho não tem) varre `pet_appointments` com status `confirmado`/`agendado` para o dia seguinte e o dia corrente, e dispara via `PetAppointmentNotifier`/Evolution uma mensagem carinhosa com nome do pet, serviço, profissional e horário. A resposta do tutor ("confirmo"/"não vou poder") é interpretada pela IA e transiciona o status (`confirmado` ou `cancelado`) reusando a máquina de estados existente — liberando o slot na hora. Painel: toggle de lembrete em `pet-settings` (ligar D-1 e/ou D-0, editar texto).
- **Dependências:** scheduler/cron transversal (hoje inexistente no nicho). Nada de foto nem gateway.
- **Métrica de sucesso:** taxa de no-show antes vs depois; % de slots cancelados que foram reagendados no mesmo dia.

### 2. Carteira de vacinas/vermífugo com lembrete de retorno

- **Problema de negócio:** vacinação e vermifugação são recorrentes por natureza, mas hoje dependem da memória do tutor. O pet shop perde a dose de reforço (e a visita) porque ninguém avisa.
- **Como funciona:** tabela nova `pet_vaccinations` (sub-entidade do animal: tipo, data aplicada, data de retorno, profissional — tudo ADMINISTRATIVO, sem parecer clínico, respeitando a trava LGPD do nicho de que `notes` não é prontuário). O cron dispara "a antirrábica do Thor vence em 15 dias, quer agendar o reforço?" e a IA já emite a tag `<agendamento_pet>`. Painel: aba "Carteira" na tela do animal, com histórico e próximo vencimento. **A IA NÃO indica qual vacina nem interpreta esquema vacinal** — só lembra a data de retorno que o tenant registrou.
- **Dependências:** scheduler/cron. Sem gateway/foto.
- **Métrica de sucesso:** % de retornos de vacina agendados via lembrete; nº de visitas recorrentes por tutor/ano.

### 3. Pacote/assinatura de banho recorrente

- **Problema de negócio:** o banho é alta frequência e baixo ticket; sem fidelização, o tutor troca de pet shop por conveniência ou preço. Empacotar antecipa caixa e trava a recompra.
- **Como funciona:** clonar o chassi de saldo pré-pago (espelho de estética/academia) — `pet_packages` com N banhos de um serviço, `sessions_remaining` materializado e decrementado transacionalmente no agendamento (UPDATE condicional `remaining > 0` fecha a corrida). Assinatura mensal = variante com renovação. A IA agenda consumindo o saldo quando o tutor tem pacote ativo; **não vende preço inventado** — o total vem do catálogo. Cancelar um agendamento que consumiu devolve a sessão. Painel: tela "Pacotes" com saldo por tutor.
- **Dependências:** para ATIVAR o pacote com pagamento real, depende do gateway #50; nesta primeira versão o pacote nasce `pendente` e o tenant ativa manualmente após receber (igual estética).
- **Métrica de sucesso:** % de tutores com pacote ativo; frequência de banho por tutor; receita antecipada/mês.

### 4. Sinal/pré-pagamento do agendamento

- **Problema de negócio:** consulta e tosa longas ocupam profissional caro; a falta sem custo é prejuízo. Um sinal muda o comportamento do tutor.
- **Como funciona:** ao confirmar o agendamento de serviços marcados como "exige sinal", a IA envia um link de pagamento; o agendamento só passa a `confirmado` com o sinal pago (webhook do gateway). No-show → sinal retido (política configurável). Painel: flag "exige sinal" e valor por serviço; visão de sinais pagos/retidos.
- **Dependências:** gateway de pagamento #50 (bloqueador global). Enquanto não existir, entra como "registro manual de sinal recebido" (o tenant marca pago) para já capturar o comportamento.
- **Métrica de sucesso:** no-show em serviços com sinal vs sem; receita retida de faltas.

### 5. Reativação de tutor inativo

- **Problema de negócio:** tutor que não aparece há meses é receita evaporando silenciosamente; recuperá-lo custa uma fração de conquistar um novo.
- **Como funciona:** query/segmento de tutores cujo animal não tem agendamento `realizado` há X meses (configurável). Campanha em massa segmentada dispara "saudade do Thor por aqui! que tal um banho com 20% off essa semana?" com cupom (feature #14). A IA responde e agenda. Painel: tela de segmento com contagem + botão de campanha.
- **Dependências:** infra de campanha em massa (transversal, feature #11) + cupom (#14). Sem foto/gateway.
- **Métrica de sucesso:** taxa de reativação (inativos que voltaram a agendar); receita recuperada por campanha.

## Dependências transversais

- **Gateway de pagamento #50 (global):** destrava sinal/pré-pagamento (#4), ativação automática de pacote/assinatura (#3), e cobrança de cupom com valor. Enquanto ausente, essas features rodam em modo "registro manual" (o tenant marca recebido) para já capturar comportamento e agenda.
- **Upload de foto/anexo (bloqueado por SERVICE_ROLE_KEY ausente):** destrava o antes/depois da tosa na página pública (#16) e a foto do pet no cadastro. Sem isso, tudo por URL colada ou adiado.
- **Scheduler/cron (inexistente no nicho hoje):** é a peça-mãe — destrava lembrete/confirmação (#1), lembrete de vacina (#2), auto-transição de no-show (#8), aniversário do pet (#9) e NPS pós-atendimento (#10). Deve ser a primeira infra construída, pois 5 features de alto valor dependem dela.
- **Campanha em massa segmentada (#11):** destrava reativação de inativo (#5), aniversário (#9), indicação (#12) e qualquer promoção por segmento (espécie/serviço). É a alavanca de marketing que multiplica o valor das demais.
- **CMS/página pública por tenant (já existe como feature flag na plataforma):** destrava a vitrine de serviços com prova social visual (#16) quando a foto for liberada.
