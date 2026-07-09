import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { TextArea } from '@/components/TextArea'
import { TextField } from '@/components/TextField'
import { ApiRequestError } from '@/lib/apiClient'
import type { Epic } from '@/types/api'
import { useCreateEpic, useUpdateEpic } from './useEpicMutations'

/**
 * Create an epic for `teamId` (no `epic`) or edit an existing one (`epic`).
 * A team is fixed at creation and never editable (requirements §5).
 */
export function EpicFormModal({
  epic,
  teamId,
  teamName,
  onClose,
}: {
  epic?: Epic | null
  teamId: string
  teamName: string
  onClose: () => void
}) {
  const isEdit = !!epic
  const create = useCreateEpic(teamId)
  const update = useUpdateEpic(epic?.id ?? '')

  const [title, setTitle] = useState(epic?.title ?? '')
  const [description, setDescription] = useState(epic?.description ?? '')
  const [titleError, setTitleError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setTitleError(undefined)
    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setTitleError('Title is required.')
      return
    }
    if (trimmedTitle.length > 255) {
      setTitleError('Title must be 255 characters or fewer.')
      return
    }
    const body = {
      title: trimmedTitle,
      description: description.trim() || null,
    }
    try {
      if (isEdit) await update.mutateAsync(body)
      else await create.mutateAsync(body)
      onClose()
    } catch (err) {
      if (err instanceof ApiRequestError && err.problem.errors?.title) {
        setTitleError(err.problem.errors.title)
        return
      }
      setFormError('Could not save the epic. Please try again.')
    }
  }

  return (
    <Modal title={isEdit ? 'Edit epic' : 'New epic'} onClose={onClose}>
      <form className="space-y-5" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}

        <div>
          <span className="text-sm font-medium text-slate-700">Team</span>
          <p className="mt-1 rounded-lg bg-slate-50 px-3.5 py-2.5 text-sm text-slate-600">
            {teamName}
          </p>
        </div>

        <TextField
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          error={titleError}
          placeholder="e.g. Onboarding revamp"
          autoFocus
        />

        <TextArea
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={4}
          placeholder="What is this epic about?"
        />

        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" pending={create.isPending || update.isPending}>
            {isEdit ? 'Save' : 'Create epic'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
