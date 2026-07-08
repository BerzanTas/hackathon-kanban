import { Alert } from './Alert'
import { Button } from './Button'
import { Modal } from './Modal'

/**
 * Reusable confirmation modal. Renders a message, an optional inline error
 * (e.g. a 409 that keeps the dialog open), and Cancel / confirm actions.
 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  onConfirm,
  onClose,
  pending,
  error,
}: {
  title: string
  message: string
  confirmLabel?: string
  onConfirm: () => void
  onClose: () => void
  pending?: boolean
  error?: string | null
}) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      size="max-w-md"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="danger"
            pending={pending}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-slate-600">{message}</p>
        {error && <Alert variant="error">{error}</Alert>}
      </div>
    </Modal>
  )
}
