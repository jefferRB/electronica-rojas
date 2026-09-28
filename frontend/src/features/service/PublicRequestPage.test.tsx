import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PublicPortal } from './portalApi'
import { PublicRequestPage } from './PublicRequestPage'
import { describeServiceDays } from './serviceDays'

const api = vi.hoisted(() => ({ fetchPublicPortal: vi.fn() }))
vi.mock('./portalApi', async (importOriginal) => ({ ...(await importOriginal<typeof import('./portalApi')>()), ...api }))

const open: PublicPortal = {
  slug: 'electronica-perez',
  accepting: true,
  welcomeMessage: 'Contanos qué equipo necesitás reparar. <b>Gracias</b>',
  successMessage: null,
  rules: {
    allowPreferredDate: true,
    allowPreferredWindow: false,
    earliestDate: '2026-10-01',
    latestDate: '2026-10-31',
    serviceDays: [1, 2, 3, 4, 5],
    servedProvinces: ['SAN_JOSE', 'HEREDIA'],
    serviceTypes: ['Lavadora', 'Refrigeradora'],
  },
  branches: [{ id: 1, name: 'San José Centro' }],
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/solicitar/:slug"
            element={
              <>
                <PublicRequestPage />
                <Where />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('PublicRequestPage (BR-SRV-009)', () => {
  it('renders the configured rules; the request is never presented as a booking', async () => {
    api.fetchPublicPortal.mockResolvedValue(open)
    renderAt('/solicitar/electronica-perez')

    expect(await screen.findByRole('heading', { name: 'Solicitar reparación a domicilio' })).toBeTruthy()
    // Messages are text: markup shows literally, it is never interpreted.
    expect(screen.getByText('Contanos qué equipo necesitás reparar. <b>Gracias</b>')).toBeTruthy()
    expect(screen.getByText(/el horario quedará confirmado cuando el equipo la apruebe/)).toBeTruthy()
    // Only served provinces; no window selector when the business does not take it; one branch = no choice.
    const provinces = Array.from((screen.getByLabelText('Provincia') as HTMLSelectElement).options).map((o) => o.text)
    expect(provinces).toEqual(['Selecciona', 'San José', 'Heredia'])
    expect(screen.queryByLabelText('Horario preferido')).toBeNull()
    expect(screen.queryByLabelText('Sucursal más cercana')).toBeNull()
    expect((screen.getByLabelText('Fecha preferida (opcional)') as HTMLInputElement).min).toBe('2026-10-01')
    // Published appliance types, plus "Otro" with free text.
    fireEvent.change(screen.getByLabelText('Tipo de electrodoméstico'), { target: { value: '__other__' } })
    expect(screen.getByLabelText('¿Qué equipo es?')).toBeTruthy()
    // The customer page does not advertise the staff login.
    expect(screen.queryByText(/Inicia sesión/)).toBeNull()
  })

  it('a previous address opens the portal and shows the current one', async () => {
    api.fetchPublicPortal.mockResolvedValue(open)
    renderAt('/solicitar/servicio-a-domicilio')

    expect(await screen.findByRole('heading', { name: 'Solicitar reparación a domicilio' })).toBeTruthy()
    expect(api.fetchPublicPortal).toHaveBeenCalledWith('servicio-a-domicilio', expect.anything())
    // The page replaces the old address once the portal has loaded: wait for that navigation.
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/solicitar/electronica-perez'))
  })

  it('a paused portal explains it and shows no form', async () => {
    api.fetchPublicPortal.mockResolvedValue({ ...open, accepting: false, rules: null, branches: [] })
    renderAt('/solicitar/electronica-perez')

    expect(await screen.findByRole('heading', { name: 'Por ahora no recibimos solicitudes en línea' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Enviar solicitud' })).toBeNull()
  })

  it('describes the service days in words', () => {
    expect(describeServiceDays([1, 2, 3, 4, 5])).toBe('lunes a viernes')
    expect(describeServiceDays([1, 3, 6])).toBe('lunes, miércoles y sábado')
    expect(describeServiceDays([1, 2, 3, 4, 5, 6, 7])).toBe('todos los días')
  })
})
