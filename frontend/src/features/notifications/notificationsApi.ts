import type { Page } from '../../shared/api/page'
import { http } from '../../shared/api/httpClient'
import type { ContactChannel, NotificationEventType, NotificationState, SkipReason } from '../../shared/i18n/consent'

/** Mirrors NotificationDtos.NotificationStatus: what an order or request shows about its notices. */
export interface NotificationStatus {
  id: number
  eventType: NotificationEventType
  channel: ContactChannel
  status: NotificationState
  skipReason: SkipReason | null
  attempts: number
  subjectId: number
  createdAt: string
  sentAt: string | null
}

/** Mirrors NotificationDtos.NotificationRow (no message body, masked address only). */
export interface NotificationRow {
  id: number
  eventType: NotificationEventType
  channel: ContactChannel
  status: NotificationState
  skipReason: SkipReason | null
  subjectType: 'REPAIR_ORDER' | 'SERVICE_VISIT'
  subjectId: number
  reference: string | null
  branchId: number
  recipientHint: string | null
  attempts: number
  maxAttempts: number
  nextAttemptAt: string
  lastError: string | null
  createdAt: string
  sentAt: string | null
}

export interface NotificationAttempt {
  attemptNumber: number
  workerId: string
  outcome: 'SENT' | 'RETRY' | 'FAILED' | 'SKIPPED'
  errorCode: string | null
  errorMessage: string | null
  startedAt: string
  finishedAt: string
}

export interface NotificationDetail {
  message: NotificationRow
  attempts: NotificationAttempt[]
}

export interface InboxMessage {
  messageId: string
  to: string
  subject: string
  body: string
  acceptedAt: string
}

export interface NotificationFilters {
  status?: NotificationState | ''
  branchId?: number
  page: number
}

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (filters: NotificationFilters) => ['notifications', 'list', filters] as const,
  detail: (id: number) => ['notifications', 'detail', id] as const,
  inbox: ['notifications', 'inbox'] as const,
}

export function fetchNotifications(filters: NotificationFilters, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(filters.page), size: '20' })
  if (filters.status) params.set('status', filters.status)
  if (filters.branchId) params.set('branchId', String(filters.branchId))
  return http.get<Page<NotificationRow>>(`/api/v1/notifications?${params}`, signal)
}

export const fetchNotification = (id: number, signal?: AbortSignal) =>
  http.get<NotificationDetail>(`/api/v1/notifications/${id}`, signal)

export const retryNotification = (id: number) => http.post<NotificationDetail>(`/api/v1/notifications/${id}/retry`)

export const fetchDevInbox = (signal?: AbortSignal) => http.get<InboxMessage[]>('/api/v1/notifications/dev-inbox', signal)
