import { http, HttpResponse } from 'msw'

const ME = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'qa@example.com',
  displayName: 'QA User',
  emailVerified: true,
}

export const handlers = [
  http.get('/auth/me', () => HttpResponse.json(ME)),
  http.post('/auth/login', () => HttpResponse.json(ME)),
  http.post('/auth/signup', () => new HttpResponse(null, { status: 202 })),
  http.post('/auth/resend', () => new HttpResponse(null, { status: 202 })),
]
