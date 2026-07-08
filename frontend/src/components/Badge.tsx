import type { ReactNode } from 'react'
import type { TicketType } from '@/types/api'

type Tone = 'slate' | 'brand' | 'emerald' | 'amber' | 'rose'

const TONES: Record<Tone, string> = {
  slate: 'border-slate-200 bg-slate-50 text-slate-600',
  brand: 'border-brand-200 bg-brand-50 text-brand-700',
  emerald: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  amber: 'border-amber-200 bg-amber-50 text-amber-700',
  rose: 'border-rose-200 bg-rose-50 text-rose-700',
}

export function Badge({
  tone = 'slate',
  children,
}: {
  tone?: Tone
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${TONES[tone]}`}
    >
      {children}
    </span>
  )
}

const TYPE_TONE: Record<TicketType, Tone> = {
  bug: 'rose',
  feature: 'brand',
  fix: 'amber',
}

/** Colored chip for a ticket's classification type. */
export function TypeBadge({ type }: { type: TicketType }) {
  return <Badge tone={TYPE_TONE[type]}>{type}</Badge>
}
