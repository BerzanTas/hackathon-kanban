# Teams & Epics Management Screens — Design

**Date:** 2026-07-08
**Status:** Approved
**Scope:** Replace the `TeamsPage` and `EpicsPage` stubs with working CRUD screens: list teams
and create / rename / delete them; list a team's epics and create / edit / delete them. Builds on
the main-page work (`2026-07-08-main-page-kanban-board-design.md`): the `AppLayout` shell, the
`api` client, react-query (`queries.ts` with `useTeams`/`useEpics`), and the shared
`Modal`/`TextField`/`TextArea`/`Select`/`Button`/`Alert` components all exist. Follows
`STYLEGUIDE.md`.

## 1. Context

### 1.1 Backend contract (`src/openapi.json`)

Session-cookie auth; mutating requests carry `X-XSRF-TOKEN` (handled by the `api` client). Errors
are RFC-7807 `ProblemDetail` (`problem.errors` field map on 400; 409 on conflicts).

| Method | Path | Body | Success | Failure |
|---|---|---|---|---|
| GET | `/teams` | — | `200` `TeamResponse[]` | `401` |
| POST | `/teams` | `CreateTeamRequest` `{ name }` | `201` `TeamResponse` | `400` field errors; `409` duplicate name |
| PUT | `/teams/{id}` | `RenameTeamRequest` `{ name }` | `200` `TeamResponse` | `400`; `409` duplicate name |
| DELETE | `/teams/{id}` | — | `204` | `409` still has tickets or epics |
| GET | `/teams/{teamId}/epics` | — | `200` `EpicResponse[]` | `401`/`404` |
| POST | `/teams/{teamId}/epics` | `CreateEpicRequest` `{ title, description? }` | `201` `EpicResponse` | `400` field errors |
| PUT | `/epics/{id}` | `UpdateEpicRequest` `{ title, description? }` | `200` `EpicResponse` | `400` |
| DELETE | `/epics/{id}` | — | `204` | `409` still referenced by tickets |

`TeamResponse` = `{ id, name, createdAt, modifiedAt }`. `EpicResponse` =
`{ id, team: TeamSummary, title, description, createdAt, modifiedAt }`. Request/response types
already exist in `src/types/api.ts` (`Team`, `Epic`, `CreateTeamRequest`, `RenameTeamRequest`,
`CreateEpicRequest`, `UpdateEpicRequest`).

### 1.2 Requirements traceability (`requirements.docx`)

- **§4 Teams:** view list; create, rename, delete; name non-empty (trimmed), unique
  case-insensitively (backend-enforced, surfaced as 409); a team cannot be deleted while it
  contains tickets or epics — UI shows a clear message, no cascade (409). All verified users
  manage all teams (no membership).
- **§5 Epics:** each epic belongs to exactly one team, chosen at creation and **not changeable**;
  separate CRUD screen; title non-empty (trimmed); optional description; an epic cannot be
  deleted while tickets reference it — UI shows a clear message (409).
- **§9/§11:** all mutations go through the API and persist; loading / empty / success / error
  states shown.

## 2. Key decisions

- **Two dedicated pages** sharing three small primitives (a `ConfirmDialog`, one form modal per
  entity, one mutations hook per entity) — not a generic config-driven CRUD abstraction
  (over-engineered for two entities) and not in-row inline editing (inconsistent with the
  create-via-modal pattern).
- **Epic team selection:** the Epics page has a team selector (reusing the board's `?team=` URL
  param, defaulting to the first team). The New-epic modal creates for the currently selected
  team, shown read-only ("Team: `<name>`"). Editing never changes an epic's team (§5).
- **Delete uses 409-on-attempt, not pre-disabling.** `GET /teams` returns no ticket/epic counts,
  so the button cannot be reliably pre-disabled (the wireframe's disabled-delete hint is not
  achievable without count data). We attempt the delete and render the backend's 409 as a clear
  in-dialog message, satisfying §4/§5's "clear validation message" requirement.
- **Data layer:** react-query mutations invalidate existing query keys so the board and
  create-ticket modal stay in sync automatically.

## 3. Teams page (`/teams`)

Replaces the stub. Owns which modal is open and the record being acted on.

- **Header:** page title + a primary **New team** `Button` (opens `TeamFormModal` empty).
- **List:** `useTeams()` → rows rendered as cards, each showing the team **name** (prominent),
  created/modified dates (secondary), and **Edit** / **Delete** actions.
- **Create / Rename:** `TeamFormModal` — a single name `TextField`. Empty for create; prefilled
  for rename. Client validation: name required (trimmed), ≤255. Submit → `useCreateTeam` (`POST
  /teams`) or `useRenameTeam` (`PUT /teams/{id}`). On **409** → inline `Alert` "A team with that
  name already exists."; on **400** → merge `problem.errors` onto the name field; other errors →
  form-level `Alert`.
- **Delete:** `ConfirmDialog` ("Delete team `<name>`?"). Confirm → `useDeleteTeam` (`DELETE
  /teams/{id}`). On **409** → in-dialog error "This team still contains tickets or epics and
  can't be deleted." (dialog stays open); on success → close + list refreshes.
- **States:** loading text, error `Alert`, empty state ("No teams yet — create your first team").

## 4. Epics page (`/epics`)

Replaces the stub.

- **Team selector** (`Select`) driving `?team=` (default first team). If there are **no teams at
  all**, an empty state prompts creating a team first (link to `/teams`).
- **Header:** page title + **New epic** `Button` (disabled until a team is selected).
- **List:** `useEpics(selectedTeamId)` → rows showing epic **title**, optional **description**
  (truncated), timestamps, and **Edit** / **Delete**.
- **Create / Edit:** `EpicFormModal` — **title** `TextField` (required, ≤255) + **description**
  `TextArea` (optional). Create shows "Team: `<selected team name>`" read-only. Submit →
  `useCreateEpic(teamId)` (`POST /teams/{teamId}/epics`) or `useUpdateEpic` (`PUT /epics/{id}`,
  title + description only). `400` → field errors; other → form `Alert`.
- **Delete:** `ConfirmDialog`; on **409** → "This epic is still referenced by tickets and can't
  be deleted."
- **States:** loading, error, empty-per-team ("No epics for this team yet").

## 5. Shared / data pieces

- **`src/components/ConfirmDialog.tsx`** — reusable confirm modal built on `Modal`: props
  `{ title, message, confirmLabel, onConfirm, onClose, pending?, error? }`. Renders the message,
  an optional inline error `Alert`, and Cancel / confirm (danger) buttons.
- **`src/features/teams/useTeamMutations.ts`** — `useCreateTeam`, `useRenameTeam`,
  `useDeleteTeam`. Success invalidates `['teams']`; rename also invalidates `['tickets']` so
  board card team labels refresh.
- **`src/features/epics/useEpicMutations.ts`** — `useCreateEpic(teamId)`, `useUpdateEpic`,
  `useDeleteEpic`. Success invalidates `['epics', teamId]` (delete/update invalidate the
  `['epics']` prefix since the id-based routes don't carry the team).
- **`src/features/teams/TeamFormModal.tsx`**, **`src/features/epics/EpicFormModal.tsx`** — reuse
  the existing form primitives; no ad-hoc input styling.

## 6. Error handling

- Every mutation is wrapped in try/catch on the `ApiRequestError`. `400` → field errors from
  `problem.errors`; `409` → the entity-specific message (duplicate name for create/rename;
  still-referenced for delete); network/5xx → generic form/dialog `Alert`.
- Submit/confirm buttons use `Button` `pending` and disable during the in-flight request.
- Client validation (non-empty, trimmed, ≤255) runs before submit.

## 7. Testing (Vitest + MSW)

Extend `src/test/mocks/handlers.ts` with default POST/PUT/DELETE handlers for teams and epics;
override per-test with `server.use(...)`. Pages render via `renderWithProviders` (QueryClient +
MemoryRouter).

- **TeamsPage:** list renders; creating a team posts and the row appears; rename posts; delete
  succeeds and the row disappears; delete → `409` shows the still-referenced message and keeps
  the row; create → `409` duplicate shows the duplicate message; empty name blocks submit (no
  network call).
- **EpicsPage:** the team selector drives the listed epics; create posts for the selected team;
  edit posts title/description; delete → `409` shows the referenced message; empty title blocks
  submit.

## 8. Files

**Create:**
- `src/components/ConfirmDialog.tsx`
- `src/features/teams/useTeamMutations.ts`, `src/features/teams/TeamFormModal.tsx`
- `src/features/epics/useEpicMutations.ts`, `src/features/epics/EpicFormModal.tsx`
- `src/features/teams/TeamsPage.test.tsx`, `src/features/epics/EpicsPage.test.tsx`

**Modify:**
- `src/features/teams/TeamsPage.tsx`, `src/features/epics/EpicsPage.tsx` (replace stubs)
- `src/test/mocks/handlers.ts` (team/epic mutation handlers)

## 9. Out of scope

- Board / ticket changes (already delivered).
- Showing per-team ticket/epic counts or pre-disabling delete (no supporting API).
- Reassigning an epic to a different team (forbidden by §5).
- Team ownership / membership / roles (out of scope per requirements §12).
