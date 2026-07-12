export type User = {
  id: number
  name: string
  email: string
}

export type PageScope = 'shared' | 'personal'

export type PageKind =
  | 'note'
  | 'vault'
  | 'calendar'
  | 'tasks'
  | 'registro'
  | 'meds'
  | 'diet'
  | 'gastos'

export type TreeNode = {
  id: number
  parent_id: number | null
  scope: PageScope
  kind: PageKind
  title: string
  icon: string | null
  position: number
  children: TreeNode[]
}

export type Tree = {
  shared: TreeNode[]
  personal: TreeNode[]
}

export type PageMeta = {
  template?: RegistroField[]
  person?: string
  restrictions?: string
  goals?: string
  generate_requested?: boolean
  generated_at?: string
  google_sheet_id?: string
}

export type PageDetail = {
  id: number
  parent_id: number | null
  owner_id: number | null
  scope: PageScope
  kind: PageKind
  title: string
  icon: string | null
  content: string | null
  meta: PageMeta | null
  position: number
  updated_at: string
}

export type VaultEntry = {
  id: number
  title: string
  username: string | null
  url: string | null
  notes: string | null
}

export type CalendarEvent = {
  id: number
  title: string
  starts_at: string
  ends_at: string | null
  all_day: boolean
  recurrence: string
  notes: string | null
  google_event_ids: Record<string, string> | null
}

export type TaskItem = {
  id: number
  content: string
  assigned_user_id: number | null
  assignee: { id: number; name: string } | null
  due_date: string | null
  done: boolean
}

export type RegistroField = {
  key: string
  label: string
  type?: 'text' | 'number' | 'date'
}

export type RegistroEntry = {
  id: number
  data: Record<string, string>
}

export type MedicationLog = {
  id: number
  taken_at: string
  taken_by: { id: number; name: string } | null
}

export type Medication = {
  id: number
  person: string
  name: string
  dose: string | null
  schedule_times: string[]
  controlled: boolean
  prescription_until: string | null
  stock: number | null
  low_stock_threshold: number | null
  notes: string | null
  active: boolean
  logs: MedicationLog[]
}

export type ExpenseEntry = {
  id: number
  date: string
  description: string
  category: string | null
  amount_cents: number
  paid_by: string | null
  card: string | null
  synced_to_sheet: boolean
}

export type ExpensesResponse = {
  entries: ExpenseEntry[]
  total_cents: number
  by_category: Record<string, number>
}

export const KIND_INFO: Record<PageKind, { icon: string; label: string; desc: string }> = {
  note: { icon: '📝', label: 'Nota', desc: 'Texto livre, como uma página do Notion' },
  tasks: { icon: '✅', label: 'Tarefas', desc: 'Lista de tarefas com responsável e prazo' },
  calendar: { icon: '📅', label: 'Agenda', desc: 'Eventos — sincroniza com Google Calendar' },
  gastos: { icon: '💸', label: 'Gastos', desc: 'Lançamentos — sincroniza com Google Sheets' },
  vault: { icon: '🔐', label: 'Senhas', desc: 'Cofre cifrado; nunca sai pelo Telegram' },
  registro: { icon: '📋', label: 'Registro', desc: 'Cadastro com campos personalizados' },
  meds: { icon: '💊', label: 'Remédios', desc: 'Medicações com lembretes no Telegram' },
  diet: { icon: '🥗', label: 'Dieta', desc: 'Plano alimentar gerado pela IA' },
}
