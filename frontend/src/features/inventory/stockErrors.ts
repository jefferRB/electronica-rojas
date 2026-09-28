import { ApiError } from '../../shared/api/httpClient'
import { describeError } from '../../shared/api/describeError'

/** Explains a failed stock operation, including the real balance on a 409 (FR-TRF-003). */
export function describeStockError(error: unknown): string {
  if (error instanceof ApiError && error.status === 409 && typeof error.properties.available === 'number') {
    return `Existencias insuficientes: hay ${error.properties.available} disponibles y se solicitaron ${String(error.properties.requested)}.`
  }
  return describeError(error)
}
