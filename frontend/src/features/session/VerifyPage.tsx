import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextField } from '@/components/TextField'
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
    <form className="mt-4 flex flex-col gap-3" onSubmit={handleSubmit} noValidate>
      <TextField
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={error}
        autoComplete="email"
      />
      <Button type="submit" pending={resend.status === 'sending'}>
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
      <main className="mx-auto max-w-md p-8 text-center">
        <h1 className="text-2xl font-semibold">Email verified</h1>
        <p className="mt-2 text-slate-600">Your account is ready to use.</p>
        <Link
          to="/login"
          className="mt-6 inline-block rounded bg-slate-900 px-4 py-2 font-medium text-white hover:bg-slate-700"
        >
          Continue to login
        </Link>
      </main>
    )
  }

  if (error === 'expired' || error === 'invalid') {
    const message =
      error === 'expired'
        ? 'This verification link has expired.'
        : 'This verification link is invalid or already used.'
    return (
      <main className="mx-auto max-w-md p-8">
        <h1 className="text-2xl font-semibold">Email verification</h1>
        <div className="mt-4">
          <Alert variant="error">{message}</Alert>
        </div>
        <p className="mt-4 text-slate-600">
          Enter your email to get a new verification link.
        </p>
        <ResendForm />
        <p className="mt-6 text-sm text-slate-600">
          <Link to="/login" className="font-medium underline">
            Back to login
          </Link>
        </p>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-semibold">Email verification</h1>
      <p className="mt-2 text-slate-600">
        Open the verification link from your email to activate your account.
      </p>
      <p className="mt-6 text-sm text-slate-600">
        <Link to="/login" className="font-medium underline">
          Back to login
        </Link>
      </p>
    </main>
  )
}
