import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

export const inputCls =
  'rounded-md border border-[#e9e9e7] bg-white px-2 py-1.5 text-sm outline-none focus:border-[#2383e2] focus:ring-2 focus:ring-[#2383e2]/15'

export function Btn({ children, className = '', type = 'submit', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  // default submit: o botão primário dentro de <form> dispara o onSubmit;
  // fora de form, submit é inofensivo. Cancelar/fechar usam GhostBtn (type=button).
  return (
    <button
      type={type}
      {...props}
      className={`rounded-md bg-[#2383e2] px-3 py-1.5 text-sm font-medium text-white transition hover:bg-[#1a75d2] disabled:opacity-50 ${className}`}
    >
      {children}
    </button>
  )
}

export function GhostBtn({ children, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className={`rounded-md border border-[#e9e9e7] px-3 py-1.5 text-sm text-[#5f5e5b] transition hover:bg-[#f1f1ef] disabled:opacity-50 ${className}`}
    >
      {children}
    </button>
  )
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-xs font-medium text-[#787774]">
      {label}
      {children}
    </label>
  )
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${inputCls} ${props.className ?? ''}`} />
}

export function money(cents: number): string {
  return 'R$ ' + (cents / 100).toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

export function parseMoney(text: string): number {
  const value = parseFloat(text.replace(/\./g, '').replace(',', '.'))
  return Number.isFinite(value) ? Math.round(value * 100) : 0
}

export function dateBr(iso: string | null): string {
  if (!iso) return ''
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${y}`
}
