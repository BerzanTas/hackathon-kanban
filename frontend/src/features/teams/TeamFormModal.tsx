import { useState } from 'react'
import type { FormEvent } from 'react'
import { Alert } from '@/components/Alert'
import { Button } from '@/components/Button'
import { Modal } from '@/components/Modal'
import { TextField } from '@/components/TextField'
import { ApiRequestError } from '@/lib/apiClient'
import type { Team } from '@/types/api'
import { useCreateTeam, useRenameTeam } from './useTeamMutations'

/** Create a new team (no `team`) or rename an existing one (`team` provided). */
export function TeamFormModal({
  team,
  onClose,
}: {
  team?: Team | null
  onClose: () => void
}) {
  const isEdit = !!team
  const create = useCreateTeam()
  const rename = useRenameTeam(team?.id ?? '')

  const [name, setName] = useState(team?.name ?? '')
  const [nameError, setNameError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setNameError(undefined)
    const trimmed = name.trim()
    if (!trimmed) {
      setNameError('Name is required.')
      return
    }
    if (trimmed.length > 255) {
      setNameError('Name must be 255 characters or fewer.')
      return
    }
    try {
      if (isEdit) await rename.mutateAsync({ name: trimmed })
      else await create.mutateAsync({ name: trimmed })
      onClose()
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.status === 409) {
          setFormError('A team with that name already exists.')
          return
        }
        if (err.problem.errors?.name) {
          setNameError(err.problem.errors.name)
          return
        }
      }
      setFormError('Could not save the team. Please try again.')
    }
  }

  return (
    <Modal title={isEdit ? 'Rename team' : 'New team'} onClose={onClose}>
      <form className="space-y-5" onSubmit={handleSubmit} noValidate>
        {formError && <Alert variant="error">{formError}</Alert>}
        <TextField
          label="Team name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={nameError}
          placeholder="e.g. Platform"
          autoFocus
        />
        <div className="flex justify-end gap-3 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" pending={create.isPending || rename.isPending}>
            {isEdit ? 'Save' : 'Create team'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
