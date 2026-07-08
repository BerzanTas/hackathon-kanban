import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { api, ApiRequestError } from '@/lib/apiClient'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
import type { SignupRequest } from '@/types/api'
import { useResend } from './useResend'
import { isEmail, minLength } from './validation'

interface FieldErrors {
  email?: string
  displayName?: string
  password?: string
  confirmPassword?: string
}

export function SignupPage() {
  const { status } = useAuth()
  const resend = useResend()

  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  function validate(): boolean {
    const errors: FieldErrors = {}
    if (!email.trim()) errors.email = 'Email is required.'
    else if (!isEmail(email)) errors.email = 'Enter a valid email address.'
    if (!displayName.trim()) errors.displayName = 'Display name is required.'
    if (!password) errors.password = 'Password is required.'
    else if (!minLength(password, 8))
      errors.password = 'Password must be at least 8 characters.'
    if (confirmPassword !== password)
      errors.confirmPassword = 'Passwords do not match.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return
    setPending(true)
    const body: SignupRequest = {
      email: email.trim(),
      displayName: displayName.trim(),
      password,
    }
    try {
      await api.post<void>('/auth/signup', body)
      setSubmittedEmail(body.email)
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.status === 409) {
          setFormError('An account with that email already exists.')
        } else if (err.status === 400 && err.problem.errors) {
          setFieldErrors(err.problem.errors as FieldErrors)
        } else {
          setFormError(err.detail ?? 'Something went wrong. Please try again.')
        }
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setPending(false)
    }
  }

  if (submittedEmail) {
    return (
      <main className="mx-auto max-w-md p-8">
        <h1 className="text-2xl font-semibold">Check your email</h1>
        <p className="mt-2 text-slate-600">
          We sent a verification link to <strong>{submittedEmail}</strong>. Click
          it to activate your account, then log in.
        </p>
        <div className="mt-6 flex flex-col gap-3">
          {resend.status === 'sent' ? (
            <Alert variant="info">
              If that account exists, we’ve sent a new link.
            </Alert>
          ) : (
            <Button
              type="button"
              pending={resend.status === 'sending'}
              onClick={() => resend.resend(submittedEmail)}
            >
              Resend email
            </Button>
          )}
          <Link to="/login" className="text-sm font-medium underline">
            Back to login
          </Link>
        </div>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Create account</h1>
      <p className="mt-1 text-slate-500">Email verification is required.</p>

      <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}
        <TextField
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          autoComplete="email"
        />
        <TextField
          label="Display name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          error={fieldErrors.displayName}
          autoComplete="name"
        />
        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          placeholder="Minimum 8 characters"
          autoComplete="new-password"
        />
        <TextField
          label="Confirm password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          error={fieldErrors.confirmPassword}
          autoComplete="new-password"
        />
        <Button type="submit" pending={pending}>
          Sign up
        </Button>
      </form>

      <p className="mt-6 text-sm text-slate-600">
        Already registered?{' '}
        <Link to="/login" className="font-medium underline">
          Log in
        </Link>
      </p>
    </main>
  )
}
