import type { ReactNode } from 'react'

type AlertVariant = 'success' | 'error' | 'info'

const STYLES: Record<AlertVariant, string> = {
  success: 'border-green-300 bg-green-50 text-green-800',
  error: 'border-red-300 bg-red-50 text-red-800',
  info: 'border-blue-300 bg-blue-50 text-blue-800',
}

export function Alert({
  variant,
  children,
}: {
  variant: AlertVariant
  children: ReactNode
}) {
  return (
    <div
      role={variant === 'success' ? 'status' : 'alert'}
      className={`rounded border px-3 py-2 text-sm ${STYLES[variant]}`}
    >
      {children}
    </div>
  )
}
