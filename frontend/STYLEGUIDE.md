# Kanban Frontend — Style Guide

The visual template every screen should follow. The auth screens (`src/features/session/`)
are the reference implementation. Tokens live in `src/index.css`; shared UI lives in
`src/components/`.

## Principles

- **Modern, calm, focused.** Generous whitespace, soft shadows, rounded corners
  (`rounded-lg` for controls, `rounded-xl`/`rounded-2xl` for cards/marks).
- **One accent, many neutrals.** Indigo→violet for brand moments; slate for everything
  structural. Color carries meaning (status), not decoration.
- **Tokens over hex.** Never hard-code brand hex in components — use the `brand-*` /
  `accent-*` utilities so a palette change is one edit in `index.css`.

## Color tokens

Defined in `src/index.css` under `@theme`; Tailwind v4 generates the utilities
(`bg-brand-600`, `text-brand-600`, `ring-brand-500`, `from-brand-600`, `border-brand-200`, …).

| Token | Hex | Use |
|---|---|---|
| `brand-50` | `#eef2ff` | Tinted backgrounds (info alert, icon halos) |
| `brand-100/200` | `#e0e7ff` / `#c7d2fe` | Borders, subtle fills |
| `brand-500` | `#6366f1` | Focus rings, gradient start-light |
| `brand-600` | `#4f46e5` | **Primary** — buttons, links, active |
| `brand-700` | `#4338ca` | Link/hover, gradient depth |
| `accent-500` | `#8b5cf6` | Gradient partner (indigo→violet) |
| `accent-400` | `#a78bfa` | Decorative glow |

**Signature gradient:** `bg-gradient-to-r from-brand-600 to-accent-500` (buttons),
`bg-gradient-to-br from-brand-700 via-brand-600 to-accent-500` (brand panels).

**Neutrals (Tailwind `slate`):** `slate-900` headings, `slate-700` body/labels,
`slate-500` secondary text, `slate-400` placeholder, `slate-300` borders,
`slate-50` app background.

**Status:** success `emerald-*`, error `red-*`, info `brand-*` — always as the trio
border-200 / bg-50 / text-700–800 (see `Alert`).

## Typography

System font stack (`system-ui, Segoe UI, …`), antialiased. Scale:

- Page title: `text-2xl font-bold tracking-tight text-slate-900`
- Marketing headline (brand panel): `text-4xl font-bold leading-tight tracking-tight`
- Body: `text-sm text-slate-600`; secondary/help: `text-sm text-slate-500`
- Labels: `text-sm font-medium text-slate-700`; field errors: `text-xs font-medium text-red-600`

## Spacing, radius, elevation

- Form field rhythm: `space-y-5`; groups: `gap-3`.
- Controls: `rounded-lg`, `px-3.5 py-2.5`. Cards/icon halos: `rounded-xl` / `rounded-full`.
- Elevation: `shadow-sm` at rest → `shadow-md` on hover for primary actions. Avoid heavy shadows.
- Focus (always visible): `focus-visible:ring-2 focus-visible:ring-brand-500` (buttons) /
  `focus:ring-2 focus:ring-brand-500/25` (inputs).

## Components (`src/components/`)

- **`Button`** — `variant`: `primary` (gradient), `secondary` (outline), `ghost`; `pending`
  shows a spinner and disables. Full-width via `className="w-full"`.
- **`TextField`** — labeled input with `error` text; wires `aria-invalid` / `aria-describedby`.
- **`Alert`** — `variant`: `success | error | info`; leading icon; `role="alert"` (status for success).
- **`icons.tsx`** — small `currentColor` icons (1.5 stroke). Add new icons here, same style.

## Layout

- **`AuthLayout`** (`src/features/session/`) — split-screen shell for unauthenticated pages:
  gradient brand panel (hidden `<lg`) + centered `max-w-md` content column. Pages render their
  heading + form as children. A mobile brand mark shows below `lg`.
- **App shell (future):** authenticated pages should get an `AppLayout` (top bar + optional
  sidebar) reusing these same tokens/components — do **not** use `AuthLayout` for in-app pages.

## Adding a new screen — checklist

1. Compose from `Button` / `TextField` / `Alert`; don't restyle inputs ad hoc.
2. Use `brand-*` / `slate-*` tokens; no raw brand hex.
3. Page title `text-2xl font-bold tracking-tight text-slate-900`; secondary text `slate-500`.
4. Every interactive element has a visible focus ring and a loading/disabled state.
5. Surface loading, empty, success, and error states (per the requirements' non-functional rules).
