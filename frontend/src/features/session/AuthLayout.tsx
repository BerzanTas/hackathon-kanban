import type { ReactNode } from 'react'
import { BoardGlyph, CheckIcon } from '@/components/icons'

const FEATURES = [
  'Organize tickets by team and epic',
  'Drag cards across a live Kanban board',
  'Comment and collaborate as a team',
]

function BrandMark({ onDark = false }: { onDark?: boolean }) {
  return (
    <span
      className={
        onDark
          ? 'flex h-9 w-9 items-center justify-center rounded-xl bg-white/15 text-white ring-1 ring-white/25 backdrop-blur'
          : 'flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-600 to-accent-500 text-white shadow-sm'
      }
    >
      <BoardGlyph className="h-5 w-5" />
    </span>
  )
}

/**
 * Split-screen shell for the unauthenticated flow: a gradient brand panel
 * (hidden below `lg`) beside a centered content column. Pages render their
 * heading + form as `children`.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <aside className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-accent-500 p-12 text-white lg:flex">
        <div className="pointer-events-none absolute -right-24 -top-24 h-80 w-80 rounded-full bg-white/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-accent-400/20 blur-3xl" />

        <div className="relative flex items-center gap-2.5">
          <BrandMark onDark />
          <span className="text-lg font-semibold tracking-tight">Kanban</span>
        </div>

        <div className="relative">
          <h2 className="max-w-sm text-4xl font-bold leading-tight tracking-tight">
            Move work forward, together.
          </h2>
          <p className="mt-4 max-w-sm text-brand-100">
            A focused ticket tracker for teams — plan epics, track tickets, and
            keep everything moving across your board.
          </p>
          <ul className="mt-8 space-y-3">
            {FEATURES.map((feature) => (
              <li
                key={feature}
                className="flex items-center gap-3 text-sm text-brand-50"
              >
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/15">
                  <CheckIcon className="h-3.5 w-3.5" />
                </span>
                {feature}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-brand-200">
          Built for teams that ship.
        </p>
      </aside>

      {/* Content panel */}
      <main className="flex w-full items-center justify-center bg-slate-50 px-6 py-12 lg:w-1/2">
        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <BrandMark />
            <span className="text-lg font-semibold tracking-tight text-slate-900">
              Kanban
            </span>
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}
