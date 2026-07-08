import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/utils'
import { TeamsPage } from './TeamsPage'

test('renders the team list', async () => {
  renderWithProviders(<TeamsPage />)
  expect(await screen.findByText('Alpha')).toBeInTheDocument()
  expect(screen.getByText('Beta')).toBeInTheDocument()
})

test('creating a team posts and closes the modal', async () => {
  let posted: { name: string } | null = null
  server.use(
    http.post('/teams', async ({ request }) => {
      posted = (await request.json()) as { name: string }
      return HttpResponse.json(
        {
          id: 'team-new',
          name: posted.name,
          createdAt: '2026-07-05T10:00:00Z',
          modifiedAt: '2026-07-05T10:00:00Z',
        },
        { status: 201 },
      )
    }),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: /new team/i }))
  fireEvent.change(screen.getByLabelText('Team name'), {
    target: { value: 'Platform' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create team/i }))

  await waitFor(() => {
    expect(posted?.name).toBe('Platform')
    expect(
      screen.queryByRole('heading', { name: 'New team' }),
    ).not.toBeInTheDocument()
  })
})

test('renaming a team prefills and PUTs', async () => {
  let put: { name: string } | null = null
  server.use(
    http.put('/teams/:id', async ({ request }) => {
      put = (await request.json()) as { name: string }
      return HttpResponse.json({
        id: 'team-a',
        name: put.name,
        createdAt: '2026-07-01T10:00:00Z',
        modifiedAt: '2026-07-05T10:00:00Z',
      })
    }),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  const row = screen.getByText('Alpha').closest('li')!
  fireEvent.click(within(row).getByRole('button', { name: 'Edit' }))

  const input = screen.getByLabelText('Team name') as HTMLInputElement
  expect(input.value).toBe('Alpha')
  fireEvent.change(input, { target: { value: 'Alpha Team' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))

  await waitFor(() => expect(put?.name).toBe('Alpha Team'))
})

test('deleting a team confirms and DELETEs', async () => {
  let deleted = false
  server.use(
    http.delete('/teams/:id', () => {
      deleted = true
      return new HttpResponse(null, { status: 204 })
    }),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: 'Delete Alpha' }))
  fireEvent.click(screen.getByRole('button', { name: 'Delete team' }))

  await waitFor(() => {
    expect(deleted).toBe(true)
    expect(
      screen.queryByRole('heading', { name: 'Delete team' }),
    ).not.toBeInTheDocument()
  })
})

test('a 409 on delete shows the still-referenced message', async () => {
  server.use(
    http.delete('/teams/:id', () =>
      HttpResponse.json({ status: 409, title: 'Conflict' }, { status: 409 }),
    ),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: 'Delete Alpha' }))
  fireEvent.click(screen.getByRole('button', { name: 'Delete team' }))

  expect(
    await screen.findByText(/still contains tickets or epics/i),
  ).toBeInTheDocument()
})

test('a 409 on create shows the duplicate-name message', async () => {
  server.use(
    http.post('/teams', () =>
      HttpResponse.json({ status: 409, title: 'Conflict' }, { status: 409 }),
    ),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: /new team/i }))
  fireEvent.change(screen.getByLabelText('Team name'), {
    target: { value: 'Alpha' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create team/i }))

  expect(await screen.findByText(/already exists/i)).toBeInTheDocument()
})

test('empty name blocks submit with no network call', async () => {
  let posted = false
  server.use(
    http.post('/teams', () => {
      posted = true
      return HttpResponse.json({ id: 'x', name: 'x' }, { status: 201 })
    }),
  )

  renderWithProviders(<TeamsPage />)
  await screen.findByText('Alpha')
  fireEvent.click(screen.getByRole('button', { name: /new team/i }))
  fireEvent.click(screen.getByRole('button', { name: /create team/i }))

  expect(await screen.findByText('Name is required.')).toBeInTheDocument()
  expect(posted).toBe(false)
})
