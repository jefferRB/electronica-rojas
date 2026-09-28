import type { ReactNode } from 'react'
import { describeError } from '../api/describeError'
import { Alert } from './Alert'
import { Icon, type IconName } from './Icon'

interface EmptyStateProps {
  icon?: IconName
  title: string
  children?: ReactNode
  /** What the user can do next (a link or button). */
  action?: ReactNode
  compact?: boolean
}

/** No records: says why the area is empty and what the user can do about it. */
export function EmptyState({ icon = 'inbox', title, children, action, compact }: EmptyStateProps) {
  return (
    <div className={compact ? 'empty-state compact' : 'empty-state'}>
      <span className="empty-state-icon">
        <Icon name={icon} size={compact ? 20 : 24} />
      </span>
      <p className="empty-state-title">{title}</p>
      {children && <p className="empty-state-text">{children}</p>}
      {action}
    </div>
  )
}

/** Loading indicator announced to assistive technology; no artificial delay. */
export function LoadingState({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}

/** A failed request: the translated reason and, when useful, a retry. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Alert tone="error">
      {describeError(error)}
      {onRetry && (
        <>
          {' '}
          <button type="button" className="link-button" onClick={onRetry}>
            Reintentar
          </button>
        </>
      )}
    </Alert>
  )
}
