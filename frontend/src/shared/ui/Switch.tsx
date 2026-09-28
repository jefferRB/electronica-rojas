import { useId, type ReactNode } from 'react'

interface SwitchProps {
  label: ReactNode
  checked: boolean
  onChange: (checked: boolean) => void
  /** Short explanation under the label. */
  description?: ReactNode
  /** Visible state next to the control ("Activo" / "Desactivado"), so the meaning is not color only. */
  stateLabels?: [on: string, off: string]
  disabled?: boolean
}

/**
 * On/off setting. A native checkbox with role="switch": Space toggles it, the label is associated,
 * and assistive technology announces "on/off" (WAI-ARIA switch pattern).
 */
export function Switch({ label, checked, onChange, description, stateLabels, disabled }: SwitchProps) {
  const id = useId()
  return (
    <div className="switch-row">
      <div className="switch-text">
        <label htmlFor={id} className="switch-label">
          {label}
        </label>
        {description && (
          <p className="field-hint" id={`${id}-hint`}>
            {description}
          </p>
        )}
      </div>
      <span className="switch-control">
        {stateLabels && (
          <span className={`switch-state ${checked ? 'is-on' : ''}`} aria-hidden="true">
            {checked ? stateLabels[0] : stateLabels[1]}
          </span>
        )}
        <input
          id={id}
          type="checkbox"
          role="switch"
          className="switch"
          checked={checked}
          disabled={disabled}
          aria-describedby={description ? `${id}-hint` : undefined}
          onChange={(event) => onChange(event.target.checked)}
        />
      </span>
    </div>
  )
}
