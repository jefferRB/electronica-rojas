/**
 * Costa Rica wall-clock helpers. America/Costa_Rica is UTC-6 all year (no daylight saving), and
 * the API speaks UTC instants: the UI converts at the edges only.
 */

const TIME = new Intl.DateTimeFormat('es-CR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'America/Costa_Rica' })
const PARTS = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: 'America/Costa_Rica',
})
const LONG_DATE = new Intl.DateTimeFormat('es-CR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'America/Costa_Rica' })

/** "2030-03-04" + "09:00" (Costa Rica) → "2030-03-04T15:00:00.000Z". */
export function crToInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00-06:00`).toISOString()
}

/** "2030-03-04T15:00:00Z" → "2030-03-04" (Costa Rica calendar day). */
export function crDate(iso: string): string {
  return PARTS.format(new Date(iso))
}

/** "2030-03-04T15:00:00Z" → "09:00". */
export function crTime(iso: string): string {
  return TIME.format(new Date(iso))
}

/** "lunes, 4 de marzo" for a Costa Rica date string. */
export function longDate(date: string): string {
  return LONG_DATE.format(new Date(`${date}T12:00:00-06:00`))
}

/** "lunes, 4 de marzo" → "Lunes, 4 de marzo" (for headings that start with the date). */
export function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** Today in Costa Rica as "YYYY-MM-DD". */
export function crToday(now: Date = new Date()): string {
  return PARTS.format(now)
}

/** Adds days to a "YYYY-MM-DD" date (calendar arithmetic, no time zone involved). */
export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number)
  const result = new Date(Date.UTC(y, m - 1, d + days))
  return result.toISOString().slice(0, 10)
}

/** Monday of the week containing `date` ("YYYY-MM-DD"). */
export function mondayOf(date: string): string {
  const [y, m, d] = date.split('-').map(Number)
  const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay() // 0 = Sunday
  return addDays(date, weekday === 0 ? -6 : 1 - weekday)
}

/** [from, to) instants covering `days` Costa Rica days from `date`. */
export function crRange(date: string, days: number): { from: string; to: string } {
  return { from: crToInstant(date, '00:00'), to: crToInstant(addDays(date, days), '00:00') }
}

/** "08:00:00" → "08:00". */
export function shortTime(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : ''
}
