import { http } from '../../shared/api/httpClient'
import type { BranchSummary } from '../branches/branchesApi'
import type { Movement, ProductSummary } from '../inventory/inventoryApi'

/** Mirrors InventoryDtos.TransferResponse. */
export interface Transfer {
  id: number
  operationId: string
  product: ProductSummary
  source: BranchSummary
  destination: BranchSummary
  quantity: number
  reason: string | null
  sourceBalanceAfter: number
  destinationBalanceAfter: number
  actor: { id: number; fullName: string }
  createdAt: string
  movements: Movement[]
}

export interface TransferInput {
  operationId: string
  sourceBranchId: number
  destinationBranchId: number
  productId: number
  quantity: number
  reason: string
}

export const transferKeys = {
  detail: (id: number) => ['transfers', id] as const,
}

export const createTransfer = (input: TransferInput) => http.post<Transfer>('/api/v1/stock-transfers', input)

export const fetchTransfer = (id: number, signal?: AbortSignal) =>
  http.get<Transfer>(`/api/v1/stock-transfers/${id}`, signal)
