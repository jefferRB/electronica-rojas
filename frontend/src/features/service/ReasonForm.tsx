import { useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { Alert } from '../../shared/ui/Alert'
import { TextAreaField } from '../../shared/ui/Field'

interface ReasonFormProps {
  title: string
  explanation?: string
  label?: string
  confirmLabel: string
  pending: boolean
  error: unknown
  onSubmit: (reason: string) => void
  onCancel: () => void
}

/** A decision that must say why (reject, cancel): one required reason, then confirm. */
export function ReasonForm({ title, explanation, label = 'Motivo', confirmLabel, pending, error, onSubmit, onCancel }: ReasonFormProps) {
  const [reason, setReason] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit(reason.trim())
  }

  return (
    <form className="card card-highlight card-danger" onSubmit={handleSubmit} noValidate aria-label={title}>
      <h2>{title}</h2>
      {explanation && <p>{explanation}</p>}
      {error != null && <Alert tone="error">{describeError(error)}</Alert>}
      <TextAreaField
        label={label}
        rows={2}
        maxLength={500}
        value={reason}
        autoFocus
        error={fieldErrorsOf(error).reason}
        onChange={(event) => setReason(event.target.value)}
      />
      <div className="form-actions">
        <button type="submit" className="button button-danger-solid" disabled={pending || !reason.trim()}>
          {pending ? 'Guardando…' : confirmLabel}
        </button>
        <button type="button" className="button button-secondary" disabled={pending} onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}
