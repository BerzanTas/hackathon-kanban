import { fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test, vi } from 'vitest'
import { server } from '@/test/mocks/server'
import { TEAMS, TICKETS } from '@/test/mocks/handlers'
import { renderWithProviders } from '@/test/utils'
import type { Comment } from '@/types/api'
import { TicketDetailsModal } from './TicketDetailsModal'

test('loads and shows the ticket fields', async () => {
  renderWithProviders(
    <TicketDetailsModal ticketId="t2" teams={TEAMS} onClose={vi.fn()} />,
  )
  const title = (await screen.findByLabelText('Title')) as HTMLInputElement
  expect(title.value).toBe('Add dark mode')
  expect(screen.getByText('QA User')).toBeInTheDocument()
})

test('editing saves via PUT and closes', async () => {
  let putBody: Record<string, unknown> | null = null
  server.use(
    http.put('/tickets/:id', async ({ request }) => {
      putBody = (await request.json()) as Record<string, unknown>
      const base = TICKETS.find((t) => t.id === 't2')
      return HttpResponse.json({ ...base, ...putBody })
    }),
  )
  const onClose = vi.fn()
  renderWithProviders(
    <TicketDetailsModal ticketId="t2" teams={TEAMS} onClose={onClose} />,
  )

  const title = (await screen.findByLabelText('Title')) as HTMLInputElement
  fireEvent.change(title, { target: { value: 'Add dark theme' } })
  fireEvent.click(screen.getByRole('button', { name: /save changes/i }))

  await waitFor(() => {
    expect(putBody?.title).toBe('Add dark theme')
    expect(onClose).toHaveBeenCalled()
  })
})

test('changing team clears the selected epic', async () => {
  renderWithProviders(
    <TicketDetailsModal ticketId="t2" teams={TEAMS} onClose={vi.fn()} />,
  )
  const epic = (await screen.findByLabelText('Epic')) as HTMLSelectElement
  await waitFor(() => expect(epic.value).toBe('epic-1'))

  fireEvent.change(screen.getByLabelText('Team'), {
    target: { value: 'team-b' },
  })
  expect((screen.getByLabelText('Epic') as HTMLSelectElement).value).toBe('')
})

test('adding a comment shows it in the thread', async () => {
  const store: Comment[] = []
  server.use(
    http.get('/tickets/:id/comments', () => HttpResponse.json(store)),
    http.post('/tickets/:id/comments', async ({ request }) => {
      const { body } = (await request.json()) as { body: string }
      const comment: Comment = {
        id: `c${store.length}`,
        author: { id: 'u1', displayName: 'QA User' },
        body,
        createdAt: '2026-07-03T10:00:00Z',
      }
      store.push(comment)
      return HttpResponse.json(comment, { status: 201 })
    }),
  )

  renderWithProviders(
    <TicketDetailsModal ticketId="t2" teams={TEAMS} onClose={vi.fn()} />,
  )
  await screen.findByLabelText('Title')

  fireEvent.change(screen.getByLabelText('Add a comment'), {
    target: { value: 'Looks good to me' },
  })
  fireEvent.click(screen.getByRole('button', { name: /add comment/i }))

  expect(await screen.findByText('Looks good to me')).toBeInTheDocument()
})
