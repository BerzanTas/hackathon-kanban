import { http, HttpResponse } from 'msw'

export const handlers = [
  http.get('/auth/me', () =>
    HttpResponse.json({
      id: '00000000-0000-0000-0000-000000000001',
      email: 'qa@example.com',
      displayName: 'QA User',
      emailVerified: true,
    }),
  ),
]
