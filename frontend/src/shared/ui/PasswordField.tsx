import { useQuery } from '@tanstack/react-query'
import { useId, useState, type InputHTMLAttributes } from 'react'
import { http } from '../api/httpClient'
import { checkPassword, type PasswordPolicy } from '../lib/passwordPolicy'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> & {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  /** New passwords show the live requirement checklist; the login field does not. */
  showRequirements?: boolean
}

/**
 * Password input with a show/hide toggle and, for new passwords, the requirements the backend
 * really enforces (fetched from /api/v1/auth/password-policy, so they cannot drift apart).
 */
export function PasswordField({ label, value, onChange, error, showRequirements = false, ...input }: Props) {
  const id = useId()
  const [visible, setVisible] = useState(false)
  const policy = useQuery({
    queryKey: ['password-policy'],
    queryFn: ({ signal }) => http.get<PasswordPolicy>('/api/v1/auth/password-policy', signal),
    enabled: showRequirements,
    staleTime: Infinity,
  })
  const checks = policy.data ? checkPassword(value, policy.data) : null
  const describedBy = [error ? `${id}-error` : null, showRequirements ? `${id}-rules` : null].filter(Boolean).join(' ')

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-input">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          value={value}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
          onChange={(event) => onChange(event.target.value)}
          {...input}
        />
        <button
          type="button"
          className="button button-secondary button-small"
          aria-controls={id}
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? 'Ocultar' : 'Mostrar'}
          <span className="visually-hidden"> contraseña</span>
        </button>
      </div>
      {showRequirements && (
        <ul className="password-rules" id={`${id}-rules`} aria-live="polite">
          {checks ? (
            <>
              <li className={checks.longEnough ? 'rule-ok' : undefined}>
                {checks.longEnough ? '✓' : '•'} Al menos {policy.data!.minLength} caracteres ({checks.length} escritos).
              </li>
              <li className={checks.withinMaximum ? 'rule-ok' : 'rule-error'}>
                {checks.withinMaximum ? '✓' : '✗'} Como máximo {policy.data!.maxBytes} bytes: cada letra sin tilde, número o
                espacio cuenta 1; letras con tilde, ñ y otros símbolos cuentan 2 o más ({checks.bytes} usados).
              </li>
              <li>No se exigen mayúsculas, números ni símbolos: una frase larga es la opción más segura.</li>
            </>
          ) : (
            <li>{policy.isError ? 'No se pudieron cargar los requisitos; el servidor los validará al guardar.' : 'Cargando requisitos…'}</li>
          )}
        </ul>
      )}
      {error && (
        <p className="field-error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}
