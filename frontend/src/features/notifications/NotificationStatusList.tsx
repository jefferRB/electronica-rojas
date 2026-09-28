import {
  CHANNEL_LABELS,
  NOTIFICATION_EVENT_LABELS,
  NOTIFICATION_STATE_TONES,
  describeNotificationState,
} from '../../shared/i18n/consent'
import { formatDateTime } from '../../shared/lib/format'
import type { NotificationStatus } from './notificationsApi'

/**
 * FR-REP-004: the customer notice of a record, shown apart from its business status. "Listo para
 * entregar" means the appliance is ready; whether the customer was told is this list.
 */
export function NotificationStatusList({ notifications, emptyText }: { notifications: NotificationStatus[]; emptyText: string }) {
  if (notifications.length === 0) return <p className="muted small">{emptyText}</p>
  return (
    <ul className="notification-list">
      {notifications.map((notice) => (
        <li key={notice.id}>
          <span className={`badge badge-${NOTIFICATION_STATE_TONES[notice.status]}`}>
            {describeNotificationState(notice.status, notice.skipReason)}
          </span>{' '}
          {NOTIFICATION_EVENT_LABELS[notice.eventType]} · {CHANNEL_LABELS[notice.channel].toLowerCase()}
          <span className="muted small">
            {' '}
            · {notice.sentAt ? `enviado ${formatDateTime(notice.sentAt)}` : `registrado ${formatDateTime(notice.createdAt)}`}
            {notice.status !== 'SKIPPED' && notice.attempts > 1 && ` · ${notice.attempts} intentos`}
          </span>
        </li>
      ))}
    </ul>
  )
}
