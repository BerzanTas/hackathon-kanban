// Ticket enums — canonical API values (mirror backend TicketState / TicketType).
export type TicketState =
  | 'new'
  | 'ready_for_implementation'
  | 'in_progress'
  | 'ready_for_acceptance'
  | 'done'

export type TicketType = 'bug' | 'feature' | 'fix'

// Board column order (workflow order).
export const STATE_ORDER: readonly TicketState[] = [
  'new',
  'ready_for_implementation',
  'in_progress',
  'ready_for_acceptance',
  'done',
]

// Human-readable labels for the board columns / ticket views.
export const STATE_LABELS: Record<TicketState, string> = {
  new: 'New',
  ready_for_implementation: 'Ready for Implementation',
  in_progress: 'In Progress',
  ready_for_acceptance: 'Ready for Acceptance',
  done: 'Done',
}

export const TICKET_TYPES: readonly TicketType[] = ['bug', 'feature', 'fix']

// ---- Nested summaries (backend *Summary records) ----
export interface TeamSummary {
  id: string
  name: string
}

export interface EpicSummary {
  id: string
  title: string
}

export interface UserSummary {
  id: string
  displayName: string
}

// ---- Responses ----
export interface Me {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
}

export interface Team {
  id: string
  name: string
  createdAt: string // ISO-8601 UTC
  modifiedAt: string
}

export interface Epic {
  id: string
  team: TeamSummary
  title: string
  description: string | null
  createdAt: string
  modifiedAt: string
}

export interface Ticket {
  id: string
  team: TeamSummary
  epic: EpicSummary | null
  type: TicketType
  state: TicketState
  title: string
  body: string
  createdBy: UserSummary
  createdAt: string
  modifiedAt: string
}

export interface Comment {
  id: string
  author: UserSummary
  body: string
  createdAt: string
}

// ---- Requests ----
export interface SignupRequest {
  email: string
  displayName: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface ResendRequest {
  email: string
}

export interface CreateTeamRequest {
  name: string
}

export interface RenameTeamRequest {
  name: string
}

export interface CreateEpicRequest {
  title: string
  description?: string | null
}

export interface UpdateEpicRequest {
  title: string
  description?: string | null
}

export interface CreateTicketRequest {
  epicId?: string | null
  type: TicketType
  title: string
  body: string
}

export interface UpdateTicketRequest {
  teamId: string
  epicId: string | null
  type: TicketType
  state: TicketState
  title: string
  body: string
}

export interface ChangeStateRequest {
  state: TicketState
}

export interface AddCommentRequest {
  body: string
}
