import { describeConflict, describeFieldError } from '../i18n/errors'
import { ApiError, NetworkError } from './httpClient'

/** User-facing Spanish message per failure kind (FR-NAV-003: distinguishable states). */
export function describeError(error: unknown): string {
  if (error instanceof NetworkError) return 'Sin conexión con el servidor. Verifica que el backend esté en ejecución.'
  if (!(error instanceof ApiError)) return 'Ocurrió un error inesperado.'
  switch (error.status) {
    case 400:
      return 'Revisa los campos marcados.'
    case 401:
      return 'Tu sesión terminó. Inicia sesión de nuevo.'
    case 403:
      return 'No tienes permiso para realizar esta acción.'
    case 404:
      return 'El recurso no existe o no tienes acceso a él.'
    case 409:
      return describeConflict(error.properties.code) ?? 'La operación entra en conflicto con el estado actual. Recarga e inténtalo de nuevo.'
    case 429:
      return 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.'
    case 502:
    case 503:
    case 504:
      return 'El servidor no está disponible en este momento.'
    default:
      return 'El servidor respondió con un error.'
  }
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries(
    error.fieldErrors.map((fieldError) => [fieldError.field, describeFieldError(fieldError.code, fieldError.message)]),
  )
}
