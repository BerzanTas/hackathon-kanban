# Service Layer Design — Kanban Backend

**Date:** 2026-07-07
**Status:** Approved
**Scope:** Application/business-logic tier — one Spring service per aggregate (User, Team, Epic, Ticket, Comment) wrapping the existing repositories, owning all validation and referential-integrity rules so the database can never reach an inconsistent state. HTTP/controllers, DTOs, JSON enum case-mapping, authentication logic, and the email-verification flow are **out of scope** for this phase.

## 1. Context

The persistence layer (see `2026-07-06-persistence-layer-design.md`) is implemented: JPA entities with **unidirectional** `@ManyToOne` relations, Liquibase-owned schema (`ddl-auto=validate`), UUID application-generated PKs, and Spring Data repositories. Stack is Spring Boot 4.1.0 on Java 25 with Lombok and PostgreSQL 18.

Two persistence-layer facts drive this design:

- **The service layer fully owns `modified_at`.** It must satisfy the requirements that *"saving unchanged values must not advance it"* and *"adding a comment does not change the ticket's `modified_at`."* **Persistence-layer correction (made during this phase):** `modified_at` was originally mapped with Hibernate `@CreationTimestamp`, which makes the column insert-only — Hibernate silently omits it from UPDATE statements, so service-driven changes to it were dropped. On `Team`, `Epic`, `Ticket`, and `User`, `modified_at` is now a plain, updatable column with a `@PrePersist` callback that defaults it on insert (so direct inserts still populate it); `created_at` remains `@CreationTimestamp`. This was verified by an integration test that updates a ticket field and asserts `modified_at` advances.
- **`Ticket.createdBy` and `Comment.author` are NOT NULL FKs to `users`**, but authentication is deferred and a fresh database contains no users. So user rows must be creatable, and the acting user is passed explicitly into ticket/comment creation (a controller will later source it from the security context).

There is **no `@Version`** on any entity: the requirements specify last-write-wins with no optimistic-locking / concurrent-edit detection.

### Decisions taken during brainstorming

- **User writes this phase:** `UserService.create` accepts an **already-encoded `passwordHash` string** and stores it verbatim — the service never handles plaintext. The auth phase later adds a thin hashing wrapper that calls this. No plaintext ever reaches the database.
- **Validation is two-layered:** add `spring-boot-starter-validation`; annotate command records (`@NotBlank`, `@Size`, `@Email`, `@NotNull`) enforced via `@Validated` on service beans for field-level checks, plus explicit Java for cross-entity business rules.
- **Service input:** immutable **command records** per mutating operation, colocated with each service, free of JSON concerns.
- **`changeState` is a dedicated method** on `TicketService`, separate from `update`, to serve the drag-and-drop board path cleanly.
- **`UserService.update` is limited to `displayName`** this phase (email change and password change belong to the auth phase).
- **Ticket `listByTeam` includes most-recently-modified-first ordering now;** advanced type/epic/substring-search filtering stays deferred to the API phase.

## 2. Package structure

Services live in the existing feature packages; one new shared package holds cross-cutting infrastructure:

```
com.berzantas.kanban
├── common   → NEW: NotFoundException, ConflictException, ValidationException, ClockConfig
├── user     → UserService, CreateUserCommand, UpdateUserCommand
├── team     → TeamService, CreateTeamCommand, RenameTeamCommand
├── epic     → EpicService, CreateEpicCommand, UpdateEpicCommand
├── ticket   → TicketService, CreateTicketCommand, UpdateTicketCommand
└── comment  → CommentService, AddCommentCommand
```

Each service depends only on its own repository plus the repositories it must consult for referential checks (e.g. `TeamService` reads `EpicRepository` and `TicketRepository` for its delete guard). No service depends on another service, keeping units independently testable.

## 3. Cross-cutting infrastructure (`common` package)

### Exceptions

Three unchecked exceptions, each extending `RuntimeException` so that a thrown business error triggers automatic Spring transaction rollback (checked exceptions would not):

| Exception | Meaning | Future HTTP mapping |
|---|---|---|
| `NotFoundException` | A referenced entity (by id) does not exist | 404 Not Found |
| `ConflictException` | Uniqueness violation, or a delete blocked by existing references | 409 Conflict |
| `ValidationException` | A business rule violated (e.g. same-team rule) not expressible as a field annotation | 400 Bad Request |

Field-level Bean Validation failures surface as `jakarta.validation.ConstraintViolationException` (from `@Validated`), which the future controller advice also maps to 400. The HTTP mapping itself is implemented in the API phase; this phase only defines and throws the exceptions.

### ClockConfig

A `@Configuration` exposing `@Bean Clock clock()` returning `Clock.systemUTC()`. Services that set `modified_at` inject this `Clock` and call `OffsetDateTime.now(clock)`, making time deterministic in tests.

## 4. Cross-cutting rules (the "no inconsistent state" guarantees)

1. **Transaction boundaries.** Each service is annotated class-level `@Transactional(readOnly = true)`; every mutating method is annotated `@Transactional`. Any business exception (all `RuntimeException`s) rolls the transaction back, so a partially-applied change can never persist.
2. **Two-layer validation.** Command records carry Bean Validation annotations enforced by `@Validated` on the service bean (non-blank title/body/name, `@Email`, password/name length, non-null enums). Cross-entity rules — existence of referenced ids, case-insensitive uniqueness, the same-team rule, delete guards, and dirty-checking — are explicit code inside the method. Client-supplied enum values are validated by `@NotNull` on the typed command field plus the DB `CHECK` backstop.
3. **Double-guarded uniqueness.** Uniqueness (team name, user email) is pre-checked with the repository `existsBy…IgnoreCase` method **and** protected by catching `DataIntegrityViolationException` from the DB `LOWER()` unique index; both paths translate to `ConflictException`. This closes the check-then-insert race.
4. **Service-owned `modified_at` via dirty-check.** Because entities have no `@UpdateTimestamp`, update methods compare each incoming field against the current persisted value. Only if at least one tracked field actually differs are the changes applied and `modifiedAt` set to `OffsetDateTime.now(clock)`. If nothing changed, `modified_at` is left untouched (satisfies "saving unchanged values must not advance it"). `created_at`/`created_by` are never modified.
5. **String normalization.** Email and all names/titles/bodies are trimmed before validation and comparison. Blank-after-trim values fail validation.

## 5. Services & methods

Return types are entities (or `Optional`/`List` of entities); the API phase adds DTO mapping. All ids are `UUID`.

### UserService (`user`)

`passwordHash` is an opaque, already-encoded string; this service performs no hashing.

- `User create(CreateUserCommand)` — command: `email`, `displayName`, `passwordHash`. Trim email; reject duplicate via `existsByEmailIgnoreCase` → `ConflictException` (DB unique index backstop → `ConflictException`); `emailVerified` defaults to `false`.
- `User getById(UUID)` — `NotFoundException` if absent.
- `Optional<User> findByEmail(String)` — trimmed, case-insensitive.
- `List<User> list()`.
- `User update(UUID, UpdateUserCommand)` — command: `displayName`. Dirty-checked; advances `modified_at` only if changed.
- `void delete(UUID)` — user FKs (`ticket.created_by_id`, `comment.author_id`) are NO ACTION, so deleting a user still referenced by any ticket or comment raises a DB error → translated to `ConflictException`.

### TeamService (`team`)

- `Team create(CreateTeamCommand)` — command: `name`. Trimmed non-empty; unique case-insensitive (pre-check + DB backstop) → `ConflictException`.
- `Team getById(UUID)` — `NotFoundException` if absent.
- `List<Team> list()`.
- `Team rename(UUID, RenameTeamCommand)` — command: `name`. Uniqueness enforced excluding self (a `findByNameIgnoreCase` match whose id differs → `ConflictException`); dirty-checked.
- `void delete(UUID)` — **guard:** if `epicRepository.existsByTeamId` **or** `ticketRepository.existsByTeamId` is true → `ConflictException` (cascading team deletion is not allowed). DB `ON DELETE RESTRICT` is the backstop.

### EpicService (`epic`)

- `Epic create(CreateEpicCommand)` — command: `teamId`, `title`, `description?`. Team must exist (`NotFoundException`); title trimmed non-empty. Team is immutable after creation.
- `Epic getById(UUID)` — `NotFoundException` if absent.
- `List<Epic> listByTeam(UUID teamId)`.
- `Epic update(UUID, UpdateEpicCommand)` — command: `title`, `description?`. Team is **not** changeable. Dirty-checked (title and description).
- `void delete(UUID)` — **guard:** if `ticketRepository.existsByEpicId` → `ConflictException`. DB `RESTRICT` backstop.

### TicketService (`ticket`)

- `Ticket create(CreateTicketCommand)` — command: `teamId`, `epicId?`, `type` (non-null `TicketType`), `title`, `body`, `createdById`. Resolves team, `createdBy` user, and (if `epicId` present) epic — each missing reference → `NotFoundException`. **Enforces the same-team rule:** if an epic is given and `epic.team != ticket.team` → `ValidationException`. `state` defaults to `NEW`.
- `Ticket getById(UUID)` — `NotFoundException` if absent.
- `List<Ticket> listByTeam(UUID teamId)` — ordered most-recently-modified first (adds `findByTeamIdOrderByModifiedAtDesc` to `TicketRepository`). Type/epic/title-search filtering is deferred to the API phase.
- `Ticket update(UUID, UpdateTicketCommand)` — command: `teamId`, `epicId?`, `type`, `state`, `title`, `body`. Editable fields: team, epic, type, state, title, body; `createdBy`/`createdAt` are immutable. Resolves the (possibly new) team and epic (missing → `NotFoundException`); re-validates the same-team rule against the **new** team → `ValidationException` on mismatch. A `null` `epicId` clears the epic. Full dirty-check across all six fields governs whether `modified_at` advances.
- `Ticket changeState(UUID, TicketState state)` — dedicated drag-and-drop path; `state` non-null. Advances `modified_at` only if the state actually changes; persists immediately.
- `void delete(UUID)` — comments are removed by the DB `ON DELETE CASCADE` on `comments.ticket_id`.

### CommentService (`comment`)

- `Comment add(AddCommentCommand)` — command: `ticketId`, `authorId`, `body`. Ticket and author must exist (`NotFoundException`); body trimmed non-empty. **Does not touch `ticket.modified_at`** (requirement) — it only inserts a comment row.
- `List<Comment> listByTicket(UUID ticketId)` — oldest-first (`findByTicketIdOrderByCreatedAtAsc`).

Comments are immutable in mandatory scope, so no update method exists; edit/delete is a stretch feature and out of scope.

## 6. Repository additions

Only one new query method is required beyond the persistence phase:

- `TicketRepository.findByTeamIdOrderByModifiedAtDesc(UUID teamId)` — board ordering for `listByTeam`.

All other required queries (`existsByTeamId`, `existsByEpicId`, `existsByNameIgnoreCase`, `findByNameIgnoreCase`, `existsByEmailIgnoreCase`, `findByEmailIgnoreCase`, `findByTicketIdOrderByCreatedAtAsc`, etc.) already exist.

## 7. Build changes

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## 8. Testing

Service-layer business-flow integration tests run against real PostgreSQL via Testcontainers, reusing the existing `AbstractPersistenceIT` base (H2 cannot model the composite FK, `timestamptz`, or `LOWER()` indexes). A fixed `Clock` is supplied where `modified_at` semantics are asserted. Coverage:

1. **Team delete guard** — deleting a team that has an epic or a ticket → `ConflictException`.
2. **Epic delete guard** — deleting an epic referenced by a ticket → `ConflictException`.
3. **Same-team rule** — creating *and* updating a ticket with an epic from a different team → `ValidationException`.
4. **`modified_at` semantics** — an update with unchanged values does not advance `modified_at`; a real field change does; adding a comment does not change the ticket's `modified_at`.
5. **Case-insensitive uniqueness** — duplicate team name and duplicate user email (differing only in case) → `ConflictException`.
6. **Ticket delete cascade** — deleting a ticket removes its comments.

This satisfies the requirement for an automated test covering at least one backend business flow.

## 9. Out of scope (this phase)

- Authentication logic: Argon2id hashing/verification, login/logout, session or bearer-token issuance. (`UserService.create` stores a caller-supplied hash verbatim.)
- Email-verification flow: SMTP delivery, token generation/redemption, 24h expiry, single-use and resend/invalidation logic.
- Controllers / HTTP API, DTOs, JSON enum case-mapping (`bug`, `ready_for_implementation`, …), and the controller advice that maps the exceptions here to status codes.
- Board filtering/search (type, epic, title substring) and pagination.
- Comment edit/delete (stretch feature).
