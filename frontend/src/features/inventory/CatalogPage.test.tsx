import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '../auth/authApi'
import { SelectedBranchProvider } from '../branches/SelectedBranchProvider'
import { CatalogPage } from './CatalogPage'
import type { Product } from './inventoryApi'

const api = vi.hoisted(() => ({ fetchProducts: vi.fn(), fetchCategories: vi.fn(), createProduct: vi.fn(), updateProduct: vi.fn() }))
vi.mock('./inventoryApi', async (importOriginal) => ({ ...(await importOriginal<typeof import('./inventoryApi')>()), ...api }))

const role = vi.hoisted(() => ({ current: 'ADMIN' as Role }))
vi.mock('../auth/session', () => ({
  useSession: () => ({ data: { user: { id: 1, email: 'a@demo.test', fullName: 'Ana', role: role.current }, branches } }),
}))

const branches = [
  { id: 1, code: 'SJ-01', name: 'San José Centro' },
  { id: 2, code: 'AL-01', name: 'Alajuela' },
]

const motor: Product = {
  id: 5,
  sku: 'MOT-001',
  name: 'Motor lavadora',
  category: 'Lavadoras',
  description: null,
  kind: 'SPARE_PART',
  unit: 'UNIT',
  active: true,
  salePrice: 12500,
  unitCost: 8000,
  chargeableByDefault: false,
  version: 0,
}

function renderCatalog() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SelectedBranchProvider userId={1} branches={branches}>
          <CatalogPage />
        </SelectedBranchProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** As testing-library reads it: the currency formatter's no-break spaces become plain spaces. */
const colones = (text: string) => text.replace(/\s+/g, ' ')

beforeEach(() => {
  role.current = 'ADMIN'
  api.fetchProducts.mockResolvedValue({ content: [motor], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  api.fetchCategories.mockResolvedValue(['Lavadoras'])
  api.createProduct.mockImplementation(async (input) => ({ ...motor, ...input, id: 9 }))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('CatalogPage pricing (BR-INV-007/008)', () => {
  it('shows sale price and cost to roles allowed to see costs', async () => {
    renderCatalog()
    const row = (await screen.findByText('Motor lavadora')).closest('tr')!

    expect(screen.getByRole('columnheader', { name: 'Precio venta' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: 'Costo' })).toBeTruthy()
    expect(within(row).getByText(colones('₡12 500,00'))).toBeTruthy()
    expect(within(row).getByText(colones('₡8 000,00'))).toBeTruthy()
    expect(within(row).getByText('Sin cargo por defecto')).toBeTruthy()
  })

  it('has no cost column for the receptionist', async () => {
    role.current = 'RECEPTIONIST'
    renderCatalog()
    await screen.findByText('Motor lavadora')

    expect(screen.getByRole('columnheader', { name: 'Precio venta' })).toBeTruthy()
    expect(screen.queryByRole('columnheader', { name: 'Costo' })).toBeNull()
    expect(screen.queryByRole('button', { name: /Nuevo producto/ })).toBeNull()
  })

  it('creates a spare part with cost, price, default charge and initial stock at the selected branch', async () => {
    renderCatalog()
    fireEvent.click(await screen.findByRole('button', { name: /Nuevo producto/ }))
    const form = screen.getByRole('form', { name: 'Nuevo producto' })

    fireEvent.change(within(form).getByLabelText('SKU'), { target: { value: 'BOM-02' } })
    fireEvent.change(within(form).getByLabelText('Nombre'), { target: { value: 'Bomba de agua' } })
    fireEvent.change(within(form).getByLabelText('Categoría'), { target: { value: 'Lavadoras' } })
    fireEvent.change(within(form).getByLabelText('Tipo'), { target: { value: 'SPARE_PART' } })
    fireEvent.change(within(form).getByLabelText('Costo unitario (opcional)'), { target: { value: '8 000' } })
    fireEvent.change(within(form).getByLabelText('Precio de venta sugerido (opcional)'), { target: { value: '12 500,50' } })
    expect(within(form).getByText(colones('₡4 500,50'))).toBeTruthy()
    expect((within(form).getByLabelText(/Cobrar al cliente por defecto/) as HTMLInputElement).checked).toBe(true)

    fireEvent.click(within(form).getByRole('switch', { name: 'Registrar existencias iniciales' }))
    expect((within(form).getByLabelText('Sucursal') as HTMLSelectElement).value).toBe('1')
    fireEvent.change(within(form).getByLabelText('Cantidad en existencia'), { target: { value: '5' } })
    expect(within(form).getByText('Entrada de 5 en San José Centro.')).toBeTruthy()
    fireEvent.click(within(form).getByRole('button', { name: 'Crear producto' }))

    await waitFor(() => expect(api.createProduct).toHaveBeenCalledTimes(1))
    expect(api.createProduct.mock.calls[0][0]).toEqual({
      sku: 'BOM-02',
      name: 'Bomba de agua',
      category: 'Lavadoras',
      description: '',
      kind: 'SPARE_PART',
      unitCost: 8000,
      salePrice: 12500.5,
      chargeableByDefault: true,
      initialStock: { branchId: 1, quantity: 5 },
    })
    expect(await screen.findByText('Producto BOM-02 creado con 5 unidades en San José Centro.')).toBeTruthy()
  })

  it('refuses an amount it cannot read and keeps merchandise without the repair option', async () => {
    renderCatalog()
    fireEvent.click(await screen.findByRole('button', { name: /Nuevo producto/ }))
    const form = screen.getByRole('form', { name: 'Nuevo producto' })
    fireEvent.change(within(form).getByLabelText('SKU'), { target: { value: 'TV-1' } })
    fireEvent.change(within(form).getByLabelText('Nombre'), { target: { value: 'Televisor' } })
    fireEvent.change(within(form).getByLabelText('Categoría'), { target: { value: 'Pantallas' } })

    expect(within(form).queryByLabelText(/Cobrar al cliente por defecto/)).toBeNull()
    fireEvent.change(within(form).getByLabelText('Precio de venta sugerido (opcional)'), { target: { value: '-5' } })
    expect(within(form).getByText(/Escribe un monto válido/)).toBeTruthy()
    expect((within(form).getByRole('button', { name: 'Crear producto' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('tells the administrator that a price change only affects new operations', async () => {
    renderCatalog()
    fireEvent.click(await screen.findByRole('button', { name: 'Editar' }))
    const form = screen.getByRole('form', { name: 'Editar MOT-001' })

    expect((within(form).getByLabelText('Precio de venta sugerido (opcional)') as HTMLInputElement).value).toBe('12500')
    expect(within(form).getByText(/aplica a operaciones nuevas/)).toBeTruthy()
    expect(within(form).queryByRole('switch', { name: 'Registrar existencias iniciales' })).toBeNull()
  })
})
