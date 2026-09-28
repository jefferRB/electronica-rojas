import { describe, expect, it } from 'vitest'
import { initials } from './initials'

describe('initials', () => {
  it('takes the first letter of the first and last words', () => {
    expect(initials('Sofía Jiménez Rojas')).toBe('SR')
    expect(initials('Restaurante La Esquina del Sabor S.A. (Administración General)')).toBe('RG')
  })

  it('handles one word, accents and empty names', () => {
    expect(initials('Ángela')).toBe('ÁN')
    expect(initials('  ')).toBe('?')
  })
})
