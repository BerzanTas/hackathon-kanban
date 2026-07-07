# Controller Layer Design — Kanban Backend

**Date:** 2026-07-07
**Status:** Approved
**Scope:** Presentation/API tier — a REST controller, DTOs, and a MapStruct mapper per aggregate (User, Team, Epic, Ticket, Comment) on top of the existing service layer, plus a global exception handler producing RFC 7807 responses and a temporary permit-all security configuration. Authentication, email verification, and the frontend are **out of scope** for this phase.

## 1. Context

The persistence layer (`2026-07-06-persistence-layer-design.md`) and service layer (`2026-07-07-service-layer-design.md`) are implemented. Services expose CRUD per aggregate, take immutable validated **command records** as input, return **entities**, own `modified_at`, and throw three business exceptions from the `common` package (`NotFoundException`, `ConflictException`, `ValidationException`). Stack is Spring Boot 4.1.0 on Java 25 with Lombok and PostgreSQL 18; `spring-boot-starter-webmvc`, `-validation`, and `-security` are already on the classpath.

This phase adds the HTTP surface: it maps request JSON to service commands, service-returned entities to response JSON, and business exceptions to HTTP status codes, and it publishes an OpenAPI contract to ease later frontend development.

### Decisions taken during brainstorming

- **Code-first with springdoc, not contract-first codegen.** Controllers and DTOs are hand-written (idiomatic records-with-validation, matching the command layer); springdoc generates the OpenAPI document (`/v3/api-docs`) and Swagger UI from the code at runtime. The generated document can be committed as an artifact for the frontend.
- **MapStruct** generates the DTO↔service mapping (see §5). It maps **request DTO → command record** on the way in and **entity → response DTO** on the way out. It never builds entities — services own entity construction.
- **Hybrid URL structure** — team-scoped paths where the domain is naturally scoped (epics, board), flat paths where a resource has a stable global id (see §4).
- **Response DTOs embed nested summaries** (`team{id,name}`, `epic{id,title}`, author/creator `{id,displayName}`) so the board and detail screens render without extra round-trips. Request DTOs carry plain UUIDs.
- **RFC 7807 `ProblemDetail`** (`application/problem+json`) is the error format for every failure (see §9).
- **Server-side board filtering** — `type`, `epicId`, and title-substring `q`, AND-combined, ordered most-recently-modified-first (see §8).
- **Updates use `PUT`** (full replacement): the service `update`/`rename` commands are complete representations of the mutable fields, so `PUT` is the honest verb. `PUT /tickets/{id}/state` is the dedicated drag-and-drop path.
- **Request-DTO validation is kept** (annotations on both the request DTO and the command record). The request DTO fails fast at the HTTP boundary with a per-field `errors` map; the `@Validated` service still guards the command for any non-HTTP caller. The small duplication buys frontend-friendly field errors.
- **Authentication is deferred to the very next phase, not built now.** See §6 for the temporary stand-ins and the acting-user seam that makes the later swap mechanical.

## 2. Build changes (`pom.xml`)

- **springdoc** — add `org.springdoc:springdoc-openapi-starter-webmvc-ui`. **Version risk:** springdoc 2.x targets Spring Boot 3 (Spring Framework 6); Spring Boot 4.1 runs on Spring Framework 7. Pin the springdoc release that supports Spring Boot 4 and verify it resolves and starts at build time. **Fallback if no compatible release exists yet:** use `springdoc-openapi-starter-webmvc-api` (contract only, no Swagger UI), or expose `/v3/api-docs` alone. This risk is resolved during implementation, not carried into the design.
- **MapStruct** — add `org.mapstruct:mapstruct` (compile) and `org.mapstruct:mapstruct-processor` + `org.projectlombok:lombok-mapstruct-binding` to the annotation-processor paths of **both** existing `maven-compiler-plugin` executions (`default-compile` and `default-testCompile`), ordered after Lombok so MapStruct sees the generated getters/setters.

## 3. Package layout

Controllers, DTOs, and mappers live in each existing **feature package**; cross-cutting web infrastructure joins `common`:

```
com.berzantas.kanban
├── common
│   ├── GlobalExceptionHandler   → @RestControllerAdvice → ProblemDetail
│   ├── SecurityConfig           → TEMPORARY permit-all filter chain
│   ├── OpenApiConfig            → API metadata (title, version, description)
│   └── CurrentUserProvider      → the acting-user seam (see §6)
├── user
│   ├── UserController
│   ├── UserMapper
│   └── dto/  CreateUserRequest, UpdateUserRequest, UserResponse, UserSummary
├── team
│   ├── TeamController
│   ├── TeamMapper
│   └── dto/  CreateTeamRequest, RenameTeamRequest, TeamResponse, TeamSummary
├── epic
│   ├── EpicController
│   ├── EpicMapper
│   └── dto/  CreateEpicRequest, UpdateEpicRequest, EpicResponse, EpicSummary
├── ticket
│   ├── TicketController
│   ├── TicketMapper
│   └── dto/  CreateTicketRequest, UpdateTicketRequest, ChangeStateRequest,
│             TicketResponse, TicketFilter
└── comment
    ├── CommentController
    ├── CommentMapper
    └── dto/  AddCommentRequest, CommentResponse
```

## 4. Endpoints

Hybrid structure. All request/response bodies are JSON. Path variables are UUIDs.

| Method | Path | Service call | Success status |
|---|---|---|---|
| GET | `/teams` | `list` | 200 |
| POST | `/teams` | `create` | 201 (+ `Location`) |
| GET | `/teams/{id}` | `getById` | 200 |
| PUT | `/teams/{id}` | `rename` | 200 |
| DELETE | `/teams/{id}` | `delete` | 204 |
| GET | `/teams/{teamId}/epics` | `listByTeam` | 200 |
| POST | `/teams/{teamId}/epics` | `create` | 201 (+ `Location`) |
| GET | `/epics/{id}` | `getById` | 200 |
| PUT | `/epics/{id}` | `update` | 200 |
| DELETE | `/epics/{id}` | `delete` | 204 |
| GET | `/teams/{teamId}/tickets` | filtered `listByTeam` | 200 |
| POST | `/teams/{teamId}/tickets` | `create` | 201 (+ `Location`) |
| GET | `/tickets/{id}` | `getById` | 200 |
| PUT | `/tickets/{id}` | `update` | 200 |
| PUT | `/tickets/{id}/state` | `changeState` | 200 |
| DELETE | `/tickets/{id}` | `delete` | 204 |
| GET | `/tickets/{ticketId}/comments` | `listByTicket` | 200 |
| POST | `/tickets/{ticketId}/comments` | `add` | 201 (+ `Location`) |
| GET | `/users` | `list` | 200 |
| POST | `/users` | `create` | 201 (+ `Location`) |
| GET | `/users/{id}` | `getById` | 200 |
| PUT | `/users/{id}` | `update` | 200 |
| DELETE | `/users/{id}` | `delete` | 204 |

**Parent ids come from the path, not the body**, on nested creates (`POST /teams/{teamId}/epics`, `POST /teams/{teamId}/tickets`, `POST /tickets/{ticketId}/comments`). The mapper assembles the service command from the path id plus the request body; those request bodies omit the parent id.

## 5. DTOs and mappers

### Request DTOs (validated records)

Carry the same field constraints as the command records (`@NotBlank`, `@Size`, `@Email`, `@NotNull`) so invalid input fails at the HTTP boundary with a per-field `errors` map.

- `CreateTeamRequest(name)`, `RenameTeamRequest(name)`
- `CreateEpicRequest(title, description?)` — `teamId` from path
- `UpdateEpicRequest(title, description?)`
- `CreateTicketRequest(epicId?, type, title, body)` — `teamId` from path; **acting user from the seam, not the body** (§6)
- `UpdateTicketRequest(teamId, epicId?, type, state, title, body)`
- `ChangeStateRequest(state)`
- `AddCommentRequest(body)` — `ticketId` from path; **author from the seam** (§6)
- `CreateUserRequest(email, displayName, password)` — `password` `@Size(min = 8)` per requirements
- `UpdateUserRequest(displayName)`

### Response DTOs (nested summaries)

- `TeamSummary(id, name)`, `TeamResponse(id, name, createdAt, modifiedAt)`
- `EpicSummary(id, title)`, `EpicResponse(id, team: TeamSummary, title, description, createdAt, modifiedAt)`
- `UserSummary(id, displayName)`
- `UserResponse(id, email, displayName, emailVerified, createdAt, modifiedAt)` — **never** exposes `passwordHash`
- `TicketResponse(id, team: TeamSummary, epic: EpicSummary?, type, state, title, body, createdBy: UserSummary, createdAt, modifiedAt)`
- `CommentResponse(id, author: UserSummary, body, createdAt)`

Timestamps serialize as ISO-8601 UTC (`OffsetDateTime`), satisfying the API timestamp requirement.

### Mappers

One MapStruct mapper per aggregate (`componentModel = "spring"`):

- **Entity → response DTO** (outbound). Nested summaries resolve automatically (MapStruct maps `Team`→`TeamSummary`, `Epic`→`EpicSummary`, `User`→`UserSummary`). `List<Entity>` → `List<Response>` for collection endpoints.
- **Request DTO → command record** (inbound), including path-supplied ids as method parameters — e.g. `toCommand(CreateTicketRequest req, UUID teamId, UUID createdById)`.

Mappers never construct entities; the controller passes commands to services, and services build entities.

## 6. Authentication deferral — temporary stand-ins

Authentication (Argon2id hashing, login/logout, email verification, protected endpoints) is a **dedicated next phase**, not part of this one. This phase carries three clearly-marked temporary stand-ins so the API is fully usable now:

1. **Temporary permit-all security** — `spring-boot-starter-security` is on the classpath, so without configuration every endpoint would demand HTTP Basic auth. `SecurityConfig` defines a `SecurityFilterChain` that permits all requests, disables CSRF (stateless JSON API), and disables httpBasic/formLogin. Marked `// TEMPORARY: replaced by the authentication phase`.
2. **User creation stores the password verbatim** — `UserService.create` takes an already-encoded `passwordHash` and stores it as-is. This phase has no hashing, so `UserController` passes the request's plaintext `password` through as the placeholder hash. **This stores plaintext and is temporary**; the auth phase inserts Argon2id encoding before the service call. Marked in code and here. `UserResponse` never returns the stored value.
3. **The acting user comes from a single seam** — creating a ticket or comment must record *who* acted. With no security context yet, that id is supplied by the client. Rather than reading it inline in multiple controllers, a single `CurrentUserProvider` component answers "who is acting?". This phase's implementation reads the id from a temporary `X-Acting-User-Id` request header; the auth phase changes only `CurrentUserProvider` to read `SecurityContextHolder`'s authenticated principal. Keeping the id in a header (not the request body) means the request DTOs are already in their final shape and nothing but `CurrentUserProvider` changes when auth lands. A missing/malformed header this phase yields a 400 via the global handler.

**No seed/mock data.** A fresh database starts empty per requirements; users are created through `POST /users`. Nothing is auto-inserted on startup.

## 7. Enum JSON mapping

The canonical API values are lowercase (`bug`, `feature`, `fix`; `new`, `ready_for_implementation`, `in_progress`, `ready_for_acceptance`, `done`) — exactly the lowercased enum names. Add to `TicketType` and `TicketState`:

- `@JsonValue` method returning `name().toLowerCase()` (serialization)
- `@JsonCreator` static factory accepting the value case-insensitively and rejecting unknown values (deserialization)

A single source of truth (the enum name) drives both directions; an unknown value produces a 400 via the global handler. springdoc documents the lowercase values in the generated schema. The DB continues to persist the uppercase `name()` (unchanged; `@Enumerated(STRING)` is untouched).

## 8. Server-side board filtering

`GET /teams/{teamId}/tickets` accepts three optional query parameters, AND-combined, results ordered `modifiedAt DESC`:

- `type` — one `TicketType` (canonical lowercase value)
- `epicId` — one epic UUID
- `q` — case-insensitive substring over `title`

Implementation:

- Add a filtered query to `TicketRepository` guarding each parameter for null, e.g.:
  ```java
  @Query("""
      select t from Ticket t
      where t.team.id = :teamId
        and (:type is null or t.type = :type)
        and (:epicId is null or t.epic.id = :epicId)
        and (:q is null or lower(t.title) like lower(concat('%', :q, '%')))
      order by t.modifiedAt desc
      """)
  List<Ticket> findByTeamFiltered(UUID teamId, TicketType type, UUID epicId, String q);
  ```
- Add `TicketService.listByTeam(UUID teamId, TicketFilter filter)` where `TicketFilter(TicketType type, UUID epicId, String q)` is a small record; `q` is trimmed and blank→null. The existing no-argument `listByTeam(teamId)` remains.
- `TicketController` binds the query params into a `TicketFilter` and calls the filtered overload.

Combined with the nested-summary responses, the board stays fast and self-describing at the required 100 tickets per team.

## 9. Global exception handling → ProblemDetail

A single `@RestControllerAdvice` (`GlobalExceptionHandler`) extending `ResponseEntityExceptionHandler`, producing `application/problem+json`:

| Exception | Status | Notes |
|---|---|---|
| `NotFoundException` | 404 | `detail` = exception message |
| `ConflictException` | 409 | delete guards, uniqueness |
| `ValidationException` | 400 | business rule (e.g. same-team) |
| `ConstraintViolationException` | 400 | service `@Validated` failures |
| `MethodArgumentNotValidException` | 400 | body `@Valid` failures; adds an `errors` map of field → message |
| `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` | 400 | malformed UUID, unknown enum, unparseable JSON |

The 409 mappings satisfy the requirement that deleting a referenced team or epic returns HTTP 409 Conflict. Every response is `ProblemDetail` with `status`, `title`, `detail`, and `instance` (request path); the field-validation case adds `errors`.

## 10. OpenAPI document

`OpenApiConfig` provides an `OpenAPI` bean with title, version, and description. springdoc serves `/v3/api-docs` (JSON) and Swagger UI. Because auth is deferred, no security scheme is declared this phase; the auth phase adds it. The document is the contract the frontend phase consumes.

## 11. Testing

The mandatory backend-business-flow test already exists at the service layer. This phase adds one **MockMvc API-flow integration test** (full Spring context over Testcontainers PostgreSQL, reusing the existing base) exercising the ticket lifecycle over HTTP and locking in the API contract:

1. `POST /users` → 201, returns id; response has no password field.
2. `POST /teams` → 201; `POST /teams/{teamId}/tickets` with the user id → 201, body shows nested `team`/`createdBy` summaries and lowercase `type`/`state`.
3. `GET /teams/{teamId}/tickets?type=&q=` → filtered, ordered `modifiedAt DESC`.
4. `PUT /tickets/{id}/state` → 200, state changes and persists.
5. Error paths: unknown id → 404 `ProblemDetail`; `DELETE /teams/{id}` while it has a ticket → 409; invalid enum / blank title → 400 with `errors` map.

This satisfies the requirement for an automated test covering at least one API flow.

## 12. Out of scope (this phase)

- Authentication: Argon2id hashing/verification, login/logout, session or bearer-token issuance, protected endpoints, security scheme in OpenAPI. (Next phase.)
- Email-verification flow: SMTP delivery, token generation/redemption/expiry/resend.
- The frontend SPA and any UI concerns.
- Comment edit/delete (stretch feature).
- Pagination (the 100-ticket board requirement is met by filtering + ordering, not paging).
