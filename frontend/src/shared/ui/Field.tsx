import {
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import { Icon } from './Icon'

interface FieldShellProps {
  id: string
  label: string
  error?: string
  hint?: string
  children: ReactNode
}

function FieldShell({ id, label, error, hint, children }: FieldShellProps) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && !error && (
        <p className="field-hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field-error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }

/** Labelled input with accessible error/hint wiring (UX-001). */
export function TextField({ label, error, hint, ...input }: TextFieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...input} />
    </FieldShell>
  )
}

/** Search input with a leading magnifier icon; same wiring as TextField. */
export function SearchField({ label, error, hint, ...input }: TextFieldProps) {
  return (
    <div className="search-field">
      <TextField type="search" label={label} error={error} hint={hint} {...input} />
      <Icon name="search" className="search-icon" />
    </div>
  )
}

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string
  error?: string
  hint?: string
  children: ReactNode
}

export function SelectField({ label, error, hint, children, ...select }: SelectFieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <select id={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...select}>
        {children}
      </select>
    </FieldShell>
  )
}

type TextAreaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string; hint?: string }

export function TextAreaField({ label, error, hint, rows = 3, ...textarea }: TextAreaFieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <textarea id={id} rows={rows} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...textarea} />
    </FieldShell>
  )
}

type MoneyFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & { label: string; error?: string; hint?: string }

/**
 * Amount in colones: text input (so "12 500,50" can be typed) with the ₡ sign as a visual prefix.
 * Parse it with `parseMoney` (shared/lib/money.ts); the server validates again.
 */
export function MoneyField({ label, error, hint, ...input }: MoneyFieldProps) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <div className="input-prefix">
        <span aria-hidden="true">₡</span>
        <input
          id={id}
          type="text"
          inputMode="decimal"
          autoComplete="off"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...input}
        />
      </div>
    </FieldShell>
  )
}
