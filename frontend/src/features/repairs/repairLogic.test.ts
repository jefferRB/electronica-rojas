import { describe, expect, it } from 'vitest'
import { describeAuditEvent } from '../../shared/i18n/audit'
import { describeConflict } from '../../shared/i18n/errors'
import { REPAIR_STATUS_LABELS, REPAIR_STATUS_TONES, REPAIR_TRANSITION_ACTIONS } from '../../shared/i18n/labels'
import { duplicateMatches } from '../customers/customerDraft'
import { parseColones, partLineSubtotal, partQuantityError, receptionRequest, type DeviceInput } from './repairLogic'

const device: DeviceInput = {
  deviceType: ' Televisor ',
  brand: 'Samsung',
  model: '',
  serialNumber: ' SN-1 ',
  reportedFault: 'No enciende',
  physicalCondition: 'Rayón',
  accessories: '  ',
}

describe('parseColones', () => {
  it.each([
    ['25000', 25000],
    ['25 000', 25000],
    ['25.000', 25000],
    ['25,000', 25000],
    ['25000,50', 25000.5],
    ['25000.5', 25000.5],
    ['25.000,50', 25000.5],
    ['₡ 1 250 000', 1250000],
    ['10.123', 10123],
  ])('reads %s as %d', (input, expected) => {
    expect(parseColones(input)).toBe(expected)
  })

  it.each(['', '0', '-5', 'abc', '1,2345', '1.2.3', '10.1234', '12345678901'])('rejects %s', (input) => {
    expect(parseColones(input)).toBeNull()
  })
})

describe('receptionRequest', () => {
  it('sends an in-scope customer by id only and trims optional device fields away', () => {
    const request = receptionRequest('op-1', 7, { kind: 'existing', match: { id: 3, fullName: 'Ana', inScope: true }, phone: '8888-7777' }, device)

    expect(request).toMatchObject({ operationId: 'op-1', branchId: 7, customerId: 3, deviceType: 'Televisor', serialNumber: 'SN-1' })
    expect(request.customerPhone).toBeUndefined()
    expect(request.model).toBeUndefined()
    expect(request.accessories).toBeUndefined()
    expect(request.newCustomer).toBeUndefined()
  })

  it('proves a customer from another branch with the phone the customer gave', () => {
    const request = receptionRequest('op-2', 7, { kind: 'existing', match: { id: 9, fullName: 'Luis', inScope: false }, phone: '8888-7777' }, device)

    expect(request.customerId).toBe(9)
    expect(request.customerPhone).toBe('8888-7777')
  })

  it('sends a new customer inline', () => {
    const request = receptionRequest('op-3', 7, { kind: 'new', customer: { fullName: 'Marta', phone: '2222-3333' } }, device)

    expect(request.newCustomer).toEqual({ fullName: 'Marta', phone: '2222-3333' })
    expect(request.customerId).toBeUndefined()
  })
})

describe('duplicateMatches', () => {
  it('reads the matches of a possible-duplicate conflict and ignores anything else', () => {
    const matches = [{ id: 1, fullName: 'Ana', inScope: true, matchedBy: 'PHONE' }]
    expect(duplicateMatches({ code: 'POSSIBLE_DUPLICATE_CUSTOMER', matches })).toEqual(matches)
    expect(duplicateMatches({ code: 'STALE_VERSION', matches })).toEqual([])
    expect(duplicateMatches({ code: 'POSSIBLE_DUPLICATE_CUSTOMER', matches: 'x' })).toEqual([])
  })
})

describe('repair labels', () => {
  it('has a Spanish label, tone and action text for every status', () => {
    for (const status of Object.keys(REPAIR_STATUS_LABELS) as (keyof typeof REPAIR_STATUS_LABELS)[]) {
      expect(REPAIR_STATUS_LABELS[status]).toBeTruthy()
      expect(REPAIR_STATUS_TONES[status]).toBeTruthy()
      expect(REPAIR_TRANSITION_ACTIONS[status]).toBeTruthy()
    }
    expect(Object.keys(REPAIR_STATUS_LABELS)).toHaveLength(9)
  })

  it('translates the repair conflict codes', () => {
    expect(describeConflict('QUOTE_ALREADY_DECIDED')).toBe('La cotización ya fue decidida y no puede cambiarse.')
    expect(describeConflict('INVALID_TRANSITION')).toContain('no está permitido')
  })

  it('describes repair audit events without customer data', () => {
    const changed = describeAuditEvent({
      action: 'REPAIR_STATUS_CHANGED',
      summary: 'Order OR-2026-000001 READY_FOR_PICKUP -> DELIVERED',
      detailsSource: 'RECORDED',
      details: { orderCode: 'OR-2026-000001', fromStatus: 'READY_FOR_PICKUP', toStatus: 'DELIVERED' },
    })
    expect(changed.title).toBe('Cambio de estado de reparación')
    expect(changed.description).toBe('La orden OR-2026-000001 pasó de «Lista para entrega» a «Entregada».')

    const decided = describeAuditEvent({
      action: 'REPAIR_QUOTE_DECIDED',
      summary: 'Quote 1 REJECTED',
      detailsSource: 'RECORDED',
      details: { orderCode: 'OR-2026-000001', decision: 'REJECTED', method: 'PHONE', amount: '25000.50', currency: 'CRC' },
    })
    expect(decided.description).toMatch(/^El cliente rechazó la cotización de .*25.*000,50 de la orden OR-2026-000001 \(por teléfono\)\.$/)

    expect(
      describeAuditEvent({ action: 'CUSTOMER_CREATED', summary: 'Customer 4 registered at SJ-01', detailsSource: 'RECORDED', details: { branchCode: 'SJ-01' } })
        .description,
    ).toBe('Se registró un cliente en SJ-01.')
  })
})

describe('partQuantityError', () => {
  it('accepts whole quantities up to what is available', () => {
    expect(partQuantityError('1', 3)).toBeUndefined()
    expect(partQuantityError(' 3 ', 3)).toBeUndefined()
  })

  it('rejects empty, fractional, zero and excessive quantities with a Spanish message', () => {
    expect(partQuantityError('', 3)).toBe('Ingresa una cantidad entera.')
    expect(partQuantityError('1.5', 3)).toBe('Ingresa una cantidad entera.')
    expect(partQuantityError('0', 3)).toBe('La cantidad debe ser al menos 1.')
    expect(partQuantityError('4', 3)).toBe('Solo hay 3 disponibles.')
    expect(partQuantityError('2', 1)).toBe('Solo hay 1 disponible.')
    expect(partQuantityError('1001', 5000)).toBe('La cantidad máxima por registro es 1000.')
  })
})

describe('spare part texts', () => {
  it('describes consumption and closed-order corrections from structured audit details', () => {
    const consumed = describeAuditEvent({
      action: 'REPAIR_PART_CONSUMED',
      summary: '2 x CORREA-01 used',
      detailsSource: 'RECORDED',
      details: { orderCode: 'OR-2026-000007', sku: 'CORREA-01', productName: 'Correa', quantity: 2, balanceBefore: 5, balanceAfter: 3, branchName: 'San José Centro' },
    })
    expect(consumed.description).toBe(
      'Se utilizaron 2 unidades del repuesto CORREA-01 (Correa) en la orden OR-2026-000007 (San José Centro). Existencias: 5 → 3.',
    )
    const returned = describeAuditEvent({
      action: 'REPAIR_PART_RETURNED',
      summary: '1 x CORREA-01 returned',
      detailsSource: 'RECORDED',
      details: { orderCode: 'OR-2026-000007', sku: 'CORREA-01', quantity: 1, orderClosed: true, orderStatus: 'DELIVERED', remainingQuantity: 1 },
    })
    expect(returned.description).toContain('con la orden ya cerrada («Entregada»)')
    expect(describeConflict('RETURN_EXCEEDS_CONSUMED')).toMatch(/No se puede devolver/)
    expect(describeConflict('PARTS_NOT_ALLOWED')).toMatch(/En reparación/)
  })
})

describe('partLineSubtotal (BR-REP-014)', () => {
  it('multiplies price by quantity for charged lines, in exact cents', () => {
    expect(partLineSubtotal(2, 12500, true)).toBe(25000)
    expect(partLineSubtotal(3, 0.1, true)).toBe(0.3)
  })

  it('is 0 for a line without charge and unknown without a price', () => {
    expect(partLineSubtotal(3, 12500, false)).toBe(0)
    expect(partLineSubtotal(1, null, true)).toBeNull()
  })
})
