import type { ReactNode } from 'react'
import { CheckCircleIcon, InfoCircleIcon, XCircleIcon } from './icons'

type AlertVariant = 'success' | 'error' | 'info'

const STYLES: Record<AlertVariant, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  error: 'border-red-200 bg-red-50 text-red-700',
  info: 'border-brand-200 bg-brand-50 text-brand-800',
}

const ICONS: Record<AlertVariant, typeof CheckCircleIcon> = {
  success: CheckCircleIcon,
  error: XCircleIcon,
  info: InfoCircleIcon,
}

export function Alert({
  variant,
  children,
}: {
  variant: AlertVariant
  children: ReactNode
}) {
  const Icon = ICONS[variant]
  return (
    <div
      role={variant === 'success' ? 'status' : 'alert'}
      className={`flex gap-2.5 rounded-lg border px-3.5 py-3 text-sm ${STYLES[variant]}`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  )
}
