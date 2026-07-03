import { apiFetch } from '@/lib/api/client'
import type { WeddingReportSummary } from '@/profiles/casamento/casamento-types'

export function getReportSummary(months: number): Promise<WeddingReportSummary> {
  return apiFetch<WeddingReportSummary>(`/api/casamento/reports/summary?months=${months}`)
}
