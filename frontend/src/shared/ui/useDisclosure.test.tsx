import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { useDisclosure } from './useDisclosure'

function Example() {
  const panel = useDisclosure()
  return (
    <>
      <button type="button" {...panel.triggerProps}>
        Agregar sucursal
      </button>
      {panel.open && (
        <div {...panel.panelProps}>
          <label htmlFor="code">Código</label>
          <input id="code" />
          <button type="button" onClick={panel.hide}>
            Cancelar
          </button>
        </div>
      )}
    </>
  )
}

afterEach(cleanup)

describe('useDisclosure (A.5)', () => {
  it('is closed by default and exposes aria-expanded/aria-controls', () => {
    render(<Example />)
    const trigger = screen.getByRole('button', { name: 'Agregar sucursal' })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByLabelText('Código')).toBeNull()
  })

  it('opens the panel, focuses the first field, and returns focus on cancel', () => {
    render(<Example />)
    const trigger = screen.getByRole('button', { name: 'Agregar sucursal' })

    act(() => fireEvent.click(trigger))
    const field = screen.getByLabelText('Código')
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(trigger.getAttribute('aria-controls')).toBe(field.closest('div')?.id)
    expect(document.activeElement).toBe(field)

    act(() => fireEvent.click(screen.getByRole('button', { name: 'Cancelar' })))
    expect(screen.queryByLabelText('Código')).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })
})
