import { http } from '../../shared/api/httpClient'
import type { BranchSummary } from '../branches/branchesApi'
import type { Page } from '../../shared/api/page'

import type { MovementType, ProductKind, StockStatus } from '../../shared/i18n/labels'

export type { MovementType, ProductKind, StockStatus }
export type StatusFilter = 'ACTIVE' | 'INACTIVE' | 'ALL'
export type StockStatusFilter = 'OUT_OF_STOCK' | 'LOW' | 'ATTENTION'
export type StandaloneMovementType = Extract<MovementType, 'RECEIPT' | 'ISSUE' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT'>

export const STANDALONE_TYPES: StandaloneMovementType[] = ['RECEIPT', 'ISSUE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT']

/**
 * Mirrors InventoryDtos.ProductResponse. Money in colones (JSON numbers from BigDecimal); null =
 * unknown. The server leaves `unitCost` out for roles that may not see costs.
 */
export interface Product {
  id: number
  sku: string
  name: string
  category: string
  description: string | null
  kind: ProductKind
  unit: string
  active: boolean
  salePrice: number | null
  unitCost?: number | null
  /** Default when the part is used in a repair; each line may decide otherwise. */
  chargeableByDefault: boolean
  version: number
}

export interface ProductSummary {
  id: number
  sku: string
  name: string
  category: string
  kind: ProductKind
  active: boolean
}

export interface BranchStockEntry {
  branch: BranchSummary
  branchActive: boolean
  quantity: number
  minimumQuantity: number
  stockStatus: StockStatus
  updatedAt: string | null
}

export interface ProductDetail {
  product: Product
  stock: BranchStockEntry[]
}

export interface StockItem {
  product: ProductSummary
  quantity: number
  minimumQuantity: number
  stockStatus: StockStatus
  updatedAt: string | null
}

/** Mirrors InventoryDtos.SparePartOption: stock at the order's branch plus the line defaults (never the cost). */
export interface SparePartOption extends StockItem {
  salePrice: number | null
  chargeableByDefault: boolean
}

/** Mirrors InventoryDtos.StockOverviewResponse: dynamic branch columns + one page of rows. */
export interface StockOverview {
  branches: BranchSummary[]
  rows: Page<{
    product: ProductSummary
    totalQuantity: number
    outOfStockBranches: number
    lowBranches: number
    cells: { branchId: number; quantity: number; minimumQuantity: number; stockStatus: StockStatus }[]
  }>
}

export interface Movement {
  id: number
  operationId: string
  type: MovementType
  branch: BranchSummary
  product: ProductSummary
  quantityDelta: number
  balanceBefore: number
  balanceAfter: number
  reason: string | null
  actor: { id: number; fullName: string }
  createdAt: string
  transferId: number | null
  /** Set on OUT_FOR_REPAIR / RETURN_FROM_REPAIR: the repair order the part was used in. */
  repairOrderId: number | null
  repairOrderCode: string | null
}

export interface ProductFilters {
  search?: string
  category?: string
  kind?: ProductKind | ''
  status?: StatusFilter
  page?: number
  size?: number
}

export interface StockFilters extends ProductFilters {
  stockStatus?: StockStatusFilter | ''
}

export interface ProductFields {
  name: string
  category: string
  description: string
  kind: ProductKind
  unitCost: number | null
  salePrice: number | null
  chargeableByDefault: boolean
}

export interface ProductInput extends ProductFields {
  sku: string
  /** Units already on the shelf of one branch, recorded as a receipt with the product. */
  initialStock?: { branchId: number; quantity: number }
}

export interface ProductUpdate extends ProductFields {
  active: boolean
  version: number
}

export interface MovementInput {
  operationId: string
  branchId: number
  productId: number
  type: StandaloneMovementType
  quantity: number
  reason: string
}

function query(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '' && value !== false) search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const inventoryKeys = {
  products: (filters: ProductFilters) => ['products', filters] as const,
  product: (id: number) => ['products', 'detail', id] as const,
  categories: ['products', 'categories'] as const,
  stock: (branchId: number, filters: StockFilters) => ['stock', branchId, filters] as const,
  overview: (filters: StockFilters) => ['stock', 'overview', filters] as const,
  movements: (branchId: number, filters: Record<string, unknown>) => ['movements', branchId, filters] as const,
}

export const fetchProducts = (filters: ProductFilters, signal?: AbortSignal) =>
  http.get<Page<Product>>(`/api/v1/products${query({ ...filters })}`, signal)

export const fetchCategories = (signal?: AbortSignal) => http.get<string[]>('/api/v1/products/categories', signal)

export const fetchProduct = (id: number, signal?: AbortSignal) => http.get<ProductDetail>(`/api/v1/products/${id}`, signal)

export const createProduct = (input: ProductInput) => http.post<Product>('/api/v1/products', input)

export const updateProduct = (id: number, input: ProductUpdate) => http.put<Product>(`/api/v1/products/${id}`, input)

export const fetchStock = (branchId: number, filters: StockFilters, signal?: AbortSignal) =>
  http.get<Page<StockItem>>(`/api/v1/branches/${branchId}/stock${query({ ...filters })}`, signal)

export const fetchStockOverview = (filters: StockFilters, signal?: AbortSignal) =>
  http.get<StockOverview>(`/api/v1/stock/overview${query({ ...filters })}`, signal)

export const updateMinimum = (branchId: number, productId: number, minimumQuantity: number) =>
  http.put<StockItem>(`/api/v1/branches/${branchId}/stock/${productId}/minimum`, { minimumQuantity })

export const fetchMovements = (
  branchId: number,
  filters: { productId?: number; type?: MovementType | ''; page?: number; size?: number },
  signal?: AbortSignal,
) => http.get<Page<Movement>>(`/api/v1/branches/${branchId}/movements${query({ ...filters })}`, signal)

export const recordMovement = (input: MovementInput) => http.post<Movement>('/api/v1/stock-movements', input)
