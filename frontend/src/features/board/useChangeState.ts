import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/apiClient'
import type { Ticket, TicketState } from '@/types/api'
import { ticketsKey } from './queries'

interface ChangeStateVars {
  ticketId: string
  state: TicketState
}

/**
 * Optimistic ticket state change for drag-and-drop. Moves the card to the new
 * column (and to the front, since it becomes most-recently-modified), persists
 * via PUT /tickets/{id}/state, rolls back on failure, and reconciles on settle.
 */
export function useChangeState(
  teamId: string,
  onError?: (message: string) => void,
) {
  const queryClient = useQueryClient()
  const key = ticketsKey(teamId)

  return useMutation({
    mutationFn: ({ ticketId, state }: ChangeStateVars) =>
      api.put<Ticket>(`/tickets/${ticketId}/state`, { state }),

    onMutate: async ({ ticketId, state }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Ticket[]>(key)
      if (previous) {
        const moved = previous.find((t) => t.id === ticketId)
        if (moved) {
          const rest = previous.filter((t) => t.id !== ticketId)
          // Front of the list = most-recently-modified within its new column.
          queryClient.setQueryData<Ticket[]>(key, [{ ...moved, state }, ...rest])
        }
      }
      return { previous }
    },

    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
      onError?.('Could not move the ticket. Please try again.')
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: key })
    },
  })
}
