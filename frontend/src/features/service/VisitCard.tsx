import { Link } from 'react-router'
import { OUTCOME_LABELS, PROVINCE_LABELS } from '../../shared/i18n/serviceLabels'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { formatPhone } from '../customers/customersApi'
import { capitalized, crDate, crTime, longDate } from './crTime'
import { VisitStatusBadge } from './ServiceBadges'
import type { Visit } from './serviceApi'

/** One visit: when, who, where (when allowed) and what happened. Read-only; actions live outside. */
export function VisitCard({ visit, showRequestLink }: { visit: Visit; showRequestLink?: boolean }) {
  return (
    <div className="visit-card">
      <div className="visit-card-head">
        <span className="visit-when">
          <Icon name="calendar" size={18} />
          {capitalized(longDate(crDate(visit.start)))}, {crTime(visit.start)}–{crTime(visit.end)}
        </span>
        <VisitStatusBadge status={visit.status} />
      </div>
      <dl className="details">
        <dt>Técnico</dt>
        <dd>
          <span className="person">
            <Avatar name={visit.technician.fullName} size="sm" />
            {visit.technician.fullName}
          </span>
        </dd>
        <dt>Cliente</dt>
        <dd>
          {visit.customer?.fullName ?? visit.contactName}
          {visit.contactPhone && (
            <span className="contact-line">
              <Icon name="phone" size={15} />
              <a href={`tel:${visit.contactPhone}`}>{formatPhone(visit.contactPhone)}</a>
            </span>
          )}
        </dd>
        <dt>Zona</dt>
        <dd>
          {visit.district ? `${visit.district}, ` : ''}
          {visit.canton}, {PROVINCE_LABELS[visit.province]}
        </dd>
        {visit.addressLine && (
          <>
            <dt>Dirección</dt>
            <dd className="text-block">{visit.addressLine}</dd>
          </>
        )}
        <dt>Equipo</dt>
        <dd>
          {visit.deviceType} {visit.brand ?? ''} {visit.model ?? ''}
        </dd>
        <dt>Problema</dt>
        <dd className="text-block">{visit.problemDescription}</dd>
        {visit.outcome && (
          <>
            <dt>Resultado</dt>
            <dd>
              {OUTCOME_LABELS[visit.outcome]}
              {visit.outcomeNotes && <span className="text-block">. {visit.outcomeNotes}</span>}
            </dd>
          </>
        )}
        {visit.cancelReason && (
          <>
            <dt>Motivo de cancelación</dt>
            <dd>{visit.cancelReason}</dd>
          </>
        )}
        {visit.repairOrder && (
          <>
            <dt>Orden de taller</dt>
            <dd>
              <Link className="order-code" to={`/repairs/${visit.repairOrder.id}`}>
                {visit.repairOrder.orderCode}
              </Link>
            </dd>
          </>
        )}
        {showRequestLink && (
          <>
            <dt>Solicitud</dt>
            <dd className="mono">{visit.requestCode}</dd>
          </>
        )}
      </dl>
    </div>
  )
}
