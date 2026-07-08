import { QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { AuthProvider } from '@/auth/AuthProvider'
import { server } from '@/test/mocks/server'
import { makeClient } from '@/test/utils'
import { AppLayout } from './AppLayout'

function renderShell() {
  const router = createMemoryRouter(
    [
      {
        element: <AppLayout />,
        children: [{ path: '/', element: <div>BOARD CONTENT</div> }],
      },
      { path: '/login', element: <div>LOGIN PAGE</div> },
    ],
    { initialEntries: ['/'] },
  )
  return render(
    <QueryClientProvider client={makeClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

test('renders nav links and the routed page', async () => {
  renderShell()
  expect(await screen.findByText('BOARD CONTENT')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Board' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Teams' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Epics' })).toBeInTheDocument()
})

test('the user menu logs out and redirects to login', async () => {
  let loggedOut = false
  server.use(
    http.post('/auth/logout', () => {
      loggedOut = true
      return new HttpResponse(null, { status: 204 })
    }),
  )

  renderShell()
  // Wait for auth to resolve so the email button renders.
  await screen.findByText('qa@example.com')

  fireEvent.click(screen.getByRole('button', { name: /qa@example.com/i }))
  fireEvent.click(screen.getByRole('menuitem', { name: /log out/i }))

  await waitFor(() => {
    expect(loggedOut).toBe(true)
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument()
  })
})
