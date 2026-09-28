import type { Page } from '../../shared/api/page'
import { http } from '../../shared/api/httpClient'
import type { NotificationStatus } from '../notifications/notificationsApi'
import type { BranchSummary } from '../branches/branchesApi'
import type {
  PreferredWindow,
  Province,
  PublicStatus,
  RequestStatus,
  ServiceEventType,
  VisitOutcome,
  VisitStatus,
} from '../../shared/i18n/serviceLabels'

export interface PersonRef {
  id: number
  fullName: string
}

// ---- Public ----

export interface PublicBranch {
  id: number
  name: string
}

export interface PublicSubmission {
  submissionId: string
  branchId: number
  contactName: string
  contactPhone: string
  contactEmail?: string
  province: Province
  canton: string
  district?: string
  addressLine: string
  deviceType: string
  brand?: string
  model?: string
  problemDescription: string
  preferredDate?: string
  preferredWindow: PreferredWindow
  additionalNotes?: string
  contactConsent: boolean
  /** Channels the customer accepts notices on (C.1); e-mail requires contactEmail. */
  emailNotifications: boolean
  whatsappNotifications: boolean
  /** Version of the consent text shown; required when a channel is accepted. */
  consentTextVersion?: string
  /** Honeypot: always empty for people. */
  website: string
}

export interface PublicReceipt {
  publicRef: string
  requestCode: string | null
  status: PublicStatus
}

export interface PublicRequestStatus {
  requestCode: string
  status: PublicStatus
  branchName: string
  deviceType: string
  preferredDate: string | null
  preferredWindow: PreferredWindow
  visitDate: string | null
  visitFrom: string | null
  visitTo: string | null
  submittedAt: string
}

export const submitPublicRequest = (form: PublicSubmission) =>
  http.post<PublicReceipt>('/api/v1/public/service-requests', form)

export const fetchPublicStatus = (ref: string, signal?: AbortSignal) =>
  http.get<PublicRequestStatus>(`/api/v1/public/service-requests/${encodeURIComponent(ref)}`, signal)

// ---- Internal ----

export interface VisitBrief {
  id: number
  status: VisitStatus
  start: string
  end: string
  technician: PersonRef
}

export interface ServiceRequestSummary {
  id: number
  requestCode: string
  status: RequestStatus
  channel: 'PUBLIC_FORM' | 'STAFF'
  branch: BranchSummary
  contactName: string
  customer: PersonRef | null
  deviceType: string
  province: Province
  canton: string
  preferredDate: string | null
  preferredWindow: PreferredWindow
  createdAt: string
  activeVisit: VisitBrief | null
}

export interface RequestActions {
  canStartReview: boolean
  canLinkCustomer: boolean
  canChangeBranch: boolean
  canSchedule: boolean
  canReject: boolean
  canCancel: boolean
}

export interface ServiceEvent {
  type: ServiceEventType
  fromStatus: string | null
  toStatus: string | null
  details: Record<string, unknown> | null
  reason: string | null
  actor: PersonRef | null
  occurredAt: string
  visitId: number | null
}

export interface VisitActions {
  canConfirm: boolean
  canReschedule: boolean
  canCancel: boolean
  canStart: boolean
  canComplete: boolean
  canLinkRepairOrder: boolean
}

/** Mirrors ServiceDtos.VisitView; contact and address are null when the user may not see them. */
export interface Visit {
  id: number
  requestId: number
  requestCode: string
  status: VisitStatus
  branch: BranchSummary
  technician: PersonRef
  start: string
  end: string
  blockedUntil: string
  customer: PersonRef | null
  contactName: string
  contactPhone: string | null
  province: Province
  canton: string
  district: string | null
  addressLine: string | null
  deviceType: string
  brand: string | null
  model: string | null
  problemDescription: string
  startedAt: string | null
  completedAt: string | null
  outcome: VisitOutcome | null
  outcomeNotes: string | null
  cancelReason: string | null
  repairOrder: { id: number; orderCode: string } | null
  version: number
  actions: VisitActions
}

export interface ServiceRequestDetail {
  id: number
  requestCode: string
  status: RequestStatus
  channel: 'PUBLIC_FORM' | 'STAFF'
  branch: BranchSummary
  customer: PersonRef | null
  contactName: string
  contactPhone: string
  contactEmail: string | null
  province: Province
  canton: string
  district: string | null
  addressLine: string
  deviceType: string
  brand: string | null
  model: string | null
  problemDescription: string
  preferredDate: string | null
  preferredWindow: PreferredWindow
  additionalNotes: string | null
  notificationsConsent: boolean
  /** Channel opt-ins of the public form; applied to the customer when staff links them. */
  emailConsent: boolean
  whatsappConsent: boolean
  contactConsentAt: string
  decisionReason: string | null
  createdBy: PersonRef | null
  createdAt: string
  version: number
  visits: Visit[]
  history: ServiceEvent[]
  /** Notices about this request's visits (confirmed, rescheduled, cancelled). */
  notifications: NotificationStatus[]
  actions: RequestActions
}

export interface RequestFilters {
  status?: RequestStatus | ''
  branchId?: number
  customerId?: number
  search?: string
  page: number
}

export interface ShiftView {
  dayOfWeek: number
  branch: BranchSummary
  start: string
  end: string
  breakStart: string | null
  breakEnd: string | null
}

export interface Availability {
  date: string
  technician: PersonRef
  shift: ShiftView | null
  durationMinutes: number
  bufferMinutes: number
  freeStarts: string[]
  confirmed: { start: string; end: string; blockedUntil: string; requestCode: string }[]
}

export interface TechnicianSchedule {
  technician: PersonRef
  shifts: ShiftView[]
}

export interface ShiftInput {
  dayOfWeek: number
  branchId: number
  start: string
  end: string
  breakStart?: string
  breakEnd?: string
}

export interface ServiceSettings {
  branchId: number
  defaultVisitMinutes: number
  bufferMinutes: number
}

export interface StaffRequestInput
  extends Omit<PublicSubmission, 'contactConsent' | 'website' | 'emailNotifications' | 'whatsappNotifications' | 'consentTextVersion'> {
  /** Staff requests do not collect channel consent: it is managed in the customer's record. */
  notificationsConsent: boolean
  customerId?: number
  registerCustomer?: boolean
  confirmedNewPerson?: boolean
}

export const serviceKeys = {
  all: ['service'] as const,
  requests: (filters: RequestFilters) => ['service', 'requests', filters] as const,
  request: (id: number) => ['service', 'request', id] as const,
  visit: (id: number) => ['service', 'visit', id] as const,
  agenda: (from: string, to: string, branchId?: number, technicianId?: number) =>
    ['service', 'agenda', from, to, branchId ?? null, technicianId ?? null] as const,
  availability: (technicianId: number, date: string, duration?: number) =>
    ['service', 'availability', technicianId, date, duration ?? null] as const,
  technicians: (branchId: number) => ['service', 'technicians', branchId] as const,
  schedules: (branchId: number) => ['service', 'schedules', branchId] as const,
  settings: (branchId: number) => ['service', 'settings', branchId] as const,
  byOrder: (orderId: number) => ['service', 'by-order', orderId] as const,
}

export function fetchServiceRequests(filters: RequestFilters, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(filters.page), size: '20' })
  if (filters.status) params.set('status', filters.status)
  if (filters.branchId) params.set('branchId', String(filters.branchId))
  if (filters.customerId) params.set('customerId', String(filters.customerId))
  if (filters.search?.trim()) params.set('search', filters.search.trim())
  return http.get<Page<ServiceRequestSummary>>(`/api/v1/service-requests?${params}`, signal)
}

export const fetchServiceRequest = (id: number, signal?: AbortSignal) =>
  http.get<ServiceRequestDetail>(`/api/v1/service-requests/${id}`, signal)

export const createStaffRequest = (input: StaffRequestInput) =>
  http.post<ServiceRequestDetail>('/api/v1/service-requests', input)

export const startReview = (id: number) => http.post<ServiceRequestDetail>(`/api/v1/service-requests/${id}/review`)

export const linkCustomer = (id: number, body: { customerId?: number; registerNew?: boolean; confirmedNewPerson?: boolean }) =>
  http.put<ServiceRequestDetail>(`/api/v1/service-requests/${id}/customer`, body)

export const changeRequestBranch = (id: number, branchId: number) =>
  http.put<ServiceRequestDetail | undefined>(`/api/v1/service-requests/${id}/branch`, { branchId })

export const rejectRequest = (id: number, reason: string) =>
  http.post<ServiceRequestDetail>(`/api/v1/service-requests/${id}/reject`, { reason })

export const cancelRequest = (id: number, reason: string) =>
  http.post<ServiceRequestDetail>(`/api/v1/service-requests/${id}/cancel`, { reason })

export const scheduleVisit = (
  requestId: number,
  body: { operationId: string; technicianId: number; start: string; durationMinutes?: number; confirm: boolean },
) => http.post<Visit>(`/api/v1/service-requests/${requestId}/visits`, body)

export const fetchVisit = (id: number, signal?: AbortSignal) => http.get<Visit>(`/api/v1/service-visits/${id}`, signal)

export const confirmVisit = (id: number) => http.post<Visit>(`/api/v1/service-visits/${id}/confirm`)

export const rescheduleVisit = (
  id: number,
  body: { technicianId: number; start: string; durationMinutes?: number; reason?: string },
) => http.put<Visit>(`/api/v1/service-visits/${id}/schedule`, body)

export const cancelVisit = (id: number, reason: string) => http.post<Visit>(`/api/v1/service-visits/${id}/cancel`, { reason })

export const startVisit = (id: number) => http.post<Visit>(`/api/v1/service-visits/${id}/start`)

export const completeVisit = (id: number, outcome: VisitOutcome, notes?: string) =>
  http.post<Visit>(`/api/v1/service-visits/${id}/complete`, { outcome, notes })

export const linkRepairOrder = (
  id: number,
  body: { existingOrderId?: number; operationId?: string; physicalCondition?: string; accessories?: string },
) => http.post<Visit>(`/api/v1/service-visits/${id}/repair-order`, body)

export function fetchAgenda(from: string, to: string, branchId?: number, technicianId?: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ from, to })
  if (branchId) params.set('branchId', String(branchId))
  if (technicianId) params.set('technicianId', String(technicianId))
  return http.get<Visit[]>(`/api/v1/service-visits?${params}`, signal)
}

export function fetchAvailability(technicianId: number, date: string, durationMinutes?: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ technicianId: String(technicianId), date })
  if (durationMinutes) params.set('durationMinutes', String(durationMinutes))
  return http.get<Availability>(`/api/v1/service-visits/availability?${params}`, signal)
}

export const fetchServiceTechnicians = (branchId: number, signal?: AbortSignal) =>
  http.get<PersonRef[]>(`/api/v1/service-visits/technicians?branchId=${branchId}`, signal)

export const fetchVisitForOrder = (orderId: number, signal?: AbortSignal) =>
  http.get<Visit>(`/api/v1/service-visits/by-repair-order/${orderId}`, signal)

export const fetchSchedules = (branchId: number, signal?: AbortSignal) =>
  http.get<TechnicianSchedule[]>(`/api/v1/technician-schedules?branchId=${branchId}`, signal)

export const saveWeek = (technicianId: number, shifts: ShiftInput[]) =>
  http.put<TechnicianSchedule>(`/api/v1/technician-schedules/${technicianId}`, { shifts })

export const fetchServiceSettings = (branchId: number, signal?: AbortSignal) =>
  http.get<ServiceSettings>(`/api/v1/branches/${branchId}/service-settings`, signal)

export const saveServiceSettings = (branchId: number, body: { defaultVisitMinutes: number; bufferMinutes: number }) =>
  http.put<ServiceSettings>(`/api/v1/branches/${branchId}/service-settings`, body)
