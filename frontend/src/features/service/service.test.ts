import { describe, expect, it } from 'vitest'
import { describeAuditEvent } from '../../shared/i18n/audit'
import { describeConflict, describeFieldError } from '../../shared/i18n/errors'
import {
  EVENT_LABELS,
  PUBLIC_STATUS_LABELS,
  REQUEST_STATUS_LABELS,
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONES,
} from '../../shared/i18n/serviceLabels'
import { addDays, crDate, crRange, crTime, crToday, crToInstant, mondayOf, shortTime } from './crTime'

describe('Costa Rica time helpers', () => {
  it('converts local wall-clock times to UTC instants and back (UTC-6, no DST)', () => {
    expect(crToInstant('2030-03-04', '09:00')).toBe('2030-03-04T15:00:00.000Z')
    expect(crToInstant('2030-07-04', '09:00')).toBe('2030-07-04T15:00:00.000Z')
    expect(crTime('2030-03-04T15:00:00Z')).toBe('09:00')
    expect(crTime('2030-03-05T05:30:00Z')).toBe('23:30')
    // 05:30 UTC on the 5th is still the 4th in Costa Rica.
    expect(crDate('2030-03-05T05:30:00Z')).toBe('2030-03-04')
    expect(crToday(new Date('2030-03-05T05:59:00Z'))).toBe('2030-03-04')
  })

  it('does calendar arithmetic without time-zone drift', () => {
    expect(addDays('2030-02-28', 1)).toBe('2030-03-01')
    expect(addDays('2030-03-01', -1)).toBe('2030-02-28')
    expect(mondayOf('2030-03-06')).toBe('2030-03-04') // Wednesday
    expect(mondayOf('2030-03-10')).toBe('2030-03-04') // Sunday belongs to the week that started Monday
    expect(mondayOf('2030-03-04')).toBe('2030-03-04')
    expect(crRange('2030-03-04', 7)).toEqual({ from: '2030-03-04T06:00:00.000Z', to: '2030-03-11T06:00:00.000Z' })
    expect(shortTime('08:30:00')).toBe('08:30')
    expect(shortTime(null)).toBe('')
  })
})

describe('home-service labels', () => {
  it('has Spanish text for every status and event', () => {
    expect(Object.keys(REQUEST_STATUS_LABELS)).toHaveLength(5)
    expect(Object.keys(VISIT_STATUS_LABELS)).toEqual(Object.keys(VISIT_STATUS_TONES))
    expect(Object.keys(PUBLIC_STATUS_LABELS)).toHaveLength(7)
    expect(Object.values(EVENT_LABELS).every((label) => label.length > 0)).toBe(true)
    // The public page never promises a confirmed time before the business confirms it.
    expect(PUBLIC_STATUS_LABELS.RECEIVED.detail).toContain('no está confirmada')
  })

  it('translates the scheduling conflicts', () => {
    expect(describeConflict('SCHEDULE_CONFLICT')).toContain('ya tiene una visita confirmada')
    expect(describeConflict('OUTSIDE_WORKING_HOURS')).toContain('fuera de la jornada')
    expect(describeConflict('CUSTOMER_NOT_LINKED')).toContain('Asocia la solicitud')
    expect(describeFieldError('VISIT_IN_PAST', 'x')).toBe('La visita debe programarse en el futuro.')
    expect(describeFieldError('AssertTrue', 'x')).toBe('Debes aceptar para continuar.')
  })

  it('describes home-service audit events from their codes', () => {
    const rescheduled = describeAuditEvent({
      action: 'SERVICE_VISIT_UPDATED',
      summary: 'Service request SR-2030-000001: VISIT_RESCHEDULED',
      detailsSource: 'RECORDED',
      details: {
        requestCode: 'SR-2030-000001',
        event: 'VISIT_RESCHEDULED',
        previousStart: '2030-03-04T15:00:00Z',
        start: '2030-03-04T20:00:00Z',
        technicianName: 'Técnico Uno',
      },
    })
    expect(rescheduled.title).toBe('Visita a domicilio actualizada')
    expect(rescheduled.description).toMatch(/^Visita reprogramada \(SR-2030-000001\): de 4 mar 2030, 9:00.* a 4 mar 2030, 2:00 p.*, técnico Técnico Uno\.$/)

    const submitted = describeAuditEvent({
      action: 'SERVICE_REQUEST_SUBMITTED',
      summary: 'Service request SR-2030-000002: SUBMITTED',
      detailsSource: 'RECORDED',
      details: { requestCode: 'SR-2030-000002', event: 'SUBMITTED', branchName: 'San José Centro' },
    })
    expect(submitted.description).toBe('Se recibió la solicitud a domicilio SR-2030-000002 para San José Centro (formulario público).')

    const linked = describeAuditEvent({
      action: 'SERVICE_VISIT_UPDATED',
      summary: '',
      detailsSource: 'RECORDED',
      details: { requestCode: 'SR-2030-000003', event: 'REPAIR_ORDER_LINKED', orderCode: 'OR-2030-000009' },
    })
    expect(linked.description).toBe('Vinculada a orden de taller: la solicitud SR-2030-000003 pasó al taller con la orden OR-2030-000009.')
  })
})
