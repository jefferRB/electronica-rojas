import type { Page } from '../../shared/api/page'
import { http } from '../../shared/api/httpClient'
import type { DecisionMethod, QuoteStatus, RepairStatus, Resolution } from '../../shared/i18n/labels'
import type { BranchSummary } from '../branches/branchesApi'
import type { NewCustomer } from '../customers/customersApi'
import type { ProductSummary, SparePartOption } from '../inventory/inventoryApi'
import type { NotificationStatus } from '../notifications/notificationsApi'

export interface PersonSummary {
  id: number
  fullName: string
}

export interface Device {
  type: string
  brand: string
  model: string | null
  serialNumber: string | null
}

/** Mirrors RepairDtos.RepairOrderSummary. */
export interface RepairOrderSummary {
  id: number
  orderCode: string
  status: RepairStatus
  resolution: Resolution | null
  inCustody: boolean
  branch: BranchSummary
  customer: PersonSummary
  device: Device
  technician: PersonSummary | null
  receivedAt: string
}

export interface StatusChange {
  fromStatus: RepairStatus | null
  toStatus: RepairStatus
  reason: string | null
  actor: PersonSummary
  changedAt: string
}

export interface Quote {
  id: number
  /** BigDecimal on the server, serialized as a JSON number with two decimals. */
  amount: number
  currency: 'CRC'
  description: string
  status: QuoteStatus
  createdBy: PersonSummary
  createdAt: string
  decidedBy: PersonSummary | null
  decidedAt: string | null
  decisionMethod: DecisionMethod | null
  decisionNote: string | null
}

/** What the caller may do now; computed by the server's RepairPolicy (which also enforces it). */
export interface OrderActions {
  transitions: { toStatus: RepairStatus; reasonRequired: boolean }[]
  canAssignTechnician: boolean
  canEditDiagnosis: boolean
  canCreateQuote: boolean
  canDecideQuote: boolean
  /** Workshop (admin, manager, assigned technician) while the order is IN_REPAIR. */
  canConsumeParts: boolean
  /** Open order: workshop; closed order: management only. False when nothing is left to return. */
  canReturnParts: boolean
  /** Management may set another price or charging decision for a new part line. */
  canOverridePartPricing: boolean
}

/** A correction of a consumed part (units back to the shelf); the original line is never changed. */
export interface PartReturn {
  id: number
  quantity: number
  reason: string
  orderStatus: RepairStatus
  recordedBy: PersonSummary
  recordedAt: string
}

/**
 * Mirrors RepairDtos.PartUsage. The price, cost and charging decision were frozen when the part was
 * used (BR-REP-014). `chargedAmount`: price x units in use when charged, 0 when not, null when it
 * is charged but has no price yet. `unitCost` only reaches roles that may see costs.
 */
export interface PartUsage {
  id: number
  product: ProductSummary
  branch: BranchSummary
  quantity: number
  returnedQuantity: number
  remainingQuantity: number
  unitPrice: number | null
  unitCost?: number | null
  chargeable: boolean
  priceOverridden: boolean
  chargedAmount: number | null
  note: string | null
  recordedBy: PersonSummary
  recordedAt: string
  returns: PartReturn[]
}

/**
 * Totals of the parts still in use. Shown next to the quotes, never added to them: a quote is an
 * amount the customer approved as a whole. `totalCost` only reaches roles that may see costs.
 */
export interface PartsSummary {
  linesInUse: number
  unitsInUse: number
  unitsWithoutCharge: number
  chargeableSubtotal: number
  unpricedLines: number
  totalCost?: number | null
  uncostedLines: number
  currency: 'CRC'
}

/** Mirrors RepairDtos.RepairOrderDetail. Customer phone/email are null for technicians. */
export interface RepairOrderDetail {
  id: number
  orderCode: string
  status: RepairStatus
  resolution: Resolution | null
  inCustody: boolean
  branch: BranchSummary
  customer: { id: number; fullName: string; phone: string | null; email: string | null }
  device: Device
  reportedFault: string
  physicalCondition: string
  accessories: string | null
  technician: PersonSummary | null
  diagnosis: string | null
  diagnosisUpdatedAt: string | null
  diagnosisUpdatedBy: PersonSummary | null
  receivedAt: string
  receivedBy: PersonSummary
  deliveredAt: string | null
  deliveredBy: PersonSummary | null
  version: number
  history: StatusChange[]
  quotes: Quote[]
  parts: PartUsage[]
  partsSummary: PartsSummary
  /** Customer notices about this order (FR-REP-004: shown apart from the business status). */
  notifications: NotificationStatus[]
  actions: OrderActions
}

export interface RepairFilters {
  branchId?: number
  status?: RepairStatus | ''
  customerId?: number
  search?: string
  from?: string
  to?: string
  page: number
}

export interface ReceptionInput {
  operationId: string
  branchId: number
  customerId?: number
  /** Proof for a customer outside the caller's scope: the phone the customer just gave. */
  customerPhone?: string
  newCustomer?: NewCustomer
  deviceType: string
  brand: string
  model?: string
  serialNumber?: string
  reportedFault: string
  physicalCondition: string
  accessories?: string
}

export const repairKeys = {
  all: ['repairs'] as const,
  list: (filters: RepairFilters) => ['repairs', 'list', filters] as const,
  detail: (id: number) => ['repairs', 'detail', id] as const,
  technicians: (branchId: number) => ['repairs', 'technicians', branchId] as const,
  partOptions: (orderId: number, search: string) => ['repairs', 'part-options', orderId, search] as const,
}

export function fetchRepairOrders(filters: RepairFilters, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(filters.page), size: '20' })
  if (filters.branchId) params.set('branchId', String(filters.branchId))
  if (filters.status) params.set('status', filters.status)
  if (filters.customerId) params.set('customerId', String(filters.customerId))
  if (filters.search?.trim()) params.set('search', filters.search.trim())
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  return http.get<Page<RepairOrderSummary>>(`/api/v1/repair-orders?${params}`, signal)
}

export const fetchRepairOrder = (id: number, signal?: AbortSignal) =>
  http.get<RepairOrderDetail>(`/api/v1/repair-orders/${id}`, signal)

export const fetchTechnicians = (branchId: number, signal?: AbortSignal) =>
  http.get<PersonSummary[]>(`/api/v1/repair-orders/technicians?branchId=${branchId}`, signal)

export const receiveRepairOrder = (input: ReceptionInput) => http.post<RepairOrderDetail>('/api/v1/repair-orders', input)

export const changeStatus = (id: number, toStatus: RepairStatus, reason?: string) =>
  http.post<RepairOrderDetail>(`/api/v1/repair-orders/${id}/status`, { toStatus, reason })

export const assignTechnician = (id: number, technicianId: number) =>
  http.put<RepairOrderDetail>(`/api/v1/repair-orders/${id}/technician`, { technicianId })

export const updateDiagnosis = (id: number, diagnosis: string, version: number) =>
  http.put<RepairOrderDetail>(`/api/v1/repair-orders/${id}/diagnosis`, { diagnosis, version })

export const createQuote = (id: number, amount: number, description: string) =>
  http.post<RepairOrderDetail>(`/api/v1/repair-orders/${id}/quotes`, { amount, description })

export const decideQuote = (
  id: number,
  quoteId: number,
  decision: 'APPROVED' | 'REJECTED',
  method: DecisionMethod,
  note?: string,
) => http.post<RepairOrderDetail>(`/api/v1/repair-orders/${id}/quotes/${quoteId}/decision`, { decision, method, note })

/** Spare parts of the order's branch with their stock (the technician's only view of inventory). */
export function fetchPartOptions(orderId: number, search: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ size: '8' })
  if (search) params.set('search', search)
  return http.get<Page<SparePartOption>>(`/api/v1/repair-orders/${orderId}/parts/options?${params}`, signal)
}

/** `unitPrice` / `chargeable` omitted = the catalog defaults; other values need management. */
export const consumePart = (
  orderId: number,
  input: { operationId: string; productId: number; quantity: number; note?: string; unitPrice?: number; chargeable?: boolean },
) => http.post<RepairOrderDetail>(`/api/v1/repair-orders/${orderId}/parts`, input)

export const returnPart = (
  orderId: number,
  usageId: number,
  input: { operationId: string; quantity: number; reason: string },
) => http.post<RepairOrderDetail>(`/api/v1/repair-orders/${orderId}/parts/${usageId}/returns`, input)
