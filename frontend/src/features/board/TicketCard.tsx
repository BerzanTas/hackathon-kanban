import { useDraggable } from '@dnd-kit/core'
import { TypeBadge } from '@/components/Badge'
import type { Ticket } from '@/types/api'

export function TicketCard({
  ticket,
  onOpen,
}: {
  ticket: Ticket
  onOpen: (ticket: Ticket) => void
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } =
    useDraggable({ id: ticket.id })

  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      role="button"
      tabIndex={0}
      onClick={() => onOpen(ticket)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onOpen(ticket)
        }
      }}
      className={`cursor-grab touch-none rounded-xl border border-slate-200 bg-white p-3 text-left shadow-sm transition hover:border-slate-300 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
        isDragging ? 'opacity-50' : ''
      }`}
    >
      <p className="text-sm font-medium leading-snug text-slate-900">
        {ticket.title}
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <TypeBadge type={ticket.type} />
        {ticket.epic && (
          <span className="truncate text-xs text-slate-500">
            {ticket.epic.title}
          </span>
        )}
      </div>
    </div>
  )
}
