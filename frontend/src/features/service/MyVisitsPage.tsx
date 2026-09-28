import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { addDays, crDate, crRange, crTime, crToday, longDate } from './crTime'
import { VisitStatusBadge } from './ServiceBadges'
import { fetchAgenda, serviceKeys, type Visit } from './serviceApi'

const DAYS_AHEAD = 14

/** The technician's list: running and upcoming visits (two weeks), grouped by day. */
export function MyVisitsPage() {
  const today = crToday()
  const { from, to } = crRange(addDays(today, -1), DAYS_AHEAD + 1)
  const visits = useQuery({
    queryKey: serviceKeys.agenda(from, to),
    queryFn: ({ signal }) => fetchAgenda(from, to, undefined, undefined, signal),
  })

  const byDay = new Map<string, Visit[]>()
  for (const visit of visits.data ?? []) {
    const day = crDate(visit.start)
    // Yesterday only matters for a visit still running.
    if (day < today && visit.status !== 'IN_PROGRESS') continue
    byDay.set(day, [...(byDay.get(day) ?? []), visit])
  }

  return (
    <section className="page page-form">
      <ModuleSurface
        eyebrow="Servicio a domicilio"
        icon="calendar"
        title="Mis visitas"
        description="Tus visitas de las próximas dos semanas. La dirección y el teléfono se muestran mientras la visita está pendiente o en curso."
      >
        {visits.isPending && <LoadingState label="Cargando visitas…" />}
        {visits.isError && <ErrorState error={visits.error} onRetry={() => visits.refetch()} />}
        {visits.data && byDay.size === 0 && (
          <EmptyState icon="calendar" title="No tienes visitas programadas">
            Cuando el mostrador te asigne una visita confirmada aparecerá aquí con su dirección.
          </EmptyState>
        )}
        {[...byDay.entries()].map(([day, dayVisits]) => (
          <section key={day} className="module-section" aria-label={longDate(day)}>
            <h2 className="visit-day-title">{day === today ? `Hoy · ${longDate(day)}` : longDate(day)}</h2>
            <ul className="visit-list">
              {dayVisits.map((visit) => (
                <li key={visit.id}>
                  <Link to={`/visits/${visit.id}`} className={`visit-row visit-row-compact visit-${visit.status.toLowerCase()}`}>
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
                        <span>{visit.customer?.fullName ?? visit.contactName}</span>
                        <span className="strong">
                          {visit.actions.canStart ? 'Iniciar visita' : visit.actions.canComplete ? 'Registrar resultado' : 'Ver detalle'}
                        </span>
                      </span>
                    </span>
                    <VisitStatusBadge status={visit.status} />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </ModuleSurface>
    </section>
  )
}
