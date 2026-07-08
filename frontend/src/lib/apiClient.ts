export type AuthErrorCode = 'bad_credentials' | 'email_not_verified'

export interface ApiError {
  status: number
  title: string
  detail?: string
  type?: string
  instance?: string
  code?: AuthErrorCode
  errors?: Record<string, string>
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly detail?: string
  readonly problem: ApiError

  constructor(problem: ApiError) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiRequestError'
    this.status = problem.status
    this.detail = problem.detail
    this.problem = problem
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const BASE = import.meta.env.VITE_API_BASE ?? ''

function readCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp('(?:^|; )' + name + '=([^;]*)'),
  )
  return match ? decodeURIComponent(match[1]) : null
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const data = await res.json()
    return {
      status: res.status,
      title: data.title ?? res.statusText,
      detail: data.detail,
      type: data.type,
      instance: data.instance,
      code: data.code,
      errors: data.errors,
    }
  } catch {
    return { status: res.status, title: res.statusText }
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (MUTATING.has(method)) {
    const token = readCookie(CSRF_COOKIE)
    if (token) headers[CSRF_HEADER] = token
  }

  const res = await fetch(BASE + path, {
    method,
    credentials: 'include',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!res.ok) {
    throw new ApiRequestError(await parseError(res))
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
