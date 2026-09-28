import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { formatDateTime, formatSigned } from '../../shared/lib/format'
import { Icon } from '../../shared/ui/Icon'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { SelectField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { useSelectedBranch } from '../branches/selectedBranch'
import { fetchMovements, inventoryKeys, type MovementType } from './inventoryApi'
import { MOVEMENT_LABELS } from '../../shared/i18n/labels'

const PAGE_SIZE = 20

/** Immutable movement history of the selected branch, newest first (BR-INV-003). */
export function MovementsPage() {
  const { selected } = useSelectedBranch()
  const [params, setParams] = useSearchParams()
  const filters = {
    type: (params.get('type') ?? '') as MovementType | '',
    page: Number(params.get('page') ?? 0),
    size: PAGE_SIZE,
  }
  const movements = useQuery({
    queryKey: selected ? inventoryKeys.movements(selected.id, filters) : ['movements', 'none'],
    queryFn: ({ signal }) => fetchMovements(selected!.id, filters, signal),
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

  if (!selected) return null

  return (
    <>
      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon tone-muted" aria-hidden="true">
            <Icon name="history" size={18} />
          </span>
          <div>
            <h2>Historial de movimientos</h2>
            <p className="card-subtitle">Registro inmutable de {selected.name}: entradas, salidas, ajustes, transferencias y repuestos.</p>
          </div>
        </div>
      </div>
      <div className="filters">
        <SelectField label="Tipo" value={filters.type} onChange={(event) => setFilter('type', event.target.value)}>
          <option value="">Todos</option>
          {(Object.keys(MOVEMENT_LABELS) as MovementType[]).map((type) => (
            <option key={type} value={type}>
              {MOVEMENT_LABELS[type]}
            </option>
          ))}
        </SelectField>
      </div>
      {movements.isPending && <LoadingState label="Cargando movimientos…" />}
      {movements.isError && <ErrorState error={movements.error} onRetry={() => movements.refetch()} />}
      {movements.data?.content.length === 0 && (
        <EmptyState icon="history" title={filters.type ? 'No hay movimientos de ese tipo' : 'Todavía no hay movimientos en esta sucursal'}>
          Cada entrada, salida, ajuste o transferencia queda registrada aquí con su saldo anterior y posterior.
        </EmptyState>
      )}
      {movements.data && movements.data.content.length > 0 && (
        <>
          <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Fecha</th>
                <th scope="col">Tipo</th>
                <th scope="col">Producto</th>
                <th scope="col" className="numeric">
                  Cantidad
                </th>
                <th scope="col" className="numeric">
                  Saldo
                </th>
                <th scope="col">Motivo</th>
                <th scope="col">Usuario</th>
              </tr>
            </thead>
            <tbody>
              {movements.data.content.map((movement) => (
                <tr key={movement.id}>
                  <td data-label="Fecha">{formatDateTime(movement.createdAt)}</td>
                  <td data-label="Tipo">
                    {movement.transferId ? (
                      <Link to={`/transfers/${movement.transferId}`}>{MOVEMENT_LABELS[movement.type]}</Link>
                    ) : (
                      MOVEMENT_LABELS[movement.type]
                    )}
                  </td>
                  <td data-label="Producto" className="span-row">
                    <span className="product-cell">
                      <Link className="product-name" to={`/inventory/products/${movement.product.id}`}>
                        {movement.product.name}
                      </Link>
                      <span className="mono cell-sub">{movement.product.sku}</span>
                    </span>
                  </td>
                  <td data-label="Cantidad" className="numeric">
                    <span className={`delta ${movement.quantityDelta < 0 ? 'delta-out' : 'delta-in'}`}>{formatSigned(movement.quantityDelta)}</span>
                  </td>
                  <td data-label="Saldo" className="numeric">
                    {movement.balanceBefore} → {movement.balanceAfter}
                  </td>
                  <td data-label="Motivo">
                    {movement.repairOrderId !== null && (
                      <Link to={`/repairs/${movement.repairOrderId}`} className="mono">
                        {movement.repairOrderCode ?? 'Orden'}
                      </Link>
                    )}
                    {movement.repairOrderId !== null && movement.reason ? ' · ' : ''}
                    {movement.reason ?? (movement.repairOrderId !== null ? '' : '—')}
                  </td>
                  <td data-label="Usuario">{movement.actor.fullName}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <Pagination
            page={movements.data.page}
            totalPages={movements.data.totalPages}
            totalElements={movements.data.totalElements}
            noun="movimientos"
            label="Paginación de movimientos"
            onChange={(page) => setFilter('page', String(page))}
          />
        </>
      )}
    </>
  )
}
