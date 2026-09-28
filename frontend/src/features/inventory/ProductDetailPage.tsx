import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { describeError } from '../../shared/api/describeError'
import { formatDateTime, formatSigned } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { useSelectedBranch } from '../branches/selectedBranch'
import { fetchMovements, fetchProduct, inventoryKeys } from './inventoryApi'
import { StockStatusBadge } from './StockStatusBadge'
import { KIND_LABELS, MOVEMENT_LABELS } from '../../shared/i18n/labels'

/** FR-INV-002: catalog data, stock per authorized branch and recent movements at the selected branch. */
export function ProductDetailPage() {
  const productId = Number(useParams().productId)
  const { selected } = useSelectedBranch()
  const detail = useQuery({
    queryKey: inventoryKeys.product(productId),
    queryFn: ({ signal }) => fetchProduct(productId, signal),
    enabled: Number.isInteger(productId),
  })
  const recentFilters = { productId, size: 10 }
  const recent = useQuery({
    queryKey: selected ? inventoryKeys.movements(selected.id, recentFilters) : ['movements', 'none'],
    queryFn: ({ signal }) => fetchMovements(selected!.id, recentFilters, signal),
    enabled: selected !== null && Number.isInteger(productId),
  })

  if (detail.isPending) return <LoadingState label="Cargando producto…" />
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
  const { product, stock } = detail.data

  return (
    <>
      <div className="panel-toolbar">
        <Link className="button button-ghost button-small" to="/inventory/catalog">
          <Icon name="chevronLeft" />
          Volver al catálogo
        </Link>
      </div>
      <div className="module-columns">
        <article className="module-section">
          <div className="product-hero">
            <span className="card-heading-icon" aria-hidden="true">
              <Icon name={product.kind === 'SPARE_PART' ? 'cpu' : 'package'} size={18} />
            </span>
            <div>
              <h2>{product.name}</h2>
              <span className="mono muted">{product.sku}</span>
            </div>
          </div>
          {!product.active && <Alert tone="info">Producto inactivo: se conserva su historial pero no admite nuevos movimientos.</Alert>}
          <dl className="details">
            <dt>Categoría</dt>
            <dd>{product.category}</dd>
            <dt>Tipo</dt>
            <dd>{KIND_LABELS[product.kind]}</dd>
            <dt>Unidad</dt>
            <dd>Unidades</dd>
            <dt>Descripción</dt>
            <dd>{product.description ?? '—'}</dd>
          </dl>
        </article>
        <article className="module-section">
          <div className="module-section-title">
            <h2>Existencias por sucursal</h2>
          </div>
          {stock.length === 0 ? (
            <p className="muted">No tienes sucursales autorizadas.</p>
          ) : (
            <div className="table-scroll keep-columns">
              <table className="data-table compact stock-table">
                <thead>
                  <tr>
                    <th scope="col">Sucursal</th>
                    <th scope="col" className="numeric">
                      Existencias
                    </th>
                    <th scope="col" className="numeric">
                      Mínimo
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {stock.map((entry) => (
                    <tr key={entry.branch.id} className={`stock-row stock-${entry.stockStatus.toLowerCase()}`}>
                      <td data-label="Sucursal">
                        {entry.branch.code} · {entry.branch.name}
                        {!entry.branchActive && <span className="badge badge-muted"> Inactiva</span>}
                      </td>
                      <td data-label="Existencias" className="numeric">
                        <strong>{entry.quantity}</strong> <StockStatusBadge status={entry.stockStatus} />
                      </td>
                      <td data-label="Mínimo" className="numeric">
                        {entry.minimumQuantity}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </article>
      </div>
      {selected && (
        <section className="module-section">
          <div className="module-section-title">
            <h2>Movimientos recientes en {selected.name}</h2>
          </div>
          {recent.isError && <Alert tone="error">{describeError(recent.error)}</Alert>}
          {recent.data?.content.length === 0 && <p className="muted">Sin movimientos en esta sucursal.</p>}
          {recent.data && recent.data.content.length > 0 && (
            <ul className="timeline">
              {recent.data.content.map((movement) => (
                <li key={movement.id}>
                  <span className="muted">{formatDateTime(movement.createdAt)}</span> · {MOVEMENT_LABELS[movement.type]}{' '}
                  <strong>{formatSigned(movement.quantityDelta)}</strong> (saldo {movement.balanceAfter}) ·{' '}
                  {movement.actor.fullName}
                  {movement.reason && <> · {movement.reason}</>}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  )
}
