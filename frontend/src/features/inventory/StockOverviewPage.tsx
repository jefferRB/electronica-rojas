import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { KIND_LABELS } from '../../shared/i18n/labels'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Alert } from '../../shared/ui/Alert'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { SearchField, SelectField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { useSelectedBranch } from '../branches/selectedBranch'
import { fetchCategories, fetchStockOverview, inventoryKeys, type ProductKind, type StockStatusFilter } from './inventoryApi'
import { StockStatusBadge } from './StockStatusBadge'
import { StockStatusFilterField } from './StockStatusFilterField'

const PAGE_SIZE = 20

/**
 * A.3: every authorized branch side by side. The columns come from the API (the server decides
 * the scope), so there is no assumption about how many branches exist.
 */
export function StockOverviewPage() {
  const [params, setParams] = useSearchParams()
  const [searchText, setSearchText] = useState(params.get('q') ?? '')
  const search = useDebouncedValue(searchText.trim())
  const { select } = useSelectedBranch()
  const navigate = useNavigate()
  const filters = {
    search,
    category: params.get('category') ?? '',
    kind: (params.get('kind') ?? '') as ProductKind | '',
    stockStatus: (params.get('stock') ?? '') as StockStatusFilter | '',
    page: Number(params.get('page') ?? 0),
    size: PAGE_SIZE,
  }
  const overview = useQuery({
    queryKey: inventoryKeys.overview(filters),
    queryFn: ({ signal }) => fetchStockOverview(filters, signal),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({ queryKey: inventoryKeys.categories, queryFn: ({ signal }) => fetchCategories(signal) })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  /** Jump to one branch's stock for this product (keeps the header selector in sync). */
  function openBranch(branchId: number, sku: string) {
    select(branchId)
    navigate(`/inventory?q=${encodeURIComponent(sku)}`)
  }

  const branches = overview.data?.branches ?? []

  return (
    <>
      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon tone-accent" aria-hidden="true">
            <Icon name="building" size={18} />
          </span>
          <div>
            <h2>Existencias de todas tus sucursales</h2>
            <p className="card-subtitle">Una columna por sucursal; toca una cantidad para abrir esa sucursal.</p>
          </div>
        </div>
      </div>
      <FilterBar
        activeCount={[filters.category, filters.kind, filters.stockStatus].filter(Boolean).length}
        canClear={Boolean(searchText || filters.category || filters.kind || filters.stockStatus)}
        onClear={() => {
          setSearchText('')
          setParams(new URLSearchParams(), { replace: true })
        }}
        search={
          <SearchField
            label="Buscar"
            placeholder="SKU o nombre"
            value={searchText}
            onChange={(event) => {
              setSearchText(event.target.value)
              setFilter('q', event.target.value.trim())
            }}
          />
        }
      >
        <SelectField label="Categoría" value={filters.category} onChange={(event) => setFilter('category', event.target.value)}>
          <option value="">Todas</option>
          {categories.data?.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </SelectField>
        <SelectField label="Tipo" value={filters.kind} onChange={(event) => setFilter('kind', event.target.value)}>
          <option value="">Todos</option>
          <option value="MERCHANDISE">{KIND_LABELS.MERCHANDISE}</option>
          <option value="SPARE_PART">{KIND_LABELS.SPARE_PART}</option>
        </SelectField>
        <StockStatusFilterField value={filters.stockStatus} onChange={(value) => setFilter('stock', value)} />
      </FilterBar>

      {overview.isPending && <LoadingState label="Cargando existencias…" />}
      {overview.isError && <ErrorState error={overview.error} onRetry={() => overview.refetch()} />}
      {overview.data && branches.length === 0 && (
        <Alert tone="info">No tienes sucursales activas autorizadas para consultar.</Alert>
      )}
      {overview.data && branches.length > 0 && overview.data.rows.content.length === 0 && (
        <EmptyState icon="box" title="Ningún producto coincide con los filtros">
          Quita algún filtro o busca por SKU o nombre.
        </EmptyState>
      )}
      {overview.data && branches.length > 0 && overview.data.rows.content.length > 0 && (
        <>
          <div className="table-scroll">
            <table className="data-table overview-table">
              <thead>
                <tr>
                  <th scope="col">Producto</th>
                  <th scope="col">Categoría</th>
                  {branches.map((branch) => (
                    <th key={branch.id} scope="col" className="numeric" title={branch.name}>
                      {branch.code}
                    </th>
                  ))}
                  <th scope="col" className="numeric">
                    Total
                  </th>
                </tr>
              </thead>
              <tbody>
                {overview.data.rows.content.map((row) => (
                  <tr key={row.product.id}>
                    <td className="cell-title">
                      <span className="product-cell">
                        <Link className="product-name" to={`/inventory/products/${row.product.id}`}>
                          {row.product.name}
                        </Link>
                        <span className="cell-sub">
                          <span className="mono">{row.product.sku}</span> · {KIND_LABELS[row.product.kind]}
                        </span>
                      </span>
                    </td>
                    <td data-label="Categoría">{row.product.category}</td>
                    {row.cells.map((cell) => {
                      const branch = branches.find((item) => item.id === cell.branchId)!
                      return (
                        <td key={cell.branchId} data-label={`${branch.code} · ${branch.name}`} className={`numeric stock-cell stock-${cell.stockStatus.toLowerCase()}`}>
                          <button
                            type="button"
                            className="cell-link"
                            onClick={() => openBranch(cell.branchId, row.product.sku)}
                            aria-label={`${row.product.sku} en ${branch.name}: ${cell.quantity} unidades. Abrir existencias de la sucursal`}
                          >
                            <strong>{cell.quantity}</strong>
                          </button>
                          {cell.stockStatus !== 'NORMAL' && <StockStatusBadge status={cell.stockStatus} />}
                        </td>
                      )
                    })}
                    <td data-label="Total" className="numeric">
                      <strong>{row.totalQuantity}</strong>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={overview.data.rows.page}
            totalPages={overview.data.rows.totalPages}
            totalElements={overview.data.rows.totalElements}
            noun="productos"
            label="Paginación de la vista consolidada"
            onChange={(page) => setFilter('page', String(page))}
          />
        </>
      )}
    </>
  )
}
