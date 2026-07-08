import { Button } from '@/components/Button'
import { Select } from '@/components/Select'
import { PlusIcon, SearchIcon } from '@/components/icons'
import { TICKET_TYPES } from '@/types/api'
import type { Epic, Team, TicketType } from '@/types/api'

export interface BoardFilters {
  type: TicketType | 'all'
  epicId: string | 'all'
  q: string
}

export const EMPTY_FILTERS: BoardFilters = { type: 'all', epicId: 'all', q: '' }

export function BoardToolbar({
  teams,
  selectedTeamId,
  onSelectTeam,
  epics,
  filters,
  onFiltersChange,
  onNewTicket,
}: {
  teams: Team[]
  selectedTeamId: string
  onSelectTeam: (id: string) => void
  epics: Epic[]
  filters: BoardFilters
  onFiltersChange: (filters: BoardFilters) => void
  onNewTicket: () => void
}) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <Select
        label="Team"
        value={selectedTeamId}
        onChange={(e) => onSelectTeam(e.target.value)}
        className="min-w-48"
      >
        {teams.map((team) => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </Select>

      <Select
        label="Type"
        value={filters.type}
        onChange={(e) =>
          onFiltersChange({
            ...filters,
            type: e.target.value as BoardFilters['type'],
          })
        }
      >
        <option value="all">All types</option>
        {TICKET_TYPES.map((type) => (
          <option key={type} value={type}>
            {type}
          </option>
        ))}
      </Select>

      <Select
        label="Epic"
        value={filters.epicId}
        onChange={(e) => onFiltersChange({ ...filters, epicId: e.target.value })}
        className="min-w-48"
      >
        <option value="all">All epics</option>
        {epics.map((epic) => (
          <option key={epic.id} value={epic.id}>
            {epic.title}
          </option>
        ))}
      </Select>

      <div className="flex flex-col gap-1.5">
        <label
          htmlFor="board-search"
          className="text-sm font-medium text-slate-700"
        >
          Search
        </label>
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            id="board-search"
            type="search"
            value={filters.q}
            onChange={(e) => onFiltersChange({ ...filters, q: e.target.value })}
            placeholder="Search titles…"
            className="w-56 rounded-lg border border-slate-300 bg-white py-2.5 pl-9 pr-3.5 text-sm text-slate-900 shadow-sm transition placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/25"
          />
        </div>
      </div>

      <Button type="button" onClick={onNewTicket} className="ml-auto">
        <PlusIcon className="h-4 w-4" />
        New ticket
      </Button>
    </div>
  )
}
