import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsumePartForm } from './PartForms'
import type { RepairOrderDetail } from './repairsApi'

const api = vi.hoisted(() => ({ fetchPartOptions: vi.fn(), consumePart: vi.fn() }))
vi.mock('./repairsApi', async (importOriginal) => ({ ...(await importOriginal<typeof import('./repairsApi')>()), ...api }))

const person = { id: 7, fullName: 'Tomás Técnico' }

function order(canOverridePartPricing: boolean): RepairOrderDetail {
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
    parts: [],
    partsSummary: { linesInUse: 0, unitsInUse: 0, unitsWithoutCharge: 0, chargeableSubtotal: 0, unpricedLines: 0, uncostedLines: 0, currency: 'CRC' },
    actions: {
      transitions: [],
      canAssignTechnician: false,
      canEditDiagnosis: false,
      canCreateQuote: false,
      canDecideQuote: false,
      canConsumeParts: true,
      canReturnParts: false,
      canOverridePartPricing,
    },
  }
}

const motor = {
  product: { id: 5, sku: 'MOT-001', name: 'Motor lavadora', category: 'Repuestos', kind: 'SPARE_PART' as const, active: true },
  quantity: 10,
  minimumQuantity: 0,
  stockStatus: 'NORMAL' as const,
  updatedAt: null,
  salePrice: 12500,
  chargeableByDefault: true,
}

/** As testing-library reads it: the currency formatter's no-break spaces become plain spaces. */
const colones = (text: string) => text.replace(/\s+/g, ' ')

function renderForm(canOverride: boolean) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ConsumePartForm order={order(canOverride)} onCancel={() => {}} onDone={() => {}} />
    </QueryClientProvider>,
  )
}

async function pickMotor(quantity: string) {
  fireEvent.click(await screen.findByRole('button', { name: /MOT-001/ }))
  fireEvent.change(screen.getByLabelText('Cantidad utilizada'), { target: { value: quantity } })
}

beforeEach(() => {
  api.fetchPartOptions.mockResolvedValue({ content: [motor], page: 0, size: 8, totalElements: 1, totalPages: 1 })
  api.consumePart.mockImplementation(async () => order(true))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ConsumePartForm pricing (BR-REP-014)', () => {
  it('shows the catalog price and the line subtotal as the quantity changes', async () => {
    renderForm(true)
    await pickMotor('2')

    expect((screen.getByLabelText('Precio por unidad') as HTMLInputElement).value).toBe('12500')
    expect(screen.getByText(colones('₡25 000,00'))).toBeTruthy()
  })

  it('management can record a part without charge: stock is still used, the subtotal says so', async () => {
    renderForm(true)
    await pickMotor('2')
    fireEvent.click(screen.getByRole('switch', { name: 'Cobrar al cliente' }))

    expect(screen.getAllByText('Sin cargo').length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: 'Registrar consumo' }))
    await waitFor(() => expect(api.consumePart).toHaveBeenCalledTimes(1))
    const [orderId, input] = api.consumePart.mock.calls[0]
    expect(orderId).toBe(1)
    // Only what departs from the catalog is sent: the charge, not the unchanged price.
    expect(input).toMatchObject({ productId: 5, quantity: 2, chargeable: false })
    expect(input.unitPrice).toBeUndefined()
  })

  it('management can set a price for this order only', async () => {
    renderForm(true)
    await pickMotor('3')
    fireEvent.change(screen.getByLabelText('Precio por unidad'), { target: { value: '10 000' } })

    expect(screen.getByText(colones('₡30 000,00'))).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Registrar consumo' }))
    await waitFor(() => expect(api.consumePart).toHaveBeenCalledTimes(1))
    expect(api.consumePart.mock.calls[0][1]).toMatchObject({ unitPrice: 10000 })
    expect(api.consumePart.mock.calls[0][1].chargeable).toBeUndefined()
  })

  it('a technician records with the catalog defaults and sees them read-only', async () => {
    renderForm(false)
    await pickMotor('1')

    expect(screen.queryByRole('switch', { name: 'Cobrar al cliente' })).toBeNull()
    expect(screen.queryByLabelText('Precio por unidad')).toBeNull()
    expect(screen.getByText(/Solo gestión puede ajustarlo/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Registrar consumo' }))
    await waitFor(() => expect(api.consumePart).toHaveBeenCalledTimes(1))
    const input = api.consumePart.mock.calls[0][1]
    expect(input.unitPrice).toBeUndefined()
    expect(input.chargeable).toBeUndefined()
  })
})
