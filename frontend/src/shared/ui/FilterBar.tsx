import { useId, useState, type ReactNode } from 'react'
import { Icon } from './Icon'

interface FilterBarProps {
  /** Always visible (usually the search field). */
  search?: ReactNode
  /** Number of active secondary filters, shown on the phone toggle. */
  activeCount?: number
  /** Resets search and filters; the button only shows while something is applied (`canClear`). */
  onClear?: () => void
  canClear?: boolean
  /** Secondary filters: inline on wide screens, behind "Filtros" on phones. */
  children?: ReactNode
}

/**
 * Filters of a list. On phones the secondary filters collapse behind a disclosure button that says
 * how many are active, so the results stay on screen; on wider screens everything is visible.
 */
export function FilterBar({ search, activeCount = 0, onClear, canClear = false, children }: FilterBarProps) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const clear = onClear && canClear && (
    <button type="button" className="button button-ghost filter-clear" onClick={onClear}>
      <Icon name="close" />
      Limpiar filtros
    </button>
  )
  return (
    <div className={search ? 'filter-bar inline' : 'filter-bar'}>
      {(search || children) && (
        <div className="filter-bar-row">
          {search && <div className="filter-search">{search}</div>}
          {children && (
            <button
              type="button"
              className="button button-secondary filter-toggle"
              aria-expanded={open}
              aria-controls={id}
              onClick={() => setOpen((value) => !value)}
            >
              <Icon name="filter" />
              Filtros{activeCount > 0 ? ` (${activeCount})` : ''}
            </button>
          )}
        </div>
      )}
      {children && (
        <div className="filter-extra" id={id} data-open={open}>
          {children}
        </div>
      )}
      {clear && <div className="filter-actions">{clear}</div>}
    </div>
  )
}
