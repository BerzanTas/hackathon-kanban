import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { TextArea } from '@/components/TextArea'
import { useAddComment, useComments } from './useTicketMutations'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

export function CommentsSection({ ticketId }: { ticketId: string }) {
  const commentsQuery = useComments(ticketId)
  const addComment = useAddComment(ticketId)
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)

  const comments = commentsQuery.data ?? []

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!body.trim()) {
      setError('Comment cannot be empty.')
      return
    }
    try {
      await addComment.mutateAsync({ body: body.trim() })
      setBody('')
    } catch {
      setError('Could not add the comment. Please try again.')
    }
  }

  return (
    <section className="border-t border-slate-200 pt-5">
      <h3 className="text-sm font-semibold text-slate-900">
        Comments{comments.length > 0 && ` (${comments.length})`}
      </h3>

      <div className="mt-3 space-y-3">
        {commentsQuery.isLoading ? (
          <p className="text-sm text-slate-500">Loading comments…</p>
        ) : comments.length === 0 ? (
          <p className="text-sm text-slate-400">No comments yet.</p>
        ) : (
          comments.map((comment) => (
            <article key={comment.id} className="rounded-lg bg-slate-50 p-3">
              <div className="flex items-baseline justify-between gap-2">
                <span className="text-sm font-medium text-slate-700">
                  {comment.author.displayName}
                </span>
                <time className="text-xs text-slate-400">
                  {formatDate(comment.createdAt)}
                </time>
              </div>
              <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600">
                {comment.body}
              </p>
            </article>
          ))
        )}
      </div>

      <form className="mt-4 space-y-3" onSubmit={handleSubmit} noValidate>
        {error && <Alert variant="error">{error}</Alert>}
        <TextArea
          label="Add a comment"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={3}
          placeholder="Write a comment…"
        />
        <div className="flex justify-end">
          <Button type="submit" pending={addComment.isPending}>
            Add comment
          </Button>
        </div>
      </form>
    </section>
  )
}
