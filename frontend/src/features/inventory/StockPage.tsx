import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { formatDateTime } from '../../shared/lib/format'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { SearchField, SelectField, TextField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { useSession } from '../auth/session'
import { permissions } from '../auth/permissions'
import type { BranchSummary } from '../branches/branchesApi'
import { useSelectedBranch } from '../branches/selectedBranch'
import {
  fetchCategories,
  fetchStock,
  inventoryKeys,
  updateMinimum,
  type ProductKind,
  type ProductSummary,
  type StockItem,
  type StockStatusFilter,
} from './inventoryApi'
import { KIND_LABELS } from '../../shared/i18n/labels'
import { MovementForm } from './MovementForm'
import { StockStatusBadge } from './StockStatusBadge'
import { StockStatusFilterField } from './StockStatusFilterField'

const PAGE_SIZE = 20

/** FR-INV-001: stock of the selected branch with search, filters (kept in the URL) and paging. */
export function StockPage() {
  const { data: session } = useSession()
  const { selected } = useSelectedBranch()
  const [params, setParams] = useSearchParams()
  const [searchText, setSearchText] = useState(params.get('q') ?? '')
  const search = useDebouncedValue(searchText.trim())
  const [moving, setMoving] = useState<{ product: ProductSummary | null } | null>(null)

  const filters = {
    search,
    category: params.get('category') ?? '',
    kind: (params.get('kind') ?? '') as ProductKind | '',
    stockStatus: (params.get('stock') ?? '') as StockStatusFilter | '',
    page: Number(params.get('page') ?? 0),
    size: PAGE_SIZE,
  }
  const categories = useQuery({ queryKey: inventoryKeys.categories, queryFn: ({ signal }) => fetchCategories(signal) })
  const stock = useQuery({
    queryKey: selected ? inventoryKeys.stock(selected.id, filters) : ['stock', 'none'],
    queryFn: ({ signal }) => fetchStock(selected!.id, filters, signal),
    enabled: selected !== null,
    placeholderData: keepPreviousData,
  })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  if (!session || !selected) return null
  const canOperate = permissions.operateInventory(session.user.role)

  return (
    <>
      {canOperate && moving && (
        <MovementForm
          key={moving.product?.id ?? 'new'}
          branch={selected}
          initialProduct={moving.product}
          onClose={() => setMoving(null)}
        />
      )}

      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon" aria-hidden="true">
            <Icon name="box" size={18} />
          </span>
          <div>
            <h2>Existencias de {selected.name}</h2>
            <p className="card-subtitle">Todo el catálogo activo con la cantidad física de la sucursal.</p>
          </div>
        </div>
        {canOperate && !moving && (
          <button type="button" className="button button-primary" onClick={() => setMoving({ product: null })}>
            <Icon name="plus" />
            Registrar movimiento
          </button>
        )}
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

      {stock.isPending && <LoadingState label="Cargando existencias…" />}
      {stock.isError && <ErrorState error={stock.error} onRetry={() => stock.refetch()} />}
      {stock.data?.content.length === 0 && (
        <EmptyState icon="box" title="Ningún producto coincide con los filtros">
          Quita algún filtro o busca por SKU o nombre.
        </EmptyState>
      )}
      {stock.data && stock.data.content.length > 0 && (
        <>
          <div className="table-scroll">
          <table className="data-table stock-table">
            <thead>
              <tr>
                <th scope="col">Producto</th>
                <th scope="col">Categoría</th>
                <th scope="col" className="numeric">
                  Existencias
                </th>
                <th scope="col" className="numeric">
                  Mínimo
                </th>
                <th scope="col">Estado</th>
                <th scope="col">Actualizado</th>
                <th scope="col">
                  <span className="visually-hidden">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {stock.data.content.map((item) => (
                <StockRow
                  key={item.product.id}
                  item={item}
                  branch={selected}
                  canOperate={canOperate}
                  onMove={() => setMoving({ product: item.product })}
                />
              ))}
            </tbody>
          </table>
          </div>
          <Pagination
            page={stock.data.page}
            totalPages={stock.data.totalPages}
            totalElements={stock.data.totalElements}
            noun="productos"
            label="Paginación de existencias"
            onChange={(page) => setFilter('page', String(page))}
          />
        </>
      )}
    </>
  )
}

function StockRow({
  item,
  branch,
  canOperate,
  onMove,
}: {
  item: StockItem
  branch: BranchSummary
  canOperate: boolean
  onMove: () => void
}) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [minimum, setMinimum] = useState(String(item.minimumQuantity))
  const save = useMutation({
    mutationFn: () => updateMinimum(branch.id, item.product.id, Number(minimum)),
    onSuccess: async () => {
      setEditing(false)
      await queryClient.invalidateQueries({ queryKey: ['stock', branch.id] })
    },
  })

  return (
    <tr className={`stock-row stock-${item.stockStatus.toLowerCase()}`}>
      <td className="cell-title">
        <span className="product-cell">
          <Link className="product-name" to={`/inventory/products/${item.product.id}`}>
            {item.product.name}
          </Link>
          <span className="mono cell-sub">{item.product.sku}</span>
        </span>
      </td>
      <td data-label="Categoría">{item.product.category}</td>
      <td data-label="Existencias" className="numeric">
        <span className="qty">{item.quantity}</span>
      </td>
      <td data-label="Mínimo" className="numeric">
        {editing ? (
          <form
            className="inline-form"
            onSubmit={(event) => {
              event.preventDefault()
              save.mutate()
            }}
          >
            <TextField
              label="Nuevo mínimo"
              type="number"
              min={0}
              value={minimum}
              error={fieldErrorsOf(save.error).minimumQuantity ?? (save.isError ? describeError(save.error) : undefined)}
              onChange={(event) => setMinimum(event.target.value)}
            />
            <button type="submit" className="button button-primary button-small" disabled={save.isPending}>
              Guardar
            </button>
            <button type="button" className="button button-secondary button-small" onClick={() => setEditing(false)}>
              Cancelar
            </button>
          </form>
        ) : (
          item.minimumQuantity
        )}
      </td>
      <td data-label="Estado">
        <StockStatusBadge status={item.stockStatus} />
      </td>
      <td data-label="Actualizado">{formatDateTime(item.updatedAt)}</td>
      <td className="actions">
        {canOperate && !editing && (
          <div className="row-actions">
            <button type="button" className="button button-secondary button-small" onClick={onMove}>
              Movimiento
            </button>
            <button type="button" className="button button-secondary button-small" onClick={() => setEditing(true)}>
              Mínimo
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}
