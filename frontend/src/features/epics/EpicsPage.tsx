import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Select } from '@/components/Select'
import { PlusIcon, TrashIcon } from '@/components/icons'
import { ApiRequestError } from '@/lib/apiClient'
import { useEpics, useTeams } from '@/features/board/queries'
import type { Epic } from '@/types/api'
import { EpicFormModal } from './EpicFormModal'
import { useDeleteEpic } from './useEpicMutations'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString()
}

export function EpicsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const teamsQuery = useTeams()
  const teams = teamsQuery.data ?? []

  const paramTeam = searchParams.get('team')
  const selectedTeamId =
    paramTeam && teams.some((t) => t.id === paramTeam)
      ? paramTeam
      : (teams[0]?.id ?? '')
  const selectedTeam = teams.find((t) => t.id === selectedTeamId)

  const epicsQuery = useEpics(selectedTeamId || undefined)
  const epics = epicsQuery.data ?? []

  const [formOpen, setFormOpen] = useState(false)
  const [editingEpic, setEditingEpic] = useState<Epic | null>(null)
  const [deletingEpic, setDeletingEpic] = useState<Epic | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const del = useDeleteEpic(deletingEpic?.id ?? '')

  async function confirmDelete() {
    if (!deletingEpic) return
    setDeleteError(null)
    try {
      await del.mutateAsync()
      setDeletingEpic(null)
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        setDeleteError(
          "This epic is still referenced by tickets and can't be deleted.",
        )
      } else {
        setDeleteError('Could not delete the epic. Please try again.')
      }
    }
  }

  if (teamsQuery.isLoading) {
    return <p className="text-sm text-slate-500">Loading…</p>
  }

  if (teams.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
        <h1 className="text-lg font-semibold text-slate-900">No teams yet</h1>
        <p className="mx-auto mt-1.5 max-w-sm text-sm text-slate-500">
          Epics belong to a team. Create a team first.
        </p>
        <Link
          to="/teams"
          className="mt-4 inline-block font-semibold text-brand-600 hover:text-brand-700"
        >
          Go to Teams →
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Epics
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Group related tickets under an epic within a team.
          </p>
        </div>
        <div className="flex items-end gap-3">
          <Select
            label="Team"
            value={selectedTeamId}
            onChange={(e) => setSearchParams({ team: e.target.value })}
            className="min-w-48"
          >
            {teams.map((team) => (
              <option key={team.id} value={team.id}>
                {team.name}
              </option>
            ))}
          </Select>
          <Button type="button" onClick={() => setFormOpen(true)}>
            <PlusIcon className="h-4 w-4" />
            New epic
          </Button>
        </div>
      </div>

      <div className="mt-6">
        {epicsQuery.isLoading ? (
          <p className="text-sm text-slate-500">Loading epics…</p>
        ) : epicsQuery.isError ? (
          <Alert variant="error">Could not load epics. Please refresh.</Alert>
        ) : epics.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
            <h2 className="text-lg font-semibold text-slate-900">
              No epics for this team yet
            </h2>
            <p className="mx-auto mt-1.5 max-w-sm text-sm text-slate-500">
              Create an epic to group related tickets.
            </p>
          </div>
        ) : (
          <ul className="space-y-2">
            {epics.map((epic) => (
              <li
                key={epic.id}
                className="flex items-center justify-between gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-slate-900">
                    {epic.title}
                  </p>
                  {epic.description && (
                    <p className="mt-0.5 truncate text-sm text-slate-600">
                      {epic.description}
                    </p>
                  )}
                  <p className="mt-0.5 text-xs text-slate-500">
                    Created {formatDate(epic.createdAt)} · Updated{' '}
                    {formatDate(epic.modifiedAt)}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => {
                      setEditingEpic(epic)
                      setFormOpen(true)
                    }}
                  >
                    Edit
                  </Button>
                  <button
                    type="button"
                    onClick={() => {
                      setDeleteError(null)
                      setDeletingEpic(epic)
                    }}
                    aria-label={`Delete ${epic.title}`}
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

      {formOpen && selectedTeam && (
        <EpicFormModal
          epic={editingEpic}
          teamId={selectedTeam.id}
          teamName={selectedTeam.name}
          onClose={() => {
            setFormOpen(false)
            setEditingEpic(null)
          }}
        />
      )}

      {deletingEpic && (
        <ConfirmDialog
          title="Delete epic"
          message={`Delete “${deletingEpic.title}”? This cannot be undone.`}
          confirmLabel="Delete epic"
          pending={del.isPending}
          error={deleteError}
          onConfirm={() => void confirmDelete()}
          onClose={() => setDeletingEpic(null)}
        />
      )}
    </div>
  )
}
