import { http } from '../../shared/api/httpClient'
import type { IconName } from '../../shared/ui/Icon'
import type { BranchSummary } from '../branches/branchesApi'

export interface Indicator {
  key: string
  count: number
}

export interface UpcomingVisit {
  id: number
  requestCode: string
  status: 'PROPOSED' | 'CONFIRMED' | 'IN_PROGRESS'
  start: string
  end: string
  technicianName: string
  deviceType: string
  canton: string
  branch: BranchSummary
}

/** Mirrors DashboardDtos.DashboardResponse. */
export interface Dashboard {
  branch: BranchSummary | null
  consolidated: boolean
  indicators: Indicator[]
  upcomingVisits: UpcomingVisit[]
  generatedAt: string
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
  scope: (branchId: number | null) => ['dashboard', branchId ?? 'all'] as const,
}

export function fetchDashboard(branchId: number | null, signal?: AbortSignal) {
  return http.get<Dashboard>(`/api/v1/dashboard${branchId ? `?branchId=${branchId}` : ''}`, signal)
}

export interface IndicatorView {
  label: string
  hint: string
  /** The list that shows exactly the records counted, with the same filters and branch scope. */
  to: string
  tone: 'info' | 'warn' | 'ok' | 'error'
  /**
   * How much a non-zero count asks for action: "urgent" (someone is waiting or stock is gone),
   * "attention" (review soon) or "info" (work in progress). Shown as text, not only as color.
   */
  priority: 'urgent' | 'attention' | 'info'
  icon: IconName
}

/**
 * D.1: label, explanation and target list of each indicator. `branchId` null = consolidated view:
 * lists without a branch filter (all the user's branches), agenda with branch=all, and the
 * consolidated stock overview. Unknown keys return null (a newer server, an older page).
 */
export function indicatorView(key: string, branchId: number | null, today: string): IndicatorView | null {
  const branch = branchId ? `&branch=${branchId}` : ''
  const repairs = (status: string) => `/repairs?status=${status}${branch}`
  const requests = (status: string) => `/service-requests?status=${status}${branch}`
  const stock = (filter: string) => (branchId ? `/inventory?stock=${filter}` : `/inventory/overview?stock=${filter}`)
  switch (key) {
    case 'REPAIRS_RECEIVED':
      return { label: 'Por diagnosticar', hint: 'Equipos recibidos sin revisión técnica', to: repairs('RECEIVED'), tone: 'info', priority: 'attention', icon: 'inbox' }
    case 'REPAIRS_DIAGNOSING':
      return { label: 'En diagnóstico', hint: 'Revisión técnica en curso', to: repairs('DIAGNOSING'), tone: 'info', priority: 'info', icon: 'cpu' }
    case 'REPAIRS_READY':
      return { label: 'Listos para entregar', hint: 'Avisar y entregar al cliente', to: repairs('READY_FOR_PICKUP'), tone: 'ok', priority: 'urgent', icon: 'checkCircle' }
    case 'REQUESTS_PENDING':
      return { label: 'Solicitudes nuevas', hint: 'A domicilio, sin revisar', to: requests('PENDING'), tone: 'warn', priority: 'urgent', icon: 'bell' }
    case 'REQUESTS_UNDER_REVIEW':
      return { label: 'Solicitudes en revisión', hint: 'Falta cliente o visita confirmada', to: requests('UNDER_REVIEW'), tone: 'info', priority: 'attention', icon: 'clipboard' }
    case 'VISITS_TODAY':
      return { label: 'Visitas de hoy', hint: 'Confirmadas y propuestas', to: `/agenda?view=day&date=${today}&branch=${branchId ?? 'all'}`, tone: 'info', priority: 'info', icon: 'calendar' }
    case 'STOCK_OUT':
      return { label: 'Sin existencias', hint: branchId ? 'Productos activos en 0' : 'Productos en 0 en alguna sucursal', to: stock('OUT_OF_STOCK'), tone: 'error', priority: 'urgent', icon: 'stockOut' }
    case 'STOCK_LOW':
      return { label: 'Bajo mínimo', hint: branchId ? 'Por debajo del mínimo' : 'Bajo mínimo en alguna sucursal', to: stock('LOW'), tone: 'warn', priority: 'attention', icon: 'trendDown' }
    case 'MY_REPAIRS_RECEIVED':
      return { label: 'Asignadas sin empezar', hint: 'Recibidas, pendientes de diagnóstico', to: repairs('RECEIVED'), tone: 'info', priority: 'attention', icon: 'inbox' }
    case 'MY_REPAIRS_DIAGNOSING':
      return { label: 'En diagnóstico', hint: 'Tus revisiones en curso', to: repairs('DIAGNOSING'), tone: 'info', priority: 'info', icon: 'cpu' }
    case 'MY_REPAIRS_APPROVED':
      return { label: 'Aprobadas para reparar', hint: 'El cliente aceptó la cotización', to: repairs('APPROVED'), tone: 'ok', priority: 'urgent', icon: 'checkCircle' }
    case 'MY_REPAIRS_IN_REPAIR':
      return { label: 'En reparación', hint: 'Registra aquí los repuestos usados', to: repairs('IN_REPAIR'), tone: 'info', priority: 'info', icon: 'wrench' }
    case 'MY_VISITS_TODAY':
      return { label: 'Mis visitas de hoy', hint: 'Visitas a domicilio asignadas', to: '/my-visits', tone: 'info', priority: 'info', icon: 'calendar' }
    default:
      return null
  }
}

export type IndicatorGroupId = 'workshop' | 'home' | 'agenda' | 'stock' | 'myRepairs' | 'myVisits'

/** D.1 grouping on the home screen: indicators are read by what they are used for. */
const GROUP_OF: Record<string, IndicatorGroupId> = {
  REPAIRS_RECEIVED: 'workshop',
  REPAIRS_DIAGNOSING: 'workshop',
  REPAIRS_READY: 'workshop',
  REQUESTS_PENDING: 'home',
  REQUESTS_UNDER_REVIEW: 'home',
  VISITS_TODAY: 'agenda',
  STOCK_OUT: 'stock',
  STOCK_LOW: 'stock',
  MY_REPAIRS_RECEIVED: 'myRepairs',
  MY_REPAIRS_DIAGNOSING: 'myRepairs',
  MY_REPAIRS_APPROVED: 'myRepairs',
  MY_REPAIRS_IN_REPAIR: 'myRepairs',
  MY_VISITS_TODAY: 'myVisits',
}

export interface ResolvedIndicator extends IndicatorView {
  key: string
  count: number
}

/** Indicators with their view, grouped in server order; unknown keys are left out. */
export function groupIndicators(
  indicators: Indicator[],
  branchId: number | null,
  today: string,
): Partial<Record<IndicatorGroupId, ResolvedIndicator[]>> {
  const groups: Partial<Record<IndicatorGroupId, ResolvedIndicator[]>> = {}
  for (const indicator of indicators) {
    const view = indicatorView(indicator.key, branchId, today)
    const group = GROUP_OF[indicator.key]
    if (!view || !group) continue
    ;(groups[group] ??= []).push({ ...view, ...indicator })
  }
  return groups
}

/** Non-zero indicators that ask for action, most urgent first (the "Prioridades" strip). */
export function priorities(indicators: Indicator[], branchId: number | null, today: string): ResolvedIndicator[] {
  const rank = { urgent: 0, attention: 1, info: 2 }
  return indicators
    .map((indicator) => {
      const view = indicatorView(indicator.key, branchId, today)
      return view ? { ...view, ...indicator } : null
    })
    .filter((item): item is ResolvedIndicator => item !== null && item.count > 0 && item.priority !== 'info')
    .sort((a, b) => rank[a.priority] - rank[b.priority])
}

/** "Buenos días" until noon, "Buenas tardes" until 19:00, then "Buenas noches" (Costa Rica hour). */
export function greeting(hour: number): string {
  if (hour < 12) return 'Buenos días'
  if (hour < 19) return 'Buenas tardes'
  return 'Buenas noches'
}
