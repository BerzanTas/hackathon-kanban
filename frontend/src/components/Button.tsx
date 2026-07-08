import type { ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  pending?: boolean
}

export function Button({
  pending,
  disabled,
  children,
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      disabled={disabled || pending}
      className={`rounded bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-60 ${
        className ?? ''
      }`}
      {...props}
    >
      {pending ? 'Please wait…' : children}
    </button>
  )
}
