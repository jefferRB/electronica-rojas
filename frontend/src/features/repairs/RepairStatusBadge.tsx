import { REPAIR_STATUS_LABELS, REPAIR_STATUS_TONES, type RepairStatus } from '../../shared/i18n/labels'

export function RepairStatusBadge({ status }: { status: RepairStatus }) {
  return <span className={`badge badge-${REPAIR_STATUS_TONES[status]}`}>{REPAIR_STATUS_LABELS[status]}</span>
}
