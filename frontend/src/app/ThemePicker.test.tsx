import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { applyTheme, readTheme } from '../shared/ui/theme'
import { ThemePicker } from './ThemePicker'

afterEach(() => {
  cleanup()
  localStorage.clear()
  applyTheme('electric')
})

describe('ThemePicker (palettes)', () => {
  it('defaults to electric blue and applies another palette at once', () => {
    render(<ThemePicker />)
    expect((screen.getByRole('radio', { name: /Azul eléctrico/ }) as HTMLInputElement).checked).toBe(true)
    fireEvent.click(screen.getByRole('radio', { name: /Petróleo profesional/ }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('petroleum')
    expect(readTheme()).toBe('petroleum')
  })

  it('ignores unknown stored values', () => {
    localStorage.setItem('electronica-rojas.theme', 'neon')
    expect(readTheme()).toBe('electric')
  })

  it('removes the attribute for the default palette', () => {
    applyTheme('indigo')
    applyTheme('electric')
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
  })
})
