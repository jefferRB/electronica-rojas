import { WEEKDAY_LABELS } from '../../shared/i18n/serviceLabels'

/** "lunes a viernes" / "lunes, miércoles y sábado", for the date hint. */
export function describeServiceDays(days: number[]): string {
  const names = days.map((day) => WEEKDAY_LABELS[day - 1].toLowerCase())
  const consecutive = days.every((day, index) => index === 0 || day === days[index - 1] + 1)
  if (days.length === 7) return 'todos los días'
  if (consecutive && days.length > 2) return `${names[0]} a ${names[names.length - 1]}`
  return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} y ${names[names.length - 1]}`
}
