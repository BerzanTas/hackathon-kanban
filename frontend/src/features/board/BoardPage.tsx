import { useAuth } from '@/auth/useAuth'

export function BoardPage() {
  const { user, logout } = useAuth()
  return (
    <main className="p-8">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Kanban Board</h1>
        <div className="flex items-center gap-4 text-sm text-slate-600">
          <span>{user?.email}</span>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded bg-slate-200 px-3 py-1 hover:bg-slate-300"
          >
            Log out
          </button>
        </div>
      </header>
      <p className="mt-4 text-slate-500">Board coming soon.</p>
    </main>
  )
}
