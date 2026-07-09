# Fix: SPA refresh on `/teams` and `/epics` shows raw backend JSON

**Date:** 2026-07-09
**Status:** Approved

## Problem

Refreshing the browser while on `http://localhost:5173/teams` or `/epics` replaces
the app with a raw JSON response from the backend. The UI never renders.

### Root cause: URL namespace collision

The SPA client-side routes and the backend API share the same URL root, and the
dev/prod proxies resolve the conflict in the backend's favor.

- Dev (`vite.config.ts`): `API_PATHS = ['/auth', '/teams', '/epics', '/tickets']`
  were each proxied to `http://localhost:8080` by path prefix.
- Prod (`nginx.conf`): `location ~ ^/(auth|teams|epics|tickets)(/|$)` proxied the
  same prefixes to the backend.

Clicking a nav link to `/teams` is handled in-app by React Router (no document
request leaves the browser), so it works. A hard refresh issues a real
`GET /teams` for the HTML document; the proxy matches the API prefix, forwards to
the backend, and returns JSON. The SPA history fallback (`index.html`) never runs.

Routes that do not collide (`/`, `/login`, `/signup`, `/verify`) refresh fine.
The bug exists identically in dev and production.

## Solution

Separate the namespaces: SPA routes own the URL root; all backend traffic lives
under `/api/*`. A path can never be both a page and an API call again.

### 1. `src/lib/apiClient.ts` — centralize the prefix

```ts
const BASE = import.meta.env.VITE_API_BASE ?? '/api'   // was ?? ''
```

Every API call already flows through the single `fetch` in this module, so
feature code (`api.get('/teams')`, `AuthProvider`, queries, mutations) is
unchanged; requests now resolve to `/api/teams`, etc.

### 2. `vite.config.ts` — one proxy rule + test env

Replace the four prefix entries with a single `/api` rule that strips the prefix
before forwarding (the backend serves at root):

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: BACKEND,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
    },
  },
},
test: { ..., env: { VITE_API_BASE: '' } },
```

`test.env` forces `BASE === ''` in jsdom, so MSW handlers keep matching root
paths (`/teams`, `/auth/me`). This is correct: MSW mocks the backend, which
serves at root. No test files change.

### 3. `nginx.conf` — mirror the split in production

```nginx
location /api/ {
    proxy_pass http://backend:8080/;   # trailing slash strips /api/
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

The existing `location / { try_files $uri $uri/ /index.html; }` now catches
`/teams` and `/epics` refreshes and serves the SPA.

### 4. Backend — no change

Serves at root; the proxy layer strips `/api` on both sides.

### CSRF — no impact

The `XSRF-TOKEN` cookie is written by `CookieCsrfTokenRepository.withHttpOnlyFalse()`
at the default path `/`, so it remains readable by `apiClient` for `/api/*`
requests. Same origin throughout.

## Data flow after fix

Refresh `/teams` → no `/api` prefix → SPA fallback serves `index.html` →
React Router renders `TeamsPage` → it calls `/api/teams` → proxy strips prefix →
backend `/teams`. Identical logic in dev (Vite) and prod (nginx).

## Verification

- `npm test` — all green, no test changes.
- `npm run build` — type-check + build succeed.
- Manual dev: `npm run dev`, refresh on `/teams` and `/epics` → UI renders and
  data loads.
- Manual prod: `docker compose up --build`, refresh both routes on the container.
