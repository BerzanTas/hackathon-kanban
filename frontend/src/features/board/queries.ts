import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { Epic, Team, Ticket } from '@/types/api'

export const teamsKey = ['teams'] as const
export const epicsKey = (teamId: string) => ['epics', teamId] as const
export const ticketsKey = (teamId: string) => ['tickets', teamId] as const

export function useTeams() {
  return useQuery({
    queryKey: teamsKey,
    queryFn: () => api.get<Team[]>('/teams'),
  })
}

export function useEpics(teamId: string | undefined) {
  return useQuery({
    queryKey: epicsKey(teamId ?? ''),
    queryFn: () => api.get<Epic[]>(`/teams/${teamId}/epics`),
    enabled: !!teamId,
  })
}

export function useTickets(teamId: string | undefined) {
  return useQuery({
    queryKey: ticketsKey(teamId ?? ''),
    queryFn: () => api.get<Ticket[]>(`/teams/${teamId}/tickets`),
    enabled: !!teamId,
  })
}
