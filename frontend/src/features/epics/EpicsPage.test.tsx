import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test } from 'vitest'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/utils'
import type { Epic } from '@/types/api'
import { EpicsPage } from './EpicsPage'

function epic(over: Partial<Epic> & { id: string; title: string }): Epic {
  return {
    team: { id: 'team-a', name: 'Alpha' },
    description: null,
    createdAt: '2026-07-01T10:00:00Z',
    modifiedAt: '2026-07-01T10:00:00Z',
    ...over,
  }
}

// Per-team epics so the selector has an observable effect.
beforeEach(() => {
  server.use(
    http.get('/teams/:teamId/epics', ({ params }) =>
      HttpResponse.json(
        params.teamId === 'team-b'
          ? [epic({ id: 'e-b', title: 'Beta epic' })]
          : [epic({ id: 'epic-1', title: 'Login flow' })],
      ),
    ),
  )
})

test('the team selector drives the listed epics', async () => {
  renderWithProviders(<EpicsPage />)
  expect(await screen.findByText('Login flow')).toBeInTheDocument()

  fireEvent.change(screen.getByLabelText('Team'), {
    target: { value: 'team-b' },
  })

  expect(await screen.findByText('Beta epic')).toBeInTheDocument()
  expect(screen.queryByText('Login flow')).not.toBeInTheDocument()
})

test('creating an epic posts for the selected team', async () => {
  let postedUrl = ''
  let posted: { title: string } | null = null
  server.use(
    http.post('/teams/:teamId/epics', async ({ request, params }) => {
      postedUrl = String(params.teamId)
      posted = (await request.json()) as { title: string }
      return HttpResponse.json(
        {
          id: 'epic-new',
          team: { id: postedUrl, name: 'Alpha' },
          title: posted.title,
          description: null,
          createdAt: '2026-07-05T10:00:00Z',
          modifiedAt: '2026-07-05T10:00:00Z',
        },
        { status: 201 },
      )
    }),
  )

  renderWithProviders(<EpicsPage />)
  await screen.findByText('Login flow')
  fireEvent.click(screen.getByRole('button', { name: /new epic/i }))
  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: 'Billing' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create epic/i }))

  await waitFor(() => {
    expect(postedUrl).toBe('team-a')
    expect(posted?.title).toBe('Billing')
    expect(
      screen.queryByRole('heading', { name: 'New epic' }),
    ).not.toBeInTheDocument()
  })
})

test('editing an epic prefills and PUTs', async () => {
  let put: { title: string } | null = null
  server.use(
    http.put('/epics/:id', async ({ request }) => {
      put = (await request.json()) as { title: string }
      return HttpResponse.json({
        id: 'epic-1',
        team: { id: 'team-a', name: 'Alpha' },
        title: put.title,
        description: null,
        createdAt: '2026-07-01T10:00:00Z',
        modifiedAt: '2026-07-05T10:00:00Z',
      })
    }),
  )

  renderWithProviders(<EpicsPage />)
  await screen.findByText('Login flow')
  const row = screen.getByText('Login flow').closest('li')!
  fireEvent.click(within(row).getByRole('button', { name: 'Edit' }))

  const input = screen.getByLabelText('Title') as HTMLInputElement
  expect(input.value).toBe('Login flow')
  fireEvent.change(input, { target: { value: 'Login & signup' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))

  await waitFor(() => expect(put?.title).toBe('Login & signup'))
})

test('a 409 on delete shows the still-referenced message', async () => {
  server.use(
    http.delete('/epics/:id', () =>
      HttpResponse.json({ status: 409, title: 'Conflict' }, { status: 409 }),
    ),
  )

  renderWithProviders(<EpicsPage />)
  await screen.findByText('Login flow')
  fireEvent.click(screen.getByRole('button', { name: 'Delete Login flow' }))
  fireEvent.click(screen.getByRole('button', { name: 'Delete epic' }))

  expect(
    await screen.findByText(/still referenced by tickets/i),
  ).toBeInTheDocument()
})

test('empty title blocks submit with no network call', async () => {
  let posted = false
  server.use(
    http.post('/teams/:teamId/epics', () => {
      posted = true
      return HttpResponse.json({ id: 'x' }, { status: 201 })
    }),
  )

  renderWithProviders(<EpicsPage />)
  await screen.findByText('Login flow')
  fireEvent.click(screen.getByRole('button', { name: /new epic/i }))
  fireEvent.click(screen.getByRole('button', { name: /create epic/i }))

  expect(await screen.findByText('Title is required.')).toBeInTheDocument()
  expect(posted).toBe(false)
})
