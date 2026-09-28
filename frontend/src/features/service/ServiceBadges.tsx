import {
  REQUEST_STATUS_LABELS,
  REQUEST_STATUS_TONES,
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONES,
  type RequestStatus,
  type VisitStatus,
} from '../../shared/i18n/serviceLabels'

export function RequestStatusBadge({ status }: { status: RequestStatus }) {
  return <span className={`badge badge-${REQUEST_STATUS_TONES[status]}`}>{REQUEST_STATUS_LABELS[status]}</span>
}

export function VisitStatusBadge({ status }: { status: VisitStatus }) {
  // A proposed visit is tentative: dashed outline in addition to its text.
  return (
    <span className={`badge badge-${VISIT_STATUS_TONES[status]}${status === 'PROPOSED' ? ' badge-dashed' : ''}`}>{VISIT_STATUS_LABELS[status]}</span>
  )
}
