import { QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { createElement } from 'react'
import type { ReactNode } from 'react'
import { expect, test, vi } from 'vitest'
import { server } from '@/test/mocks/server'
import { makeClient } from '@/test/utils'
import type { Ticket } from '@/types/api'
import { ticketsKey } from './queries'
import { useChangeState } from './useChangeState'

function seed(): Ticket[] {
  return [
    {
      id: 't1',
      team: { id: 'team-a', name: 'Alpha' },
      epic: null,
      type: 'bug',
      state: 'new',
      title: 'Fix login bug',
      body: 'x',
      createdBy: { id: 'u1', displayName: 'QA' },
      createdAt: '2026-07-02T10:00:00Z',
      modifiedAt: '2026-07-02T10:00:00Z',
    },
  ]
}

test('optimistically moves the card and persists on success', async () => {
  const client = makeClient()
  client.setQueryData(ticketsKey('team-a'), seed())
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children)

  const { result } = renderHook(() => useChangeState('team-a'), { wrapper })

  result.current.mutate({ ticketId: 't1', state: 'in_progress' })

  // Optimistic update lands synchronously in the cache.
  await waitFor(() => {
    const cached = client.getQueryData<Ticket[]>(ticketsKey('team-a'))
    expect(cached?.[0].state).toBe('in_progress')
  })
  await waitFor(() => expect(result.current.isSuccess).toBe(true))
})

test('rolls back and reports an error when the request fails', async () => {
  server.use(
    http.put('/tickets/:id/state', () => new HttpResponse(null, { status: 500 })),
  )
  const client = makeClient()
  client.setQueryData(ticketsKey('team-a'), seed())
  const onError = vi.fn()
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children)

  const { result } = renderHook(() => useChangeState('team-a', onError), {
    wrapper,
  })

  result.current.mutate({ ticketId: 't1', state: 'done' })

  await waitFor(() => expect(onError).toHaveBeenCalled())
  const cached = client.getQueryData<Ticket[]>(ticketsKey('team-a'))
  expect(cached?.[0].state).toBe('new')
})
