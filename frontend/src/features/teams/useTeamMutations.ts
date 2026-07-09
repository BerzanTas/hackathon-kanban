import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import { teamsKey } from '@/features/board/queries'
import type { CreateTeamRequest, RenameTeamRequest, Team } from '@/types/api'

export function useCreateTeam() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTeamRequest) => api.post<Team>('/teams', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsKey }),
  })
}

export function useRenameTeam(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: RenameTeamRequest) => api.put<Team>(`/teams/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: teamsKey })
      // Board cards embed team names; refresh them too.
      queryClient.invalidateQueries({ queryKey: ['tickets'] })
    },
  })
}

export function useDeleteTeam(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.del<void>(`/teams/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsKey }),
  })
}
