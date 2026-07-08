import { useDroppable } from '@dnd-kit/core'
import { STATE_LABELS } from '@/types/api'
import type { Ticket, TicketState } from '@/types/api'
import { TicketCard } from './TicketCard'

export function BoardColumn({
  state,
  tickets,
  onOpenTicket,
}: {
  state: TicketState
  tickets: Ticket[]
  onOpenTicket: (ticket: Ticket) => void
}) {
  const { setNodeRef, isOver } = useDroppable({ id: state })

  return (
    <section
      className="flex w-72 shrink-0 flex-col rounded-xl bg-slate-100/70"
      aria-label={STATE_LABELS[state]}
    >
      <header className="flex items-center justify-between px-3 py-2.5">
        <h2 className="text-sm font-semibold text-slate-700">
          {STATE_LABELS[state]}
        </h2>
        <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-600">
          {tickets.length}
        </span>
      </header>
      <div
        ref={setNodeRef}
        className={`flex min-h-24 flex-1 flex-col gap-2 rounded-lg p-2 transition ${
          isOver ? 'bg-brand-50 ring-2 ring-inset ring-brand-300' : ''
        }`}
      >
        {tickets.length === 0 ? (
          <p className="px-1 py-6 text-center text-xs text-slate-400">
            No tickets
          </p>
        ) : (
          tickets.map((ticket) => (
            <TicketCard
              key={ticket.id}
              ticket={ticket}
              onOpen={onOpenTicket}
            />
          ))
        )}
      </div>
    </section>
  )
}
