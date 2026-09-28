import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RepairOrderDetail } from './repairsApi'
import { RepairPartsCard } from './RepairPartsCard'
import { formatColones } from '../../shared/lib/format'

/** As testing-library reads it: the formatter's no-break spaces become plain spaces. */
const colones = (amount: number) => formatColones(amount).replace(/\s+/g, ' ')

const person = { id: 7, fullName: 'Tomás Técnico' }

function order(overrides: Partial<RepairOrderDetail> = {}): RepairOrderDetail {
  return {
    id: 1,
    orderCode: 'OR-2026-000001',
    status: 'IN_REPAIR',
    resolution: null,
    inCustody: true,
    branch: { id: 1, code: 'SJ-01', name: 'San José Centro' },
    customer: { id: 1, fullName: 'Cliente Ficticio', phone: null, email: null },
    device: { type: 'Lavadora', brand: 'Whirlpool', model: null, serialNumber: null },
    reportedFault: 'No centrifuga',
    physicalCondition: 'Bueno',
    accessories: null,
    technician: person,
    diagnosis: null,
    diagnosisUpdatedAt: null,
    diagnosisUpdatedBy: null,
    receivedAt: '2026-09-27T15:00:00Z',
    receivedBy: person,
    deliveredAt: null,
    deliveredBy: null,
    version: 3,
    history: [],
    quotes: [],
    notifications: [],
    parts: [
      {
        id: 10,
        product: { id: 5, sku: 'CORREA-01', name: 'Correa', category: 'Repuestos', kind: 'SPARE_PART', active: true },
        branch: { id: 1, code: 'SJ-01', name: 'San José Centro' },
        quantity: 3,
        returnedQuantity: 1,
        remainingQuantity: 2,
        unitPrice: 12500,
        chargeable: true,
        priceOverridden: false,
        chargedAmount: 25000,
        note: null,
        recordedBy: person,
        recordedAt: '2026-09-27T16:00:00Z',
        returns: [
          { id: 20, quantity: 1, reason: 'Sobró una unidad', orderStatus: 'DELIVERED', recordedBy: person, recordedAt: '2026-09-28T16:00:00Z' },
        ],
      },
      {
        id: 11,
        product: { id: 6, sku: 'BOMBA-02', name: 'Bomba', category: 'Repuestos', kind: 'SPARE_PART', active: true },
        branch: { id: 1, code: 'SJ-01', name: 'San José Centro' },
        quantity: 1,
        returnedQuantity: 1,
        remainingQuantity: 0,
        unitPrice: 4000,
        chargeable: false,
        priceOverridden: false,
        chargedAmount: 0,
        note: 'Cambio preventivo',
        recordedBy: person,
        recordedAt: '2026-09-27T16:05:00Z',
        returns: [],
      },
    ],
    actions: {
      transitions: [],
      canAssignTechnician: false,
      canEditDiagnosis: false,
      canCreateQuote: false,
      canDecideQuote: false,
      canConsumeParts: true,
      canReturnParts: true,
      canOverridePartPricing: false,
    },
    partsSummary: {
      linesInUse: 1,
      unitsInUse: 2,
      unitsWithoutCharge: 0,
      chargeableSubtotal: 25000,
      unpricedLines: 0,
      uncostedLines: 0,
      currency: 'CRC',
    },
    ...overrides,
  }
}

afterEach(cleanup)

describe('RepairPartsCard (B.5)', () => {
  it('shows each line with what was returned and the correction as its own row', () => {
    render(<RepairPartsCard order={order()} disabled={false} onReturn={() => {}} />)

    const rows = screen.getAllByRole('row')
    // Header + two lines + one correction.
    expect(rows).toHaveLength(4)
    expect(within(rows[1]).getByText(/CORREA-01/)).toBeTruthy()
    expect(within(rows[1]).getByText('1 devuelta · 2 en uso')).toBeTruthy()
    expect(within(rows[2]).getByText('Devolución')).toBeTruthy()
    expect(within(rows[2]).getByText(/Sobró una unidad/)).toBeTruthy()
    expect(within(rows[2]).getByText('Registrada con la orden en «Entregada».')).toBeTruthy()
    expect(within(rows[3]).getByText('Nota: Cambio preventivo')).toBeTruthy()
  })

  it('offers a correction only for lines with units still in use', () => {
    const onReturn = vi.fn()
    render(<RepairPartsCard order={order()} disabled={false} onReturn={onReturn} />)

    const buttons = screen.getAllByRole('button', { name: 'Corregir' })
    expect(buttons).toHaveLength(1)
    fireEvent.click(buttons[0])
    expect(onReturn).toHaveBeenCalledWith(expect.objectContaining({ id: 10 }))
  })

  it('shows the frozen price, the charged amount and "Sin cargo" apart from the price', () => {
    render(<RepairPartsCard order={order()} disabled={false} onReturn={() => {}} />)

    const rows = screen.getAllByRole('row')
    expect(within(rows[1]).getByText(colones(12500))).toBeTruthy()
    expect(within(rows[1]).getByText(colones(25000))).toBeTruthy()
    // The uncharged line keeps its price but says it is not charged.
    expect(within(rows[3]).getByText(colones(4000))).toBeTruthy()
    expect(within(rows[3]).getByText('Sin cargo')).toBeTruthy()
    const totals = screen.getByLabelText('Totales de repuestos')
    expect(within(totals).getByText('2 unidades')).toBeTruthy()
    expect(within(totals).getByText(colones(25000))).toBeTruthy()
    // No cost in the payload (technician, receptionist): nothing about costs is shown.
    expect(screen.queryByText(/Costo/)).toBeNull()
  })

  it('shows internal cost only when the API sent it and flags lines without price', () => {
    const managed = order({
      partsSummary: { ...order().partsSummary, totalCost: 16000, unpricedLines: 1 },
      parts: [{ ...order().parts[0], unitCost: 8000 }, { ...order().parts[1], unitPrice: null, chargeable: true, chargedAmount: null, remainingQuantity: 1 }],
    })
    render(<RepairPartsCard order={managed} disabled={false} onReturn={() => {}} />)

    expect(screen.getByText('Costo interno')).toBeTruthy()
    expect(screen.getByText(colones(16000))).toBeTruthy()
    expect(screen.getByText(`Costo ${colones(8000)}`)).toBeTruthy()
    expect(screen.getByText('Precio por definir')).toBeTruthy()
    expect(screen.getByText('Sin incluir 1 línea sin precio')).toBeTruthy()
  })

  it('hides corrections from users who may not make them and explains when parts are recorded', () => {
    const readOnly = order({ actions: { ...order().actions, canReturnParts: false } })
    render(<RepairPartsCard order={readOnly} disabled={false} onReturn={() => {}} />)
    expect(screen.queryByRole('button', { name: 'Corregir' })).toBeNull()
    cleanup()

    render(<RepairPartsCard order={order({ parts: [], status: 'DIAGNOSING' })} disabled={false} onReturn={() => {}} />)
    expect(screen.getByText(/Los repuestos se registran mientras la orden está «En reparación»/)).toBeTruthy()
  })
})
