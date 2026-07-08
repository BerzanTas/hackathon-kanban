import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import App from './App'
import { server } from './test/mocks/server'

test('bootstraps the session and renders the board when authenticated', async () => {
  render(<App />)

  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: /new ticket/i }),
    ).toBeInTheDocument(),
  )
  expect(screen.getByText('qa@example.com')).toBeInTheDocument()
})

test('redirects to the login page when unauthenticated', async () => {
  server.use(
    http.get('/auth/me', () => new HttpResponse(null, { status: 401 })),
  )

  render(<App />)

  await waitFor(() =>
    expect(
      screen.getByRole('heading', { name: /log in/i }),
    ).toBeInTheDocument(),
  )
})
