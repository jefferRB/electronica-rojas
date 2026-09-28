import { STOCK_STATUS_LABELS, STOCK_STATUS_TONES, type StockStatus } from '../../shared/i18n/labels'

/** One look for stock status in every view (A.4): the text carries the meaning, color reinforces it. */
export function StockStatusBadge({ status }: { status: StockStatus }) {
  return <span className={`badge badge-${STOCK_STATUS_TONES[status]}`}>{STOCK_STATUS_LABELS[status]}</span>
}
