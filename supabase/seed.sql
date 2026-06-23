-- supabase/seed.sql — roda AUTOMATICAMENTE no fim de cada `supabase db reset` (dev local).
-- Torna o ambiente local reproduzível: usuários logáveis + 1 tenant de exemplo (Comida Modelo).
-- NÃO roda em prod (prod nunca executa `supabase db reset`). UUIDs FIXOS p/ determinismo.
--
-- Usuários (senha: bofo-meca-oleo):
--   igorhaf@gmail.com    → super-admin (allowlist ADMIN_SUPER_ADMIN_EMAILS; SEM linha em public.users)
--   igorhaf16@gmail.com  → tenant-admin da Comida Modelo (perfil comida)
--
-- O shape de auth.users + auth.identities replica o que a Admin API do GoTrue gera
-- (instance_id zero-UUID, tokens '', identity provider 'email' com identity_data) — sem
-- isso o login falha invalid_credentials. pgcrypto (crypt/gen_salt) já vem no Supabase.

-- ========================================================================
-- 1) USUÁRIOS DO AUTH (auth.users + auth.identities) — idempotente
-- ========================================================================
do $$
declare
  super_id uuid := 'a0000000-0000-0000-0000-000000000001';
  tenant_id uuid := 'a0000000-0000-0000-0000-000000000016';
begin
  -- super-admin
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token)
  values (
    '00000000-0000-0000-0000-000000000000', super_id, 'authenticated', 'authenticated',
    'igorhaf@gmail.com', crypt('bofo-meca-oleo', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}', '{"email_verified":true}', now(), now(),
    '', '', '', '')
  on conflict (id) do nothing;

  insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (super_id::text, super_id,
    jsonb_build_object('sub', super_id::text, 'email', 'igorhaf@gmail.com', 'email_verified', true, 'phone_verified', false),
    'email', now(), now(), now())
  on conflict (provider, provider_id) do nothing;

  -- tenant-admin (Comida Modelo)
  insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token)
  values (
    '00000000-0000-0000-0000-000000000000', tenant_id, 'authenticated', 'authenticated',
    'igorhaf16@gmail.com', crypt('bofo-meca-oleo', gen_salt('bf')), now(),
    '{"provider":"email","providers":["email"]}', '{"email_verified":true}', now(), now(),
    '', '', '', '')
  on conflict (id) do nothing;

  insert into auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (tenant_id::text, tenant_id,
    jsonb_build_object('sub', tenant_id::text, 'email', 'igorhaf16@gmail.com', 'email_verified', true, 'phone_verified', false),
    'email', now(), now(), now())
  on conflict (provider, provider_id) do nothing;
end $$;

-- ========================================================================
-- 2) TENANT "Comida Modelo" (perfil comida) + cardápio com opções
-- ========================================================================
insert into public.companies (id, name, slug, profile_id)
values ('c8000000-0000-0000-0000-000000000016', 'Comida Modelo', 'comida-modelo', 'comida')
on conflict (id) do update set name = excluded.name, profile_id = excluded.profile_id;

-- tenant-admin ligado à company (id casa com auth.users acima)
insert into public.users (id, company_id, email, role)
values ('a0000000-0000-0000-0000-000000000016', 'c8000000-0000-0000-0000-000000000016', 'igorhaf16@gmail.com', 'admin')
on conflict (id) do update set company_id = excluded.company_id, role = excluded.role;

insert into public.comida_config (company_id, delivery_fee_cents, min_order_cents)
values ('c8000000-0000-0000-0000-000000000016', 700, 2000)
on conflict (company_id) do update set delivery_fee_cents = excluded.delivery_fee_cents, min_order_cents = excluded.min_order_cents;

insert into public.comida_menu_items (id, company_id, name, description, price_cents, category) values
  ('cf000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', 'X-Burger',        'Pão, hambúrguer 150g, queijo', 2500, 'lanches'),
  ('cf000000-0000-0000-0000-000000000072', 'c8000000-0000-0000-0000-000000000016', 'Pizza Mussarela', 'Mussarela, molho, orégano',    4500, 'pizzas'),
  ('cf000000-0000-0000-0000-000000000073', 'c8000000-0000-0000-0000-000000000016', 'Batata Frita',    'Porção 300g',                  1500, 'porcoes'),
  ('cf000000-0000-0000-0000-000000000074', 'c8000000-0000-0000-0000-000000000016', 'Coca-Cola Lata',  '350ml',                        600,  'bebidas')
on conflict (id) do nothing;

insert into public.comida_menu_item_options
  (id, company_id, menu_item_id, group_label, option_label, price_delta_cents, sort_order) values
  ('c0000000-0000-0000-0000-0000000007a1', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Tamanho',    'Médio',        0,   0),
  ('c0000000-0000-0000-0000-0000000007a2', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Tamanho',    'Grande',       500, 1),
  ('c0000000-0000-0000-0000-0000000007a3', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Adicionais', 'Bacon',        300, 0),
  ('c0000000-0000-0000-0000-0000000007a4', 'c8000000-0000-0000-0000-000000000016', 'cf000000-0000-0000-0000-000000000071', 'Adicionais', 'Queijo extra', 200, 1)
on conflict (id) do nothing;

-- whatsapp instance + contatos + conversa (pra smoke de pedido/notificação)
insert into public.whatsapp_instances (id, company_id, instance_name, evolution_token)
values ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', 'comida-modelo-inst', 'tok-comida')
on conflict (id) do nothing;

insert into public.contacts (id, company_id, phone_number, name) values
  ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016', '+5511933332222', 'João Cliente'),
  ('c0000000-0000-0000-0000-000000000072', 'c8000000-0000-0000-0000-000000000016', '+5511922221111', 'Ana Cliente')
on conflict (id) do nothing;

insert into public.conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by)
values ('c0000000-0000-0000-0000-000000000071', 'c8000000-0000-0000-0000-000000000016',
        'c0000000-0000-0000-0000-000000000071', 'c0000000-0000-0000-0000-000000000071', 'open', 'ai')
on conflict (id) do nothing;

-- ========================================================================
-- 3) UM TENANT DE EXEMPLO POR NICHO (roteamento de domínios)
-- Cada nicho ganha 1 company com slug navegável ({slug}.meadadigital.local).
-- CMS ALTERNADO: with_cms=true → cms_sites publicado + cms_pages home publicada
-- (cai na página pública /p/{slug}); with_cms=false → sem CMS (cai no login do nicho).
-- Slugs NUNCA colidem com subdomínio de nicho (validação slug_reserved_niche).
-- NÃO cria usuário logável por tenant — serve ao smoke de roteamento (público).
-- ========================================================================
do $$
declare
  t record;
  cid uuid;
begin
  for t in
    select * from (values
      ('sushilegal',   'Sushi Legal',        'sushi',      true),
      ('sorrisolegal', 'Sorriso Legal',      'dental',     false),
      ('juridicopro',  'Jurídico Pro',       'legal',      true),
      ('mesafarta',    'Mesa Farta',         'restaurant', false),
      ('belezapura',   'Beleza Pura',        'salon',      true),
      ('recantoaltos', 'Recanto dos Altos',  'pousada',    false),
      ('corpoemforma', 'Corpo em Forma',     'academia',   true),
      ('patanuvem',    'Pata na Nuvem',      'pet',        false),
      ('motorforte',   'Motor Forte',        'oficina',    true),
      ('nutrevida',    'Nutre Vida',         'nutri',      false),
      ('navalhaouro',  'Navalha de Ouro',    'barbearia',  true),
      ('festamax',     'Festa Max',          'eventos',    false),
      ('glowestetica', 'Glow Estética',      'estetica',   true)
    ) as v(slug, name, profile_id, with_cms)
  loop
    -- company idempotente por slug
    insert into public.companies (name, slug, profile_id)
    values (t.name, t.slug, t.profile_id)
    on conflict (slug) do update set name = excluded.name, profile_id = excluded.profile_id
    returning id into cid;
    if cid is null then
      select id into cid from public.companies where slug = t.slug;
    end if;

    if t.with_cms then
      insert into public.cms_sites (company_id, published)
      values (cid, true)
      on conflict (company_id) do update set published = true;

      insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published)
      values (cid, 'home', t.name,
        jsonb_build_array(jsonb_build_object(
          'id', 'hero-1', 'type', 'hero',
          'props', jsonb_build_object('title', t.name, 'subtitle', 'Bem-vindo!'))),
        true, true)
      on conflict do nothing;
    end if;
  end loop;
end $$;

-- ========================================================================
-- 4) COMPANY-ÂNCORA DA PLATAFORMA (migration 44) — o "Meada" institucional.
-- is_platform=true; o super-admin edita o CMS dela DIRETO no painel (/dashboard/cms),
-- sem ser tenant. A raiz meadadigital.local serve este CMS. id/slug fixos e canônicos.
-- (Recriada aqui pra sobreviver a db reset + deleções acidentais.)
-- ========================================================================
insert into public.companies (id, name, slug, profile_id, status, is_platform)
values ('00000000-0000-0000-0000-000000000000', 'Meada', 'meada', 'generic', 'active', true)
on conflict (id) do update set is_platform = true, slug = excluded.slug, name = excluded.name;

-- site com tema meada-dark (identidade da marca: near-black + gradiente azul→roxo→rosa)
insert into public.cms_sites (company_id, published, theme)
values ('00000000-0000-0000-0000-000000000000', true, '{"preset":"meada-dark"}'::jsonb)
on conflict (company_id) do update set published = true, theme = excluded.theme;

-- A LANDING institucional do Meada, montada com os blocos meada_* (navbar/hero/services/
-- portfolio/cta/footer) — conteúdo idêntico ao defaultProps() de lib/cms/cms-block-type.ts
-- (réplica do meada-page). blocks em formato FLAT (normalizeToTree converte na leitura).
-- Editável pelo super-admin em /dashboard/cms.
insert into public.cms_pages (company_id, page_slug, title, blocks, is_home, published)
values ('00000000-0000-0000-0000-000000000000', 'home', 'Meada',
$json$[
  {"id":"navbar-1","type":"meada_navbar","props":{"brandName":"Meada","brandSuffix":"Digital","links":[{"label":"Serviços","href":"/servicos"},{"label":"Produtos","href":"/produtos"},{"label":"Sobre","href":"/sobre"},{"label":"Contato","href":"/contato"}],"ctaLabel":"Pedir orçamento","ctaHref":"/contato"}},
  {"id":"hero-1","type":"meada_hero","props":{"titlePrefix":"Sites e Sistemas","gradientText":"Sob Medida","titleSuffix":"pra Crescer","subtitle":"Desenvolvimento personalizado do site institucional ao sistema completo. Código limpo, prazo claro e foco no que importa pro seu negócio.","primaryLabel":"Comece Agora →","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos","stats":[{"value":"50+","label":"Projetos"},{"value":"20+","label":"Tecnologias"},{"value":"5+","label":"Anos no mercado"}],"showcase":"terminal","terminalTitle":"meada — projeto.sh","terminalLines":[{"kind":"cmd","text":"meada start --tipo=ecommerce"},{"kind":"info","text":"Discovery e arquitetura definidos."},{"kind":"check","text":"Frontend Next.js + Tailwind"},{"kind":"check","text":"Backend escalável + API REST"},{"kind":"check","text":"Banco de dados + migrations"},{"kind":"check","text":"Pagamentos integrados"},{"kind":"check","text":"CI/CD + deploy em produção"},{"kind":"check","text":"Painel admin para gestão"},{"kind":"done","text":"Projeto entregue ✦ pronto pra escalar"}],"terminalCaptionLeft":"do briefing ao ar em produção","terminalCaptionRight":"~ 2-6 sem","chatTitle":"Assistente Meada","chatMessage":"Olá! 👋 Sou o assistente da Meada Digital. Como posso te ajudar hoje?"}},
  {"id":"services-1","type":"meada_services","props":{"eyebrow":"Capacidades","title":"Tudo o Que Você Precisa para Crescer","items":[{"icon":"Code","color":"#60a5fa","title":"Desenvolvimento Personalizado","description":"Sites e sistemas feitos sob medida, do institucional ao mais complexo.","linkLabel":"Saiba mais →","linkHref":"/servicos/desenvolvimento"},{"icon":"Cloud","color":"#a855f7","title":"Infraestrutura em Nuvem","description":"Deploy, CI/CD, monitoramento e escalabilidade sem dores de cabeça.","linkLabel":"Saiba mais →","linkHref":"/servicos/nuvem"},{"icon":"Heart","color":"#ec4899","title":"Manutenção & Suporte","description":"Acompanhamento contínuo, evolução de funcionalidades e correções com prazo previsível.","linkLabel":"Saiba mais →","linkHref":"/contato"},{"icon":"Smartphone","color":"#22d3ee","title":"Design Mobile First","description":"Experiências nativas e fluidas em qualquer dispositivo e tamanho de tela.","linkLabel":"Saiba mais →","linkHref":"/servicos/mobile"},{"icon":"Layers","color":"#34d399","title":"Design & UX","description":"Interfaces bonitas e funcionais. Do wireframe ao Design System completo.","linkLabel":"Saiba mais →","linkHref":"/servicos/design-ux"},{"icon":"BarChart3","color":"#f97316","title":"APIs & Integrações","description":"Pagamentos, CRMs, ERPs e qualquer sistema conectado em uma arquitetura coesa.","linkLabel":"Saiba mais →","linkHref":"/servicos/apis-integracoes"}]}},
  {"id":"portfolio-1","type":"meada_portfolio","props":{"eyebrow":"Portfolio","title":"Soluções Prontas para Usar","linkLabel":"Ver todos os projetos →","linkHref":"/portfolio","items":[]}},
  {"id":"cta-1","type":"meada_cta","props":{"titlePrefix":"Pronto para","gradientText":"Transformar seu Negócio?","subtitle":"Do site institucional ao sistema completo. Sem enrolação, com prazo claro e resultado.","primaryLabel":"Agendar Consultoria","primaryHref":"/contato","secondaryLabel":"Ver Produtos","secondaryHref":"/produtos"}},
  {"id":"footer-1","type":"meada_footer","props":{"brandName":"Meada","brandSuffix":"Digital","tagline":"Agência digital especializada em sites e sistemas sob medida para pequenos e médios negócios.","instagramUrl":"https://instagram.com/meadadigital","whatsappUrl":"https://wa.me/5581992612292","columns":[{"heading":"Serviços","links":[{"label":"Sites Profissionais","href":"/servicos"},{"label":"Sistemas sob Medida","href":"/servicos"},{"label":"Manutenção & Suporte","href":"/contato"}]},{"heading":"Empresa","links":[{"label":"Sobre Nós","href":"/sobre"},{"label":"Produtos","href":"/produtos"},{"label":"Serviços","href":"/servicos"}]},{"heading":"Contato","links":[{"label":"oi@meadadigital.com","href":"mailto:oi@meadadigital.com"},{"label":"(81) 99261-2292","href":"https://wa.me/5581992612292"},{"label":"@meadadigital","href":"https://instagram.com/meadadigital"}]}],"copyright":"© Meada Agência Digital. Todos os direitos reservados."}}
]$json$::jsonb,
  true, true)
on conflict do nothing;
