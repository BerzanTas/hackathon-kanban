import { fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test, vi } from 'vitest'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/utils'
import { CreateTicketModal } from './CreateTicketModal'

test('valid submit creates the ticket and closes', async () => {
  let posted = false
  server.use(
    http.post('/teams/:teamId/tickets', async ({ request }) => {
      posted = true
      const body = (await request.json()) as Record<string, unknown>
      return HttpResponse.json({ id: 'new', ...body }, { status: 201 })
    }),
  )
  const onClose = vi.fn()
  renderWithProviders(
    <CreateTicketModal teamId="team-a" epics={[]} onClose={onClose} />,
  )

  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: 'New card' },
  })
  fireEvent.change(screen.getByLabelText('Body'), {
    target: { value: 'Some details' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create ticket/i }))

  await waitFor(() => {
    expect(posted).toBe(true)
    expect(onClose).toHaveBeenCalled()
  })
})

test('empty title blocks submit with no network call', async () => {
  let posted = false
  server.use(
    http.post('/teams/:teamId/tickets', () => {
      posted = true
      return HttpResponse.json({ id: 'new' }, { status: 201 })
    }),
  )
  renderWithProviders(
    <CreateTicketModal teamId="team-a" epics={[]} onClose={vi.fn()} />,
  )

  fireEvent.change(screen.getByLabelText('Body'), {
    target: { value: 'Some details' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create ticket/i }))

  expect(await screen.findByText('Title is required.')).toBeInTheDocument()
  expect(posted).toBe(false)
})

test('server field errors are shown inline', async () => {
  server.use(
    http.post('/teams/:teamId/tickets', () =>
      HttpResponse.json(
        { status: 400, title: 'Bad Request', errors: { title: 'must be unique' } },
        { status: 400 },
      ),
    ),
  )
  renderWithProviders(
    <CreateTicketModal teamId="team-a" epics={[]} onClose={vi.fn()} />,
  )

  fireEvent.change(screen.getByLabelText('Title'), {
    target: { value: 'Dupe' },
  })
  fireEvent.change(screen.getByLabelText('Body'), {
    target: { value: 'Body' },
  })
  fireEvent.click(screen.getByRole('button', { name: /create ticket/i }))

  expect(await screen.findByText('must be unique')).toBeInTheDocument()
})
