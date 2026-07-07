# Security & Authentication Design — Kanban Backend

**Date:** 2026-07-07
**Status:** Approved
**Scope:** Replace the temporary permit-all security and the mocked acting-user seam with real
authentication and authorization. Adds local email/password sign-up, Argon2id password hashing,
SMTP email verification (24h single-use tokens, resend), session-cookie login/logout, and
endpoint protection. The frontend SPA is **not built yet**, so this phase delivers the backend
**auth JSON API** that the project's own login/verification screens will later call. There is no
Spring Security default login page.

## 1. Context

The persistence, service, and controller layers are implemented
(`2026-07-06-persistence-layer-design.md`, `2026-07-07-service-layer-design.md`,
`2026-07-07-controller-layer-design.md`). Authentication was explicitly deferred by the controller
phase, which left three clearly-marked temporary stand-ins that this phase removes:

1. **`SecurityConfig`** — a permit-all `SecurityFilterChain`.
2. **`UserMapper.toCreateCommand`** — passes the request's plaintext `password` through as the
   stored `passwordHash` (no hashing).
3. **`CurrentUserProvider`** — reads the acting user id from the temporary `X-Acting-User-Id`
   request header instead of a security context.

The domain model already anticipates this phase: `User` carries `passwordHash` and `emailVerified`;
`EmailVerificationToken` (with `token`, `expiresAt`, `consumedAt`, `user`) and its repository
(`findByToken`, `findByUserIdAndConsumedAtIsNull`) and the Liquibase changeset
`002-create-email-verification-tokens.yaml` all exist. `UserService.create` already takes an opaque
already-encoded `passwordHash` and stores it verbatim.

Stack: Spring Boot 4.1.0 on Java 25 (Spring Framework 7 / Spring Security 6.x DSL), Lombok,
PostgreSQL 18, Liquibase, MapStruct, springdoc. `spring-boot-starter-security` and
`spring-boot-starter-security-test` are already on the classpath.

### Decisions taken during brainstorming

- **Session-cookie authentication** (`HttpSession` + `JSESSIONID`), not JWT. Simpler and safer for
  an SPA with its own login screen: server-side logout, no token in JavaScript. CSRF is handled
  with a cookie-based token repository.
- **Own JSON login endpoint**, not Spring Security's default form-login page. `formLogin` and
  `httpBasic` are disabled.
- **Argon2id** password hashing via Spring Security's `Argon2PasswordEncoder`.
- **Mailpit** container for local/QA email testing; `relay1.dataart.com` remains configurable via
  the same `SPRING_MAIL_*` environment variables.
- **User API trimmed to auth essentials.** Public `POST /users` is replaced by `POST /auth/signup`;
  `PUT`/`DELETE /users` are removed (no user-management or profile screen in requirements);
  `GET /auth/me` is added. See §4 and §10.
- **Unverified login is blocked at login** with a clear `email_not_verified` code (HTTP 403) so the
  login screen can offer resend, rather than authenticating and forbidding business endpoints.
- **Verification link points to the backend** (`GET /auth/verify`), which verifies and then
  `302`-redirects to the frontend login screen. QA can click the emailed link and land on login even
  before a frontend verification screen exists.
- **No roles / RBAC.** The only authorization level is "authenticated and verified"; all verified
  users can manage all teams/epics/tickets/comments, per requirements.

## 2. Build changes (`pom.xml`)

- Add **`org.springframework.boot:spring-boot-starter-mail`** (JavaMailSender for SMTP).
- Add **`org.bouncycastle:bcprov-jdk18on`** — Spring Security's `Argon2PasswordEncoder` requires
  BouncyCastle at runtime. **Version risk:** confirm a release compatible with the Spring Boot 4.1
  managed dependency set resolves and that the encoder produces/verifies Argon2id hashes at build
  time. Resolved during implementation, not carried into the design.
- No springdoc/MapStruct build changes; both are already wired.

## 3. Package layout

```
com.berzantas.kanban
├── security
│   ├── SecurityConfig                   → real SecurityFilterChain (replaces permit-all)
│   ├── KanbanUserDetailsService         → loads User by email (case-insensitive) → UserPrincipal
│   ├── UserPrincipal                    → UserDetails: id, email, passwordHash, enabled=emailVerified
│   ├── PasswordConfig                   → Argon2PasswordEncoder (Argon2id) bean
│   ├── ProblemDetailAuthEntryPoint      → 401 as application/problem+json
│   └── ProblemDetailAccessDeniedHandler → 403 as application/problem+json
├── auth
│   ├── AuthController                   → /auth/signup, /login, /logout, /verify, /resend, /me
│   ├── AuthService                      → orchestrates signup / verify / resend
│   └── dto/  SignupRequest, LoginRequest, ResendRequest, MeResponse
├── email
│   ├── EmailVerificationService         → token generation / consumption / resend invalidation
│   └── VerificationMailSender           → JavaMailSender wrapper + verification link body
└── common
    └── CurrentUserProvider              → REWRITTEN: reads SecurityContextHolder (header gone)
```

`user` package changes: remove the temporary plaintext mapping and the public create/update/delete
HTTP surface (see §10). `UserService` is unchanged.

## 4. Auth endpoints

All bodies are JSON unless noted. Public endpoints are listed in §5.

| Method | Path | Behavior | Success status |
|---|---|---|---|
| POST | `/auth/signup` | Create user (`emailVerified=false`), Argon2id-hash the password, generate a verification token, send the email. **No auto-login.** | 202 |
| POST | `/auth/login` | Authenticate via `AuthenticationManager`; persist `SecurityContext` to the session. Returns `MeResponse`. | 200 |
| POST | `/auth/logout` | Invalidate the session and clear the security context. | 204 |
| GET | `/auth/verify?token=…` | Verify and consume the token → `emailVerified=true`; then `302` redirect to `${app.frontend-base-url}/login?verified=true` (or `?error=expired` / `?error=invalid`). | 302 |
| POST | `/auth/resend` | For an unverified user: invalidate prior unused tokens, create a new one, send it. Generic response regardless of whether the account exists (no enumeration signal). | 202 |
| GET | `/auth/me` | Current authenticated user (`id`, `email`, `displayName`, `emailVerified`). Protected. | 200 |

### Request DTOs (validated records)

- `SignupRequest(email, displayName, password)` — `@NotBlank @Email @Size(max=320)` email;
  `@NotBlank @Size(max=255)` displayName; `@NotBlank @Size(min=8, max=255)` password (the ≥8
  requirement enforced at the HTTP boundary).
- `LoginRequest(email, password)` — `@NotBlank` both.
- `ResendRequest(email)` — `@NotBlank @Email`.
- `MeResponse(id, email, displayName, emailVerified)` — never exposes `passwordHash`.

### Login and unverified handling

`AuthController.login` calls `authenticationManager.authenticate(new
UsernamePasswordAuthenticationToken(email, password))`, then builds an empty `SecurityContext`, sets
the returned `Authentication`, and saves it via the `SecurityContextRepository`
(`HttpSessionSecurityContextRepository`) so the session carries it on subsequent requests.

`UserPrincipal.isEnabled()` returns `emailVerified`. `DaoAuthenticationProvider` therefore throws
`DisabledException` for an unverified account → mapped to **403** with code `email_not_verified` so
the login screen can offer resend. A bad password or unknown email → `BadCredentialsException` →
**401** `bad_credentials`. Email lookup is case-insensitive and trimmed (reuses
`UserRepository.findByEmailIgnoreCase`).

### Sign-up flow

`AuthService.signup`:
1. Argon2id-encode the plaintext password.
2. Call `UserService.create(new CreateUserCommand(email, displayName, encodedHash))` — the service
   still owns uniqueness (case-insensitive) and throws `ConflictException` → 409 on duplicate email.
3. Create and persist a verification token (§6).
4. **After the transaction commits**, send the verification email best-effort (§6).

## 5. Filter chain (`SecurityConfig`)

- **Public**: `/auth/signup`, `/auth/login`, `/auth/verify`, `/auth/resend`, `/v3/api-docs/**`,
  `/swagger-ui/**`. Everything else `authenticated()`.
- **CSRF**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN`, header
  `X-XSRF-TOKEN`) protects authenticated state-changing requests. The public bootstrap endpoints
  `/auth/login`, `/auth/signup`, `/auth/resend` are CSRF-exempt (no session to protect yet);
  `/auth/verify` is a GET. Note this exemption in `SecurityConfig`.
- **Session**: default creation policy (a session is created at login). `httpBasic` and `formLogin`
  disabled.
- **exceptionHandling**: `authenticationEntryPoint(ProblemDetailAuthEntryPoint)` and
  `accessDeniedHandler(ProblemDetailAccessDeniedHandler)`.
- Health/readiness and static SPA assets are not served by the backend in this phase; if
  `spring-boot-actuator` is added later, `/actuator/health` should be permitted.

## 6. Email verification (SMTP)

- `EmailVerificationToken` (existing entity) — `token` = 32 cryptographically-random bytes,
  Base64URL-encoded; `expiresAt = now + 24h`; `consumedAt` set on use.
- **Verify**: look up by token; reject (redirect `?error=invalid`) if missing or already consumed;
  reject (redirect `?error=expired`) if past `expiresAt`; otherwise set `user.emailVerified = true`
  and `token.consumedAt = now`, then redirect `?verified=true`. Single-use is enforced by
  `consumedAt`.
- **Resend**: `findByUserIdAndConsumedAtIsNull` → mark each returned token consumed (invalidating
  earlier unused tokens per requirements), create a fresh token, send it.
- **Delivery**: send **after** the sign-up transaction commits and best-effort — an SMTP failure is
  logged and does **not** roll back the created account; the user can request a resend. (Implement
  as post-commit / `@Async` sending so signup latency and mail errors are decoupled.)
- **Config**: `spring-boot-starter-mail` with `SPRING_MAIL_HOST`/`PORT`/`USERNAME`/`PASSWORD` env.
  Local/QA default host is `mailpit`; `relay1.dataart.com` is set through the same variables. The
  verification link base is `${APP_FRONTEND_BASE_URL}` (configurable; dev default
  `http://localhost:5173`). No SMTP secret is committed.

### docker-compose

Add a `mailpit` service (SMTP `1025`, web UI `8025`). Point the backend at it via
`SPRING_MAIL_HOST=mailpit`, `SPRING_MAIL_PORT=1025`, and set `APP_FRONTEND_BASE_URL`. The stack
still starts from a clean checkout with `docker compose up --build`, and QA can inspect verification
emails in the Mailpit UI.

## 7. `CurrentUserProvider` — replacing the seam

Rewritten to read `SecurityContextHolder.getContext().getAuthentication()`, cast the principal to
`UserPrincipal`, and return its `id`. The `X-Acting-User-Id` header and all temporary logic are
removed. Behind the authenticated filter chain the principal is always present; a missing/foreign
principal is an internal error. No controller, mapper, or service other than this class changes —
the seam works exactly as the controller phase intended.

## 8. Error handling

401 and 403 arise in the filter chain, before `DispatcherServlet`, so `@RestControllerAdvice`
(`GlobalExceptionHandler`) does not see them. Two components keep the error format consistent:

- `ProblemDetailAuthEntryPoint` (`AuthenticationEntryPoint`) → **401** `application/problem+json`.
- `ProblemDetailAccessDeniedHandler` (`AccessDeniedHandler`) → **403** `application/problem+json`.

Login-specific failures are handled inside `AuthController` (which runs inside the dispatcher):
`DisabledException` → 403 `ProblemDetail` with code `email_not_verified`; `BadCredentialsException`
→ 401 `ProblemDetail` with code `bad_credentials`. Every response carries `status`, `title`,
`detail`, and `instance`, matching the existing handler's shape.

## 9. OpenAPI

`OpenApiConfig` declares the session-cookie security scheme (previously omitted because auth was
deferred). The auth endpoints appear in the generated document; protected endpoints are marked as
requiring authentication. springdoc's `/v3/api-docs` and Swagger UI remain public.

## 10. User API changes

- **Remove** public `POST /users` (superseded by `/auth/signup`), `PUT /users/{id}`, and
  `DELETE /users/{id}` — no requirement screen backs them.
- **Remove** `CreateUserRequest`/`UpdateUserRequest` HTTP DTOs and the temporary plaintext mapping
  in `UserMapper` (`toCreateCommand`/`toUpdateCommand`); `UserMapper` keeps `toResponse`/`toSummary`.
- `GET /users` and `GET /users/{id}` are retained only if a consumer needs them; otherwise removed.
  Default: **remove** unless referenced, keeping the surface minimal. `UserSummary` (used inside
  ticket/comment responses) stays.
- `UserService` is unchanged (`create`, `getById`, `findByEmail`, `list`, `update`, `delete` remain
  for internal/service use; `AuthService` calls `create`).

## 11. Testing and migration of existing tests

- **Migration cost (must be done this phase):** the existing `ApiFlowIntegrationTest` drives the API
  with the `X-Acting-User-Id` header against permit-all. Both go away, so the test must be updated to
  authenticate — establish a session via `/auth/login` (after seeding a verified user) or use
  `spring-security-test` `SecurityMockMvcRequestPostProcessors`/`@WithUserDetails`. The acting user
  now comes from the session, not a header.
- **New `AuthFlowIntegrationTest`** (MockMvc + full Spring context over Testcontainers PostgreSQL,
  reusing the existing base):
  1. `POST /auth/signup` → 202; a verification token row exists; unverified.
  2. `POST /auth/login` for the unverified user → 403 `email_not_verified`.
  3. `GET /auth/verify?token=…` (token read from the repository) → account verified.
  4. `POST /auth/login` → 200, session cookie issued; a protected request (`GET /auth/me` or a team
     endpoint) succeeds with the cookie.
  5. `POST /auth/logout` → 204; the same protected request now → 401.
  6. Error/rule paths: signup with a <8-char password → 400 with field `errors`; duplicate email →
     409; bad password → 401; resend creates a new token and marks the prior unused token consumed.

This satisfies the requirement for automated tests covering a backend business flow and an API flow,
now including authentication.

## 12. Out of scope (this phase)

- Password reset flow (stretch).
- Comment edit/delete (stretch).
- Roles, administrators, team membership, per-resource access control.
- The frontend SPA and its screens (login, sign-up, verification-result) — this phase delivers the
  JSON API they consume.
- Account-enumeration hardening beyond the generic resend response.
- JWT/bearer tokens (session cookies chosen instead).
