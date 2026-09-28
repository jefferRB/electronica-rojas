import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { MoreMenu } from './MoreMenu'

const links = [
  { to: '/transfers', label: 'Transferencias' },
  { to: '/notifications', label: 'Notificaciones' },
]

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <MoreMenu links={links} />
      <p>Fuera</p>
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('MoreMenu (D.3)', () => {
  it('is a disclosure: closed by default, opens with its button and closes with Escape', () => {
    renderAt('/')
    const button = screen.getByRole('button', { name: /Más/ })
    expect(button.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('link', { name: 'Notificaciones' })).toBeNull()

    fireEvent.click(button)
    expect(button.getAttribute('aria-expanded')).toBe('true')
    expect(button.getAttribute('aria-controls')).toBe(screen.getByRole('list').id)
    expect(screen.getByRole('link', { name: 'Notificaciones' }).getAttribute('href')).toBe('/notifications')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('list')).toBeNull()
    expect(document.activeElement).toBe(button)
  })

  it('closes on a click outside and marks itself when the page is one of its links', () => {
    renderAt('/notifications')
    const button = screen.getByRole('button', { name: /Más/ })
    expect(button.className).toBe('active')
    fireEvent.click(button)
    fireEvent.pointerDown(screen.getByText('Fuera'))
    expect(screen.queryByRole('list')).toBeNull()
  })

  it('renders nothing without links', () => {
    const { container } = render(
      <MemoryRouter>
        <MoreMenu links={[]} />
      </MemoryRouter>,
    )
    expect(container.innerHTML).toBe('')
  })
})
