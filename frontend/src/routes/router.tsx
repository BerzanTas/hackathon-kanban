import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { LoginPage } from '@/features/session/LoginPage'
import { SignupPage } from '@/features/session/SignupPage'
import { VerifyPage } from '@/features/session/VerifyPage'
import { BoardPage } from '@/features/board/BoardPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/verify', element: <VerifyPage /> },
  {
    element: <ProtectedRoute />,
    children: [{ path: '/', element: <BoardPage /> }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
