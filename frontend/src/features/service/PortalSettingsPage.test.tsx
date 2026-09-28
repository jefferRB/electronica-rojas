import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PortalSettings } from './portalApi'
import { PortalSettingsPage } from './PortalSettingsPage'

const api = vi.hoisted(() => ({
  fetchPortalSettings: vi.fn(),
  updatePortalSettings: vi.fn(),
  changePortalSlug: vi.fn(),
}))
vi.mock('./portalApi', async (importOriginal) => ({ ...(await importOriginal<typeof import('./portalApi')>()), ...api }))

const settings: PortalSettings = {
  enabled: true,
  slug: 'servicio-a-domicilio',
  allowPreferredDate: true,
  allowPreferredWindow: true,
  minNoticeDays: 0,
  maxDaysAhead: 90,
  serviceDays: [1, 2, 3, 4, 5, 6, 7],
  servedProvinces: ['SAN_JOSE', 'HEREDIA'],
  serviceTypes: ['Lavadora'],
  welcomeMessage: null,
  successMessage: null,
  previousSlugs: [],
  updatedAt: '2026-09-28T15:00:00Z',
  updatedBy: null,
  version: 3,
}

const url = () => `${window.location.origin}/solicitar/servicio-a-domicilio`

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PortalSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.fetchPortalSettings.mockResolvedValue(settings)
  api.updatePortalSettings.mockImplementation(async (input) => ({ ...settings, ...input, version: 4 }))
  api.changePortalSlug.mockImplementation(async (slug: string) => ({
    ...settings,
    slug,
    previousSlugs: ['servicio-a-domicilio'],
    version: 4,
  }))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('PortalSettingsPage (BR-SRV-009)', () => {
  it('shows the shareable link, its QR code and the portal state', async () => {
    renderPage()

    expect(await screen.findByTestId('portal-url')).toHaveProperty('textContent', url())
    expect(screen.getByRole('img', { name: `Código QR que abre ${url()}` })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Descargar PNG/ })).toBeTruthy()
    expect(screen.getAllByRole('link', { name: /Abrir|Ver página pública/ }).every((link) => link.getAttribute('target') === '_blank')).toBe(true)
    const toggle = screen.getByRole('switch', { name: 'Aceptar solicitudes desde el portal público' })
    expect((toggle as HTMLInputElement).checked).toBe(true)
    expect(screen.getByText('Activo')).toBeTruthy()
  })

  it('copies the link and confirms it', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Copiar' }))
    expect(await screen.findByRole('button', { name: 'Copiado' })).toBeTruthy()
    expect(writeText).toHaveBeenCalledWith(url())
  })

  it('pausing the portal is a pending change until it is saved', async () => {
    renderPage()
    const toggle = await screen.findByRole('switch', { name: 'Aceptar solicitudes desde el portal público' })
    const save = screen.getByRole('button', { name: 'Guardar cambios' }) as HTMLButtonElement
    expect(save.disabled).toBe(true)

    fireEvent.click(toggle)
    expect(screen.getByText('Desactivado')).toBeTruthy()
    expect(screen.getByText('Cambios sin guardar')).toBeTruthy()
    fireEvent.click(save)

    await waitFor(() => expect(api.updatePortalSettings).toHaveBeenCalledTimes(1))
    expect(api.updatePortalSettings.mock.calls[0][0]).toMatchObject({ enabled: false, version: 3, maxDaysAhead: 90 })
    expect(await screen.findByText(/Cambios guardados/)).toBeTruthy()
  })

  it('does not let the latest day be before the minimum notice', async () => {
    renderPage()
    fireEvent.change(await screen.findByLabelText('Anticipación mínima (días)'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Máximo de días hacia adelante'), { target: { value: '5' } })

    expect(screen.getByText('No puede ser menor que la anticipación mínima.')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Guardar cambios' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('changing the address warns and needs an explicit confirmation', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Cambiar dirección/ }))
    const input = screen.getByLabelText('Dirección')

    fireEvent.change(input, { target: { value: 'Mi Portal' } })
    expect(screen.getByText(/letras minúsculas, números y guiones/)).toBeTruthy()

    fireEvent.change(input, { target: { value: 'electronica-perez' } })
    expect(screen.getByText('Cambiará el enlace que compartes.')).toBeTruthy()
    const confirm = screen.getByRole('button', { name: 'Confirmar nueva dirección' }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)

    fireEvent.click(screen.getByLabelText('Entiendo que el enlace del portal cambiará.'))
    fireEvent.click(confirm)
    await waitFor(() => expect(api.changePortalSlug).toHaveBeenCalledWith('electronica-perez', 3))
    expect(await screen.findByText(/Dirección actualizada/)).toBeTruthy()
    expect(screen.getByText('/solicitar/servicio-a-domicilio')).toBeTruthy()
  })
})
