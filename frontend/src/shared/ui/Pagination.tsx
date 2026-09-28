import { Icon } from './Icon'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  noun: string
  label: string
  onChange: (page: number) => void
}

/** Previous/next pager for PageResponse lists (UX-003). `page` is zero-based. */
export function Pagination({ page, totalPages, totalElements, noun, label, onChange }: PaginationProps) {
  return (
    <nav className="pagination" aria-label={label}>
      <button type="button" className="button button-secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <Icon name="chevronLeft" />
        Anterior
      </button>
      <span className="pagination-status">
        Página {page + 1} de {Math.max(totalPages, 1)} · {totalElements} {noun}
      </span>
      <button
        type="button"
        className="button button-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Siguiente
        <Icon name="chevronRight" />
      </button>
    </nav>
  )
}
