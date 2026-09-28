import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { branchKeys } from '../branches/branchesApi'
import { repairKeys, type RepairOrderDetail } from './repairsApi'
import { ReceiptPage } from './ReceiptPage'

const person = { id: 7, fullName: 'Ana Recepción' }

const order: RepairOrderDetail = {
  id: 5,
  orderCode: 'OR-2026-000005',
  status: 'IN_REPAIR',
  resolution: null,
  inCustody: true,
  branch: { id: 1, code: 'CEN', name: 'San José Centro' },
  customer: { id: 3, fullName: 'Cliente Ficticio', phone: '+50688010101', email: null },
  device: { type: 'Televisor', brand: 'LG', model: '55UQ', serialNumber: 'SER-123' },
  reportedFault: 'No muestra imagen',
  physicalCondition: 'Rayón en el marco',
  accessories: 'Control remoto',
  technician: { id: 9, fullName: 'Técnico Asignado' },
  diagnosis: 'Tarjeta principal dañada (dato interno)',
  diagnosisUpdatedAt: '2026-09-27T16:00:00Z',
  diagnosisUpdatedBy: { id: 9, fullName: 'Técnico Asignado' },
  receivedAt: '2026-09-27T15:00:00Z',
  receivedBy: person,
  deliveredAt: null,
  deliveredBy: null,
  version: 2,
  history: [],
  quotes: [
    {
      id: 1,
      amount: 25000,
      description: 'Cambio de tarjeta (cotización interna)',
      status: 'PENDING',
      createdBy: person,
      createdAt: '2026-09-27T16:00:00Z',
      decidedBy: null,
      decidedAt: null,
      decisionMethod: null,
      decisionNote: null,
    } as unknown as RepairOrderDetail['quotes'][number],
  ],
  parts: [],
  partsSummary: {
    linesInUse: 0,
    unitsInUse: 0,
    unitsWithoutCharge: 0,
    chargeableSubtotal: 0,
    unpricedLines: 0,
    uncostedLines: 0,
    currency: 'CRC',
  },
  notifications: [],
  actions: {} as RepairOrderDetail['actions'],
}

afterEach(cleanup)

function renderReceipt() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  client.setQueryData(repairKeys.detail(5), order)
  client.setQueryData(branchKeys.detail(1), { id: 1, code: 'CEN', name: 'San José Centro', address: 'Avenida 2', active: true, version: 0 })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/repairs/5/receipt']}>
        <Routes>
          <Route path="/repairs/:orderId/receipt" element={<ReceiptPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ReceiptPage (reception receipt)', () => {
  it('shows what the customer handed over, with the order code and branch', () => {
    renderReceipt()
    expect(screen.getByLabelText('Comprobante de recepción OR-2026-000005')).toBeTruthy()
    expect(screen.getByText('Cliente Ficticio')).toBeTruthy()
    expect(screen.getByText('No muestra imagen')).toBeTruthy()
    expect(screen.getByText('Rayón en el marco')).toBeTruthy()
    expect(screen.getByText('Control remoto')).toBeTruthy()
    expect(screen.getByText('Avenida 2')).toBeTruthy()
  })

  it('never prints the diagnosis, quotes or the technician', () => {
    const { container } = renderReceipt()
    expect(container.textContent).not.toContain('Tarjeta principal dañada')
    expect(container.textContent).not.toContain('cotización interna')
    expect(container.textContent).not.toContain('Técnico Asignado')
  })
})
