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
