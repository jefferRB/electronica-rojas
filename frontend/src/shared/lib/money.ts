/**
 * Amounts typed by people, in colones: "25000", "25 000", "25.000,50", "25000.50", "₡25 000".
 * One convention for the whole app (quotes, prices, costs): when both separators appear the last
 * one is the decimal separator; "25.000" / "25,000" alone is twenty-five thousand. Returns null
 * unless it is an amount >= 0 with at most 10 integer digits and 2 decimals (the server's
 * NUMERIC(12,2)).
 */
export function parseAmount(input: string): number | null {
  let text = input.replace(/[\s ₡]/g, '')
  if (!text) return null
  const lastComma = text.lastIndexOf(',')
  const lastDot = text.lastIndexOf('.')
  if (lastComma >= 0 && lastDot >= 0) {
    // Both present: the last one is the decimal separator, the other groups thousands.
    const decimal = lastComma > lastDot ? ',' : '.'
    text = text.replaceAll(decimal === ',' ? '.' : ',', '').replace(decimal, '.')
  } else if (/^\d{1,3}([.,]\d{3})+$/.test(text)) {
    // Only group separators: "25.000" or "25,000" is twenty-five thousand.
    text = text.replace(/[.,]/g, '')
  } else {
    text = text.replace(',', '.')
  }
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(text)) return null
  return Number(text)
}

/** An optional money field: empty is "unknown" (null), anything unparseable is an error. */
export type MoneyInput = { ok: true; value: number | null } | { ok: false }

export function parseMoney(text: string): MoneyInput {
  if (!text.replace(/[\s ₡]/g, '')) return { ok: true, value: null }
  const value = parseAmount(text)
  return value === null ? { ok: false } : { ok: true, value }
}

/** The value as the input shows it when editing ("12500,5" for 12500.5); empty for unknown. */
export function moneyToInput(value: number | null | undefined): string {
  return value === null || value === undefined ? '' : String(value).replace('.', ',')
}

export const MONEY_HINT = 'Escribe un monto válido, por ejemplo 12500 o 12 500,50 (máximo 2 decimales).'
