import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiRequestError } from './apiClient'

describe('apiClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    // reset cookies
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  const mockFetch = () => fetch as unknown as ReturnType<typeof vi.fn>

  it('GET parses JSON and sends credentials', async () => {
    mockFetch().mockResolvedValue(
      new Response(JSON.stringify({ id: '1', email: 'a@b.c' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await api.get<{ id: string; email: string }>('/auth/me')

    expect(result).toEqual({ id: '1', email: 'a@b.c' })
    const [, init] = mockFetch().mock.calls[0]
    expect(init.method).toBe('GET')
    expect(init.credentials).toBe('include')
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('POST attaches the CSRF header from the XSRF-TOKEN cookie', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-abc123'
    mockFetch().mockResolvedValue(new Response(null, { status: 204 }))

    await api.post('/teams', { name: 'Alpha' })

    const [, init] = mockFetch().mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('csrf-abc123')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe(JSON.stringify({ name: 'Alpha' }))
  })

  it('throws ApiRequestError with the parsed ProblemDetail on non-2xx', async () => {
    mockFetch().mockResolvedValue(
      new Response(
        JSON.stringify({ title: 'Conflict', detail: 'Name already exists' }),
        { status: 409, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(api.post('/teams', { name: 'dup' })).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 409,
      detail: 'Name already exists',
    })
    await expect(api.post('/teams', { name: 'dup' })).rejects.toBeInstanceOf(
      ApiRequestError,
    )
  })

  it('captures the machine-readable auth error code', async () => {
    mockFetch().mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Forbidden',
          detail: 'Email not verified',
          code: 'email_not_verified',
        }),
        { status: 403, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(api.post('/auth/login', {})).rejects.toMatchObject({
      status: 403,
      problem: { code: 'email_not_verified' },
    })
  })

  it('resolves to undefined for a 204 No Content response', async () => {
    mockFetch().mockResolvedValue(new Response(null, { status: 204 }))
    await expect(api.del('/tickets/1')).resolves.toBeUndefined()
  })
})
