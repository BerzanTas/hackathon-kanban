# Main Page Design — App Shell + Kanban Board

**Date:** 2026-07-08
**Status:** Approved
**Scope:** The authenticated landing screen a user sees after login. Delivers the app shell
(`AppLayout` — top nav + user menu), the primary **Kanban board** for a selected team (five
state columns, cards, drag-and-drop with persistence and rollback, filtering), a **create-ticket
modal**, and a **full ticket-details modal** (view / edit / delete / comments). Builds on the
frontend skeleton and the auth screens (`2026-07-08-auth-screens-design.md`): the typed `api`
client, `useAuth`, react-query (`QueryClientProvider` already wired in `App.tsx`), the router, the
shared `Button`/`TextField`/`Alert`/`icons`, and the Vitest + MSW harness all exist. Follows
`STYLEGUIDE.md`.

## 1. Context

### 1.1 Backend contract (from `src/openapi.json`)

Session-cookie auth; every endpoint below requires authentication. Errors are RFC-7807
`ProblemDetail`; mutating requests send the `X-XSRF-TOKEN` header (handled by the `api` client).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/teams` | — | `200` `TeamResponse[]` | all teams (no membership model) |
| GET | `/teams/{teamId}/epics` | — | `200` `EpicResponse[]` | epics of one team |
| GET | `/teams/{teamId}/tickets` | — (query: `type`, `epicId`, `q`) | `200` `TicketResponse[]` | server filters exist but go unused — see §2 |
| POST | `/teams/{teamId}/tickets` | `CreateTicketRequest` | `201` `TicketResponse` | `{ epicId?, type, title, body }` |
| GET | `/tickets/{id}` | — | `200` `TicketResponse` | |
| PUT | `/tickets/{id}` | `UpdateTicketRequest` | `200` `TicketResponse` | `{ teamId, epicId, type, state, title, body }` |
| PUT | `/tickets/{id}/state` | `ChangeStateRequest` | `200` `TicketResponse` | `{ state }` — used by drag-and-drop |
| DELETE | `/tickets/{id}` | — | `204` | also deletes its comments |
| GET | `/tickets/{ticketId}/comments` | — | `200` `CommentResponse[]` | chronological, oldest first |
| POST | `/tickets/{ticketId}/comments` | `AddCommentRequest` | `201` `CommentResponse` | `{ body }`; does **not** bump ticket `modifiedAt` |

Enums (already in `src/types/api.ts`): `TicketState` = `new | ready_for_implementation |
in_progress | ready_for_acceptance | done` (with `STATE_ORDER` and `STATE_LABELS`);
`TicketType` = `bug | feature | fix` (with `TICKET_TYPES`). Response/request types (`Ticket`,
`Team`, `Epic`, `Comment`, `Create*`, `Update*`, `ChangeStateRequest`, `AddCommentRequest`) all
exist in `src/types/api.ts`.

### 1.2 Requirements traceability (`requirements.docx`)

- **§8 Kanban board:** five columns in workflow order (3.2); card shows title + type, epic
  recommended (3.3); drag between columns persists via API, rollback + error on failure (3.4);
  within-column order = most-recently-modified first (3.2); create + open ticket (3.1, §4);
  filter by type + epic + case-insensitive title substring, AND-combined (3.1); usable at 100
  tickets (client-side filter over one fetch — §2).
- **§6 Tickets / §7 Comments:** create, view all fields (incl. created-by / created-at /
  modified-at), edit type/team/epic/title/body/state, team-change clears epic, delete with
  confirmation, comments oldest-first, adding a comment does not change ticket ordering (§4).
- **§10 Minimum screens:** Kanban board with team selector, ticket create/edit/details view —
  both delivered here. Team management + epic management ship as **stub pages** so the nav works;
  their CRUD is separate specs.
- **§11 Non-functional:** loading / empty / success / error states throughout; no secrets in the
  SPA (cookie session via the skeleton).

## 2. Key decisions

- **Selected team lives in the URL** (`/?team=<id>`), defaulting to the first team returned by
  `GET /teams`. Refresh-safe and shareable; not used as a system-of-record store (§9 compliant).
  Read/written with `useSearchParams`.
- **Filtering is client-side** over the single `GET /teams/{teamId}/tickets` result (no query
  params sent). All tickets must load anyway to populate five columns; filtering in memory is
  instant and trivially fine at 100 tickets, and avoids a refetch per keystroke. The server-side
  `type`/`epicId`/`q` params are intentionally left unused.
- **Data layer is react-query** (already wired). Query keys: `['teams']`,
  `['epics', teamId]`, `['tickets', teamId]`. Mutations invalidate the relevant keys; the
  drag-and-drop `changeState` mutation is optimistic (see §5).
- **Modals are local React state** on the board (not routes) — the board stays a single `/`
  route; create/details modals open/close via component state.
- **Drag-and-drop uses `@dnd-kit/core`** (`DndContext` + `useDraggable` + `useDroppable`). No
  sortable / manual-order package: within-column order is derived from `modifiedAt`, never
  persisted manually.
- **New dependency:** `@dnd-kit/core`.

## 3. App shell — `AppLayout`

`src/components/AppLayout.tsx`. Wraps the authenticated routes (rendered inside the
`ProtectedRoute` branch, containing an `<Outlet />`).

- **Top bar:** white surface, `shadow-sm`, sticky. Left: brand mark (reuse the auth gradient
  logo treatment). Center/left nav: `Board` (`/`), `Teams` (`/teams`), `Epics` (`/epics`) as
  `NavLink`s — active link `text-brand-600` with a subtle indicator, inactive `text-slate-600`.
- **User menu (right):** a button showing `useAuth().user.email` + chevron. Opens a dropdown
  containing **Log out**, which calls `useAuth().logout()` then `navigate('/login', { replace })`.
  Accessible: button `aria-haspopup`/`aria-expanded`; closes on outside-click and Escape; menu
  items focusable.
- **Content:** `<main>` with page padding renders `<Outlet />`.

## 4. Board page

`src/features/board/BoardPage.tsx` (replaces the current stub). Owns the selected team (URL),
filter state, and which modal is open.

- **`BoardToolbar`** (`src/features/board/BoardToolbar.tsx`): team `Select` (writes `?team`),
  type `Select` (All / bug / feature / fix), epic `Select` (All + current team's epics), title
  search `TextField`, and a primary **New ticket** `Button` (opens the create modal). Disabled /
  hidden sensibly when there is no selected team.
- **`BoardColumn`** ×5 (`src/features/board/BoardColumn.tsx`): rendered in `STATE_ORDER`, header
  = `STATE_LABELS[state]` + a count of cards in that column, body = a droppable zone holding the
  filtered, `modifiedAt`-desc-sorted `TicketCard`s. Empty column shows a faint placeholder.
- **`TicketCard`** (`src/features/board/TicketCard.tsx`): draggable; shows title, a type `Badge`,
  and epic title when present. Click (non-drag) opens the details modal for that ticket.
- **Drag-and-drop:** a `DndContext` around the columns. On drop into a *different* column, the
  `changeState` mutation runs (§5). On drop into the same column, no-op.
- **States:** while `teams`/`tickets` load → skeleton columns; query error → error `Alert`;
  **no teams at all** → empty state prompting the user to create a team (links to `/teams`); a
  selected team with no tickets → all five columns empty with hints.
- **Drag failure surface:** a dismissible error `Alert` pinned above the columns (in addition to
  the card rolling back), per §8.

## 5. Data hooks

- **`src/features/board/queries.ts`** — `useTeams()`, `useEpics(teamId)`, `useTickets(teamId)`
  react-query hooks over the `api` client. `useTickets` is enabled only when a `teamId` is set.
- **`src/features/board/useChangeState.ts`** — optimistic state-change mutation:
  1. `onMutate`: cancel in-flight `['tickets', teamId]` fetches, snapshot the cache, and
     optimistically update the moved ticket's `state` (and bump it to the front of its new
     column) in the cache.
  2. `mutationFn`: `PUT /tickets/{id}/state` with `{ state }`.
  3. `onError`: restore the snapshot and raise a board-level error (drives the pinned `Alert`).
  4. `onSettled`: invalidate `['tickets', teamId]` to reconcile canonical `modifiedAt` / order.
- **`src/features/tickets/useTicketMutations.ts`** — `useCreateTicket(teamId)`,
  `useUpdateTicket()`, `useDeleteTicket()`, `useAddComment(ticketId)`. Each invalidates the
  relevant `['tickets', teamId]` (and, for comments, the comment query) on success.

## 6. Ticket modals

New accessible **`Modal`** primitive (`src/components/Modal.tsx`): backdrop, centered panel,
focus trap, Escape to close, `role="dialog"` + `aria-modal`, labelled by its heading.

- **`CreateTicketModal`** (`src/features/tickets/CreateTicketModal.tsx`): fields — type `Select`
  (default `feature`), title `TextField`, body `TextArea`, optional epic `Select` (current
  team's epics). Client validation mirrors the backend: title non-empty (≤255), body non-empty.
  Submit → `POST /teams/{teamId}/tickets` with `{ epicId?, type, title, body }`; on `201`
  invalidate tickets and close. `400` → merge `problem.errors` onto fields; other errors →
  form-level `Alert`.
- **`TicketDetailsModal`** (`src/features/tickets/TicketDetailsModal.tsx`): loads via
  `GET /tickets/{id}` (or seeds from the card and refetches). Shows all fields including
  `createdBy.displayName`, `createdAt`, `modifiedAt`, type, state, epic.
  - **Edit:** type / team / epic / title / body / state controls. **Changing team clears the
    selected epic** and repopulates the epic `Select` from the newly chosen team's epics (§6).
    Save → `PUT /tickets/{id}` with the full `UpdateTicketRequest`
    (`{ teamId, epicId, type, state, title, body }`); invalidate and refresh. Saving unchanged
    values is allowed (backend decides whether `modifiedAt` advances).
  - **Delete:** a confirm step, then `DELETE /tickets/{id}` → `204`, invalidate, close.
  - **Comments:** `GET /tickets/{id}/comments` (oldest first) listing author `displayName` +
    `createdAt` + body; an add-comment `TextArea` + button → `POST` → refresh the comment list
    only (ticket ordering unchanged, per §7).

## 7. Shared components & icons

Added to `src/components/` (token-based, no ad-hoc input styling — per the styleguide):

- **`Modal`** — dialog primitive (above).
- **`Select`** — labelled `<select>` matching `TextField`'s look (label, focus ring, `error`).
- **`TextArea`** — labelled multi-line input matching `TextField`.
- **`Badge`** — small chip for ticket type (and reusable for state). Subtle, token-based tints;
  color used to distinguish, not decorate.
- **`icons.tsx`** — add `ChevronDownIcon`, `PlusIcon`, `SearchIcon`, `LogoutIcon`, `TrashIcon`,
  `CloseIcon`, `UserIcon` in the existing 1.5-stroke `currentColor` style.

## 8. Routing

`src/routes/router.tsx`: the `ProtectedRoute` element gains `AppLayout` as a nested layout so all
authenticated pages share the shell. Children: `/` → `BoardPage`, `/teams` → `TeamsPage` (stub),
`/epics` → `EpicsPage` (stub). Auth routes (`/login`, `/signup`, `/verify`) are unchanged.

Stub pages (`src/features/teams/TeamsPage.tsx`, `src/features/epics/EpicsPage.tsx`) render inside
the shell with a "management coming soon" placeholder so the nav is fully functional; their CRUD
is deferred to separate specs.

## 9. Testing (Vitest + MSW)

Extend `src/test/mocks/handlers.ts` with default handlers for teams, epics, tickets (list /
create / get / update / delete / change-state), and comments (list / add); override per-test with
`server.use(...)`. Pages render inside `MemoryRouter` + `QueryClientProvider` (+ `AuthProvider`
where `useAuth` is used).

- **BoardPage:** renders five columns with `STATE_LABELS`; groups tickets into the correct
  columns; type/epic/title filters narrow the visible cards (AND); a state change persists via
  the change-state handler; a failing change-state rolls the card back and surfaces the error
  `Alert`.
- **CreateTicketModal:** valid submit posts and closes; empty title blocks submit (no network
  call); a `400` surfaces field errors.
- **TicketDetailsModal:** loads and shows all fields; edit saves via `PUT`; changing team clears
  the epic selection; adding a comment posts and appears in the thread.
- **AppLayout:** nav links render; opening the user menu and clicking Log out calls
  `/auth/logout` and redirects to `/login`.

Drag-and-drop is exercised through the change-state mutation (and dnd-kit's keyboard path) rather
than synthetic pointer-drag events.

## 10. Files

**Create:**
- `src/components/AppLayout.tsx`, `Modal.tsx`, `Select.tsx`, `TextArea.tsx`, `Badge.tsx`
- `src/features/board/BoardToolbar.tsx`, `BoardColumn.tsx`, `TicketCard.tsx`, `queries.ts`,
  `useChangeState.ts`
- `src/features/tickets/CreateTicketModal.tsx`, `TicketDetailsModal.tsx`, `useTicketMutations.ts`
- `src/features/teams/TeamsPage.tsx`, `src/features/epics/EpicsPage.tsx` (stubs)
- `src/features/board/BoardPage.test.tsx`, `src/features/tickets/CreateTicketModal.test.tsx`,
  `TicketDetailsModal.test.tsx`, `src/components/AppLayout.test.tsx`

**Modify:**
- `src/features/board/BoardPage.tsx` (replace stub)
- `src/components/icons.tsx` (add icons)
- `src/routes/router.tsx` (AppLayout wrap + `/teams`, `/epics`)
- `src/test/mocks/handlers.ts` (board/ticket/comment handlers)
- `package.json` (add `@dnd-kit/core`)

## 11. Out of scope

- Team CRUD and Epic CRUD (stub pages only here; separate specs).
- Board/list virtualization (stretch).
- Comment edit/delete (stretch).
- Password reset, profile/account screens, OAuth/SSO.
- Persisting manual within-column card order (not required).
