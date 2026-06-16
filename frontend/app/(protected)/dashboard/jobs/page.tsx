'use client'

import { Construction } from 'lucide-react'

import { PageHeader } from '@/components/layout/page-header'
import { Card } from '@/components/ui/card'

export default function JobsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Jobs agendados" description="Lembretes, reativação e tarefas cron." />
      <Card className="flex flex-col items-center justify-center gap-3 py-16 text-center">
        <Construction className="size-10 text-muted-foreground" />
        <h2 className="text-base font-medium">Em construção</h2>
        <p className="max-w-sm text-sm text-muted-foreground">
          Esta tela será implementada na fase 6.4. Acompanhe o ROADMAP.
        </p>
      </Card>
    </div>
  )
}
