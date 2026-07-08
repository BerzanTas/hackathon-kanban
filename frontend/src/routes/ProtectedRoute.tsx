import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'

export function ProtectedRoute() {
  const { status } = useAuth()
  if (status === 'loading') {
    return <div className="p-8 text-slate-500">Loading…</div>
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}
