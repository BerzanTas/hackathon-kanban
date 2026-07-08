import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { Select } from '@/components/Select'
import { TextArea } from '@/components/TextArea'
import { TextField } from '@/components/TextField'
import { TrashIcon } from '@/components/icons'
import { ApiRequestError } from '@/lib/apiClient'
import { useEpics } from '@/features/board/queries'
import {
  STATE_LABELS,
  STATE_ORDER,
  TICKET_TYPES,
} from '@/types/api'
import type { Team, TicketState, TicketType } from '@/types/api'
import {
  useDeleteTicket,
  useTicket,
  useUpdateTicket,
} from './useTicketMutations'
import { CommentsSection } from './CommentsSection'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

export function TicketDetailsModal({
  ticketId,
  teams,
  onClose,
}: {
  ticketId: string
  teams: Team[]
  onClose: () => void
}) {
  const ticketQuery = useTicket(ticketId)
  const update = useUpdateTicket(ticketId)
  const remove = useDeleteTicket(ticketId)

  const [teamId, setTeamId] = useState('')
  const [epicId, setEpicId] = useState('')
  const [type, setType] = useState<TicketType>('feature')
  const [state, setState] = useState<TicketState>('new')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const ticket = ticketQuery.data

  // Seed the form once the ticket loads.
  useEffect(() => {
    if (!ticket) return
    setTeamId(ticket.team.id)
    setEpicId(ticket.epic?.id ?? '')
    setType(ticket.type)
    setState(ticket.state)
    setTitle(ticket.title)
    setBody(ticket.body)
  }, [ticket])

  const epicsQuery = useEpics(teamId || undefined)

  function selectTeam(newTeamId: string) {
    setTeamId(newTeamId)
    // A ticket may only reference an epic from its own team (requirements §6).
    setEpicId('')
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const errors: Record<string, string> = {}
    if (!title.trim()) errors.title = 'Title is required.'
    if (!body.trim()) errors.body = 'Body is required.'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    try {
      await update.mutateAsync({
        teamId,
        epicId: epicId || null,
        type,
        state,
        title: title.trim(),
        body: body.trim(),
      })
      onClose()
    } catch (err) {
      if (err instanceof ApiRequestError && err.problem.errors) {
        setFieldErrors(err.problem.errors)
      } else {
        setFormError('Could not save the ticket. Please try again.')
      }
    }
  }

  async function handleDelete() {
    setFormError(null)
    try {
      await remove.mutateAsync()
      onClose()
    } catch {
      setFormError('Could not delete the ticket. Please try again.')
    }
  }

  return (
    <Modal title="Ticket details" onClose={onClose} size="max-w-2xl">
      {ticketQuery.isLoading ? (
        <p className="text-sm text-slate-500">Loading ticket…</p>
      ) : ticketQuery.isError || !ticket ? (
        <Alert variant="error">Could not load this ticket.</Alert>
      ) : (
        <div className="space-y-6">
          <form className="space-y-5" onSubmit={handleSave} noValidate>
            {formError && <Alert variant="error">{formError}</Alert>}

            <TextField
              label="Title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              error={fieldErrors.title}
            />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Select
                label="Type"
                value={type}
                onChange={(e) => setType(e.target.value as TicketType)}
              >
                {TICKET_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </Select>

              <Select
                label="State"
                value={state}
                onChange={(e) => setState(e.target.value as TicketState)}
              >
                {STATE_ORDER.map((s) => (
                  <option key={s} value={s}>
                    {STATE_LABELS[s]}
                  </option>
                ))}
              </Select>

              <Select
                label="Team"
                value={teamId}
                onChange={(e) => selectTeam(e.target.value)}
              >
                {teams.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </Select>

              <Select
                label="Epic"
                value={epicId}
                onChange={(e) => setEpicId(e.target.value)}
              >
                <option value="">No epic</option>
                {(epicsQuery.data ?? []).map((epic) => (
                  <option key={epic.id} value={epic.id}>
                    {epic.title}
                  </option>
                ))}
              </Select>
            </div>

            <TextArea
              label="Body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              error={fieldErrors.body}
              rows={5}
            />

            <dl className="grid grid-cols-1 gap-x-6 gap-y-1.5 rounded-lg bg-slate-50 p-3.5 text-xs text-slate-500 sm:grid-cols-3">
              <div>
                <dt className="font-medium text-slate-600">Created by</dt>
                <dd>{ticket.createdBy.displayName}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-600">Created</dt>
                <dd>{formatDate(ticket.createdAt)}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-600">Modified</dt>
                <dd>{formatDate(ticket.modifiedAt)}</dd>
              </div>
            </dl>

            <div className="flex items-center justify-between gap-3 pt-1">
              {confirmDelete ? (
                <div className="flex items-center gap-2 text-sm">
                  <span className="text-slate-600">Delete this ticket?</span>
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => void handleDelete()}
                    pending={remove.isPending}
                  >
                    Confirm delete
                  </Button>
                  <button
                    type="button"
                    className="text-sm text-slate-500 hover:text-slate-700"
                    onClick={() => setConfirmDelete(false)}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-red-600 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                >
                  <TrashIcon className="h-4 w-4" />
                  Delete
                </button>
              )}

              <div className="ml-auto flex gap-3">
                <Button type="button" variant="secondary" onClick={onClose}>
                  Cancel
                </Button>
                <Button type="submit" pending={update.isPending}>
                  Save changes
                </Button>
              </div>
            </div>
          </form>

          <CommentsSection ticketId={ticketId} />
        </div>
      )}
    </Modal>
  )
}
