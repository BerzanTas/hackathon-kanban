import { useCallback, useState } from 'react'
import { api } from '@/lib/apiClient'

export type ResendStatus = 'idle' | 'sending' | 'sent' | 'error'

/**
 * Requests a fresh verification email. The backend returns a generic 202 regardless
 * of whether the account exists (no enumeration), so a resolved call always maps to
 * `sent`; only transport/5xx failures map to `error`.
 */
export function useResend() {
  const [status, setStatus] = useState<ResendStatus>('idle')

  const resend = useCallback((email: string) => {
    setStatus('sending')
    api
      .post<void>('/auth/resend', { email: email.trim() })
      .then(() => setStatus('sent'))
      .catch(() => setStatus('error'))
  }, [])

  return { status, resend }
}
