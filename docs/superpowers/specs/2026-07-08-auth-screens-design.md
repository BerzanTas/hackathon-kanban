# Auth Screens Design — Login / Signup / Email Verification

**Date:** 2026-07-08
**Status:** Approved
**Scope:** Replace the frontend session-page stubs (`LoginPage`, `SignupPage`, `VerifyPage`) with
working screens for local email/password login, registration, and email-verification result — the
UI for the already-implemented backend auth JSON API
(`2026-07-07-security-authentication-design.md`). Builds on the frontend skeleton
(`2026-07-08-frontend-skeleton-design.md`): the typed `api` client, `useAuth`, the router, and the
MSW test harness already exist. Includes one small backend redirect change.

## 1. Context

The backend exposes the auth contract (verified against `AuthController.java` and the auth DTOs):

| Method | Path | Body | Success | Failure |
|---|---|---|---|---|
| POST | `/auth/signup` | `{ email, displayName, password }` | `202` (no auto-login) | `400` field `errors`; `409` duplicate email |
| POST | `/auth/login` | `{ email, password }` | `200` → `MeResponse` | `401` `code: bad_credentials`; `403` `code: email_not_verified` |
| POST | `/auth/logout` | — | `204` | — |
| GET | `/auth/verify?token=…` | — | `302` redirect to frontend | — |
| POST | `/auth/resend` | `{ email }` | `202` (generic, no enumeration) | — |
| GET | `/auth/me` | — | `200` → `MeResponse` | `401` |

The emailed activation link points **at the backend** `GET /auth/verify?token=…`. The backend
verifies/consumes the token itself and then `302`-redirects to the frontend. The SPA never calls
`/auth/verify`; its job for verification is to **display the outcome** carried in the redirect's
query params.

The frontend skeleton already provides:

- `src/lib/apiClient.ts` — `api.{get,post,put,del}`; `ApiRequestError` with `status`, `detail`, and
  `problem: ApiError` where `ApiError` carries `code?: 'bad_credentials' | 'email_not_verified'`
  and `errors?: Record<string, string>` (RFC-7807 field map).
- `src/auth/AuthProvider.tsx` / `useAuth()` — `{ user, status, login, logout }`; `login()` already
  posts to `/auth/login` and hydrates context.
- `src/routes/router.tsx` — routes `/login`, `/signup`, `/verify` (currently stubs), `/` protected.
- `src/test/` — Vitest + MSW harness (`handlers.ts`, `server.ts`).
- Request types in `src/types/api.ts`: `SignupRequest`, `LoginRequest`, `ResendRequest`, `Me`.

## 2. Backend change (in scope)

`AuthController.verify()` currently builds its redirect target as `frontendBaseUrl + "/login?..."`.
Change the three targets to land on the dedicated verification page:

```java
String target = switch (outcome) {
    case VERIFIED -> frontendBaseUrl + "/verify?verified=true";
    case EXPIRED  -> frontendBaseUrl + "/verify?error=expired";
    case INVALID  -> frontendBaseUrl + "/verify?error=invalid";
};
```

Nothing else on the backend behaves differently. Two integration tests assert on the redirect
target and must be updated from `**/login?...` to `**/verify?...`:
`AuthControllerIntegrationTest` (verified case) and `AuthFlowIntegrationTest` (verified + invalid
cases). `OpenApiConfig` also has a human-readable `302` description that says "frontend login page";
reword it to "frontend verification page" for accuracy (documentation only).

## 3. Screens

All three live under `src/features/session/`. Each is a centered card (`mx-auto max-w-md p-8`),
consistent with the current stubs.

### 3.1 LoginPage (`/login`)

- Form: email, password. Client validation: both required; email must be a valid format.
- Submit → `useAuth().login({ email, password })`. On success → `navigate('/', { replace: true })`.
- On `ApiRequestError`, branch on `err.problem.code`:
  - `email_not_verified` (403) → info/warning `Alert` ("Your email isn't verified yet.") plus a
    **Resend verification email** button that calls the shared resend hook with the email in the
    form.
  - `bad_credentials` (401) or anything else → generic error `Alert` ("Invalid email or password.").
- Redirect guard: if `useAuth().status === 'authenticated'`, `<Navigate to="/" replace />`.
- Link to `/signup` ("Need an account? Sign up").

### 3.2 SignupPage (`/signup`)

- Form: email, displayName, password, **confirm password**. Client validation mirrors backend:
  email required + valid; displayName required (max 255); password required + **≥ 8 chars**
  (max 255); confirm password required + must equal password.
  - **`displayName` is mandatory** — the backend `SignupRequest` declares it `@NotBlank`, and it is
    the author label shown on tickets/comments. Reference Wireframe 2 omits this field, but
    following the wireframe would make every signup fail with `400`; the backend contract wins.
  - **Confirm password is client-side only** — validated for equality before submit and **not** sent
    to the backend (there is no password-reset flow, so a typo would otherwise lock the account).
- A form-level "Already registered? Log in →" link to `/login`.
- Submit → `api.post('/auth/signup', { email, displayName, password })` (confirm password excluded).
- On `202` success: store the submitted email in local state and **swap the form for a confirmation
  panel**: "Check your email — we sent a verification link to `<email>`", a **Resend email** button
  (shared resend hook, prefilled with that email), and a "Back to login" link.
- On `400`: merge `err.problem.errors` (field→message) onto the matching fields.
- On `409` (duplicate email): error `Alert` ("An account with that email already exists.") with a
  link to `/login`.
- Redirect guard as in LoginPage.

### 3.3 VerifyPage (`/verify`)

- No API call. Reads `useSearchParams()`:
  - `verified=true` → success state ("Email verified — your account is ready to use.") + a
    prominent **Continue to login** button to `/login`. Satisfies §3's "a successful verification
    leads the user to the login screen" without auto-login.
  - `error=expired` → error `Alert` ("This verification link has expired.") + a resend affordance
    (below).
  - `error=invalid` → error `Alert` ("This verification link is invalid or already used.") + the
    same resend affordance, plus a link to `/login`.
  - no/unknown params → neutral message + link to `/login`.
- **Resend affordance (both error cases):** a small email input + **Resend** button (shared resend
  hook), since no email is otherwise in scope on this page. Reference Wireframe 2 groups "expired or
  invalid link → show an error and a resend action", so resend is offered on both error branches
  (broader than §10's "expired" minimum, and still compliant).

## 4. Shared building blocks

New folder `src/components/` (presentational primitives, no data logic):

- `TextField` — `{ label, type?, value, onChange, error?, ...input }`; renders label, input, and a
  red error line when `error` is set; sets `aria-invalid` / `aria-describedby`.
- `Button` — `{ pending?, children, ...button }`; disables and shows a pending label when
  `pending`.
- `Alert` — `{ variant: 'success' | 'error' | 'info', children }`; Tailwind color per variant;
  `role="alert"` for error/info.

New hook `src/features/session/useResend.ts`:

- `useResend()` → `{ status: 'idle' | 'sending' | 'sent' | 'error', resend(email: string): void }`.
- Wraps `api.post<void>('/auth/resend', { email })`. Always resolves to a generic "sent" state
  (the backend gives a non-enumerating `202`); on network/5xx → `error`. Rendered as a generic
  confirmation ("If that account exists, we've sent a new link.").

Optional tiny validation helpers (inline in the pages or a small `validation.ts`): `isEmail`,
`minLength`. Kept minimal — no form library.

## 5. Error handling

- Every `api` call is wrapped in try/catch; `ApiRequestError` drives either field errors (`400`
  `errors` map) or a top-of-form `Alert` (`401` / `403` / `409` / network).
- Submit buttons use the `Button` `pending` state and are disabled during the in-flight request.
- Client-side validation runs before submit; server-side field errors from a `400` augment it.

## 6. Testing (Vitest + MSW)

Extend `src/test/mocks/handlers.ts` with default auth handlers and override per-test with
`server.use(...)`. Each page gets a co-located test file. Because pages call `useNavigate` /
`useSearchParams`, tests render them inside a `MemoryRouter` (or a minimal `createMemoryRouter`) and
wrap in `QueryClientProvider` + `AuthProvider` where `useAuth` is exercised.

- **LoginPage:** valid submit → navigates to the board (assert board content / a redirect target);
  `bad_credentials` → generic error alert; `email_not_verified` → resend button appears and posting
  it hits `/auth/resend` (assert via MSW handler spy) and shows the sent confirmation.
- **SignupPage:** valid submit → confirmation panel showing the email; short password → client
  validation blocks submit (no network call); mismatched confirm password → client validation
  blocks submit; `409` → duplicate-email alert.
- **VerifyPage:** `verified=true`, `error=expired`, `error=invalid`, and no-param each render the
  correct outcome; on both `expired` and `invalid`, entering an email and clicking Resend calls
  `/auth/resend`.

## 6a. Requirements traceability

Against `requirements.docx`:

- **§3 Authentication:** sign-up (email/displayName/password, ≥8 chars), login/logout, unverified
  accounts blocked (`email_not_verified`), successful verification leading to login without
  auto-login, and resend "from the login or verification-result screen" — all covered by §3.1–3.3.
- **§10 Minimum Screens:** Sign-up screen (3.2), **Email verification result screen** (3.3, a
  dedicated route — the reason for the §2 backend redirect change), Login screen (3.1), and the
  verification-email resend action for unverified/expired cases (§4 `useResend`, wired into all
  three screens).
- **§11 Non-Functional:** loading / success / error states via `Button` pending and `Alert`
  variants; no secrets or tokens in the SPA (cookie session handled by the skeleton).
- **§13 Definition of Done:** contributes the sign-up → verify → log-in path (frontend half).

Auth items in requirements that are **out of this spec's scope** (handled elsewhere or by the
backend): SMTP delivery / `relay1.dataart.com` (backend), 24h single-use tokens (backend), and all
non-auth screens (teams, epics, tickets, comments, board).

## 7. Out of scope

- Password reset (not in backend).
- Remember-me / "stay signed in" beyond the session cookie the backend already sets.
- Profile / account management screens.
- Social / OAuth login.
- Board or other feature screens (separate work).

## 8. Files

**Frontend — create:**
- `src/components/TextField.tsx`, `src/components/Button.tsx`, `src/components/Alert.tsx`
- `src/features/session/useResend.ts`
- (optional) `src/features/session/validation.ts`
- `src/features/session/LoginPage.test.tsx`, `SignupPage.test.tsx`, `VerifyPage.test.tsx`

**Frontend — modify:**
- `src/features/session/LoginPage.tsx`, `SignupPage.tsx`, `VerifyPage.tsx` (replace stubs)
- `src/test/mocks/handlers.ts` (add auth handlers)

**Backend — modify:**
- `AuthController.java` (redirect targets `/login` → `/verify`)
- `AuthControllerIntegrationTest.java`, `AuthFlowIntegrationTest.java` (expected redirect paths)
- `OpenApiConfig.java` (302 description wording: "login page" → "verification page")
