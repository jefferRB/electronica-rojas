import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerPicker } from './CustomerPicker'
import type { CustomerCandidate, CustomerLookup } from './customersApi'
import { useCustomerPicker, type CustomerPickerState } from './useCustomerPicker'

const lookupMock = vi.hoisted(() => vi.fn())
vi.mock('./customersApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./customersApi')>()),
  fetchCustomerLookup: lookupMock,
}))

const candidate = (id: number, fullName: string, phone: string | null = '+50688887777', inScope = true): CustomerCandidate => ({
  id,
  fullName,
  phone,
  email: null,
  registeredBranch: inScope ? { id: 1, code: 'SJ-01', name: 'San José' } : null,
  inScope,
  nameMatch: true,
  phoneMatch: false,
})

const answer = (...candidates: CustomerCandidate[]): CustomerLookup => ({ candidates, moreInScope: 0 })

/** Latest picker state, captured after each render so tests can assert what the form would submit. */
const captured: { current: CustomerPickerState | null } = { current: null }

function Harness() {
  const picker = useCustomerPicker()
  useEffect(() => {
    captured.current = picker
  })
  return (
    <div>
      <CustomerPicker picker={picker} />
      <button type="button">Fuera</button>
    </div>
  )
}

function renderPicker() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <Harness />
    </QueryClientProvider>,
  )
}

const nameInput = () => screen.getByRole('combobox', { name: 'Nombre del cliente' }) as HTMLInputElement
const phoneInput = () => screen.getByRole('combobox', { name: /Teléfono del cliente/ }) as HTMLInputElement
const type = (input: HTMLInputElement, value: string) => act(() => fireEvent.change(input, { target: { value } }))

beforeEach(() => lookupMock.mockReset())
afterEach(cleanup)

describe('CustomerPicker', () => {
  it('shows name and phone from the start and searches only from 3 characters, after the debounce', async () => {
    lookupMock.mockResolvedValue(answer(candidate(1, 'Ana Pérez')))
    renderPicker()
    expect(nameInput()).toBeTruthy()
    expect(phoneInput()).toBeTruthy()

    type(nameInput(), 'an')
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(lookupMock).not.toHaveBeenCalled()

    type(nameInput(), 'ana')
    type(nameInput(), 'ana p')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy())
    // One request for the final text, not one per keystroke.
    expect(lookupMock).toHaveBeenCalledTimes(1)
    expect(lookupMock.mock.calls[0].slice(0, 2)).toEqual(['ana p', ''])
    expect(nameInput().getAttribute('aria-expanded')).toBe('true')
    expect(nameInput().getAttribute('aria-controls')).toBe(screen.getByRole('listbox').id)
  })

  it('never lets an older, slower answer replace the results of the latest search', async () => {
    let resolveOld: (value: CustomerLookup) => void = () => {}
    lookupMock.mockImplementation((name: string) =>
      name === 'ana' ? new Promise<CustomerLookup>((resolve) => (resolveOld = resolve)) : Promise.resolve(answer(candidate(2, 'Ana Rojas'))),
    )
    renderPicker()
    type(nameInput(), 'ana')
    await waitFor(() => expect(lookupMock).toHaveBeenCalledTimes(1))
    type(nameInput(), 'ana rojas')
    await waitFor(() => expect(screen.getByRole('option', { name: /Ana Rojas/ })).toBeTruthy())

    // The first request was aborted when its key stopped being observed…
    expect((lookupMock.mock.calls[0][2] as AbortSignal).aborted).toBe(true)
    // …and even if its answer arrives now, it is not shown.
    await act(async () => resolveOld(answer(candidate(1, 'Ana Pérez'))))
    expect(screen.queryByRole('option', { name: /Ana Pérez/ })).toBeNull()
    expect(screen.getAllByRole('option')).toHaveLength(1)
  })

  it('chooses with the keyboard, fills the fields and marks an existing customer', async () => {
    lookupMock.mockResolvedValue(answer(candidate(1, 'Ana Pérez'), candidate(2, 'Ana Rojas', '+50670001234')))
    renderPicker()
    type(nameInput(), 'ana')
    await waitFor(() => expect(screen.getAllByRole('option')).toHaveLength(2))

    act(() => fireEvent.keyDown(nameInput(), { key: 'ArrowDown' }))
    act(() => fireEvent.keyDown(nameInput(), { key: 'ArrowDown' }))
    const second = screen.getAllByRole('option')[1]
    expect(second.getAttribute('aria-selected')).toBe('true')
    expect(nameInput().getAttribute('aria-activedescendant')).toBe(second.id)
    act(() => fireEvent.keyDown(nameInput(), { key: 'Enter' }))

    expect(screen.queryByRole('listbox')).toBeNull()
    expect(nameInput().value).toBe('Ana Rojas')
    expect(phoneInput().value).toBe('7000-1234')
    expect(screen.getByText('Cliente existente')).toBeTruthy()
    expect(captured.current?.choice).toEqual({ kind: 'existing', match: { id: 2, fullName: 'Ana Rojas', inScope: true }, phone: '7000-1234' })
  })

  it('unlinks the chosen customer when a field is edited, and lets the user change the choice', async () => {
    lookupMock.mockResolvedValue(answer(candidate(1, 'Ana Pérez')))
    renderPicker()
    type(nameInput(), 'ana')
    await waitFor(() => expect(screen.getByRole('option')).toBeTruthy())
    act(() => fireEvent.click(screen.getByRole('option')))
    expect(captured.current?.draft.selected?.id).toBe(1)

    type(phoneInput(), '8888-7778')
    expect(captured.current?.draft.selected).toBeNull()
    expect(screen.queryByText('Cliente existente')).toBeNull()
  })

  it('closes the list with Escape and with a click outside', async () => {
    lookupMock.mockResolvedValue(answer(candidate(1, 'Ana Pérez')))
    renderPicker()
    type(nameInput(), 'ana')
    await waitFor(() => expect(screen.getByRole('listbox')).toBeTruthy())

    act(() => fireEvent.keyDown(nameInput(), { key: 'Escape' }))
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(nameInput().getAttribute('aria-expanded')).toBe('false')

    act(() => fireEvent.focus(nameInput()))
    expect(screen.getByRole('listbox')).toBeTruthy()
    act(() => fireEvent.mouseDown(screen.getByRole('button', { name: 'Fuera' })))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('offers "Registrar como cliente nuevo", ticked by default when nobody matches', async () => {
    lookupMock.mockResolvedValue(answer())
    renderPicker()
    type(nameInput(), 'Marta Solís')
    type(phoneInput(), '2222-3333')
    const register = await screen.findByRole('checkbox', { name: 'Registrar como cliente nuevo' })
    await waitFor(() => expect((register as HTMLInputElement).checked).toBe(true))
    expect(screen.getByText('No hay clientes con esos datos en tus sucursales.')).toBeTruthy()
    expect(captured.current?.choice).toEqual({ kind: 'new', customer: { fullName: 'Marta Solís', phone: '2222-3333', allowDuplicatePhone: undefined } })

    act(() => fireEvent.click(register))
    expect(captured.current?.choice).toBeNull()
  })

  it('shows a customer of another branch without contact data', async () => {
    lookupMock.mockResolvedValue(answer(candidate(9, 'Marta Solís', null, false)))
    renderPicker()
    type(phoneInput(), '2222-3333')
    const option = await screen.findByRole('option')
    expect(option.textContent).toContain('cliente de otra sucursal')
    act(() => fireEvent.click(option))
    expect(captured.current?.choice).toEqual({ kind: 'existing', match: { id: 9, fullName: 'Marta Solís', inScope: false }, phone: '2222-3333' })
  })
})
