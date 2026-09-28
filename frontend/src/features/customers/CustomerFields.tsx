import { TextAreaField, TextField } from '../../shared/ui/Field'
import type { CustomerFormValues } from './customersApi'

interface CustomerFieldsProps {
  values: CustomerFormValues
  errors: Record<string, string>
  /** Prefix of server field names, e.g. "customer." for the create request. */
  errorPrefix?: string
  onChange: (values: CustomerFormValues) => void
  /** The reception form asks for the phone first, so it can hide this field. */
  hidePhone?: boolean
}

/** Customer data fields shared by create, edit and the reception form (FR-CUS-001). */
export function CustomerFields({ values, errors, errorPrefix = '', onChange, hidePhone }: CustomerFieldsProps) {
  const set = (key: keyof CustomerFormValues) => (event: { target: { value: string } }) =>
    onChange({ ...values, [key]: event.target.value })
  // Jakarta errors come prefixed (customer.phone); business ones (PHONE_INVALID) use the bare name.
  const error = (key: string) => errors[`${errorPrefix}${key}`] ?? errors[key]

  return (
    <div className="form-grid">
      <TextField label="Nombre completo" value={values.fullName} maxLength={160} autoComplete="off" error={error('fullName')} onChange={set('fullName')} />
      {!hidePhone && (
        <TextField
          label="Teléfono"
          type="tel"
          value={values.phone}
          maxLength={30}
          autoComplete="off"
          hint="8 dígitos de Costa Rica, o un número internacional con +."
          error={error('phone')}
          onChange={set('phone')}
        />
      )}
      <TextField label="Correo electrónico (opcional)" type="email" value={values.email} maxLength={254} autoComplete="off" error={error('email')} onChange={set('email')} />
      <TextField label="Dirección (opcional)" value={values.address} maxLength={300} autoComplete="off" error={error('address')} onChange={set('address')} />
      <TextAreaField
        label="Notas internas (opcional)"
        rows={2}
        maxLength={1000}
        value={values.internalNotes}
        hint="Solo para el personal. No anotes datos sensibles (cédula, tarjetas, contraseñas)."
        error={error('internalNotes')}
        onChange={set('internalNotes')}
      />
    </div>
  )
}
