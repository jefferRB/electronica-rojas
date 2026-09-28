const costaRica = new Intl.DateTimeFormat('es-CR', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Costa_Rica',
})

/** API instants are UTC (ISO 8601); the UI shows them in Costa Rica time (DATA-006). */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? costaRica.format(new Date(iso)) : '—'
}

/** "+5" / "-3" for stock deltas. */
export function formatSigned(value: number): string {
  return value > 0 ? `+${value}` : String(value)
}

/** A new id per user intent, reused on retries (FR-TRF-002). Secure contexts only (localhost/HTTPS). */
export function newOperationId(): string {
  return crypto.randomUUID()
}

const colones = new Intl.NumberFormat('es-CR', { style: 'currency', currency: 'CRC', minimumFractionDigits: 2 })

/** Amounts arrive as JSON numbers from BigDecimal; shown as "₡25 000,50". */
export function formatColones(amount: number): string {
  return colones.format(amount)
}
