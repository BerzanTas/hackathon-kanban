import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test } from 'vitest'
import { AuthProvider } from '@/auth/AuthProvider'
import { LoginPage } from './LoginPage'
import { server } from '@/test/mocks/server'

// Every login test needs an unauthenticated session so the redirect guard stays off.
beforeEach(() => {
  server.use(http.get('/auth/me', () => new HttpResponse(null, { status: 401 })))
})

function renderLogin() {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/', element: <div>BOARD PAGE</div> },
      { path: '/signup', element: <div>SIGNUP PAGE</div> },
    ],
    { initialEntries: ['/login'] },
  )
  return render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
}

function fillCredentials(email = 'jane@example.com', password = 'password123') {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: password },
  })
}

test('valid credentials log in and navigate to the board', async () => {
  server.use(
    http.post('/auth/login', () =>
      HttpResponse.json({
        id: '1',
        email: 'jane@example.com',
        displayName: 'Jane',
        emailVerified: true,
      }),
    ),
  )

  renderLogin()
  fillCredentials()
  fireEvent.click(screen.getByRole('button', { name: /log in/i }))

  await waitFor(() =>
    expect(screen.getByText('BOARD PAGE')).toBeInTheDocument(),
  )
})

test('bad credentials show a generic error', async () => {
  server.use(
    http.post(
      '/auth/login',
      () =>
        HttpResponse.json(
          { status: 401, title: 'Unauthorized', code: 'bad_credentials' },
          { status: 401 },
        ),
    ),
  )

  renderLogin()
  fillCredentials()
  fireEvent.click(screen.getByRole('button', { name: /log in/i }))

  await waitFor(() =>
    expect(screen.getByText('Invalid email or password.')).toBeInTheDocument(),
  )
})

test('unverified login offers resend, which calls /auth/resend', async () => {
  let resendCalled = false
  server.use(
    http.post(
      '/auth/login',
      () =>
        HttpResponse.json(
          { status: 403, title: 'Forbidden', code: 'email_not_verified' },
          { status: 403 },
        ),
    ),
    http.post('/auth/resend', () => {
      resendCalled = true
      return new HttpResponse(null, { status: 202 })
    }),
  )

  renderLogin()
  fillCredentials()
  fireEvent.click(screen.getByRole('button', { name: /log in/i }))

  const resendButton = await screen.findByRole('button', {
    name: /resend verification email/i,
  })
  fireEvent.click(resendButton)

  await waitFor(() => {
    expect(resendCalled).toBe(true)
    expect(
      screen.getByText(/we’ve sent a new link/i),
    ).toBeInTheDocument()
  })
})

test('client validation blocks submit when fields are empty', async () => {
  let loginCalled = false
  server.use(
    http.post('/auth/login', () => {
      loginCalled = true
      return new HttpResponse(null, { status: 200 })
    }),
  )

  renderLogin()
  fireEvent.click(screen.getByRole('button', { name: /log in/i }))

  expect(await screen.findByText('Email is required.')).toBeInTheDocument()
  expect(screen.getByText('Password is required.')).toBeInTheDocument()
  expect(loginCalled).toBe(false)
})
