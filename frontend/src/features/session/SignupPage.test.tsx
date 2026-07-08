import { fireEvent, render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test } from 'vitest'
import { AuthProvider } from '@/auth/AuthProvider'
import { SignupPage } from './SignupPage'
import { server } from '@/test/mocks/server'

beforeEach(() => {
  server.use(http.get('/auth/me', () => new HttpResponse(null, { status: 401 })))
})

function renderSignup() {
  const router = createMemoryRouter(
    [
      { path: '/signup', element: <SignupPage /> },
      { path: '/login', element: <div>LOGIN PAGE</div> },
    ],
    { initialEntries: ['/signup'] },
  )
  return render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
}

function fillForm({
  email = 'jane@example.com',
  displayName = 'Jane',
  password = 'password123',
  confirm = 'password123',
}: Partial<{
  email: string
  displayName: string
  password: string
  confirm: string
}> = {}) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(screen.getByLabelText('Display name'), {
    target: { value: displayName },
  })
  fireEvent.change(screen.getByLabelText('Password'), {
    target: { value: password },
  })
  fireEvent.change(screen.getByLabelText('Confirm password'), {
    target: { value: confirm },
  })
}

test('valid signup shows the check-your-email confirmation', async () => {
  server.use(
    http.post('/auth/signup', () => new HttpResponse(null, { status: 202 })),
  )

  renderSignup()
  fillForm()
  fireEvent.click(screen.getByRole('button', { name: /sign up/i }))

  expect(
    await screen.findByRole('heading', { name: /check your email/i }),
  ).toBeInTheDocument()
  expect(screen.getByText('jane@example.com')).toBeInTheDocument()
})

test('short password is blocked client-side without a network call', async () => {
  let signupCalled = false
  server.use(
    http.post('/auth/signup', () => {
      signupCalled = true
      return new HttpResponse(null, { status: 202 })
    }),
  )

  renderSignup()
  fillForm({ password: 'short', confirm: 'short' })
  fireEvent.click(screen.getByRole('button', { name: /sign up/i }))

  expect(
    await screen.findByText('Password must be at least 8 characters.'),
  ).toBeInTheDocument()
  expect(signupCalled).toBe(false)
})

test('mismatched confirm password is blocked client-side', async () => {
  let signupCalled = false
  server.use(
    http.post('/auth/signup', () => {
      signupCalled = true
      return new HttpResponse(null, { status: 202 })
    }),
  )

  renderSignup()
  fillForm({ confirm: 'different1' })
  fireEvent.click(screen.getByRole('button', { name: /sign up/i }))

  expect(
    await screen.findByText('Passwords do not match.'),
  ).toBeInTheDocument()
  expect(signupCalled).toBe(false)
})

test('duplicate email (409) shows an error alert', async () => {
  server.use(
    http.post(
      '/auth/signup',
      () =>
        HttpResponse.json(
          { status: 409, title: 'Conflict', detail: 'Email already exists' },
          { status: 409 },
        ),
    ),
  )

  renderSignup()
  fillForm()
  fireEvent.click(screen.getByRole('button', { name: /sign up/i }))

  expect(
    await screen.findByText('An account with that email already exists.'),
  ).toBeInTheDocument()
})
