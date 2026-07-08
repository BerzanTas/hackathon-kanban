import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
import { CheckCircleIcon } from '@/components/icons'
import { AuthLayout } from './AuthLayout'
import { useResend } from './useResend'
import { isEmail } from './validation'

function ResendForm() {
  const resend = useResend()
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | undefined>()

  if (resend.status === 'sent') {
    return (
      <Alert variant="info">If that account exists, we’ve sent a new link.</Alert>
    )
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!isEmail(email)) {
      setError('Enter a valid email address.')
      return
    }
    setError(undefined)
    resend.resend(email)
  }

  return (
    <form className="mt-5 space-y-4" onSubmit={handleSubmit} noValidate>
      <TextField
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={error}
        autoComplete="email"
        placeholder="name@example.com"
      />
      <Button type="submit" pending={resend.status === 'sending'} className="w-full">
        Resend
      </Button>
    </form>
  )
}

export function VerifyPage() {
  const [params] = useSearchParams()
  const verified = params.get('verified') === 'true'
  const error = params.get('error')

  if (verified) {
    return (
      <AuthLayout>
        <div className="flex flex-col items-center text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 ring-8 ring-emerald-50/60">
            <CheckCircleIcon className="h-8 w-8" />
          </span>
          <h1 className="mt-5 text-2xl font-bold tracking-tight text-slate-900">
            Email verified
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Your account is ready to use.
          </p>
          <Link
            to="/login"
            className="mt-8 inline-flex w-full items-center justify-center rounded-lg bg-gradient-to-r from-brand-600 to-accent-500 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 hover:shadow-md hover:brightness-110"
          >
            Continue to login
          </Link>
        </div>
      </AuthLayout>
    )
  }

  if (error === 'expired' || error === 'invalid') {
    const message =
      error === 'expired'
        ? 'This verification link has expired.'
        : 'This verification link is invalid or already used.'
    return (
      <AuthLayout>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Email verification
        </h1>
        <div className="mt-4">
          <Alert variant="error">{message}</Alert>
        </div>
        <p className="mt-5 text-sm text-slate-500">
          Enter your email to get a new verification link.
        </p>
        <ResendForm />
        <p className="mt-8 text-center text-sm text-slate-500">
          <Link
            to="/login"
            className="font-semibold text-brand-600 hover:text-brand-700"
          >
            Back to login
          </Link>
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">
        Email verification
      </h1>
      <p className="mt-2 text-sm text-slate-500">
        Open the verification link from your email to activate your account.
      </p>
      <p className="mt-8 text-center text-sm text-slate-500">
        <Link
          to="/login"
          className="font-semibold text-brand-600 hover:text-brand-700"
        >
          Back to login
        </Link>
      </p>
    </AuthLayout>
  )
}
