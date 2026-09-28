import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { WEEKDAY_LABELS } from '../../shared/i18n/serviceLabels'
import { SelectField } from '../../shared/ui/Field'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { permissions } from '../auth/permissions'
import { useSession } from '../auth/session'
import { useSelectedBranch } from '../branches/selectedBranch'
import { capitalized, addDays, crDate, crRange, crTime, crToday, longDate, mondayOf } from './crTime'
import { VisitStatusBadge } from './ServiceBadges'
import { fetchAgenda, fetchServiceTechnicians, serviceKeys, type Visit } from './serviceApi'

/**
 * Technicians' agenda (FR-SRV-004): a week of seven columns or one day, filtered by branch and
 * technician. Proposed visits are shown as tentative; cancelled ones are left out by the API.
 */
export function AgendaPage() {
  const { data: session } = useSession()
  const { selected } = useSelectedBranch()
  const [params, setParams] = useSearchParams()
  const view = params.get('view') === 'day' ? 'day' : 'week'
  const date = params.get('date') ?? crToday()
  // 'all' = every branch of the user (the dashboard's consolidated link); otherwise one branch.
  const branchParam = params.get('branch')
  const branchId = branchParam === 'all' ? undefined : branchParam ? Number(branchParam) : selected?.id
  const technicianId = params.get('technician') ? Number(params.get('technician')) : undefined
  const start = view === 'week' ? mondayOf(date) : date
  const days = view === 'week' ? 7 : 1
  const { from, to } = crRange(start, days)

  const agenda = useQuery({
    queryKey: serviceKeys.agenda(from, to, branchId, technicianId),
    queryFn: ({ signal }) => fetchAgenda(from, to, branchId, technicianId, signal),
    placeholderData: keepPreviousData,
  })
  const technicians = useQuery({
    queryKey: branchId ? serviceKeys.technicians(branchId) : ['service', 'technicians', 'none'],
    queryFn: ({ signal }) => fetchServiceTechnicians(branchId!, signal),
    enabled: branchId !== undefined,
  })

  function set(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }

  const role = session?.user.role
  const staff = role !== undefined && permissions.handleServiceRequests(role)
  const byDay = new Map<string, Visit[]>()
  for (const visit of agenda.data ?? []) {
    const day = crDate(visit.start)
    byDay.set(day, [...(byDay.get(day) ?? []), visit])
  }
  const dayList = Array.from({ length: days }, (_, index) => addDays(start, index))
  const today = crToday()
  const total = agenda.data?.length ?? 0

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Servicio a domicilio"
        icon="calendar"
        title="Agenda de técnicos"
        description="Visitas confirmadas y propuestas. Una propuesta no reserva la hora hasta confirmarla."
        actions={
          role &&
          permissions.manageSchedules(role) && (
            <Link className="button button-secondary" to="/agenda/horarios">
              <Icon name="clock" />
              Horarios de técnicos
            </Link>
          )
        }
        className="agenda-card"
      >
        <div className="panel-toolbar agenda-toolbar">
          <div className="agenda-nav" role="group" aria-label="Navegar fechas">
            <button type="button" className="button button-secondary button-icon" onClick={() => set({ date: addDays(start, -days) })}>
              <Icon name="chevronLeft" label={view === 'week' ? 'Semana anterior' : 'Día anterior'} />
            </button>
            <button type="button" className="button button-secondary" onClick={() => set({ date: undefined })}>
              Hoy
            </button>
            <button type="button" className="button button-secondary button-icon" onClick={() => set({ date: addDays(start, days) })}>
              <Icon name="chevronRight" label={view === 'week' ? 'Semana siguiente' : 'Día siguiente'} />
            </button>
            <h2 className="agenda-range" aria-live="polite">
              {view === 'week' ? `Semana del ${longDate(start)}` : capitalized(longDate(start))}
              {agenda.data && (
                <span className="muted small">
                  {' '}
                  · {total} {total === 1 ? 'visita' : 'visitas'}
                </span>
              )}
            </h2>
          </div>
          <div className="segmented" role="group" aria-label="Vista">
            <button type="button" aria-pressed={view === 'week'} onClick={() => set({ view: undefined })}>
              Semana
            </button>
            <button type="button" aria-pressed={view === 'day'} onClick={() => set({ view: 'day' })}>
              Día
            </button>
          </div>
        </div>

        <div className="panel-toolbar agenda-filters">
          {(session?.branches.length ?? 0) > 1 && (
            <SelectField label="Sucursal" value={branchId ? String(branchId) : 'all'} onChange={(event) => set({ branch: event.target.value, technician: undefined })}>
              <option value="all">Todas mis sucursales</option>
              {session?.branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </SelectField>
          )}
          {staff && (
            <SelectField label="Técnico" value={technicianId ? String(technicianId) : ''} onChange={(event) => set({ technician: event.target.value })}>
              <option value="">Todos</option>
              {technicians.data?.map((technician) => (
                <option key={technician.id} value={technician.id}>
                  {technician.fullName}
                </option>
              ))}
            </SelectField>
          )}
          <ul className="agenda-legend" aria-label="Estados de las visitas">
            <li className="legend-proposed">Propuesta</li>
            <li className="legend-confirmed">Confirmada</li>
            <li className="legend-in_progress">En curso</li>
            <li className="legend-completed">Completada</li>
          </ul>
        </div>

        {agenda.isError && <ErrorState error={agenda.error} onRetry={() => agenda.refetch()} />}
        {agenda.isPending && <LoadingState label="Cargando agenda…" />}
        {agenda.data && (
          <div className={view === 'week' ? 'panel-body week-grid' : 'panel-body day-list'}>
            {dayList.map((day, index) => {
              const visits = byDay.get(day) ?? []
              return (
                <section
                  key={day}
                  className={`agenda-day${day === today ? ' agenda-today' : ''}${visits.length === 0 ? ' agenda-empty' : ''}`}
                  aria-label={`${longDate(day)}: ${visits.length} ${visits.length === 1 ? 'visita' : 'visitas'}`}
                >
                  <h3 className="agenda-day-title">
                    {view === 'week' ? (
                      <>
                        <span>{WEEKDAY_LABELS[index]}</span>
                        <span className="agenda-day-number">{Number(day.slice(8))}</span>
                      </>
                    ) : (
                      <span>{capitalized(longDate(day))}</span>
                    )}
                    {day === today && <span className="badge badge-info badge-plain">Hoy</span>}
                  </h3>
                  {visits.length === 0 && <p className="agenda-none">Sin visitas</p>}
                  {visits.map((visit) => (
                    <Link
                      key={visit.id}
                      className={`agenda-visit agenda-${visit.status.toLowerCase()}`}
                      to={staff ? `/service-requests/${visit.requestId}` : `/visits/${visit.id}`}
                    >
                      <span className="agenda-time">
                        {crTime(visit.start)}–{crTime(visit.end)}
                      </span>
                      <span className="agenda-what">
                        {visit.deviceType} · {visit.customer?.fullName ?? visit.contactName}
                      </span>
                      <span className="agenda-where">
                        <Icon name="mapPin" size={13} />
                        {visit.canton}
                      </span>
                      <span className="agenda-who">
                        <Avatar name={visit.technician.fullName} size="sm" />
                        <span className="truncate">{visit.technician.fullName}</span>
                      </span>
                      <VisitStatusBadge status={visit.status} />
                    </Link>
                  ))}
                </section>
              )
            })}
          </div>
        )}
      </ModuleSurface>
    </section>
  )
}
