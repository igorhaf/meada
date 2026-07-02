import { apiFetch } from '@/lib/api/client'
import type { AtelieReportSummary } from '@/profiles/atelie/atelie-types'

export function getReportSummary(months: number): Promise<AtelieReportSummary> {
  return apiFetch<AtelieReportSummary>(`/api/atelie/reports/summary?months=${months}`)
}
