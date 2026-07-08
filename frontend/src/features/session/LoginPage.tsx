import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { ApiRequestError } from '@/lib/apiClient'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
import { useResend } from './useResend'
import { isEmail } from './validation'

interface FieldErrors {
  email?: string
  password?: string
}

export function LoginPage() {
  const { status, login } = useAuth()
  const navigate = useNavigate()
  const resend = useResend()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [unverified, setUnverified] = useState(false)
  const [pending, setPending] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  function validate(): boolean {
    const errors: FieldErrors = {}
    if (!email.trim()) errors.email = 'Email is required.'
    else if (!isEmail(email)) errors.email = 'Enter a valid email address.'
    if (!password) errors.password = 'Password is required.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setUnverified(false)
    if (!validate()) return
    setPending(true)
    try {
      await login({ email: email.trim(), password })
      navigate('/', { replace: true })
    } catch (err) {
      if (
        err instanceof ApiRequestError &&
        err.problem.code === 'email_not_verified'
      ) {
        setUnverified(true)
      } else {
        setFormError('Invalid email or password.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Log in</h1>
      <p className="mt-1 text-slate-500">Use your verified account.</p>

      <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}

        {unverified && (
          <Alert variant="info">
            <p>Your email isn’t verified yet.</p>
            {resend.status === 'sent' ? (
              <p className="mt-2">If that account exists, we’ve sent a new link.</p>
            ) : (
              <button
                type="button"
                className="mt-2 font-medium underline disabled:opacity-60"
                disabled={resend.status === 'sending'}
                onClick={() => resend.resend(email)}
              >
                Resend verification email
              </button>
            )}
          </Alert>
        )}

        <TextField
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          autoComplete="email"
        />
        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          autoComplete="current-password"
        />
        <Button type="submit" pending={pending}>
          Log in
        </Button>
      </form>

      <p className="mt-6 text-sm text-slate-600">
        Need an account?{' '}
        <Link to="/signup" className="font-medium underline">
          Create an account
        </Link>
      </p>
    </main>
  )
}
