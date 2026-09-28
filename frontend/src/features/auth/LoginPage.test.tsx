import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

vi.mock('./session', () => ({
  useSession: () => ({ data: null }),
  useLogin: () => ({ mutate: vi.fn(), isPending: false, error: null }),
}))

afterEach(cleanup)

describe('LoginPage', () => {
  it('is for collaborators only: no customer portal entry and no sign-up', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
    expect(screen.getByLabelText('Correo electrónico')).toBeTruthy()
    expect(screen.queryByText(/Eres cliente/)).toBeNull()
    expect(screen.queryByText(/Solicita una reparación/)).toBeNull()
    expect(document.querySelector('a[href*="solicitar"]')).toBeNull()
    expect(screen.queryByText(/Regístrate|Crear cuenta/)).toBeNull()
  })
})
