import { apiFetch } from './client'

/**
 * Empresa na listagem do painel super-admin (GET /admin/companies). Espelha o
 * CompanyResponse do backend.
 *
 * id e createdAt são string (JSON serializa UUID e Instant como string). status é union
 * literal — mesma razão do backend (CHECK constraint garante; frontend só tipa).
 * createdAt fica como string ISO-8601 cru: a formatação (new Date(...)) é da TELA, não
 * desta camada (separation of concerns — aqui é só shape).
 */
export type Company = {
  id: string
  name: string
  slug: string
  status: 'active' | 'suspended'
  createdAt: string
}

/**
 * Lista GLOBAL de empresas. Super-admin only — mas a autorização é do backend (retorna
 * 403 forbidden_not_super_admin para tenant-admin); o frontend só consome e trata o erro.
 */
export async function getCompanies(): Promise<Company[]> {
  return apiFetch<Company[]>('/admin/companies')
}
