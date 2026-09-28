import { ApiError, http } from '../../shared/api/httpClient'
import type { BranchSummary } from '../branches/branchesApi'

import type { Role } from '../../shared/i18n/labels'

export type { Role }

export const ROLES: Role[] = ['ADMIN', 'BRANCH_MANAGER', 'RECEPTIONIST', 'TECHNICIAN']

/** Mirrors SessionController.SessionResponse. */
export interface Session {
  user: { id: number; email: string; fullName: string; role: Role }
  /** Active branches the user may work in (the selector options). */
  branches: BranchSummary[]
}

/** Returns null when nobody is logged in (401 is an expected answer here, not an error). */
export async function fetchSession(): Promise<Session | null> {
  try {
    return await http.get<Session>('/api/v1/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

/** Form-encoded, as expected by Spring Security's form login filter. */
export function login(email: string, password: string): Promise<void> {
  return http.postForm<void>('/api/v1/auth/login', { email, password })
}

export function logout(): Promise<void> {
  return http.post<void>('/api/v1/auth/logout')
}
