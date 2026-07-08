import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { VerifyPage } from './VerifyPage'
import { server } from '@/test/mocks/server'

function renderVerify(entry: string) {
  const router = createMemoryRouter(
    [
      { path: '/verify', element: <VerifyPage /> },
      { path: '/login', element: <div>LOGIN PAGE</div> },
    ],
    { initialEntries: [entry] },
  )
  return render(<RouterProvider router={router} />)
}

test('verified=true shows success and a continue-to-login action', () => {
  renderVerify('/verify?verified=true')

  expect(
    screen.getByRole('heading', { name: /email verified/i }),
  ).toBeInTheDocument()
  expect(
    screen.getByRole('link', { name: /continue to login/i }),
  ).toBeInTheDocument()
})

test('error=expired shows the expired message and a resend form', () => {
  renderVerify('/verify?error=expired')

  expect(
    screen.getByText('This verification link has expired.'),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /resend/i })).toBeInTheDocument()
})

test('error=invalid shows the invalid message and a resend form', () => {
  renderVerify('/verify?error=invalid')

  expect(
    screen.getByText('This verification link is invalid or already used.'),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /resend/i })).toBeInTheDocument()
})

test('no params shows the neutral instruction', () => {
  renderVerify('/verify')

  expect(
    screen.getByText(/open the verification link from your email/i),
  ).toBeInTheDocument()
})

test('resend on the expired page calls /auth/resend', async () => {
  let resendCalled = false
  server.use(
    http.post('/auth/resend', () => {
      resendCalled = true
      return new HttpResponse(null, { status: 202 })
    }),
  )

  renderVerify('/verify?error=expired')
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: 'jane@example.com' },
  })
  fireEvent.click(screen.getByRole('button', { name: /resend/i }))

  await waitFor(() => {
    expect(resendCalled).toBe(true)
    expect(screen.getByText(/we’ve sent a new link/i)).toBeInTheDocument()
  })
})
