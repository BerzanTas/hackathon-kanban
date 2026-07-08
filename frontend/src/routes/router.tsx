import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { AppLayout } from '@/components/AppLayout'
import { LoginPage } from '@/features/session/LoginPage'
import { SignupPage } from '@/features/session/SignupPage'
import { VerifyPage } from '@/features/session/VerifyPage'
import { BoardPage } from '@/features/board/BoardPage'
import { TeamsPage } from '@/features/teams/TeamsPage'
import { EpicsPage } from '@/features/epics/EpicsPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/verify', element: <VerifyPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <BoardPage /> },
          { path: '/teams', element: <TeamsPage /> },
          { path: '/epics', element: <EpicsPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
