import { http } from '../../shared/api/httpClient'
import type { Page } from '../../shared/api/page'
import type { Role } from '../auth/authApi'
import type { BranchSummary } from '../branches/branchesApi'

/** Mirrors UserDtos.UserResponse. */
export interface User {
  id: number
  email: string
  fullName: string
  role: Role
  active: boolean
  branches: BranchSummary[]
  version: number
}


export interface CreateUserInput {
  email: string
  fullName: string
  role: Role
  password: string
  branchIds: number[]
}

export interface UpdateUserInput {
  fullName: string
  role: Role
  active: boolean
  branchIds: number[]
  version: number
}

export const userKeys = {
  all: ['users'] as const,
  page: (page: number) => ['users', 'page', page] as const,
}

export const PAGE_SIZE = 20

export const fetchUsers = (page: number, signal?: AbortSignal) =>
  http.get<Page<User>>(`/api/v1/users?page=${page}&size=${PAGE_SIZE}`, signal)

export const createUser = (input: CreateUserInput) => http.post<User>('/api/v1/users', input)

export const updateUser = (id: number, input: UpdateUserInput) => http.put<User>(`/api/v1/users/${id}`, input)

export const resetPassword = (id: number, newPassword: string, version: number) =>
  http.post<void>(`/api/v1/users/${id}/password-reset`, { newPassword, version })
