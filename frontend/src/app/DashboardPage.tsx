import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { VISIT_STATUS_LABELS, VISIT_STATUS_TONES } from '../shared/i18n/serviceLabels'
import { ROLE_LABELS } from '../shared/i18n/labels'
import { Alert } from '../shared/ui/Alert'
import { Avatar } from '../shared/ui/Avatar'
import { Icon, type IconName } from '../shared/ui/Icon'
import { EmptyState, ErrorState, LoadingState } from '../shared/ui/States'
import { permissions } from '../features/auth/permissions'
import { useSession } from '../features/auth/session'
import type { Role } from '../features/auth/authApi'
import { useSelectedBranch } from '../features/branches/selectedBranch'
import {
  dashboardKeys,
  fetchDashboard,
  greeting,
  groupIndicators,
  priorities,
  type ResolvedIndicator,
  type UpcomingVisit,
} from '../features/dashboard/dashboardApi'
import { capitalized, addDays, crDate, crTime, crToday, longDate } from '../features/service/crTime'

/**
 * Operational home (D.1-D.3). Counts come from the server, computed with the same queries and
 * branch scope as the lists they open; the SPA never downloads records to count them. Indicators are
 * grouped by use (workshop, home service, agenda, stock) and the ones that ask for action are
 * summarized first. Managers and administrators can switch between the selected branch and all their
 * branches; a technician sees their own repairs and visits.
 */
export function DashboardPage() {
  const { data: session } = useSession()
  const { selected } = useSelectedBranch()
  const [params, setParams] = useSearchParams()
  const role = session?.user.role
  const technician = role === 'TECHNICIAN'
  const mayConsolidate = role === 'ADMIN' || role === 'BRANCH_MANAGER'
  // Technicians see their own work across their branches; others the selected branch by default.
  const consolidated = technician || (mayConsolidate && params.get('scope') === 'all')
  const branchId = consolidated ? null : (selected?.id ?? null)
  const dashboard = useQuery({
    queryKey: dashboardKeys.scope(branchId),
    queryFn: ({ signal }) => fetchDashboard(branchId, signal),
    enabled: session !== undefined && (branchId !== null || consolidated),
    refetchInterval: 60_000,
  })

  if (!session) return null
  const today = crToday()
  const hour = Number(crTime(new Date().toISOString()).slice(0, 2))
  const firstName = session.user.fullName.split(/\s+/)[0]
  const scopeLabel = technician ? 'Tu trabajo asignado' : consolidated ? 'Todas tus sucursales' : (selected?.name ?? 'Sin sucursal')
  const data = dashboard.data
  const groups = data ? groupIndicators(data.indicators, branchId, today) : {}
  const urgent = data ? priorities(data.indicators, branchId, today) : []
  const agendaLink = technician ? '/my-visits' : `/agenda?branch=${branchId ?? 'all'}`

  return (
    <section className="page dashboard">
      <header className="hero">
        <div className="hero-top">
          <div className="hero-heading">
            <p className="eyebrow">
              <Icon name="spark" />
              Centro de operaciones
            </p>
            <h1>
              {greeting(hour)}, {firstName}
            </h1>
            <div className="hero-chips">
              <span className="hero-chip">
                <Icon name="calendar" size={15} />
                {capitalized(longDate(today))}
              </span>
              <span className="hero-chip">
                <Icon name="user" size={15} />
                {ROLE_LABELS[session.user.role]}
              </span>
              <span className="hero-chip">
                <Icon name="building" size={15} />
                {scopeLabel}
              </span>
              {data && (
                <span className="hero-chip">
                  <Icon name="clock" size={15} />
                  Actualizado {crTime(data.generatedAt)}
                </span>
              )}
            </div>
          </div>
          {mayConsolidate && (session.user.role === 'ADMIN' || session.branches.length > 1) && (
            <div className="segmented segmented-dark" role="group" aria-label="Alcance del resumen">
              <button type="button" aria-pressed={!consolidated} onClick={() => setParams({}, { replace: true })}>
                Sucursal seleccionada
              </button>
              <button type="button" aria-pressed={consolidated} onClick={() => setParams({ scope: 'all' }, { replace: true })}>
                Todas mis sucursales
              </button>
            </div>
          )}
        </div>

        {data && (
          <div className="hero-priorities">
            <h2 className="hero-priorities-title">Prioridades</h2>
            {urgent.length === 0 ? (
              <p className="hero-clear">
                <Icon name="checkCircle" size={18} />
                Todo al día: no hay pendientes que requieran acción ahora.
              </p>
            ) : (
              <ul>
                {urgent.map((item) => (
                  <li key={item.key}>
                    <Link to={item.to} className={`priority-pill priority-${item.priority}`}>
                      <strong>{item.count}</strong>
                      {item.label}
                      <span className="visually-hidden">{item.priority === 'urgent' ? ' (requiere acción)' : ' (revisar)'}</span>
                      <Icon name="arrowRight" size={16} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </header>

      {!technician && !consolidated && !selected && (
        <Alert tone="info">
          No tienes sucursales asignadas.{' '}
          {session.user.role === 'ADMIN' ? 'Crea una en Sucursales.' : 'Solicita acceso a un administrador.'}
        </Alert>
      )}
      {dashboard.isError && <ErrorState error={dashboard.error} onRetry={() => dashboard.refetch()} />}
      {dashboard.isPending && dashboard.fetchStatus !== 'idle' && <LoadingState label="Cargando resumen…" />}

      {data && (
        <div className="dashboard-grid">
          {technician ? (
            <IndicatorGroup
              className="span-full"
              title="Mis reparaciones"
              subtitle="Órdenes asignadas por etapa"
              icon="wrench"
              link={{ to: '/repairs', label: 'Ver mis órdenes' }}
              items={groups.myRepairs}
            />
          ) : (
            <>
              <IndicatorGroup
                className="span-7"
                title="Taller"
                subtitle="Equipos en custodia por etapa"
                icon="wrench"
                link={{ to: branchId ? `/repairs?branch=${branchId}` : '/repairs', label: 'Ver reparaciones' }}
                items={groups.workshop}
              />
              <IndicatorGroup
                className="span-5"
                title="A domicilio"
                subtitle="Solicitudes que esperan al personal"
                icon="van"
                iconTone="accent"
                link={{ to: branchId ? `/service-requests?branch=${branchId}` : '/service-requests', label: 'Ver bandeja' }}
                items={groups.home}
              />
            </>
          )}

          <UpcomingVisits
            className="span-7"
            visits={data.upcomingVisits}
            today={today}
            todayIndicator={(technician ? groups.myVisits : groups.agenda)?.[0]}
            consolidated={data.consolidated}
            agendaLink={agendaLink}
            role={session.user.role}
          />

          <div className="span-5 stack-lg">
            {!technician && (
              <IndicatorGroup
                title="Inventario"
                subtitle={branchId ? 'Alertas de existencias de la sucursal' : 'Alertas en alguna de tus sucursales'}
                icon="box"
                iconTone="warn"
                link={{ to: branchId ? '/inventory' : '/inventory/overview', label: 'Ver existencias' }}
                items={groups.stock}
              />
            )}
            <QuickActions role={session.user.role} />
          </div>
        </div>
      )}
    </section>
  )
}

function IndicatorGroup({
  title,
  subtitle,
  icon,
  iconTone = 'primary',
  link,
  items,
  className,
}: {
  title: string
  subtitle: string
  icon: IconName
  iconTone?: 'primary' | 'accent' | 'warn'
  link: { to: string; label: string }
  items: ResolvedIndicator[] | undefined
  className?: string
}) {
  if (!items || items.length === 0) return null
  return (
    <section className={className ? `card indicator-group ${className}` : 'card indicator-group'} aria-label={title}>
      <div className="card-header">
        <div className="card-heading">
          <span className={`card-heading-icon tone-${iconTone}`} aria-hidden="true">
            <Icon name={icon} size={18} />
          </span>
          <div>
            <h2>{title}</h2>
            <p className="card-subtitle">{subtitle}</p>
          </div>
        </div>
        <Link to={link.to} className="card-link">
          {link.label}
          <Icon name="arrowRight" size={16} />
        </Link>
      </div>
      <div className="tile-grid">
        {items.map((item) => (
          <IndicatorTile key={item.key} item={item} />
        ))}
      </div>
    </section>
  )
}

/** One indicator: the count opens the list with the same filter and scope (FR-DSH-002). */
function IndicatorTile({ item }: { item: ResolvedIndicator }) {
  const active = item.count > 0
  const flag = active && item.priority === 'urgent' ? 'Requiere acción' : active && item.priority === 'attention' ? 'Revisar' : null
  return (
    <Link
      to={item.to}
      className={`tile tile-${active ? item.tone : 'idle'}${flag ? ` tile-${item.priority}` : ''}`}
      aria-label={`${item.label}: ${item.count}. ${item.hint}${flag ? `. ${flag}` : ''}`}
    >
      <span className="tile-top">
        <span className="tile-icon" aria-hidden="true">
          <Icon name={item.icon} size={18} />
        </span>
        {flag && <span className="tile-flag">{flag}</span>}
      </span>
      <span className="tile-count">{item.count}</span>
      <span className="tile-label">{item.label}</span>
      <span className="tile-hint">{item.hint}</span>
    </Link>
  )
}

function dayHeading(date: string, today: string): string {
  if (date === today) return 'Hoy'
  if (date === addDays(today, 1)) return 'Mañana'
  const label = longDate(date)
  return label.charAt(0).toUpperCase() + label.slice(1)
}

function UpcomingVisits({
  visits,
  today,
  todayIndicator,
  consolidated,
  agendaLink,
  role,
  className,
}: {
  visits: UpcomingVisit[]
  today: string
  todayIndicator: ResolvedIndicator | undefined
  consolidated: boolean
  agendaLink: string
  role: Role
  className?: string
}) {
  const days = new Map<string, UpcomingVisit[]>()
  for (const visit of visits) {
    const date = crDate(visit.start)
    days.set(date, [...(days.get(date) ?? []), visit])
  }
  return (
    <section className={`card upcoming ${className ?? ''}`} aria-label="Agenda">
      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon" aria-hidden="true">
            <Icon name="calendar" size={18} />
          </span>
          <div>
            <h2>{role === 'TECHNICIAN' ? 'Tus próximas visitas' : 'Agenda de visitas'}</h2>
            <p className="card-subtitle">Próximas visitas de los siguientes 7 días</p>
          </div>
        </div>
        <Link to={agendaLink} className="card-link">
          {role === 'TECHNICIAN' ? 'Mis visitas' : 'Abrir agenda'}
          <Icon name="arrowRight" size={16} />
        </Link>
      </div>

      {todayIndicator && (
        <Link to={todayIndicator.to} className="today-strip">
          <span className="today-count">{todayIndicator.count}</span>
          <span>
            <strong>{todayIndicator.label}</strong>
            <span className="muted small"> · {todayIndicator.hint}</span>
          </span>
          <Icon name="arrowRight" size={16} />
        </Link>
      )}

      {visits.length === 0 ? (
        <EmptyState
          compact
          icon="calendar"
          title="Sin visitas en los próximos 7 días"
          action={
            permissions.handleServiceRequests(role) ? (
              <Link to="/service-requests" className="button button-secondary button-small">
                Revisar solicitudes
              </Link>
            ) : undefined
          }
        >
          {permissions.handleServiceRequests(role)
            ? 'Programa una visita desde una solicitud a domicilio para verla aquí.'
            : 'Cuando te asignen una visita aparecerá aquí.'}
        </EmptyState>
      ) : (
        <div className="visit-days">
          {[...days.entries()].map(([date, dayVisits]) => (
            <div key={date} className="visit-day">
              <h3 className="visit-day-title">{dayHeading(date, today)}</h3>
              <ul className="visit-list">
                {dayVisits.map((visit) => (
                  <li key={visit.id}>
                    <Link to={`/visits/${visit.id}`} className={`visit-row visit-${visit.status.toLowerCase()}`}>
                      <span className="visit-time">
                        <strong>{crTime(visit.start)}</strong>
                        <span>{crTime(visit.end)}</span>
                      </span>
                      <span className="visit-main">
                        <span className="visit-title">
                          {visit.deviceType}
                          <span className="muted"> · {visit.canton}</span>
                        </span>
                        <span className="visit-meta">
                          <span className="mono">{visit.requestCode}</span>
                          {consolidated && <span>{visit.branch.name}</span>}
                        </span>
                      </span>
                      {role !== 'TECHNICIAN' && (
                        <span className="visit-tech">
                          <Avatar name={visit.technicianName} size="sm" />
                          <span className="truncate">{visit.technicianName}</span>
                        </span>
                      )}
                      <span className={`badge badge-${VISIT_STATUS_TONES[visit.status]}${visit.status === 'PROPOSED' ? ' badge-dashed' : ''}`}>
                        {VISIT_STATUS_LABELS[visit.status]}
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

interface QuickAction {
  to: string
  label: string
  hint: string
  icon: IconName
}

/** FR-DSH-003: shortcuts only to what the role can do (the API authorizes anyway). */
function QuickActions({ role }: { role: Role }) {
  const actions = [
    permissions.receiveRepairs(role) && { to: '/repairs/new', label: 'Recibir equipo', hint: 'Nueva orden de taller', icon: 'wrench' },
    permissions.handleServiceRequests(role) && {
      to: '/service-requests/new',
      label: 'Solicitud a domicilio',
      hint: 'Registrar por teléfono',
      icon: 'van',
    },
    permissions.handleServiceRequests(role) && { to: '/agenda', label: 'Ver agenda', hint: 'Visitas por día o semana', icon: 'calendar' },
    permissions.ownVisits(role) && { to: '/my-visits', label: 'Mis visitas', hint: 'Dirección y contacto', icon: 'calendar' },
    role === 'TECHNICIAN' && { to: '/repairs?status=IN_REPAIR', label: 'En reparación', hint: 'Registrar repuestos', icon: 'wrench' },
    permissions.manageCustomers(role) && { to: '/customers', label: 'Buscar cliente', hint: 'Historial y avisos', icon: 'users' },
    permissions.operateInventory(role) && { to: '/inventory', label: 'Movimiento', hint: 'Entrada, salida o ajuste', icon: 'box' },
    permissions.transfer(role) && { to: '/transfers', label: 'Transferir', hint: 'Entre sucursales', icon: 'transfer' },
  ].filter((action): action is QuickAction => Boolean(action))
  return (
    <section className="card" aria-label="Acciones rápidas">
      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon tone-accent" aria-hidden="true">
            <Icon name="spark" size={18} />
          </span>
          <h2>Acciones rápidas</h2>
        </div>
      </div>
      <ul className="action-grid">
        {actions.map((action) => (
          <li key={action.to}>
            <Link to={action.to} className="action-tile">
              <span className="action-icon" aria-hidden="true">
                <Icon name={action.icon} size={18} />
              </span>
              <span className="action-text">
                <strong>{action.label}</strong>
                <span>{action.hint}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
