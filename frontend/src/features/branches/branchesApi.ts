import { http } from '../../shared/api/httpClient'

/** Mirrors BranchDtos.BranchSummary. */
export interface BranchSummary {
  id: number
  code: string
  name: string
}

/** Mirrors BranchDtos.BranchResponse. */
export interface Branch extends BranchSummary {
  address: string | null
  active: boolean
  version: number
}

export interface BranchInput {
  code: string
  name: string
  address: string
}

export interface BranchUpdate {
  name: string
  address: string
  active: boolean
  version: number
}

export const branchKeys = {
  all: ['branches'] as const,
  detail: (id: number) => ['branches', id] as const,
}

export const fetchBranches = (signal?: AbortSignal) => http.get<Branch[]>('/api/v1/branches', signal)

export const fetchBranch = (id: number, signal?: AbortSignal) => http.get<Branch>(`/api/v1/branches/${id}`, signal)

export const createBranch = (input: BranchInput) => http.post<Branch>('/api/v1/branches', input)

export const updateBranch = (id: number, input: BranchUpdate) => http.put<Branch>(`/api/v1/branches/${id}`, input)
