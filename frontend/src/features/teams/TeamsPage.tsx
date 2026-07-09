import { useState } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { PlusIcon, TrashIcon } from '@/components/icons'
import { ApiRequestError } from '@/lib/apiClient'
import { useTeams } from '@/features/board/queries'
import type { Team } from '@/types/api'
import { TeamFormModal } from './TeamFormModal'
import { useDeleteTeam } from './useTeamMutations'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString()
}

export function TeamsPage() {
  const teamsQuery = useTeams()
  const teams = teamsQuery.data ?? []

  const [formOpen, setFormOpen] = useState(false)
  const [editingTeam, setEditingTeam] = useState<Team | null>(null)
  const [deletingTeam, setDeletingTeam] = useState<Team | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const del = useDeleteTeam(deletingTeam?.id ?? '')

  function openCreate() {
    setEditingTeam(null)
    setFormOpen(true)
  }

  function openEdit(team: Team) {
    setEditingTeam(team)
    setFormOpen(true)
  }

  function openDelete(team: Team) {
    setDeleteError(null)
    setDeletingTeam(team)
  }

  async function confirmDelete() {
    if (!deletingTeam) return
    setDeleteError(null)
    try {
      await del.mutateAsync()
      setDeletingTeam(null)
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setDeleteError(
          "This team still contains tickets or epics and can't be deleted.",
        )
      } else {
        setDeleteError('Could not delete the team. Please try again.')
      }
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Teams
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Create and manage the teams your tickets are grouped by.
          </p>
        </div>
        <Button type="button" onClick={openCreate}>
          <PlusIcon className="h-4 w-4" />
          New team
        </Button>
      </div>

      <div className="mt-6">
        {teamsQuery.isLoading ? (
          <p className="text-sm text-slate-500">Loading teams…</p>
        ) : teamsQuery.isError ? (
          <Alert variant="error">Could not load teams. Please refresh.</Alert>
        ) : teams.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
            <h2 className="text-lg font-semibold text-slate-900">
              No teams yet
            </h2>
            <p className="mx-auto mt-1.5 max-w-sm text-sm text-slate-500">
              Create your first team to start organizing tickets and epics.
            </p>
          </div>
        ) : (
          <ul className="space-y-2">
            {teams.map((team) => (
              <li
                key={team.id}
                className="flex items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-slate-900">
                    {team.name}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    Created {formatDate(team.createdAt)} · Updated{' '}
                    {formatDate(team.modifiedAt)}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => openEdit(team)}
                  >
                    Edit
                  </Button>
                  <button
                    type="button"
                    onClick={() => openDelete(team)}
                    aria-label={`Delete ${team.name}`}
                    className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-sm font-medium text-red-600 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                  >
                    <TrashIcon className="h-4 w-4" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {formOpen && (
        <TeamFormModal
          team={editingTeam}
          onClose={() => {
            setFormOpen(false)
            setEditingTeam(null)
          }}
        />
      )}

      {deletingTeam && (
        <ConfirmDialog
          title="Delete team"
          message={`Delete “${deletingTeam.name}”? This cannot be undone.`}
          confirmLabel="Delete team"
          pending={del.isPending}
          error={deleteError}
          onConfirm={() => void confirmDelete()}
          onClose={() => setDeletingTeam(null)}
        />
      )}
    </div>
  )
}
