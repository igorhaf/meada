'use client'

import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { SignOutButton } from '@/components/sign-out-button'
import { getMe } from '@/lib/api/me'

/**
 * Hub do dashboard após login. Roteia por PAPEL:
 *  - super_admin → redireciona para /dashboard/companies (sua home funcional);
 *  - tenant_admin → placeholder "área restrita ao super-admin" (tela própria do tenant
 *    é 4.3+).
 *
 * O redirect usa useEffect + router.replace (NÃO no render): chamar router.replace
 * durante o render quebra ("Cannot update a component while rendering") — o useEffect
 * agenda para o próximo tick. replace (não push) evita que o "voltar" do browser caia
 * de novo em /dashboard e gere loop de redirect.
 */
export default function DashboardPage() {
  const router = useRouter()
  const { data: me, isPending, isError, error } = useQuery({ queryKey: ['me'], queryFn: getMe })

  const isSuperAdmin = me?.role === 'super_admin'

  useEffect(() => {
    // Só dispara quando me chegou e é super-admin. Durante isPending/isError,
    // isSuperAdmin é false (me undefined) — useEffect roda mas não age.
    if (isSuperAdmin) {
      router.replace('/dashboard/companies')
    }
  }, [isSuperAdmin, router])

  if (isPending) {
    return <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Carregando…</div>
  }

  if (isError) {
    console.error('failed to load /admin/me:', error)
    return (
      <div className="mx-auto max-w-3xl p-8">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-xl font-semibold">Dashboard</h1>
          <SignOutButton />
        </div>
        <p className="text-sm text-destructive">
          Erro ao carregar perfil. Tente sair e entrar de novo.
        </p>
      </div>
    )
  }

  if (isSuperAdmin) {
    // useEffect acima já disparou o replace; este render é só o tick intermediário.
    return <div className="mx-auto max-w-3xl p-8 text-sm text-muted-foreground">Redirecionando…</div>
  }

  // tenant_admin
  return (
    <div className="mx-auto max-w-3xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <SignOutButton />
      </div>
      <p className="text-sm text-muted-foreground">
        Painel do tenant em breve. Esta área de gestão é restrita ao super-admin.
      </p>
    </div>
  )
}
