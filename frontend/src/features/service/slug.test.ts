import { describe, expect, it } from 'vitest'
import { slugify, slugProblem } from './slug'

describe('portal slug rules (mirror of PortalSlugs)', () => {
  it('accepts lower-case letters, digits and single hyphens', () => {
    expect(slugProblem('servicio-a-domicilio')).toBeNull()
    expect(slugProblem('taller24')).toBeNull()
  })

  it('rejects spaces, accents, double hyphens, short slugs and reserved words', () => {
    expect(slugProblem('mi portal')).not.toBeNull()
    expect(slugProblem('electrónica')).not.toBeNull()
    expect(slugProblem('dos--guiones')).not.toBeNull()
    expect(slugProblem('ab')).not.toBeNull()
    expect(slugProblem('admin')).toMatch(/sistema/)
  })

  it('suggests a valid slug from a business name', () => {
    expect(slugify('Electrónica Pérez & Hijos')).toBe('electronica-perez-hijos')
    expect(slugProblem(slugify('  Taller Ñandú  '))).toBeNull()
  })
})
