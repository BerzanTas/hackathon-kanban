import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { Select } from '@/components/Select'
import { TextArea } from '@/components/TextArea'
import { TextField } from '@/components/TextField'
import { ApiRequestError } from '@/lib/apiClient'
import { TICKET_TYPES } from '@/types/api'
import type { Epic, TicketType } from '@/types/api'
import { useCreateTicket } from './useTicketMutations'

interface FieldErrors {
  title?: string
  body?: string
}

export function CreateTicketModal({
  teamId,
  epics,
  onClose,
}: {
  teamId: string
  epics: Epic[]
  onClose: () => void
}) {
  const create = useCreateTicket(teamId)
  const [type, setType] = useState<TicketType>('feature')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [epicId, setEpicId] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  function validate(): boolean {
    const errors: FieldErrors = {}
    if (!title.trim()) errors.title = 'Title is required.'
    else if (title.trim().length > 255)
      errors.title = 'Title must be 255 characters or fewer.'
    if (!body.trim()) errors.body = 'Body is required.'
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!validate()) return
    try {
      await create.mutateAsync({
        type,
        title: title.trim(),
        body: body.trim(),
        epicId: epicId || null,
      })
      onClose()
    } catch (err) {
      if (err instanceof ApiRequestError && err.problem.errors) {
        setFieldErrors(err.problem.errors)
      } else {
        setFormError('Could not create the ticket. Please try again.')
      }
    }
  }

  return (
    <Modal title="New ticket" onClose={onClose}>
      <form className="space-y-5" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Type"
          value={type}
          onChange={(e) => setType(e.target.value as TicketType)}
        >
          {TICKET_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </Select>

        <TextField
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={fieldErrors.title}
          placeholder="Short summary"
        />

        <TextArea
          label="Body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          error={fieldErrors.body}
          rows={5}
          placeholder="Describe the ticket…"
        />

        <Select
          label="Epic (optional)"
          value={epicId}
          onChange={(e) => setEpicId(e.target.value)}
        >
          <option value="">No epic</option>
          {epics.map((epic) => (
            <option key={epic.id} value={epic.id}>
              {epic.title}
            </option>
          ))}
        </Select>

        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" pending={create.isPending}>
            Create ticket
          </Button>
        </div>
      </form>
    </Modal>
  )
}
