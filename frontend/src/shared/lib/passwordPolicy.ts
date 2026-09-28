/** Mirrors users.PasswordPolicy.Description (GET /api/v1/auth/password-policy). */
export interface PasswordPolicy {
  minLength: number
  maxBytes: number
}

export interface PasswordChecks {
  /** Characters as the backend counts them (Unicode code points). */
  length: number
  /** UTF-8 bytes, the unit of BCrypt's 72-byte limit. */
  bytes: number
  longEnough: boolean
  withinMaximum: boolean
}

/** Same rules as the backend: code points for the minimum, UTF-8 bytes for the maximum. */
export function checkPassword(password: string, policy: PasswordPolicy): PasswordChecks {
  const length = [...password].length
  const bytes = new TextEncoder().encode(password).length
  return {
    length,
    bytes,
    longEnough: password.trim().length > 0 && length >= policy.minLength,
    withinMaximum: bytes <= policy.maxBytes,
  }
}
