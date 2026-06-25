>>> SLOT ATRIBUÍDO (ver docs/prompts-gordos/README.md — fonte única de verdade, tem precedência
>>> sobre qualquer "provisório" no corpo): ordem 8 · camada 8.13 · migration 57_projetos.sql ·
>>> tenant igorhaf24 (company/user sufixo -024) · ids de seed sufixo -11x. Reconfirmar no arranque
>>> que a fila não avançou; se avançou, deslocar conforme o README.

[TAREFA — SUB-MARATONA: PERFIL PROJETOS / Projetos (camada 8.13)]

[CONTEXTO]
PROJETO MEADA WHATSAPP em /home/igorhaf/meada/projects/whatsapp.
17 perfis verticais reais hoje no enum (… comida 8.4, floricultura 8.5, pizzaria 8.6 — o último
declarado) + generic. ESTE perfil é o 18º vertical real (19º com generic). Lê CONTEXT.md e o
filesystem no arranque pra cravar convenções, nº de migration, contagem do Surefire e numeração de
tenant ANTES de escrever qualquer código. NÃO hardcodar a contagem do mvn — relatar a REAL do
Surefire ao final. Valores esperados (CONFIRMAR no filesystem antes; podem ter avançado se
Casamento/Pizzaria/outra SM foi executada primeiro e o slot mudou): migration 51_projetos
(PROVISÓRIO — 50_pizzaria.sql já existe no disco e há outros drafts disputando o slot 50/51;
CONFERIR o maior nº presente em supabase/migrations/ e usar o PRÓXIMO livre), tenant igorhaf17
(PROVISÓRIO — o prompt do Casamento também mirava igorhaf17; conferir qual usuário já existe no
Supabase e usar o PRÓXIMO livre, ex.: igorhaf18), company c?0000000-...-018, user a?0000000-...-018.
IDs de namespace compartilhado (contacts/instance/conversation) NO SEED com sufixo NOVO que NÃO
colida com nenhum seed anterior.

Projetos é template de nicho pra empresa de MÓVEIS SOB MEDIDA / MARCENARIA / PAISAGISMO — UM perfil
ÚNICO (NÃO três perfis). Os três tipos de negócio compartilham o MESMO chassi: projeto sob medida →
orçamento → aprovação → execução por etapas. Tenant acessa projetos.meadadigital.local e vê o
produto "Projetos". A IA atende clientes via WhatsApp, identifica pelo telefone, ABRE uma proposta a
partir do briefing (tipo de projeto, ambiente, medidas aproximadas, o que o cliente imagina), a
equipe monta o ORÇAMENTO no painel, e a IA CAPTURA a aprovação/recusa em 2 fases. Tom prestativo-
consultivo de quem projeta sob medida (marceneiro/projetista/paisagista), sem prometer o que a
equipe não cravou.

>>> TRAVA DE COMPORTAMENTO DA IA (cravada — o que a IA NUNCA faz) <<<
- NUNCA fecha contrato, preço ou desconto por conta própria — quem orça e fecha é a equipe no
  painel.
- NUNCA confirma um PRAZO de entrega/montagem/produção nem uma MEDIDA que a equipe não tenha cravado
  — diz "vou confirmar prazo e medidas com a equipe na visita técnica".
- NUNCA inventa material, acabamento, valor, item de orçamento ou serviço que não esteja cadastrado.
- NUNCA promete resultado ("seu móvel vai ficar perfeito"), durabilidade, ou compatibilidade de
  medida/ambiente que dependa de medição presencial — acolhe a ideia sem criar expectativa fora do
  controle da equipe.
- NUNCA gerencia as ETAPAS DE EXECUÇÃO do projeto pela conversa — as etapas (medição/produção/
  entrega/montagem/plantio) são montadas e transicionadas pela equipe no painel. A IA SÓ abre a
  proposta e captura a aprovação.

EVOLUÇÃO ESTRUTURAL: CLONA o chassi do EVENTOS (camada 8.2) / OFICINA (7.9) — proposta order-based
+ itens de orçamento (total materializado) + gate de aprovação em 2 fases via tag que muta o estado
de um artefato existente. Cliente NÃO é entidade (continua o contact; snapshots na proposta). UMA
escapada nova:

  ESCAPADA — SUB-ENTIDADE NOVA: ETAPAS DE EXECUÇÃO DO PROJETO (project_milestones). Depois de
  aprovado, um projeto sob medida tem FASES FÍSICAS DE EXECUÇÃO, cada uma com data prevista,
  ORDEM e TRÊS estados de progresso (pendente/em_andamento/concluída). Ex.: "medição final" →
  "corte/produção" → "entrega" → "montagem/instalação" → "plantio". É uma SEQUÊNCIA DE EXECUÇÃO
  PÓS-APROVAÇÃO que acompanha a obra/produção. DIFERENTE de tudo que veio antes:
    - O CRONOGRAMA do eventos/casamento é o roteiro de UM DIA (ordenado por hora, organizacional).
    - O CHECKLIST do casamento é PRÉ-evento BINÁRIO (pendente/concluída, ordenado por prazo).
    - As ETAPAS de projetos são PÓS-APROVAÇÃO, com TRÊS estados de progresso + ORDEM explícita +
      data prevista — é o acompanhamento da PRODUÇÃO/OBRA, não um checklist binário nem um roteiro
      de um dia.
  Cada projeto também tem um project_type (moveis|marcenaria|paisagismo) HARDCODED com parity — o
  MESMO perfil serve os três; o tipo é um CAMPO da proposta, NÃO um perfil separado. As etapas são
  gerenciadas SÓ no painel (SEM tag de IA, igual o cronograma do eventos/casamento).

NÃO TEM nesta SM (registrado pra não inventar): conflito de agenda/data (não há agenda — o projeto
não disputa slot; datas das etapas são previsões livres), catálogo de materiais/acabamentos pré-
cadastrados (orçamento ad-hoc, a equipe digita os itens), contrato com assinatura digital/PDF/e-sign
(o "contrato" é o estado 'fechada'), pagamento/sinal/parcelas (Stripe é #50, fase futura),
fornecedores externos como pool com agenda própria, anexo de planta/projeto/render/medida em arquivo
(bloqueador SERVICE_ROLE_KEY — foto/anexo off), lembrete automático de data prevista de etapa (a
data é informativa; scheduler é fase futura), múltiplos projetistas com agenda/conflito por
profissional (catálogo SIMPLES, atribuição opcional — igual event_planners). Fases futuras.

DECISÕES CRAVADAS (revisor decidiu pelo Igor):
1. UM perfil ÚNICO 'projetos' serve móveis sob medida + marcenaria + paisagismo. O tipo é
   project_type (CAMPO da proposta), NÃO três perfis. project_type é HARDCODED com parity test
   Java↔TS (moveis|marcenaria|paisagismo).
2. Proposta = artefato central, espelho do event_proposals (order-based, total materializado,
   snapshots de cliente, gate de aprovação em 2 fases). MANTER.
3. ETAPAS DE EXECUÇÃO (project_milestones) = a escapada da SM. Sub-entidade NOVA, distinta dos itens
   de ORÇAMENTO (que entram no total). Cada etapa tem: title, due_date (NULLABLE — previsão), status
   TRÊS estados (pendente/em_andamento/concluída) e position (ORDEM explícita inteira). Ordenada por
   position asc. Gerenciada SÓ no painel — SEM tag de IA.
4. Status da ETAPA é TRÊS estados (pendente/em_andamento/concluída) — NÃO é binário como o checklist
   do casamento. NÃO tem parity test próprio: é um CHECK simples na coluna + validação app-level no
   service (transição LIVRE entre os 3 — o projetista pode voltar de em_andamento pra pendente). O
   que TEM parity é o project_type e o status da PROPOSTA.
5. Funil da PROPOSTA IDÊNTICO ao EventProposalStatus: rascunho→orcada→aprovada→fechada→realizada +
   recusada/cancelada. A trava de itens a partir de 'fechada' (itemsLocked) congela os itens de
   ORÇAMENTO E as ETAPAS de execução.
6. Tags: <proposta_projeto> (ABRE a proposta em rascunho, UM modo só — resolve o contato da conversa;
   total 0, sem itens, sem etapas; carrega project_type+briefing) e <aprovacao_projeto> (MUTA o
   estado, decisao aprovada|recusada, só se a proposta está 'orcada'). Namespaces DISTINTOS de
   <proposta_evento>/<aprovacao_proposta>, de <proposta_casamento>/<aprovacao_casamento> e de TODAS
   as outras tags.
7. SEM conflito de data transacional: as datas (proposta e etapas) são campos livres. project_type
   nasce na abertura (default 'moveis' se a tag não trouxer; a equipe ajusta no painel).

[FUNDAÇÃO — migration 51_projetos (CONFIRMAR o próximo nº livre no disco)]
- ALTER companies CHECK aceitar 'projetos' (estende a constraint atual incluindo TODOS os ids já
  presentes — copiar a lista EXATA da última migration de perfil no disco e ADICIONAR 'projetos' ao
  fim; 18º perfil real).
- RLS enable+force, policies via app.company_id(), grants authenticated + service_role (INSERT de
  propostas/itens/etapas pelo BACKEND via service_role; tenant SELECT/UPDATE — igual às migrations
  30-50). Espelhar 45_eventos.sql inteiro (estrutura, comentários, índices, policies).
- total_cents (na proposta) e line_total_cents (no item de orçamento) MATERIALIZADOS no INSERT/UPDATE;
  NÃO colunas geradas (lição end_at/total das SMs anteriores).
- Tabelas:
  * project_planners — projetistas/responsáveis (catálogo SIMPLES, sem agenda/conflito; espelho
    event_planners). (id, company_id, name CHECK 1..200, specialty texto livre (marcenaria/móveis
    planejados/paisagismo…), active default true, notes, timestamps). Atribuir à proposta é opcional.
    delete em uso → 409 planner_in_use; preferir desativar.
  * project_config — config 1:1 com company; SEM horário/slot (não há agenda). (company_id PK,
    business_name nullable (nome da empresa/ateliê), notes, timestamps). Ausente → defaults vazios.
    Espelho event_config.
  * project_proposals — propostas de projeto (order-based, total materializado, snapshots).
    (id, company_id, contact_id refs contacts on delete set null, planner_id refs project_planners
    on delete set null, conversation_id nullable, customer_name NOT NULL snapshot, customer_phone
    snapshot, project_type text NOT NULL default 'moveis' CHECK in ('moveis','marcenaria',
    'paisagismo'), environment text (ambiente: cozinha/quarto/jardim/sala… texto livre), briefing
    text (o que o cliente imagina + medidas aproximadas), estimated_date date (previsão de entrega —
    CAMPO LIVRE, sem conflito de agenda), total_cents int NOT NULL default 0 (MATERIALIZADO), status
    text CHECK ('rascunho'|'orcada'|'aprovada'|'fechada'|'realizada'|'recusada'|'cancelada') default
    'rascunho', notes, opened_at, closed_at, status_updated_at, timestamps). Espelho event_proposals
    (event_type→project_type com CHECK; event_date→estimated_date; + environment).
  * project_proposal_items — itens de ORÇAMENTO (entram no total; line_total materializado).
    (id, company_id, proposal_id refs project_proposals on delete cascade, description CHECK 1..200,
    quantity int default 1 CHECK >0, unit_price_cents int CHECK >=0, line_total_cents int CHECK >=0
    (= quantity*unit_price, materializado), timestamps). Espelho event_proposal_items.
  * project_milestones — A ENTIDADE NOVA: etapas de execução (ordenadas por position, NÃO entram no
    total). (id, company_id, proposal_id refs project_proposals on delete cascade, title CHECK
    1..200, description, due_date date NULLABLE (previsão da etapa), status text NOT NULL default
    'pendente' CHECK in ('pendente','em_andamento','concluida'), position int NOT NULL default 0
    (ORDEM explícita), started_at timestamptz nullable (preenchido quando entra em em_andamento),
    completed_at timestamptz nullable (preenchido quando concluida), timestamps). Índice
    (proposal_id, position). Leitura ordenada por position asc, created_at asc. RLS/grants como os
    sub-itens do eventos (SELECT/UPDATE pelo tenant; INSERT pelo backend/service_role). COMMENT
    cravando que é a sub-entidade nova: SEQUÊNCIA DE EXECUÇÃO PÓS-APROVAÇÃO com 3 estados + ordem +
    data prevista; NÃO entra no total; gerenciada no painel sem tag de IA; ≠ cronograma de um dia
    (eventos/casamento) e ≠ checklist binário (casamento).
- Status da PROPOSTA hardcoded (ProjectProposalStatus enum Java + const TS + parity test):
  rascunho → orcada, cancelada ; orcada → aprovada, recusada, cancelada ; aprovada → fechada,
  cancelada ; fechada → realizada, cancelada ; realizada/recusada/cancelada → terminal.
  itemsLocked()=true em fechada/realizada/recusada/cancelada (trava itens de orçamento E etapas).
  Espelho EventProposalStatus.
- project_type hardcoded (ProjectType enum Java + const TS + ProjectTypeParityTest):
  moveis|marcenaria|paisagismo (id + label "Móveis sob medida"/"Marcenaria"/"Paisagismo"). NÃO
  confundir com a enum de plataforma ProfileType — é uma enum LOCAL do perfil projetos (sugestão de
  nome: ProjectType em ...profiles/projetos/, e const project-type.ts em frontend/profiles/projetos/
  ou lib/profiles/, decisão do agente — só não colidir com ProfileType).
- O status da ETAPA (pendente/em_andamento/concluida) NÃO tem enum/parity — é CHECK na coluna +
  constante/validação no service e no front. (Só o status da PROPOSTA e o project_type têm parity.)
- TODAS as 5 tabelas novas entram na migration ANTES de tocar o banco (banco se aplica A PARTIR do
  arquivo versionado — lição os_config da SM-J) e na lista de TRUNCATE/SCRIPTS do
  AbstractIntegrationTest.

[BACKEND]
- Planners: CRUD padrão (espelho EventPlannerService/Controller/Repository). delete em uso (proposta
  com planner_id) → 409 planner_in_use; preferir desativar (active=false).
- Config: GET (fallback default) + PUT. (espelho EventConfig*; sem horário)
- Proposals (chassi event_proposals + os 2 editores: ORÇAMENTO + ETAPAS):
  * open (a partir da tag/IA ou manual): nasce 'rascunho', total 0, sem sub-itens. project_type
    obrigatório (default 'moveis' se ausente, validado contra ProjectType). Snapshots de cliente
    (customer_name/phone do contact). conversation_id quando veio da conversa.
  * itens de ORÇAMENTO: add/update/delete sob requireMutableProposal (trava itemsLocked); cada
    mutação RECALCULA e MATERIALIZA total_cents.
  * etapas de execução (NOVO): add/update/delete + reorder + transição de estado, TODOS sob a MESMA
    trava requireMutableProposal; SEM recálculo de total.
      - add: cria com status default 'pendente', position = (max(position)+1) da proposta.
      - update: title/description/due_date (parse de data inválida → 400 invalid_date).
      - reorder: recebe a ORDEM nova (lista de ids ou {id,position}) e re-materializa position
        sequencial 0..N na MESMA transação (decisão do agente sobre o formato exato do payload —
        recomendo lista ordenada de ids).
      - transição de estado: pendente⇄em_andamento⇄concluida LIVRE (sem máquina rígida); ao entrar
        em em_andamento seta started_at (se ainda null); ao entrar em concluida seta completed_at
        (now()); ao SAIR de concluida zera completed_at; ao sair de em_andamento/concluida pra
        pendente zera started_at/completed_at conforme o estado destino (decisão do agente sobre a
        regra exata de limpeza dos timestamps — documentar a escolha). status inválido → 400
        invalid_milestone_status.
      - Exceptions: milestone_not_found (404), proposal_locked (409), invalid_date (400),
        invalid_milestone_status (400).
      - leitura ordenada por position asc, created_at asc.
  * updateStatus (da PROPOSTA): valida transição (inválida → 409 invalid_status_transition); ir pra
    'orcada' exige total_cents > 0 (→ 400 empty_budget); terminal preenche closed_at; dispara
    notificação outbound conforme o status.
  * O detalhe da proposta hidrata as DUAS listas (items de orçamento + milestones de execução).
- Notifier (espelho EventProposalNotifier): best-effort, persiste OUTBOUND/HUMAN, texto defensivo
  SEM promessa de resultado/prazo cravado pela IA. Notifica: orcada (com total + tipo de projeto),
  aprovada, fechada, recusada. rascunho/realizada/cancelada silenciosos.
- IA:
  * Persona prestativa-consultiva (projetista sob medida) com a TRAVA DE COMPORTAMENTO acima
    embutida no prompt. Adicionar a persona PROJETOS em ProfilePromptContext (espelho do bloco
    EVENTOS) + branch em segmentFor pra 'projetos'.
  * Contexto injetado (cache TTL 20s, espelho EventosContextCache, keyed por (companyId,
    contactId)): projetistas ativos + propostas do contato em ABERTO (rascunho/orcada, com id+
    project_type+environment+estimated_date+status+total) + instruções e as 2 tags. NÃO injeta as
    ETAPAS de execução (organizacionais do painel). Invalidação em toda mutação.
  * Tag <proposta_projeto>{"project_type":"moveis|marcenaria|paisagismo","environment":"texto|null",
    "estimated_date":"YYYY-MM-DD|null","briefing":"texto","planner_id":"UUID|null","notes":"texto"}
    → PropostaProjetoConfirmHandler (espelho PropostaEventoConfirmHandler; cria proposta em rascunho;
    project_type inválido/ausente → default 'moveis'; planner_id inválido → ignora planner mas abre;
    best-effort).
  * Tag <aprovacao_projeto>{"proposal_id":"UUID","decisao":"aprovada|recusada"} →
    AprovacaoProjetoHandler (espelho AprovacaoPropostaHandler; só aplica se a proposta está 'orcada';
    proposta NÃO-orcada → empty).
  * JwtFilter autentica /api/projetos/**. OutboundService ganha maybeProcessPropostaProjeto +
    maybeProcessAprovacaoProjeto (best-effort, contactId via findContactIdByConversation, encadeados
    APÓS os outros perfis — perfil é único, só um age; REMOVE a tag antes de enviar ao cliente).
- Guard: ProjetosProfileGuard (403 forbidden_wrong_profile) — espelho EventosProfileGuard.

[FRONTEND]
- /dashboard/projetos-planners (CRUD projetistas; desativar preferido a excluir),
  /dashboard/projetos-proposals (lista por status com badge de project_type; detalhe com DOIS
  editores inline: ORÇAMENTO com total recalculado + ETAPAS DE EXECUÇÃO ordenadas por position com
  os 3 estados — seletor pendente/em_andamento/concluída por etapa, botões ↑↓ ou drag pra reordenar,
  form de título+data prevista; ambos respeitam a trava por status — somem/recusam quando a proposta
  está fechada+),
  /dashboard/projetos-settings (nome da empresa + notas; sem horário).
- types + SDKs (planners, config, proposals com os endpoints de orçamento + etapas: add/update/
  delete/reorder/transição) espelhando eventos. ProjectMilestone: { id, proposalId, title,
  description, dueDate: string|null, status: 'pendente'|'em_andamento'|'concluida', position: number,
  startedAt: string|null, completedAt: string|null, ... }.
- Status TS project-proposal-status.ts (7 ids, ALLOWED_NEXT, ITEMS_LOCKED, statusLabel) +
  ProjectProposalStatusParityTest Java↔TS. project-type.ts (3 ids + labels) + ProjectTypeParityTest
  Java↔TS. (O status da ETAPA NÃO tem parity — é uma constante de 3 strings no front + CHECK no
  banco.)
- getNavForProfile('projetos') injeta "Projetos" (3 itens: Projetistas, Propostas, Configurações).
  ATENÇÃO: floricultura ficou no enum SEM branch em getNavForProfile (fallback) — NÃO repetir esse
  gap; projetos PRECISA do branch próprio. Subdomínio projetos.meadadigital.local.
  Paleta: sugerir 'oliva' / 'eucalipto' / 'aco' (madeira/verde — combina com móveis/marcenaria/
  paisagismo). Conferir em lib/themes/palettes.ts qual já existe; se nenhuma das 3 existir, criar
  uma nova paleta verde-madeira ou reaproveitar uma livre (decisão do agente — só registrar a
  escolha; 'aco' já é usada pela oficina, então preferir 'oliva'/'eucalipto' se livres).
- npm build limpo.

[DOCS]
- CLAUDE.md: seção "## Perfil Projetos (camada 8.7)" espelhando as seções de perfil + nota de que é
  o 18º perfil real (19º com generic), CLONA o EVENTOS (proposta order-based + aprovação em 2 fases)
  e inaugura a sub-entidade de ETAPAS DE EXECUÇÃO pós-aprovação (3 estados + ordem + data prevista).
  Documentar EXPLÍCITO: UM perfil serve os três tipos (project_type moveis/marcenaria/paisagismo,
  hardcoded+parity, campo da proposta); a diferença entre etapa (3 estados, ordem, pós-aprovação),
  cronograma de um dia (eventos/casamento) e checklist binário (casamento); etapas gerenciadas só no
  painel sem tag; a trava de comportamento da IA; as 2 tags.
- docs/PERFIL_PROJETOS.md: guia operacional (projetistas, propostas — os 2 editores, estados,
  notificações, trava de edição; o editor de ETAPAS com os 3 estados e a ordem; como a IA atende; o
  bloco "o que a IA NÃO faz"). Espelhar PERFIL_EVENTOS.md.
- NÃO mexer em system-template.txt nem em outros perfis.

[TESTES BACKEND]
Espelhar a suíte do eventos (service + controller integration por entidade):
- ProjectProposalStatusParityTest + ProjectTypeParityTest + ProfileTypeParityTest (projetos no
  enum/const).
- ProjectPlannerServiceTest + ControllerIntegrationTest (CRUD, delete-em-uso 409 planner_in_use).
- ProjectConfigServiceTest/ControllerIntegrationTest (GET fallback + PUT).
- ProjectProposalServiceTest (open nasce rascunho/total 0/project_type validado; add item recalcula
  total; transição válida/inválida; orcada sem item → empty_budget; trava itemsLocked em fechada+;
  ETAPAS: add cria pendente com position incremental; transição pendente→em_andamento (started_at
  set) →concluida (completed_at set) →pendente (timestamps zerados); reorder re-materializa position
  sequencial; ordena por position; trava proposal_locked quando a proposta está fechada+) +
  ControllerIntegrationTest (os endpoints de orçamento E de etapas: add/update/delete/reorder/
  transição; 409 proposal_locked; 404 milestone_not_found; 400 invalid_milestone_status;
  wrongProfile 403).
- PropostaProjetoConfirmHandlerTest (abre proposta em rascunho; project_type ausente → 'moveis';
  sem tag → empty; planner_id inválido → ignora planner mas abre).
- AprovacaoProjetoHandlerTest (proposta orcada + decisao aprovada → aprovada; recusada → recusada;
  proposta NÃO-orcada → empty; sem tag → empty).
mvn final = relatar contagem REAL do Surefire (não estimar).

[CONSTRAINTS DUROS]
- Migration única (próximo nº livre — provavelmente 51; CONFERIR). Sem foto/anexo (bloqueador
  SERVICE_ROLE_KEY).
- UM perfil 'projetos' serve móveis sob medida + marcenaria + paisagismo. project_type é CAMPO da
  proposta (hardcoded + parity), NÃO três perfis.
- Cliente NÃO é entidade do core — continua o contact; snapshots customer_name/phone na proposta.
- DOIS sub-itens distintos no mesmo artefato: itens de ORÇAMENTO (entram no total) + ETAPAS de
  execução (NÃO entram no total). A etapa tem 3 estados (pendente/em_andamento/concluida — CHECK,
  sem parity), due_date nullable, position (ordem), gerenciada só no painel (sem tag), trava junto
  com itemsLocked().
- total_cents/line_total_cents materializados (não generated). estimated_date / due_date campos
  livres (sem conflito de agenda).
- Funil idêntico ao EventProposalStatus; orcada exige total>0 (empty_budget); trava de itens (E
  etapas) a partir de fechada.
- IA: NUNCA fecha contrato/preço/desconto, NUNCA confirma prazo/medida não cravado pela equipe,
  NUNCA inventa material/item/valor, NUNCA promete resultado, NUNCA gerencia etapas pela conversa.
- Tags <proposta_projeto> e <aprovacao_projeto> distintas de TODAS as outras (em especial das de
  casamento/eventos).
- NÃO mexer em outros perfis nem em system-template.txt. Webhook OFF.
- Cache TTL 20s + invalidação em toda mutação. NÃO injetar as ETAPAS no contexto da IA.
- 529 → inline. Gate 3× → pausar. Working tree sujo → pausar. git add EXPLÍCITO (nunca git add .);
  .env/CONTEXT.md/secrets NUNCA staged.
- SEED com timestamptz: usar `at time zone 'America/Sao_Paulo'` (lição do fuso).
- IDs de namespace compartilhado no seed com sufixo NOVO (conferir os usados; evitar colisão FK).
- Tabela nova entra na migration ANTES de tocar o banco (lição os_config). Adicionar as 5 tabelas ao
  TRUNCATE/SCRIPTS do AbstractIntegrationTest.
- Decisões menores: agente decide (layout exato, ícones do nav, nome de constante, payload do
  reorder, regra exata de limpeza de timestamps na transição de etapa, paleta final).

[PASSO FINAL — TENANT + SEED + COMMIT + PUSH + SMOKE + RELATÓRIO]
F.1 — TENANT igorhaf17 (Projetos Modelo, profile=projetos) — PROVISÓRIO: se igorhaf17 já existe (ex.:
      tomado pelo Casamento), usar o PRÓXIMO livre (igorhaf18). Padrão GoTrue, senha em comunicação
      direta. company c?0000000-...-018 / user a?0000000-...-018 (ajustar sufixo ao tenant real).
      Caddy + /etc/hosts pra projetos.meadadigital.local.
F.2 — Seed /tmp/seed-projetos.sql (NÃO COMITAR; `at time zone 'America/Sao_Paulo'`; ids sufixo
      novo; lição os_config: as 5 tabelas já existem na migration versionada antes do seed):
  - config: business_name "Ateliê Projetos Modelo".
  - 2 projetistas: "Rafael Costa" (specialty "móveis planejados / marcenaria"), "Lívia Andrade"
    (specialty "paisagismo / jardins").
  - contact "Helena Martins" +5511944443333 (VINCULADO: instance+conversation, pra smoke de
    notificação) + contact "Bruno Carvalho" +5511955554444 (sem vínculo).
  - 3 propostas cobrindo estados E os project_type:
    * VINCULADA, status 'orcada' (Helena / Rafael / project_type 'moveis' / environment "cozinha
      planejada" / estimated_date +60d) COM 3 itens de orçamento (módulos, ferragens, instalação →
      total>0) e 4 ETAPAS de execução com position 0..3 e estados variados ("medição final"
      concluida, "corte/produção" em_andamento, "entrega" pendente, "montagem" pendente — pra smoke
      de ordenação e transição).
    * 'rascunho' (Bruno / Lívia / project_type 'paisagismo' / environment "jardim dos fundos" /
      estimated_date +120d) sem itens (pra smoke de empty_budget na transição pra orcada).
    * 'aprovada' (Helena / Rafael / project_type 'marcenaria') com itens E etapas (pra smoke de
      transição aprovada→fechada e trava de edição dos itens E das etapas).
F.3 — JwtFilter /api/projetos/** (se ainda não).
F.4-F.6 — git add EXPLÍCITO dos arquivos da SM + sanity (sem .env/secrets/CONTEXT) + commit.
      Mensagem padrão (feat(camada-8): perfil projetos/Projetos (camada 8.7) com FUNDAÇÃO/BACKEND/
      FRONTEND/DECISÕES/VALIDAÇÃO contagem REAL/NÃO TOCADO/FECHAMENTO + Co-Authored-By: Claude
      Opus 4.8). Tag fase-8.7-fechada (ou o nº real confirmado no arranque).
F.7 — git push origin main + tags.
F.8 — docker compose restart backend + aguardar /admin/me → 401.
F.9 — Smoke E2E:
  BLOCO A: auth — igorhaf17/18 → /admin/me → role=tenant_admin, profileId=projetos,
    productName=Projetos.
  BLOCO B: catalog + guard — GET planners (2), CRUD smoke, delete em uso 409 planner_in_use; GET
    config + PUT; tenant de OUTRO perfil (floricultura/pizzaria) → /api/projetos/planners → 403
    forbidden_wrong_profile.
  BLOCO C: proposta + orçamento — GET proposals (3) com project_type correto; abrir a 'rascunho' do
    Bruno; POST item de orçamento → total recalcula; PATCH rascunho→orcada SEM item (na proposta
    vazia) → 400 empty_budget; com item → 200.
  BLOCO D: ETAPAS DE EXECUÇÃO (a escapada desta SM) [CHAVE] —
    - POST 3 etapas → GET retorna ordenado por position (0,1,2)
    - PATCH reorder (inverter ordem) → GET reflete a nova position sequencial
    - PATCH transição pendente→em_andamento (started_at preenchido) → em_andamento→concluida
      (completed_at preenchido) → concluida→pendente (started_at/completed_at zerados conforme a
      regra cravada)
    - PATCH status inválido (ex.: 'xpto') → 400 invalid_milestone_status
    - DELETE etapa → 204
    - tudo isso na proposta 'orcada'/'rascunho' (mutável); na proposta 'fechada' → 409
      proposal_locked
  BLOCO E: aprovação em 2 fases + notificação —
    - <aprovacao_projeto>{Helena orcada, aprovada} via handler/teste → status vira aprovada
    - aprovacao em proposta NÃO-orcada → empty
    - PATCH status orcada→aprovada (Helena vinculada) → 200 + msg outbound (com total/tipo de
      projeto); asserção casa o conteúdo EXATO (lição do fuso/substring)
    - transição inválida → 409 invalid_status_transition
    - trava: numa proposta 'fechada', PATCH item de orçamento → 409 proposal_locked E PATCH/POST de
      etapa → 409 proposal_locked (os DOIS sub-itens travam juntos)
  BLOCO F: project_type — GET de uma proposta de cada tipo (moveis/marcenaria/paisagismo) confirma
    o campo; POST de proposta manual com project_type inválido → rejeitado/normalizado conforme a
    regra cravada.
  BLOCO G: regressão — os perfis anteriores intactos (smoke leve 1 endpoint cada);
    projetos → /api/eventos/* → 403; projetos → /api/casamento/* → 403 (se casamento existir);
    projetos → /api/floricultura/* → 403.
  BLOCO H: paridade — mvn test -Dtest=ProjectProposalStatusParityTest,ProjectTypeParityTest,
    ProfileTypeParityTest → verde.
  Cleanup smoke + restaurar seed pristine. mvn final: contagem REAL.
F.10 — RELATÓRIO consolidado + DESTAQUE EXPLÍCITO:
  - "18º perfil vertical — camada 8.7 (UM perfil serve móveis sob medida / marcenaria / paisagismo)"
  - "CLONA o EVENTOS (proposta order-based + aprovação em 2 fases) e inaugura a sub-entidade de
     ETAPAS DE EXECUÇÃO pós-aprovação"
  - "DOIS tipos de sub-item: orçamento (entra no total) / ETAPAS de execução (3 estados pendente/
     em_andamento/concluida + ordem + data prevista, NÃO entra no total)"
  - "project_type moveis/marcenaria/paisagismo hardcoded com parity — campo da proposta, NÃO três
     perfis"
  - "BLOCO D prova as ETAPAS: ordenação por position, reorder, transição com started_at/completed_at,
     trava proposal_locked"
  - "BLOCO E prova o gate de aprovação em 2 fases + notificação + trava dos DOIS sub-itens em fechada"
  - "Etapas gerenciadas só no painel (sem tag de IA); IA não fecha contrato, não confirma prazo/
     medida, não inventa material/valor, não promete resultado"
  - "Seed usou at time zone + sufixo de ids novo (sem fuso/colisão)"
  - "as 5 tabelas criadas DENTRO da migration (lição os_config)"
  - PENDÊNCIAS: catálogo de materiais/acabamentos pré-cadastrados, contrato e-sign, pagamento/sinal
     (Stripe), anexo de planta/render/medida, lembrete automático de data de etapa, multi-projetista
     com agenda + a dívida acumulada (webhook, cliente real, olho humano sobre os verticais).

[REPORTAR]
Igual SMs anteriores. Incluir EXPLICITAMENTE:
- "ProfileType.PROJETOS adicionado (18º perfil real, camada 8.7)"
- "UM perfil 'projetos' serve móveis sob medida / marcenaria / paisagismo (project_type, não três
   perfis)"
- "Paridade ProjectProposalStatus, ProjectType e ProfileType validadas"
- "Tenant igorhaf17/18 criado seguindo padrão GoTrue + Caddy/etc/hosts"
- "DOIS sub-itens no mesmo artefato (orçamento/etapas de execução); as ETAPAS são a escapada"
- "Etapa = 3 estados (pendente/em_andamento/concluida, CHECK sem parity) + position (ordem) +
   due_date nullable, só no painel, trava junto com itemsLocked"
- "Gate de aprovação em 2 fases: <proposta_projeto> abre, <aprovacao_projeto> muta (só orcada)"
- "OutboundService ganhou maybeProcessPropostaProjeto + maybeProcessAprovacaoProjeto"
- "JwtFilter autentica /api/projetos/**"
- "getNavForProfile('projetos') com branch próprio (não repetir o gap do floricultura)"
- "Cliente NÃO é entidade do core — continua o contact; snapshots na proposta"
- "as 5 tabelas criadas DENTRO da migration (lição os_config)"
- "Seed: at time zone America/Sao_Paulo + sufixo de ids novo (sem bug de fuso, sem colisão FK)"
- "Próximas fases: catálogo de materiais/contrato e-sign/pagamento-sinal/anexo de planta/lembrete de
   etapa/multi-projetista com agenda + fila de prioridade (webhook, cliente real, olho humano sobre
   os verticais)"
