import { http } from '../../shared/api/httpClient'
import type { Page } from '../../shared/api/page'
import type { AuditEventLike } from '../../shared/i18n/audit'

/** Mirrors audit.AuditEventResponse. */
export interface AuditEvent extends AuditEventLike {
  id: number
  occurredAt: string
  actorId: number | null
  actorName: string | null
  entityType: string
  entityId: string
  branchId: number | null
  operationId: string | null
  correlationId: string | null
}

export interface AuditFilters {
  action?: string
  branchId?: string
  /** Costa Rica calendar days, YYYY-MM-DD, both inclusive. */
  from?: string
  to?: string
  page: number
  size: number
}

export function fetchAuditEvents(filters: AuditFilters, signal?: AbortSignal) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  return http.get<Page<AuditEvent>>(`/api/v1/audit-events?${query}`, signal)
}
