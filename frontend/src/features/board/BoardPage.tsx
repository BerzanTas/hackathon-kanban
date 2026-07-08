import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import type { DragEndEvent } from '@dnd-kit/core'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/Alert'
import { STATE_ORDER } from '@/types/api'
import type { Ticket, TicketState } from '@/types/api'
import { CreateTicketModal } from '@/features/tickets/CreateTicketModal'
import { TicketDetailsModal } from '@/features/tickets/TicketDetailsModal'
import { BoardColumn } from './BoardColumn'
import { BoardToolbar, EMPTY_FILTERS } from './BoardToolbar'
import type { BoardFilters } from './BoardToolbar'
import { useEpics, useTeams, useTickets } from './queries'
import { useChangeState } from './useChangeState'

const NO_TICKETS: Ticket[] = []

function matches(ticket: Ticket, filters: BoardFilters): boolean {
  if (filters.type !== 'all' && ticket.type !== filters.type) return false
  if (filters.epicId !== 'all' && ticket.epic?.id !== filters.epicId)
    return false
  if (
    filters.q.trim() &&
    !ticket.title.toLowerCase().includes(filters.q.trim().toLowerCase())
  )
    return false
  return true
}

export function BoardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [filters, setFilters] = useState<BoardFilters>(EMPTY_FILTERS)
  const [createOpen, setCreateOpen] = useState(false)
  const [openTicket, setOpenTicket] = useState<Ticket | null>(null)
  const [dragError, setDragError] = useState<string | null>(null)

  const teamsQuery = useTeams()
  const teams = teamsQuery.data ?? []

  const paramTeam = searchParams.get('team')
  const selectedTeamId =
    paramTeam && teams.some((t) => t.id === paramTeam)
      ? paramTeam
      : (teams[0]?.id ?? '')

  const epicsQuery = useEpics(selectedTeamId || undefined)
  const ticketsQuery = useTickets(selectedTeamId || undefined)
  const tickets = ticketsQuery.data ?? NO_TICKETS

  const changeState = useChangeState(selectedTeamId, setDragError)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  )

  const columns = useMemo(() => {
    const visible = tickets.filter((t) => matches(t, filters))
    const byState: Record<TicketState, Ticket[]> = {
      new: [],
      ready_for_implementation: [],
      in_progress: [],
      ready_for_acceptance: [],
      done: [],
    }
    for (const ticket of visible) byState[ticket.state].push(ticket)
    for (const state of STATE_ORDER) {
      byState[state].sort(
        (a, b) =>
          new Date(b.modifiedAt).getTime() - new Date(a.modifiedAt).getTime(),
      )
    }
    return byState
  }, [tickets, filters])

  function selectTeam(id: string) {
    setSearchParams({ team: id })
    setFilters(EMPTY_FILTERS)
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (!over) return
    const ticket = tickets.find((t) => t.id === active.id)
    const target = over.id as TicketState
    if (!ticket || ticket.state === target) return
    setDragError(null)
    changeState.mutate({ ticketId: String(active.id), state: target })
  }

  if (teamsQuery.isLoading) {
    return <p className="text-sm text-slate-500">Loading board…</p>
  }

  if (teamsQuery.isError) {
    return <Alert variant="error">Could not load teams. Please refresh.</Alert>
  }

  if (teams.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
        <h1 className="text-lg font-semibold text-slate-900">No teams yet</h1>
        <p className="mx-auto mt-1.5 max-w-sm text-sm text-slate-500">
          Tickets are grouped by team. Create your first team to start building
          a board.
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
    <div className="flex flex-col gap-5">
      <BoardToolbar
        teams={teams}
        selectedTeamId={selectedTeamId}
        onSelectTeam={selectTeam}
        epics={epicsQuery.data ?? []}
        filters={filters}
        onFiltersChange={setFilters}
        onNewTicket={() => setCreateOpen(true)}
      />

      {dragError && <Alert variant="error">{dragError}</Alert>}

      {ticketsQuery.isLoading ? (
        <p className="text-sm text-slate-500">Loading tickets…</p>
      ) : ticketsQuery.isError ? (
        <Alert variant="error">Could not load tickets. Please refresh.</Alert>
      ) : (
        <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
          <div className="flex gap-4 overflow-x-auto pb-4">
            {STATE_ORDER.map((state) => (
              <BoardColumn
                key={state}
                state={state}
                tickets={columns[state]}
                onOpenTicket={setOpenTicket}
              />
            ))}
          </div>
        </DndContext>
      )}

      {createOpen && (
        <CreateTicketModal
          teamId={selectedTeamId}
          epics={epicsQuery.data ?? []}
          onClose={() => setCreateOpen(false)}
        />
      )}

      {openTicket && (
        <TicketDetailsModal
          ticketId={openTicket.id}
          teams={teams}
          onClose={() => setOpenTicket(null)}
        />
      )}
    </div>
  )
}
