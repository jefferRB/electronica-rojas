import { useQuery } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { describeError } from '../../shared/api/describeError'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { fetchProducts, inventoryKeys, type ProductSummary } from './inventoryApi'

interface Props {
  label: string
  value: ProductSummary | null
  onChange: (product: ProductSummary | null) => void
  error?: string
}

/**
 * Searchable picker over the paginated catalog (never loads the whole catalog). Only active
 * products are offered for new operations (BR-INV-004).
 */
export function ProductPicker({ label, value, onChange, error }: Props) {
  const id = useId()
  const [search, setSearch] = useState('')
  const debounced = useDebouncedValue(search.trim())
  const filters = { search: debounced, status: 'ACTIVE' as const, size: 8 }
  const results = useQuery({
    queryKey: inventoryKeys.products(filters),
    queryFn: ({ signal }) => fetchProducts(filters, signal),
    enabled: value === null && debounced.length >= 2,
  })

  if (value) {
    return (
      <div className="field">
        <span className="field-label">{label}</span>
        <div className="picked">
          <span>
            <strong>{value.sku}</strong> · {value.name}
          </span>
          <button type="button" className="button button-secondary button-small" onClick={() => onChange(null)}>
            Cambiar
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="field product-picker">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="search"
        placeholder="Buscar por SKU o nombre (mín. 2 caracteres)"
        value={search}
        autoComplete="off"
        aria-invalid={error ? true : undefined}
        onChange={(event) => setSearch(event.target.value)}
      />
      {error && <p className="field-error">{error}</p>}
      {results.isFetching && <p className="field-hint">Buscando…</p>}
      {results.isError && <p className="field-error">{describeError(results.error)}</p>}
      {results.data && (
        <ul className="picker-results" aria-label="Resultados">
          {results.data.content.length === 0 && <li className="muted">Sin resultados activos.</li>}
          {results.data.content.map((product) => (
            <li key={product.id}>
              <button type="button" onClick={() => onChange(product)}>
                <strong>{product.sku}</strong> · {product.name} <span className="muted">({product.category})</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
