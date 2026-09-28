import { describe, expect, it } from 'vitest'
import { checkPassword } from './passwordPolicy'

const policy = { minLength: 12, maxBytes: 72 }

describe('checkPassword (mirrors users.PasswordPolicy)', () => {
  it('requires 12 characters', () => {
    expect(checkPassword('abcdefghijk', policy).longEnough).toBe(false)
    expect(checkPassword('abcdefghijkl', policy).longEnough).toBe(true)
    expect(checkPassword('            ', policy).longEnough).toBe(false)
  })

  it('counts UTF-8 bytes for the 72-byte maximum', () => {
    expect(checkPassword('a'.repeat(72), policy).withinMaximum).toBe(true)
    expect(checkPassword('a'.repeat(73), policy).withinMaximum).toBe(false)
    const accented = checkPassword('ñ'.repeat(37), policy)
    expect(accented.length).toBe(37)
    expect(accented.bytes).toBe(74)
    expect(accented.withinMaximum).toBe(false)
  })
})
