import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { ModuleSurface } from './ModuleSurface'

afterEach(cleanup)

describe('ModuleSurface', () => {
  it('renders the header as the first band of the surface, with its action inside', () => {
    const { container } = render(
      <ModuleSurface
        eyebrow="Taller"
        title="Reparaciones"
        description="Equipos recibidos."
        actions={<button type="button">Recibir equipo</button>}
      >
        <nav aria-label="Estado">Todas</nav>
      </ModuleSurface>,
    )
    const surface = container.firstElementChild!
    expect(surface.className).toBe('card panel module-surface')
    expect(surface.firstElementChild?.tagName).toBe('HEADER')
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(surface.contains(screen.getByRole('button', { name: 'Recibir equipo' }))).toBe(true)
    // Bands follow the header in reading order.
    expect(surface.children[1]).toBe(screen.getByRole('navigation', { name: 'Estado' }))
  })

  it('keeps extra classes for module-specific layouts', () => {
    const { container } = render(<ModuleSurface title="Agenda" className="agenda-card" />)
    expect(container.firstElementChild?.className).toBe('card panel module-surface agenda-card')
  })
})
