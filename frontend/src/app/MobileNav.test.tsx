import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createRef, type ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SelectedBranchProvider } from '../features/branches/SelectedBranchProvider'
import { BottomNav, MobileDrawer } from './MobileNav'
import { bottomNav, primaryNav, secondaryNav } from './navigation'
import { UserMenu } from './UserMenu'

const user = { id: 1, email: 'ana@demo.test', fullName: 'Ana Solís', role: 'ADMIN' as const }
const branches = [{ id: 1, code: 'CEN', name: 'San José Centro' }]

function wrap(children: ReactNode) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <SelectedBranchProvider userId={1} branches={branches}>
          {children}
        </SelectedBranchProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

afterEach(() => {
  cleanup()
  document.body.classList.remove('scroll-locked')
})

describe('MobileDrawer (phones and tablets)', () => {
  function renderDrawer(open: boolean, onClose = vi.fn()) {
    const opener = createRef<HTMLButtonElement>()
    const view = render(
      wrap(
        <>
          <button ref={opener} type="button">
            Menú
          </button>
          <MobileDrawer
            open={open}
            onClose={onClose}
            returnFocusTo={opener}
            user={user}
            primary={primaryNav('ADMIN')}
            secondary={secondaryNav('ADMIN')}
            loggingOut={false}
            onLogout={vi.fn()}
          />
        </>,
      ),
    )
    return { ...view, opener, onClose }
  }

  it('is a labelled modal dialog that takes the focus and locks the page scroll', () => {
    renderDrawer(true)
    const dialog = screen.getByRole('dialog', { name: 'Menú' })
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cerrar menú' }))
    expect(document.body.classList.contains('scroll-locked')).toBe(true)
    // Grouped sections, with the administrative modules apart.
    expect(screen.getByRole('navigation', { name: 'Operación' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Auditoría' }).getAttribute('href')).toBe('/audit')
  })

  it('closes with Escape and keeps Tab inside the dialog', () => {
    const { onClose } = renderDrawer(true)
    const close = screen.getByRole('button', { name: 'Cerrar menú' })
    const logout = screen.getByRole('button', { name: 'Cerrar sesión' })
    logout.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(document.activeElement).toBe(close)
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(logout)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })

  it('returns the focus to the button that opened it and unlocks the scroll', () => {
    const { rerender, opener } = renderDrawer(true)
    rerender(
      wrap(
        <>
          <button ref={opener} type="button">
            Menú
          </button>
          <MobileDrawer
            open={false}
            onClose={vi.fn()}
            returnFocusTo={opener}
            user={user}
            primary={[]}
            secondary={[]}
            loggingOut={false}
            onLogout={vi.fn()}
          />
        </>,
      ),
    )
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.body.classList.contains('scroll-locked')).toBe(false)
    expect(document.activeElement).toBe(opener.current)
  })
})

describe('BottomNav', () => {
  it('keeps the visible text inside each accessible name and opens the menu', () => {
    const onMenu = vi.fn()
    render(wrap(<BottomNav items={bottomNav('RECEPTIONIST')} menuOpen={false} onMenu={onMenu} />))
    expect(screen.getByRole('link', { name: 'Taller: Reparaciones' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'A domicilio' })).toBeTruthy()
    const menu = screen.getByRole('button', { name: 'Menú' })
    expect(menu.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(menu)
    expect(onMenu).toHaveBeenCalledWith(menu)
  })

  it('never lists modules the role cannot open', () => {
    const technician = bottomNav('TECHNICIAN').map((item) => item.label)
    expect(technician).toEqual(['Inicio', 'Reparaciones', 'Mis visitas'])
    expect(secondaryNav('RECEPTIONIST')).toEqual([])
  })
})

describe('UserMenu', () => {
  it('opens the account panel and closes it with Escape, returning the focus', () => {
    render(wrap(<UserMenu user={user} loggingOut={false} onLogout={vi.fn()} />))
    const button = screen.getByRole('button', { name: /Ana Solís/ })
    expect(button.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByRole('group', { name: 'Paleta de colores' })).toBeTruthy()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('group', { name: 'Paleta de colores' })).toBeNull()
    expect(document.activeElement).toBe(button)
  })
})
