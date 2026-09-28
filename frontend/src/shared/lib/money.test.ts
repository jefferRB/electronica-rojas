import { describe, expect, it } from 'vitest'
import { moneyToInput, parseAmount, parseMoney } from './money'

describe('parseAmount (one money convention for quotes, prices and costs)', () => {
  it('reads the ways people write colones', () => {
    expect(parseAmount('12500')).toBe(12500)
    expect(parseAmount('12 500,50')).toBe(12500.5)
    expect(parseAmount('₡12.500,5')).toBe(12500.5)
    expect(parseAmount('12,500.75')).toBe(12500.75)
    // A lone group separator is thousands, never decimals.
    expect(parseAmount('12.500')).toBe(12500)
    expect(parseAmount('0')).toBe(0)
  })

  it('refuses what the server would refuse', () => {
    expect(parseAmount('-5')).toBeNull()
    expect(parseAmount('10,555')).toBe(10555)
    expect(parseAmount('10.5555')).toBeNull()
    expect(parseAmount('12345678901')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
  })
})

describe('parseMoney (optional amount fields)', () => {
  it('treats an empty field as unknown and garbage as an error', () => {
    expect(parseMoney('  ')).toEqual({ ok: true, value: null })
    expect(parseMoney('8 000')).toEqual({ ok: true, value: 8000 })
    expect(parseMoney('ocho mil')).toEqual({ ok: false })
  })

  it('round-trips a stored value into the input', () => {
    expect(moneyToInput(12500.5)).toBe('12500,5')
    expect(parseMoney(moneyToInput(12500.5))).toEqual({ ok: true, value: 12500.5 })
    expect(moneyToInput(null)).toBe('')
  })
})
