-- migrate-admin-emails.sql  (one-off, idempotente)
-- Migra os tenant-admins do padrão antigo (igorhaf{N}@gmail.com / {slug}@meadadigital.com) para o
-- novo padrão determinístico: meada_{slug}_{admin_token}@meadadigital.com.
--
-- Atualiza o email em public.users E em auth.users (GoTrue). NÃO toca o super-admin
-- (igorhaf@gmail.com, sem linha em public.users). Idempotente: só migra quem ainda NÃO está no
-- padrão meada_%. A senha NÃO muda (continua bofo-meca-oleo em dev).
--
-- Rodar: psql ... -f supabase/snippets/migrate-admin-emails.sql

begin;

-- 1) atualiza public.users (alvo: tenant-admins ainda fora do padrão).
with target as (
  select u.id as user_id,
         'meada_' || c.slug || '_' || c.admin_token || '@meadadigital.com' as new_email
  from public.users u
  join public.companies c on c.id = u.company_id
  where u.role = 'admin'
    and u.email not like 'meada\_%@meadadigital.com'   -- ainda não migrado
)
update public.users u
   set email = t.new_email, updated_at = now()
  from target t
 where u.id = t.user_id;

-- 2) espelha o mesmo email em auth.users (GoTrue), pelo id (sub) que casa public.users.id.
with target as (
  select u.id as user_id, u.email as new_email
  from public.users u
  where u.role = 'admin' and u.email like 'meada\_%@meadadigital.com'
)
update auth.users a
   set email = t.new_email,
       raw_user_meta_data = jsonb_set(
         coalesce(a.raw_user_meta_data, '{}'::jsonb), '{email}', to_jsonb(t.new_email), true),
       updated_at = now()
  from target t
 where a.id = t.user_id
   and a.email <> t.new_email;

commit;

-- conferência
select c.slug, u.email
from public.users u join public.companies c on c.id = u.company_id
where u.role = 'admin' order by c.slug;
