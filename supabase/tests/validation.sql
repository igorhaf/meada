-- =============================================================================
-- validation.sql — roteiro guiado dos 10 critérios de aceite do schema.
--
-- COMO RODAR: cole no SQL Editor do Supabase (ou psql como postgres) e execute
--   por blocos. Cada critério traz a query, o resultado ESPERADO em comentário,
--   e diz se deve RETORNAR LINHAS, RETORNAR VAZIO ou DAR ERRO.
--
-- COMO SIMULAR UM USUÁRIO LOGADO (o pulo do gato):
--   O SQL Editor roda como superuser (que tem BYPASSRLS) — rodar as queries cru
--   NÃO exercita o RLS. Para testar de verdade, viramos o role para
--   `authenticated` e forjamos o JWT claim que alimenta auth.uid():
--
--     set local role authenticated;
--     set local request.jwt.claims = '{"sub":"<uuid-do-user>","role":"authenticated"}';
--
--   auth.uid() lê o 'sub' desse claim → app.company_id() resolve o tenant.
--   `set local` vale só até o fim da transação (COMMIT/ROLLBACK), por isso cada
--   bloco de teste é uma transação isolada. Para voltar a superuser: `reset role`.
--
-- IMPORTANTE: este script SEMEIA dados de teste. Rode em ambiente de
--   desenvolvimento. O bloco final (LIMPEZA) remove tudo que foi semeado.
-- =============================================================================


-- =============================================================================
-- SEED — duas empresas (A e B), um usuário cada, dados mínimos.
-- Roda como superuser (service_role-equivalente): ignora RLS para popular.
-- Usamos UUIDs fixos para referência fácil nos testes.
-- =============================================================================
begin;

-- Empresas
insert into companies (id, name, slug) values
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Empresa A', 'empresa-a'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Empresa B', 'empresa-b')
on conflict (id) do nothing;

-- auth.users (normalmente criados pelo Supabase Auth; aqui inserimos direto
--   para o teste). Só os campos mínimos para a FK de users funcionar.
insert into auth.users (id, email) values
  ('a0000000-0000-0000-0000-000000000001', 'user-a@test.local'),
  ('b0000000-0000-0000-0000-000000000001', 'user-b@test.local')
on conflict (id) do nothing;

-- users de aplicação, vinculados às empresas
insert into users (id, company_id, email, role) values
  ('a0000000-0000-0000-0000-000000000001',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'user-a@test.local', 'owner'),
  ('b0000000-0000-0000-0000-000000000001',
   'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'user-b@test.local', 'owner')
on conflict (id) do nothing;

-- instâncias WhatsApp (com token — só service_role lê o token)
insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values
  ('a1000000-0000-0000-0000-000000000001',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'inst-a', 'SECRET-TOKEN-A'),
  ('b1000000-0000-0000-0000-000000000001',
   'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'inst-b', 'SECRET-TOKEN-B')
on conflict (id) do nothing;

-- contatos
insert into contacts (id, company_id, phone_number, name) values
  ('a2000000-0000-0000-0000-000000000001',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '+5511999990001', 'Cliente A'),
  ('b2000000-0000-0000-0000-000000000001',
   'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '+5511999990002', 'Cliente B')
on conflict (id) do nothing;

-- conversas
insert into conversations (id, company_id, contact_id, whatsapp_instance_id) values
  ('a3000000-0000-0000-0000-000000000001',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   'a2000000-0000-0000-0000-000000000001',
   'a1000000-0000-0000-0000-000000000001'),
  ('b3000000-0000-0000-0000-000000000001',
   'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   'b2000000-0000-0000-0000-000000000001',
   'b1000000-0000-0000-0000-000000000001')
on conflict (id) do nothing;

-- mensagens
insert into messages (company_id, conversation_id, direction, sender, content, evolution_message_id) values
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   'a3000000-0000-0000-0000-000000000001', 'inbound', 'contact', 'Oi, empresa A', 'EVT-A-1'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   'b3000000-0000-0000-0000-000000000001', 'inbound', 'contact', 'Oi, empresa B', 'EVT-B-1')
on conflict (evolution_message_id) where evolution_message_id is not null do nothing;

commit;


-- =============================================================================
-- CRITÉRIO 1 — Isolamento de LEITURA. Usuário de A vê só linhas de A.
-- ESPERADO: a primeira query retorna 1 linha (a conversa de A). A contagem
--   de conversas visíveis = 1, nunca 2.
-- =============================================================================
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  -- deve retornar SOMENTE a conversa de A
  select company_id, content from messages order by created_at;
  -- ESPERADO: 1 linha, company_id = aaaa..., content = 'Oi, empresa A'

  select count(*) as conversas_visiveis from conversations;
  -- ESPERADO: 1
rollback;


-- =============================================================================
-- CRITÉRIO 2 — Isolamento de ESCRITA. Usuário de A não grava com company_id de B.
-- ESPERADO: o INSERT FALHA — "new row violates row-level security policy".
--   O WITH CHECK rejeita; não é silenciosamente reescrito.
-- =============================================================================
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  -- tentativa de criar contato no tenant de B estando logado como A
  insert into contacts (company_id, phone_number)
  values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '+5511000000000');
  -- ESPERADO: ERRO de violação de RLS (WITH CHECK). Se inserir, critério FALHOU.
rollback;


-- =============================================================================
-- CRITÉRIO 3 — Token blindado por column-grant.
-- ESPERADO:
--   (3a) como authenticated, SELECT do token DÁ ERRO ("permission denied for
--        column evolution_token").
--   (3b) como service_role, retorna o valor.
-- =============================================================================
-- 3a — authenticated não lê o token
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  select evolution_token from whatsapp_instances;
  -- ESPERADO: ERRO "permission denied for column evolution_token"
rollback;

-- 3a-bis — as colunas permitidas funcionam normalmente (controle positivo)
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  select id, instance_name, status from whatsapp_instances;
  -- ESPERADO: 1 linha (inst-a) — leitura de colunas não-sensíveis OK
rollback;

-- 3b — service_role lê o token
begin;
  set local role service_role;
  select instance_name, evolution_token from whatsapp_instances order by instance_name;
  -- ESPERADO: 2 linhas com os tokens (service_role bypassa RLS e tem grant total)
rollback;


-- =============================================================================
-- CRITÉRIO 4 — Bootstrap do RLS: usuário lê a própria linha em users.
-- ESPERADO: retorna a linha do próprio usuário; app.company_id() resolve não-NULL.
-- =============================================================================
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  select id, company_id, role from users;
  -- ESPERADO: 1 linha (o próprio user A) — a policy users_select_self/tenant deixa

  select app.company_id() as meu_tenant;
  -- ESPERADO: aaaaaaaa-... (não-NULL). Se NULL, o bootstrap furou.
rollback;


-- =============================================================================
-- CRITÉRIO 5 — Idempotência do webhook (UNIQUE parcial em evolution_message_id).
-- ESPERADO: o segundo INSERT com o mesmo evolution_message_id FALHA por unique.
--   Rodamos como service_role (é quem insere mensagens de webhook).
-- =============================================================================
begin;
  set local role service_role;
  insert into messages (company_id, conversation_id, direction, sender, content, evolution_message_id)
  values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
          'a3000000-0000-0000-0000-000000000001', 'inbound', 'contact', 'duplicata', 'EVT-A-1');
  -- ESPERADO: ERRO "duplicate key value violates unique constraint
  --           uq_messages_evolution_id" — EVT-A-1 já existe (do seed).
rollback;


-- =============================================================================
-- CRITÉRIO 7 — Cascade de identidade e RESTRICT no tenant.
-- =============================================================================
-- 7a — apagar auth.users CASCATEIA para users de aplicação.
begin;
  set local role service_role;
  -- cria um auth.user descartável + perfil
  insert into auth.users (id, email)
  values ('c0000000-0000-0000-0000-000000000099', 'descartavel@test.local')
  on conflict (id) do nothing;
  insert into users (id, company_id, email)
  values ('c0000000-0000-0000-0000-000000000099',
          'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'descartavel@test.local');

  delete from auth.users where id = 'c0000000-0000-0000-0000-000000000099';
  select count(*) as perfil_orfao from users
   where id = 'c0000000-0000-0000-0000-000000000099';
  -- ESPERADO: 0 — o perfil foi removido junto (ON DELETE CASCADE)
rollback;

-- 7b — apagar empresa com usuários vivos é BARRADO (RESTRICT).
begin;
  set local role service_role;
  delete from companies where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  -- ESPERADO: ERRO "update or delete on table companies violates foreign key
  --           constraint" — users.company_id é ON DELETE RESTRICT.
rollback;


-- =============================================================================
-- CRITÉRIO 8 — FK composta rejeita contaminação cross-tenant (mesmo sob service_role).
-- ESPERADO: o INSERT FALHA por FK violation (não por RLS) — a conversa de A
--   referenciada com company_id de B não existe no par (id, company_id).
-- =============================================================================
begin;
  set local role service_role;  -- service_role IGNORA RLS; só a FK protege aqui
  insert into messages (company_id, conversation_id, direction, sender, content)
  values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',   -- company B
          'a3000000-0000-0000-0000-000000000001',   -- conversa de A
          'inbound', 'contact', 'cross-tenant');
  -- ESPERADO: ERRO "violates foreign key constraint" — o par
  --   (conversation_id=conversaA, company_id=B) não casa com conversations(id,company_id).
rollback;


-- =============================================================================
-- CRITÉRIO 9 — RLS usa ÍNDICE, não seq scan.
-- ESPERADO: o plano mostra "Index Scan"/"Index Only Scan" usando
--   idx_messages_conversation, NÃO "Seq Scan".
-- =============================================================================
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  explain (analyze, buffers)
  select * from messages
   where conversation_id = 'a3000000-0000-0000-0000-000000000001'
   order by created_at
   limit 50;
  -- ESPERADO: Index Scan em idx_messages_conversation. (Em tabela com poucas
  --   linhas o planner pode preferir Seq Scan — para um teste fiel, popular
  --   alguns milhares de mensagens antes. Ver nota no fim.)
rollback;


-- =============================================================================
-- CRITÉRIO 10 — Unique parcial respeita soft delete.
-- ESPERADO:
--   (10a) criar dois serviços ativos com o mesmo nome FALHA.
--   (10b) soft-deletar e recriar com o mesmo nome FUNCIONA.
-- =============================================================================
-- 10a — dois ativos com mesmo nome falham
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into services (company_id, name) values
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Corte');
  insert into services (company_id, name) values
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Corte');
  -- ESPERADO: o 2º INSERT FALHA por uq_services_company_name_active.
rollback;

-- 10b — soft delete + recriar mesmo nome funciona
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into services (id, company_id, name)
  values ('a4000000-0000-0000-0000-000000000001',
          'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Barba');
  -- soft delete
  update services set deleted_at = now()
   where id = 'a4000000-0000-0000-0000-000000000001';
  -- recriar com o mesmo nome: deve passar (a linha antiga está "deletada")
  insert into services (company_id, name)
  values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Barba');
  -- ESPERADO: sem erro — o unique parcial só conta deleted_at IS NULL.
rollback;


-- =============================================================================
-- CRITÉRIO 10-bis — CHECK chk_documents_path_tenant força prefixo do tenant.
-- ESPERADO:
--   (bis-a) inserir documento com storage_path fora do formato '<company_id>/...'
--           FALHA pelo CHECK.
--   (bis-b) inserir com o prefixo correto (company_id real + '/arquivo') PASSA.
-- =============================================================================
-- bis-a — path com uuid errado: rejeitado pelo CHECK
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into documents (company_id, filename, storage_path)
  values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'file.pdf', 'wrong-uuid/file.pdf');
  -- ESPERADO: ERRO "new row for relation \"documents\" violates check
  --           constraint \"chk_documents_path_tenant\"" — o path não começa
  --           com o company_id da linha.
rollback;

-- bis-b — path com o prefixo correto: aceito
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into documents (company_id, filename, storage_path)
  values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'file.pdf',
          'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/file.pdf');
  -- ESPERADO: sucesso — prefixo = company_id da linha, em lowercase.
rollback;


-- =============================================================================
-- CRITÉRIO 6 — Storage: upload sob prefixo de OUTRA empresa é negado.
-- Não dá para testar 100% só em SQL (envolve a API de Storage), mas dá para
--   exercitar a POLICY diretamente em storage.objects:
-- ESPERADO:
--   (6a) como user A, inserir objeto sob 'aaaa.../doc.pdf' PASSA.
--   (6b) como user A, inserir objeto sob 'bbbb.../doc.pdf' FALHA (RLS).
-- =============================================================================
-- 6a — prefixo do próprio tenant: OK
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into storage.objects (bucket_id, name, owner)
  values ('documents',
          'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/doc.pdf',
          'a0000000-0000-0000-0000-000000000001');
  -- ESPERADO: sucesso — 1º segmento do path = company_id de A.
rollback;

-- 6b — prefixo de outro tenant: NEGADO
begin;
  set local role authenticated;
  set local request.jwt.claims = '{"sub":"a0000000-0000-0000-0000-000000000001","role":"authenticated"}';

  insert into storage.objects (bucket_id, name, owner)
  values ('documents',
          'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/doc.pdf',
          'a0000000-0000-0000-0000-000000000001');
  -- ESPERADO: ERRO de violação de RLS — A não grava sob o prefixo de B.
rollback;


-- =============================================================================
-- LIMPEZA — remove tudo que o seed criou. Rode como superuser/service_role.
-- =============================================================================
begin;
  set local role service_role;
  delete from messages           where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from conversations      where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from contacts           where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from services           where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from whatsapp_instances where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from users              where company_id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from companies          where id in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
  delete from auth.users         where id in
    ('a0000000-0000-0000-0000-000000000001','b0000000-0000-0000-0000-000000000001');
  delete from storage.objects    where bucket_id = 'documents'
    and (storage.foldername(name))[1] in
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
commit;

-- NOTA sobre o critério 9: com tabela quase vazia o planner escolhe Seq Scan por
--   ser mais barato — isso NÃO significa índice ausente. Para um teste fiel,
--   antes do EXPLAIN insira alguns milhares de mensagens na conversa de A e rode
--   ANALYZE messages; aí o Index Scan aparece. O critério valida que o índice
--   EXISTE e é ELEGÍVEL, não que é usado em N pequeno.
