# Frontend Skeleton — Design

**Date:** 2026-07-08
**Status:** Approved
**Scope:** Scaffold the React + TypeScript frontend for the Kanban ticket tracker. Skeleton only — all wiring, no feature screens.

## 1. Context

The repository is a three-tier Kanban ticket tracker. The backend (Spring Boot, cookie-session auth) and PostgreSQL are already implemented and orchestrated via `docker-compose.yml` at the repo root. This spec covers scaffolding the SPA frontend in `frontend/`.

### Backend API contract (already built)

Root-path REST endpoints (no `/api` prefix), UUID identifiers, ISO-8601 UTC timestamps:

- **Auth:** `POST /auth/signup`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/verify?token=…`, `POST /auth/resend`, `GET /auth/me`
- **Teams:** `GET /teams`, `GET /teams/{id}`, `POST /teams`, `PUT /teams/{id}`, `DELETE /teams/{id}`
- **Epics:** `GET /teams/{teamId}/epics`, `POST /teams/{teamId}/epics`, `GET /epics/{id}`, `PUT /epics/{id}`, `DELETE /epics/{id}`
- **Tickets:** `GET /teams/{teamId}/tickets`, `POST /teams/{teamId}/tickets`, `GET /tickets/{id}`, `PUT /tickets/{id}`, `PUT /tickets/{id}/state`, `DELETE /tickets/{id}`
- **Comments:** `GET /tickets/{ticketId}/comments`, `POST /tickets/{ticketId}/comments`

**Auth mechanism:** cookie-based session. CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()` — the backend sets an `XSRF-TOKEN` cookie and expects the value echoed in the `X-XSRF-TOKEN` header on mutating requests. `/auth/login`, `/auth/signup`, `/auth/resend` are CSRF-exempt (pre-session bootstrap). Errors are RFC-7807 `ProblemDetail` JSON bodies.

**No CORS config on the backend** → the frontend must be served **same-origin** with the API. Achieved via a Vite dev proxy (dev) and an nginx reverse proxy (prod).

## 2. Stack & Tooling

- **Vite + React 19 + TypeScript**; package manager **npm**.
- **ESLint (flat config) + Prettier** for lint/format.
- **Vitest + React Testing Library + jsdom + MSW** as the test harness (satisfies the "at least one frontend/API flow" requirement), with one smoke test that renders the app and asserts routing works against a mocked `/auth/me`.
- **Node 22 LTS** as the Docker build base.

## 3. Folder Structure (feature-based)

```
src/
  main.tsx                     # entry: mounts <App/>
  App.tsx                      # router + providers (QueryClient, Auth)
  index.css                    # Tailwind import + base layer
  routes/
    router.tsx                 # route tree
    ProtectedRoute.tsx         # redirects unauthenticated -> /login
  lib/
    apiClient.ts               # typed fetch wrapper (CSRF, errors, JSON)
    queryClient.ts             # TanStack Query client config
  auth/
    AuthProvider.tsx           # session context; hydrates via GET /auth/me
    useAuth.ts                 # useAuth() hook
  features/                    # empty dirs: teams/ epics/ tickets/ comments/ board/
  types/
    api.ts                     # DTOs + enums mirroring the backend
  components/                  # shared UI primitives (empty to start)
  test/
    setup.ts                   # Vitest + MSW + jest-dom setup
    mocks/
      handlers.ts              # MSW request handlers
      server.ts                # MSW node server for tests
```

Placeholder route components (Login, Signup, Verify, Board) live as lightweight stubs so navigation works before features exist. They are intentionally minimal and will be replaced feature-by-feature.

## 4. API Client & CSRF

A thin typed `apiClient` wrapping `fetch`:

- Always `credentials: "include"` so the session cookie is sent.
- On mutating methods (POST/PUT/DELETE) it reads the `XSRF-TOKEN` cookie and sets the `X-XSRF-TOKEN` header.
- Sets `Content-Type: application/json` and serializes/deserializes JSON.
- On non-2xx, parses the RFC-7807 `ProblemDetail` body into a typed `ApiError { status, title, detail, … }` and throws, so screens can surface real validation messages.
- Base URL from `import.meta.env.VITE_API_BASE` (default `""` = same-origin via proxy).

Exposes helpers: `api.get(path)`, `api.post(path, body)`, `api.put(path, body)`, `api.del(path)`.

## 5. Server State

**TanStack Query v5** provider mounted at the root, using the shared `queryClient`. The skeleton ships the client + provider and the `apiClient` only — no feature queries yet. The README documents the intended pattern: query keys per resource, mutations with cache invalidation, and optimistic updates for the drag-and-drop board (with rollback on failure, per the requirements).

## 6. Auth Wiring

- `AuthProvider` calls `GET /auth/me` on mount to hydrate the session and exposes `{ user, status, login, logout }` via context. `status` is `"loading" | "authenticated" | "unauthenticated"`.
- `useAuth()` reads the context.
- `login(email, password)` POSTs `/auth/login` then sets the user; `logout()` POSTs `/auth/logout` then clears it.
- `ProtectedRoute` renders a loading state while `status === "loading"`, redirects to `/login` when unauthenticated, and renders children when authenticated.

## 7. Types

`types/api.ts` mirrors the backend:

- `TicketState = "new" | "ready_for_implementation" | "in_progress" | "ready_for_acceptance" | "done"`
- `TicketType = "bug" | "feature" | "fix"`
- `STATE_LABELS: Record<TicketState, string>` for human-readable board column labels (spaces).
- `STATE_ORDER: TicketState[]` in workflow order for the five columns.
- Response DTOs: `Team`, `Epic`, `Ticket`, `Comment`, `Me` (matching the backend response records).
- Request DTOs: `CreateTeamRequest`, `RenameTeamRequest`, `CreateEpicRequest`, `UpdateEpicRequest`, `CreateTicketRequest`, `UpdateTicketRequest`, `ChangeStateRequest`, `SignupRequest`, `LoginRequest`, `ResendRequest`, `CreateCommentRequest`.

## 8. Same-Origin Proxying

- **Dev:** `vite.config.ts` proxies `/auth`, `/teams`, `/epics`, `/tickets` → `http://localhost:8080`. The browser only ever talks to `:5173`; cookies and CSRF work without CORS.
- **Prod:** multi-stage `Dockerfile` (Node build → **nginx** serve). `nginx.conf`:
  - serves the built SPA with SPA history fallback (`try_files … /index.html`);
  - reverse-proxies `/auth`, `/teams`, `/epics`, `/tickets` to `http://backend:8080` — same-origin again.
- `docker-compose.yml`: add a `frontend` service built from `./frontend`, mapped to host `5173:80`, depending on `backend`. This keeps the backend's existing `APP_FRONTEND_BASE_URL=http://localhost:5173` valid. The email verification link continues to hit the backend at `:8080` directly and redirects to `:5173`.

## 9. Styling

**Tailwind CSS v4** via its Vite plugin. `index.css` contains the Tailwind import and a minimal base layer (page background, default font). No design system yet — utilities are applied per-component as features are built.

## 10. Out of Scope (this spec)

- Any feature screen implementation (teams, epics, tickets, comments, the board itself) beyond navigable placeholder stubs.
- Form libraries, drag-and-drop libraries — chosen when the relevant feature is built.
- Auth flows beyond the `AuthProvider`/`ProtectedRoute` wiring and the `login`/`logout` calls.

## 11. Definition of Done (this spec)

- `npm install && npm run dev` starts the SPA on `:5173`; API calls proxy to the backend.
- `npm run build` produces a production bundle; `npm run lint` and `npm test` pass.
- The smoke test renders the app and verifies routing/auth-bootstrap against mocked endpoints.
- `docker compose up --build` from the repo root builds and serves the frontend on `:5173` alongside the existing services.
- Folder structure, API client, TanStack Query provider, auth context, and typed API contract are in place, ready for feature work.
