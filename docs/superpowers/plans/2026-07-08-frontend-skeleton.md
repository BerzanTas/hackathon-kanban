# Frontend Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold a React + TypeScript SPA in `frontend/` that is fully wired (routing, data layer, typed API client, auth, styling, tests, Docker) and ready for feature work — with no feature screens beyond navigable placeholders.

**Architecture:** Vite + React 19 + TypeScript. Server state via TanStack Query over a thin typed `fetch` client that carries the session cookie and CSRF header. Same-origin with the backend everywhere: a Vite dev proxy in development and an nginx reverse proxy in the production container. Feature-based folder layout.

**Tech Stack:** Vite 7, React 19, TypeScript 5.9 (strict), Tailwind CSS 4, TanStack Query 5, React Router 7, Vitest 3 + React Testing Library + MSW 2, ESLint 9 (flat) + Prettier, Node 22 (Docker build), nginx (Docker serve).

## Global Constraints

These apply to every task:

- **Package manager:** npm. No host runtime required beyond Node/Docker.
- **TypeScript strict mode** on; no `any` in committed code.
- **Same-origin only** — the backend has no CORS config. The browser must only ever talk to the frontend origin; API traffic is proxied (Vite in dev, nginx in prod). Do not add CORS assumptions or hard-coded backend origins in app code.
- **API base paths** proxied to the backend: `/auth`, `/teams`, `/epics`, `/tickets`. Backend origin in dev: `http://localhost:8080`; in the compose network: `http://backend:8080`.
- **Auth is cookie-session + CSRF.** All requests use `credentials: "include"`. Mutating requests (POST/PUT/PATCH/DELETE) echo the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header. Never store session/token state in `localStorage`.
- **Dev port 5173**, prod container serves on port 80 mapped to host 5173 (keeps backend's `APP_FRONTEND_BASE_URL=http://localhost:5173` valid).
- **Ticket enums (canonical API values):** state = `new | ready_for_implementation | in_progress | ready_for_acceptance | done`; type = `bug | feature | fix`.
- **Version note:** the versions in `package.json` below are the intended majors. If `npm install` reports a peer-dependency conflict, install the latest stable release of the *same major* rather than downgrading a major.
- **Container runtime:** the user runs **Podman**, not Docker. `docker compose` commands may need to be `podman compose`. Do not assume a Docker daemon is present when verifying.

---

### Task 1: Project bootstrap — tooling, config, and a bootable app

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/.gitignore`
- Create: `frontend/tsconfig.json`, `frontend/tsconfig.app.json`, `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/eslint.config.js`
- Create: `frontend/.prettierrc`
- Create: `frontend/index.html`
- Create: `frontend/src/vite-env.d.ts`
- Create: `frontend/src/index.css`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/test/setup.ts`

**Interfaces:**
- Produces: a bootable Vite app; the path alias `@/*` → `src/*`; npm scripts `dev`, `build`, `lint`, `format`, `test`; a Vitest config (jsdom + `src/test/setup.ts`); a dev proxy for `/auth`, `/teams`, `/epics`, `/tickets` → `http://localhost:8080`; Tailwind CSS available via `@import 'tailwindcss'`.

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "kanban-frontend",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "lint": "eslint .",
    "format": "prettier --write .",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@tanstack/react-query": "^5.62.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.1.0"
  },
  "devDependencies": {
    "@eslint/js": "^9.17.0",
    "@tailwindcss/vite": "^4.0.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "@vitejs/plugin-react": "^5.0.0",
    "eslint": "^9.17.0",
    "eslint-config-prettier": "^9.1.0",
    "eslint-plugin-react-hooks": "^5.1.0",
    "eslint-plugin-react-refresh": "^0.4.16",
    "globals": "^15.14.0",
    "jsdom": "^26.0.0",
    "msw": "^2.7.0",
    "prettier": "^3.4.2",
    "tailwindcss": "^4.0.0",
    "typescript": "^5.9.0",
    "typescript-eslint": "^8.19.0",
    "vite": "^7.0.0",
    "vitest": "^3.0.0"
  }
}
```

- [ ] **Step 2: Create `frontend/.gitignore`**

```gitignore
node_modules
dist
coverage
*.local
.env
.env.*
!.env.example
.DS_Store
```

- [ ] **Step 3: Create the TypeScript configs**

`frontend/tsconfig.json`:

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

`frontend/tsconfig.app.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] }
  },
  "include": ["src"]
}
```

`frontend/tsconfig.node.json`:

```json
{
  "compilerOptions": {
    "target": "ES2023",
    "lib": ["ES2023"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 4: Create `frontend/vite.config.ts`** (dev proxy + Tailwind + Vitest)

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const API_PATHS = ['/auth', '/teams', '/epics', '/tickets']
const BACKEND = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      API_PATHS.map((path) => [
        path,
        { target: BACKEND, changeOrigin: true },
      ]),
    ),
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
})
```

- [ ] **Step 5: Create `frontend/eslint.config.js`**

```js
import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  { ignores: ['dist', 'coverage'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
    },
  },
  prettier,
)
```

- [ ] **Step 6: Create `frontend/.prettierrc`**

```json
{
  "semi": false,
  "singleQuote": true,
  "trailingComma": "all"
}
```

- [ ] **Step 7: Create `frontend/index.html`**

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Kanban</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Create `frontend/src/vite-env.d.ts`**

```ts
/// <reference types="vite/client" />
```

- [ ] **Step 9: Create `frontend/src/index.css`**

```css
@import 'tailwindcss';

:root {
  color-scheme: light;
}

body {
  margin: 0;
  font-family: system-ui, Avenir, Helvetica, Arial, sans-serif;
  background-color: #f8fafc;
  color: #0f172a;
}
```

- [ ] **Step 10: Create `frontend/src/main.tsx`**

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

- [ ] **Step 11: Create a minimal `frontend/src/App.tsx`** (replaced in Task 5)

```tsx
export default function App() {
  return <div className="p-8 text-2xl font-semibold">Kanban</div>
}
```

- [ ] **Step 12: Create `frontend/src/test/setup.ts`** (extended in Task 6)

```ts
import '@testing-library/jest-dom/vitest'
```

- [ ] **Step 13: Install dependencies**

Run (from `frontend/`): `npm install`
Expected: completes and writes `package-lock.json`. If a peer-dependency error appears, re-install the offending package at the latest stable of the same major (see Global Constraints).

- [ ] **Step 14: Verify build, lint, and dev server**

Run: `npm run build`
Expected: `tsc -b` passes and Vite writes `dist/` with no errors.

Run: `npm run lint`
Expected: no errors (warnings acceptable).

Run: `npm run dev` then open `http://localhost:5173`
Expected: page shows "Kanban" with Tailwind's `p-8 text-2xl font-semibold` applied. Stop the dev server (Ctrl-C) after confirming.

- [ ] **Step 15: Commit** (include the design spec per the repo's commit-timing preference)

```bash
git add frontend/ docs/superpowers/specs/2026-07-08-frontend-skeleton-design.md docs/superpowers/plans/2026-07-08-frontend-skeleton.md
git commit -m "feat(frontend): scaffold Vite + React + TS skeleton with tooling"
```

---

### Task 2: API type contract

**Files:**
- Create: `frontend/src/types/api.ts`

**Interfaces:**
- Produces: `TicketState`, `TicketType`, `STATE_ORDER`, `STATE_LABELS`, `TICKET_TYPES`; response types `Me`, `Team`, `TeamSummary`, `EpicSummary`, `UserSummary`, `Epic`, `Ticket`, `Comment`; request types `SignupRequest`, `LoginRequest`, `ResendRequest`, `CreateTeamRequest`, `RenameTeamRequest`, `CreateEpicRequest`, `UpdateEpicRequest`, `CreateTicketRequest`, `UpdateTicketRequest`, `ChangeStateRequest`, `AddCommentRequest`. All field names mirror the backend records verbatim (verified against `backend/src/main/java/.../dto/*.java`).

- [ ] **Step 1: Create `frontend/src/types/api.ts`**

```ts
// Ticket enums — canonical API values (mirror backend TicketState / TicketType).
export type TicketState =
  | 'new'
  | 'ready_for_implementation'
  | 'in_progress'
  | 'ready_for_acceptance'
  | 'done'

export type TicketType = 'bug' | 'feature' | 'fix'

// Board column order (workflow order).
export const STATE_ORDER: readonly TicketState[] = [
  'new',
  'ready_for_implementation',
  'in_progress',
  'ready_for_acceptance',
  'done',
]

// Human-readable labels for the board columns / ticket views.
export const STATE_LABELS: Record<TicketState, string> = {
  new: 'New',
  ready_for_implementation: 'Ready for Implementation',
  in_progress: 'In Progress',
  ready_for_acceptance: 'Ready for Acceptance',
  done: 'Done',
}

export const TICKET_TYPES: readonly TicketType[] = ['bug', 'feature', 'fix']

// ---- Nested summaries (backend *Summary records) ----
export interface TeamSummary {
  id: string
  name: string
}

export interface EpicSummary {
  id: string
  title: string
}

export interface UserSummary {
  id: string
  displayName: string
}

// ---- Responses ----
export interface Me {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
}

export interface Team {
  id: string
  name: string
  createdAt: string // ISO-8601 UTC
  modifiedAt: string
}

export interface Epic {
  id: string
  team: TeamSummary
  title: string
  description: string | null
  createdAt: string
  modifiedAt: string
}

export interface Ticket {
  id: string
  team: TeamSummary
  epic: EpicSummary | null
  type: TicketType
  state: TicketState
  title: string
  body: string
  createdBy: UserSummary
  createdAt: string
  modifiedAt: string
}

export interface Comment {
  id: string
  author: UserSummary
  body: string
  createdAt: string
}

// ---- Requests ----
export interface SignupRequest {
  email: string
  displayName: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface ResendRequest {
  email: string
}

export interface CreateTeamRequest {
  name: string
}

export interface RenameTeamRequest {
  name: string
}

export interface CreateEpicRequest {
  title: string
  description?: string | null
}

export interface UpdateEpicRequest {
  title: string
  description?: string | null
}

export interface CreateTicketRequest {
  epicId?: string | null
  type: TicketType
  title: string
  body: string
}

export interface UpdateTicketRequest {
  teamId: string
  epicId: string | null
  type: TicketType
  state: TicketState
  title: string
  body: string
}

export interface ChangeStateRequest {
  state: TicketState
}

export interface AddCommentRequest {
  body: string
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npx tsc -b`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/api.ts
git commit -m "feat(frontend): add typed API contract mirroring backend DTOs"
```

---

### Task 3: Typed API client with CSRF + error handling (TDD)

This is the one unit in the skeleton with real branching logic, so it is test-driven.

**Files:**
- Create: `frontend/src/lib/apiClient.ts`
- Test: `frontend/src/lib/apiClient.test.ts`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces:
  - `class ApiRequestError extends Error` with `status: number`, `detail?: string`, `problem: ApiError`.
  - `type AuthErrorCode = 'bad_credentials' | 'email_not_verified'`.
  - `interface ApiError { status: number; title: string; detail?: string; type?: string; instance?: string; code?: AuthErrorCode; errors?: Record<string, string> }` — mirrors the backend RFC-7807 `ProblemDetail` (incl. the machine-readable `code` on auth failures and the field-level `errors` map on 400s).
  - `const api` with `get<T>(path): Promise<T>`, `post<T>(path, body?): Promise<T>`, `put<T>(path, body?): Promise<T>`, `del<T>(path): Promise<T>`. All use `credentials: 'include'`; mutating verbs attach `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie; non-2xx throws `ApiRequestError`; 204/empty body resolves to `undefined`.

- [ ] **Step 1: Write the failing tests** — `frontend/src/lib/apiClient.test.ts`

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiRequestError } from './apiClient'

describe('apiClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    // reset cookies
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  const mockFetch = () => fetch as unknown as ReturnType<typeof vi.fn>

  it('GET parses JSON and sends credentials', async () => {
    mockFetch().mockResolvedValue(
      new Response(JSON.stringify({ id: '1', email: 'a@b.c' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await api.get<{ id: string; email: string }>('/auth/me')

    expect(result).toEqual({ id: '1', email: 'a@b.c' })
    const [, init] = mockFetch().mock.calls[0]
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('include')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('POST attaches the CSRF header from the XSRF-TOKEN cookie', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-abc123'
    mockFetch().mockResolvedValue(new Response(null, { status: 204 }))

    await api.post('/teams', { name: 'Alpha' })

    const [, init] = mockFetch().mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('csrf-abc123')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe(JSON.stringify({ name: 'Alpha' }))
  })

  it('throws ApiRequestError with the parsed ProblemDetail on non-2xx', async () => {
    mockFetch().mockResolvedValue(
      new Response(
        JSON.stringify({ title: 'Conflict', detail: 'Name already exists' }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(api.post('/teams', { name: 'dup' })).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 409,
      detail: 'Name already exists',
    })
    await expect(api.post('/teams', { name: 'dup' })).rejects.toBeInstanceOf(
      ApiRequestError,
    )
  })

  it('captures the machine-readable auth error code', async () => {
    mockFetch().mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Forbidden',
          detail: 'Email not verified',
          code: 'email_not_verified',
        }),
        { status: 403, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(api.post('/auth/login', {})).rejects.toMatchObject({
      status: 403,
      problem: { code: 'email_not_verified' },
    })
  })

  it('resolves to undefined for a 204 No Content response', async () => {
    mockFetch().mockResolvedValue(new Response(null, { status: 204 }))
    await expect(api.del('/tickets/1')).resolves.toBeUndefined()
  })
})
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/lib/apiClient.test.ts`
Expected: FAIL — cannot resolve `./apiClient`.

- [ ] **Step 3: Implement `frontend/src/lib/apiClient.ts`**

```ts
export type AuthErrorCode = 'bad_credentials' | 'email_not_verified'

export interface ApiError {
  status: number
  title: string
  detail?: string
  type?: string
  instance?: string
  code?: AuthErrorCode
  errors?: Record<string, string>
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly detail?: string
  readonly problem: ApiError

  constructor(problem: ApiError) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiRequestError'
    this.status = problem.status
    this.detail = problem.detail
    this.problem = problem
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const BASE = import.meta.env.VITE_API_BASE ?? ''

function readCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name + '=([^;]*)'),
  )
  return match ? decodeURIComponent(match[1]) : null
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const data = await res.json()
    return {
      status: res.status,
      title: data.title ?? res.statusText,
      detail: data.detail,
      type: data.type,
      instance: data.instance,
      code: data.code,
      errors: data.errors,
    }
  } catch {
    return { status: res.status, title: res.statusText }
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (MUTATING.has(method)) {
    const token = readCookie(CSRF_COOKIE)
    if (token) headers[CSRF_HEADER] = token
  }

  const res = await fetch(BASE + path, {
    method,
    credentials: 'include',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!res.ok) {
    throw new ApiRequestError(await parseError(res))
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/lib/apiClient.test.ts`
Expected: PASS — 5 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/apiClient.ts frontend/src/lib/apiClient.test.ts
git commit -m "feat(frontend): add typed API client with CSRF and ProblemDetail handling"
```

---

### Task 4: Query client and auth context

**Files:**
- Create: `frontend/src/lib/queryClient.ts`
- Create: `frontend/src/auth/AuthProvider.tsx`
- Create: `frontend/src/auth/useAuth.ts`

**Interfaces:**
- Consumes: `api` (Task 3); `Me`, `LoginRequest` (Task 2).
- Produces:
  - `queryClient: QueryClient`.
  - `type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'`.
  - `interface AuthContextValue { user: Me | null; status: AuthStatus; login(credentials: LoginRequest): Promise<void>; logout(): Promise<void> }`.
  - `AuthContext` (React context of `AuthContextValue | null`), `AuthProvider` component, `useAuth(): AuthContextValue`.

- [ ] **Step 1: Create `frontend/src/lib/queryClient.ts`**

```ts
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
})
```

- [ ] **Step 2: Create `frontend/src/auth/AuthProvider.tsx`**

```tsx
import { createContext, useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '@/lib/apiClient'
import type { LoginRequest, Me } from '@/types/api'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  user: Me | null
  status: AuthStatus
  login: (credentials: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  useEffect(() => {
    let active = true
    api
      .get<Me>('/auth/me')
      .then((me) => {
        if (!active) return
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (!active) return
        setUser(null)
        setStatus('unauthenticated')
      })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    const me = await api.post<Me>('/auth/login', credentials)
    setUser(me)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      setUser(null)
      setStatus('unauthenticated')
    }
  }, [])

  return (
    <AuthContext.Provider value={{ user, status, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
```

- [ ] **Step 3: Create `frontend/src/auth/useAuth.ts`**

```ts
import { useContext } from 'react'
import { AuthContext } from './AuthProvider'
import type { AuthContextValue } from './AuthProvider'

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
```

- [ ] **Step 4: Verify it compiles**

Run: `npx tsc -b`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/queryClient.ts frontend/src/auth/
git commit -m "feat(frontend): add TanStack Query client and auth context"
```

---

### Task 5: Router, protected route, placeholder pages, and app wiring

**Files:**
- Create: `frontend/src/routes/ProtectedRoute.tsx`
- Create: `frontend/src/routes/router.tsx`
- Create: `frontend/src/features/session/LoginPage.tsx`
- Create: `frontend/src/features/session/SignupPage.tsx`
- Create: `frontend/src/features/session/VerifyPage.tsx`
- Create: `frontend/src/features/board/BoardPage.tsx`
- Modify: `frontend/src/App.tsx` (replace the Task 1 placeholder)

**Interfaces:**
- Consumes: `useAuth` (Task 4); `queryClient` (Task 4); `AuthProvider` (Task 4).
- Produces: `ProtectedRoute` component (renders `<Outlet/>` when authenticated, redirects to `/login` when not, shows a loading state while `status === 'loading'`); `router` (a `createBrowserRouter` instance); page stubs `LoginPage`, `SignupPage`, `VerifyPage`, `BoardPage`; `App` mounting `QueryClientProvider` → `AuthProvider` → `RouterProvider`.

- [ ] **Step 1: Create `frontend/src/routes/ProtectedRoute.tsx`**

```tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'

export function ProtectedRoute() {
  const { status } = useAuth()
  if (status === 'loading') {
    return <div className="p-8 text-slate-500">Loading…</div>
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}
```

- [ ] **Step 2: Create the session page stubs**

`frontend/src/features/session/LoginPage.tsx`:

```tsx
export function LoginPage() {
  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Log in</h1>
      <p className="mt-2 text-slate-500">Login form coming soon.</p>
    </main>
  )
}
```

`frontend/src/features/session/SignupPage.tsx`:

```tsx
export function SignupPage() {
  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Sign up</h1>
      <p className="mt-2 text-slate-500">Sign-up form coming soon.</p>
    </main>
  )
}
```

`frontend/src/features/session/VerifyPage.tsx`:

```tsx
export function VerifyPage() {
  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Email verification</h1>
      <p className="mt-2 text-slate-500">Verification result coming soon.</p>
    </main>
  )
}
```

> **Note for feature work:** `GET /auth/verify` is a browser 302 redirect to the frontend **login** page with a `?verified=true` / `?error=expired` / `?error=invalid` query param — the SPA never fetches it. The verification *result* should therefore be surfaced on `LoginPage` by reading those query params (and offering a resend on `error=expired`). This `/verify` route is retained only as an optional landing/resend surface; do not wire it to call the endpoint.

- [ ] **Step 3: Create `frontend/src/features/board/BoardPage.tsx`** (exercises `useAuth`)

```tsx
import { useAuth } from '@/auth/useAuth'

export function BoardPage() {
  const { user, logout } = useAuth()
  return (
    <main className="p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Kanban Board</h1>
        <div className="flex items-center gap-4 text-sm text-slate-600">
          <span>{user?.email}</span>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded bg-slate-200 px-3 py-1 hover:bg-slate-300"
          >
            Log out
          </button>
        </div>
      </header>
      <p className="mt-4 text-slate-500">Board coming soon.</p>
    </main>
  )
}
```

- [ ] **Step 4: Create `frontend/src/routes/router.tsx`**

```tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { LoginPage } from '@/features/session/LoginPage'
import { SignupPage } from '@/features/session/SignupPage'
import { VerifyPage } from '@/features/session/VerifyPage'
import { BoardPage } from '@/features/board/BoardPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/verify', element: <VerifyPage /> },
  {
    element: <ProtectedRoute />,
    children: [{ path: '/', element: <BoardPage /> }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
```

- [ ] **Step 5: Replace `frontend/src/App.tsx`**

```tsx
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthProvider'
import { queryClient } from '@/lib/queryClient'
import { router } from '@/routes/router'

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
```

- [ ] **Step 6: Verify build and lint**

Run: `npm run build`
Expected: passes.

Run: `npm run lint`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/routes/ frontend/src/features/ frontend/src/App.tsx
git commit -m "feat(frontend): wire router, protected route, and placeholder pages"
```

---

### Task 6: Test harness (MSW) and app smoke test

**Files:**
- Create: `frontend/src/test/mocks/handlers.ts`
- Create: `frontend/src/test/mocks/server.ts`
- Modify: `frontend/src/test/setup.ts` (add MSW lifecycle)
- Create: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `App` (Task 5); MSW.
- Produces: `handlers` (default MSW handlers incl. `GET /auth/me`), `server` (MSW node server); global test lifecycle wiring MSW; a passing smoke test covering the authenticated and unauthenticated bootstrap flows (satisfies the "at least one frontend/API flow" requirement).

- [ ] **Step 1: Create `frontend/src/test/mocks/handlers.ts`**

```ts
import { http, HttpResponse } from 'msw'

export const handlers = [
  http.get('/auth/me', () =>
    HttpResponse.json({
      id: '00000000-0000-0000-0000-000000000001',
      email: 'qa@example.com',
      displayName: 'QA User',
      emailVerified: true,
    }),
  ),
]
```

- [ ] **Step 2: Create `frontend/src/test/mocks/server.ts`**

```ts
import { setupServer } from 'msw/node'
import { handlers } from './handlers'

export const server = setupServer(...handlers)
```

- [ ] **Step 3: Replace `frontend/src/test/setup.ts`** (add MSW lifecycle)

```ts
import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './mocks/server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
```

- [ ] **Step 4: Write the smoke test — `frontend/src/App.test.tsx`**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import App from './App'
import { server } from './test/mocks/server'

test('bootstraps the session and renders the board when authenticated', async () => {
  render(<App />)

  await waitFor(() =>
    expect(
      screen.getByRole('heading', { name: /kanban board/i }),
    ).toBeInTheDocument(),
  )
  expect(screen.getByText('qa@example.com')).toBeInTheDocument()
})

test('redirects to the login page when unauthenticated', async () => {
  server.use(
    http.get('/auth/me', () => new HttpResponse(null, { status: 401 })),
  )

  render(<App />)

  await waitFor(() =>
    expect(
      screen.getByRole('heading', { name: /log in/i }),
    ).toBeInTheDocument(),
  )
})
```

Note: the two tests share the module-level `queryClient`; because each asserts via a distinct route outcome and MSW resets handlers between tests, no explicit cache reset is needed. The `/auth/me` request is issued by `apiClient` via `fetch`, which MSW intercepts in Node.

- [ ] **Step 5: Run the full test suite**

Run: `npm test`
Expected: PASS — `apiClient.test.ts` (5) and `App.test.tsx` (2), 7 tests total.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/test/ frontend/src/App.test.tsx
git commit -m "test(frontend): add MSW harness and app bootstrap smoke tests"
```

---

### Task 7: Docker packaging and README

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `frontend/.dockerignore`
- Create: `frontend/.env.example`
- Create: `frontend/README.md`
- Modify: `docker-compose.yml` (repo root — add the `frontend` service)

**Interfaces:**
- Consumes: the built SPA in `dist/` (Task 5 build), the backend service named `backend` on port 8080 in the compose network.
- Produces: a multi-stage image (Node build → nginx serve) that serves the SPA and reverse-proxies API paths to `backend:8080`; a `frontend` service in the root compose file exposed on host 5173.

- [ ] **Step 1: Create `frontend/Dockerfile`**

```dockerfile
# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Serve stage ---
FROM nginx:1.27-alpine AS serve
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

- [ ] **Step 2: Create `frontend/nginx.conf`** (SPA fallback + same-origin API proxy)

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # SPA history fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Same-origin reverse proxy to the backend API
    location ~ ^/(auth|teams|epics|tickets)(/|$) {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

- [ ] **Step 3: Create `frontend/.dockerignore`**

```gitignore
node_modules
dist
coverage
.git
*.local
```

- [ ] **Step 4: Create `frontend/.env.example`**

```bash
# Optional. Leave empty to use the same-origin dev proxy (recommended).
# Set only if you need to point the SPA at a backend on a different origin.
VITE_API_BASE=
```

- [ ] **Step 5: Add the `frontend` service to the root `docker-compose.yml`**

Insert this service under `services:` (e.g. after the `backend` service):

```yaml
  frontend:
    build:
      context: ./frontend
    container_name: kanban-frontend
    ports:
      - "${FRONTEND_PORT:-5173}:80"
    depends_on:
      - backend
    restart: unless-stopped
```

- [ ] **Step 6: Create `frontend/README.md`**

```markdown
# Kanban Frontend

React + TypeScript SPA for the Kanban ticket tracker.

## Stack

Vite • React 19 • TypeScript • Tailwind CSS 4 • TanStack Query 5 •
React Router 7 • Vitest + React Testing Library + MSW.

## Prerequisites

- Node 22+ and npm (for local development), **or**
- Docker / Podman Compose (for the full stack).

## Local development

```bash
npm install
npm run dev
```

The app runs at http://localhost:5173. API calls to `/auth`, `/teams`,
`/epics`, and `/tickets` are proxied to the backend at
http://localhost:8080 (see `vite.config.ts`), so the browser stays
same-origin and the session cookie + CSRF token work without CORS. Start
the backend separately (e.g. `docker compose up backend postgres mailpit`
from the repo root).

## Scripts

- `npm run dev` — dev server with API proxy
- `npm run build` — type-check and produce a production build in `dist/`
- `npm run preview` — preview the production build
- `npm run lint` — ESLint
- `npm run format` — Prettier
- `npm test` — run the test suite once
- `npm run test:watch` — watch mode

## Full stack (Docker/Podman Compose)

From the repository root:

```bash
docker compose up --build
# or, with Podman:
podman compose up --build
```

The frontend is served by nginx on http://localhost:5173. nginx serves
the SPA and reverse-proxies the API paths to the `backend` container, so
everything is same-origin in production too.

## Architecture

- `src/lib/apiClient.ts` — typed `fetch` wrapper. Sends `credentials:
  'include'`; attaches the `X-XSRF-TOKEN` header from the `XSRF-TOKEN`
  cookie on mutating requests; parses RFC-7807 `ProblemDetail` errors into
  `ApiRequestError`.
- `src/lib/queryClient.ts` — TanStack Query client. Feature data is
  fetched with `useQuery`/`useMutation`; invalidate the relevant query
  keys after mutations. For the board's drag-and-drop, use an optimistic
  `useMutation` that rolls the card back on error.
- `src/auth/` — `AuthProvider` hydrates the session via `GET /auth/me`;
  `useAuth()` exposes `{ user, status, login, logout }`.
- `src/routes/` — route tree and `ProtectedRoute`.
- `src/features/` — one folder per feature (session, board, and the
  upcoming teams/epics/tickets/comments).
- `src/types/api.ts` — request/response types and ticket enums mirroring
  the backend.

## Testing

`src/test/` holds the Vitest + MSW setup. `apiClient.test.ts` covers the
client's CSRF/error logic; `App.test.tsx` is an end-to-end bootstrap flow
(authenticated → board, unauthenticated → login) against mocked endpoints.
```

- [ ] **Step 7: Verify the production build stage locally**

Run: `npm run build`
Expected: `dist/` is produced without errors (this is exactly what the Docker build stage runs).

- [ ] **Step 8: (Optional, environment-permitting) Verify the container build**

Run (from repo root): `docker compose build frontend` (or `podman compose build frontend`)
Expected: image builds through both stages. Skip if no container runtime is available in this environment; the build-stage verification in Step 7 covers the app compilation.

- [ ] **Step 9: Commit**

```bash
git add frontend/Dockerfile frontend/nginx.conf frontend/.dockerignore frontend/.env.example frontend/README.md docker-compose.yml
git commit -m "feat(frontend): add Docker packaging, nginx proxy, and README"
```

---

## Self-Review

**Spec coverage:**
- §2 Stack & Tooling → Task 1. ✅
- §3 Folder structure → Tasks 1–6 create the exact tree. ✅
- §4 API client & CSRF → Task 3 (TDD). ✅
- §5 Server state (TanStack Query) → Task 4 (`queryClient`) + Task 5 (provider). ✅
- §6 Auth wiring (`/auth/me`, `useAuth`, `ProtectedRoute`) → Tasks 4–5. ✅
- §7 Types → Task 2 (verified against backend records). ✅
- §8 Same-origin proxying (Vite dev + nginx prod + compose) → Task 1 (dev proxy) + Task 7 (nginx + compose). ✅
- §9 Styling (Tailwind v4) → Task 1. ✅
- §11 DoD: `npm run dev` (Task 1 §14), `npm run build`/`lint`/`test` (Tasks 1/5/6), smoke test (Task 6), `docker compose up --build` (Task 7), structure ready (all). ✅

**Placeholder scan:** No "TBD/TODO/handle edge cases" — every code step contains complete content. The intentional page stubs (Login/Signup/Verify/Board) are real, compiling components, not placeholders in the plan sense. ✅

**Type consistency:** `AuthContextValue`, `AuthStatus`, `Me`, `LoginRequest`, `api.{get,post,put,del}`, `ApiRequestError`, `AuthErrorCode`, `queryClient`, `router`, and the page component names are used identically across the tasks that define and consume them. Backend field names (`team`, `epic`, `createdBy` as nested summaries; `displayName`; `emailVerified`) match the verified records. ✅

**OpenAPI cross-check (v1 spec):** All request/response schemas, enums, and status codes in Tasks 2–3 verified against the pasted OpenAPI document — including nested `TeamSummary`/`EpicSummary`/`UserSummary`, nullable `epic`/`epicId`/`description`, the `202` (signup/resend) / `204` (logout/delete) / `201` (create) codes handled by the client's empty-body path, the session cookie name `JSESSIONID`, and the `ProblemDetail` fields (`code`, `type`, `instance`, `errors`) captured by `ApiError`. `GET /auth/verify` (302 browser redirect) is intentionally not called by the SPA (see Task 5 note). ✅
