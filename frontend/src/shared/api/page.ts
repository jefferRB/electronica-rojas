/** Mirrors shared.web.PageResponse on the backend. `page` is zero-based. */
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
