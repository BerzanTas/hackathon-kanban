import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { ApiRequestError } from '@/lib/apiClient'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
import { AuthLayout } from './AuthLayout'
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
    <AuthLayout>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">Log in</h1>
      <p className="mt-1.5 text-sm text-slate-500">
        Welcome back — sign in to your account.
      </p>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}

        {unverified && (
          <Alert variant="info">
            <p className="font-medium">Your email isn’t verified yet.</p>
            {resend.status === 'sent' ? (
              <p className="mt-1">If that account exists, we’ve sent a new link.</p>
            ) : (
              <button
                type="button"
                className="mt-1.5 font-semibold text-brand-700 underline underline-offset-2 hover:text-brand-800 disabled:opacity-60"
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
          placeholder="name@example.com"
        />
        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          autoComplete="current-password"
        />
        <Button type="submit" pending={pending} className="w-full">
          Log in
        </Button>
      </form>

      <p className="mt-8 text-center text-sm text-slate-500">
        Need an account?{' '}
        <Link
          to="/signup"
          className="font-semibold text-brand-600 hover:text-brand-700"
        >
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
