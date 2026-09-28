import { describe, expect, it } from 'vitest'
import { describeConflict, describeFieldError } from './errors'

describe('error translations', () => {
  it('translates known field and conflict codes', () => {
    expect(describeFieldError('NotBlank', 'must not be blank')).toBe('Este campo es obligatorio.')
    expect(describeFieldError('PASSWORD_TOO_SHORT', 'x')).toBe('La contraseña es demasiado corta.')
    expect(describeConflict('LAST_ADMIN')).toBe('Debe quedar al menos un administrador activo.')
  })

  it('falls back safely for unknown codes', () => {
    expect(describeFieldError('Unknown', 'server text')).toBe('server text')
    expect(describeFieldError(undefined, 'server text')).toBe('server text')
    expect(describeConflict(undefined)).toBeUndefined()
  })
})
