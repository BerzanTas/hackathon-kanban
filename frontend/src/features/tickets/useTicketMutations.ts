import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type {
  AddCommentRequest,
  Comment,
  CreateTicketRequest,
  Ticket,
  UpdateTicketRequest,
} from '@/types/api'

const commentsKey = (ticketId: string) => ['comments', ticketId] as const

// Invalidate every team board (an update may move a ticket between teams).
function invalidateBoards(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ['tickets'] })
}

export function useTicket(ticketId: string | undefined) {
  return useQuery({
    queryKey: ['ticket', ticketId],
    queryFn: () => api.get<Ticket>(`/tickets/${ticketId}`),
    enabled: !!ticketId,
  })
}

export function useCreateTicket(teamId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTicketRequest) =>
      api.post<Ticket>(`/teams/${teamId}/tickets`, body),
    onSuccess: () => invalidateBoards(queryClient),
  })
}

export function useUpdateTicket(ticketId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateTicketRequest) =>
      api.put<Ticket>(`/tickets/${ticketId}`, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(['ticket', ticketId], updated)
      return invalidateBoards(queryClient)
    },
  })
}

export function useDeleteTicket(ticketId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.del<void>(`/tickets/${ticketId}`),
    onSuccess: () => invalidateBoards(queryClient),
  })
}

export function useComments(ticketId: string | undefined) {
  return useQuery({
    queryKey: commentsKey(ticketId ?? ''),
    queryFn: () => api.get<Comment[]>(`/tickets/${ticketId}/comments`),
    enabled: !!ticketId,
  })
}

export function useAddComment(ticketId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AddCommentRequest) =>
      api.post<Comment>(`/tickets/${ticketId}/comments`, body),
    // Comments do not change ticket ordering, so only the thread refreshes.
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: commentsKey(ticketId) }),
  })
}
