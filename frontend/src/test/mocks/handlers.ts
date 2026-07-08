import { http, HttpResponse } from 'msw'
import type {
  Comment,
  Epic,
  Team,
  Ticket,
  TicketState,
} from '@/types/api'

const ME = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'qa@example.com',
  displayName: 'QA User',
  emailVerified: true,
}

// ---- Board fixtures (tests override per-case with server.use) ----

export const TEAMS: Team[] = [
  {
    id: 'team-a',
    name: 'Alpha',
    createdAt: '2026-07-01T10:00:00Z',
    modifiedAt: '2026-07-01T10:00:00Z',
  },
  {
    id: 'team-b',
    name: 'Beta',
    createdAt: '2026-07-01T10:00:00Z',
    modifiedAt: '2026-07-01T10:00:00Z',
  },
]

export const EPICS: Epic[] = [
  {
    id: 'epic-1',
    team: { id: 'team-a', name: 'Alpha' },
    title: 'Login flow',
    description: null,
    createdAt: '2026-07-01T10:00:00Z',
    modifiedAt: '2026-07-01T10:00:00Z',
  },
]

function makeTicket(over: Partial<Ticket> & { id: string }): Ticket {
  return {
    team: { id: 'team-a', name: 'Alpha' },
    epic: null,
    type: 'feature',
    state: 'new',
    title: 'A ticket',
    body: 'Body text',
    createdBy: { id: 'u1', displayName: 'QA User' },
    createdAt: '2026-07-02T10:00:00Z',
    modifiedAt: '2026-07-02T10:00:00Z',
    ...over,
  }
}

export const TICKETS: Ticket[] = [
  makeTicket({ id: 't1', title: 'Fix login bug', type: 'bug', state: 'new' }),
  makeTicket({
    id: 't2',
    title: 'Add dark mode',
    type: 'feature',
    state: 'in_progress',
    epic: { id: 'epic-1', title: 'Login flow' },
  }),
  makeTicket({ id: 't3', title: 'Refactor API', type: 'fix', state: 'done' }),
]

export const handlers = [
  // Auth (from the auth-screens work)
  http.get('/auth/me', () => HttpResponse.json(ME)),
  http.post('/auth/login', () => HttpResponse.json(ME)),
  http.post('/auth/signup', () => new HttpResponse(null, { status: 202 })),
  http.post('/auth/resend', () => new HttpResponse(null, { status: 202 })),
  http.post('/auth/logout', () => new HttpResponse(null, { status: 204 })),

  // Board / teams / epics / tickets / comments (defaults)
  http.get('/teams', () => HttpResponse.json(TEAMS)),
  http.get('/teams/:teamId/epics', () => HttpResponse.json(EPICS)),
  http.get('/teams/:teamId/tickets', () => HttpResponse.json(TICKETS)),
  http.post('/teams/:teamId/tickets', async ({ request }) => {
    const body = (await request.json()) as Record<string, unknown>
    return HttpResponse.json(makeTicket({ id: 'new', ...body }), {
      status: 201,
    })
  }),
  http.get('/tickets/:id', ({ params }) =>
    HttpResponse.json(
      TICKETS.find((t) => t.id === params.id) ?? makeTicket({ id: String(params.id) }),
    ),
  ),
  http.put('/tickets/:id', async ({ params, request }) => {
    const body = (await request.json()) as Record<string, unknown>
    return HttpResponse.json(makeTicket({ id: String(params.id), ...body }))
  }),
  http.put('/tickets/:id/state', async ({ params, request }) => {
    const { state } = (await request.json()) as { state: TicketState }
    return HttpResponse.json(makeTicket({ id: String(params.id), state }))
  }),
  http.delete('/tickets/:id', () => new HttpResponse(null, { status: 204 })),
  http.get('/tickets/:id/comments', () =>
    HttpResponse.json([] as Comment[]),
  ),
  http.post('/tickets/:id/comments', async ({ request }) => {
    const { body } = (await request.json()) as { body: string }
    const comment: Comment = {
      id: 'c-new',
      author: { id: 'u1', displayName: 'QA User' },
      body,
      createdAt: '2026-07-03T10:00:00Z',
    }
    return HttpResponse.json(comment, { status: 201 })
  }),
]
