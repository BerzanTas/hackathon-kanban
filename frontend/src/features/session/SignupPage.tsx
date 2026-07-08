import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { api, ApiRequestError } from '@/lib/apiClient'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
import { MailIcon } from '@/components/icons'
import type { SignupRequest } from '@/types/api'
import { AuthLayout } from './AuthLayout'
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
      <AuthLayout>
        <div className="flex flex-col items-center text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-brand-50 text-brand-600 ring-8 ring-brand-50/60">
            <MailIcon className="h-7 w-7" />
          </span>
          <h1 className="mt-5 text-2xl font-bold tracking-tight text-slate-900">
            Check your email
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            We sent a verification link to{' '}
            <strong className="font-semibold text-slate-700">
              {submittedEmail}
            </strong>
            . Click it to activate your account, then log in.
          </p>
        </div>
        <div className="mt-8 flex flex-col gap-3">
          {resend.status === 'sent' ? (
            <Alert variant="info">
              If that account exists, we’ve sent a new link.
            </Alert>
          ) : (
            <Button
              type="button"
              pending={resend.status === 'sending'}
              onClick={() => resend.resend(submittedEmail)}
              className="w-full"
            >
              Resend email
            </Button>
          )}
          <Link
            to="/login"
            className="text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
          >
            Back to login
          </Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">
        Create account
      </h1>
      <p className="mt-1.5 text-sm text-slate-500">
        Email verification is required before your first login.
      </p>

      <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}
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
          label="Display name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          error={fieldErrors.displayName}
          autoComplete="name"
          placeholder="Jane Doe"
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
        <Button type="submit" pending={pending} className="w-full">
          Sign up
        </Button>
      </form>

      <p className="mt-8 text-center text-sm text-slate-500">
        Already registered?{' '}
        <Link
          to="/login"
          className="font-semibold text-brand-600 hover:text-brand-700"
        >
          Log in
        </Link>
      </p>
    </AuthLayout>
  )
}
