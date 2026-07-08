import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { CreateEpicRequest, Epic, UpdateEpicRequest } from '@/types/api'

// Invalidate the whole epics prefix; the id-based routes don't carry the team.
function invalidateEpics(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ['epics'] })
}

export function useCreateEpic(teamId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateEpicRequest) =>
      api.post<Epic>(`/teams/${teamId}/epics`, body),
    onSuccess: () => invalidateEpics(queryClient),
  })
}

export function useUpdateEpic(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateEpicRequest) => api.put<Epic>(`/epics/${id}`, body),
    onSuccess: () => invalidateEpics(queryClient),
  })
}

export function useDeleteEpic(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.del<void>(`/epics/${id}`),
    onSuccess: () => invalidateEpics(queryClient),
  })
}
