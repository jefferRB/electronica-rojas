import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { formatDateTime, formatSigned } from '../../shared/lib/format'
import { MOVEMENT_LABELS } from '../../shared/i18n/labels'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { fetchTransfer, transferKeys } from './transfersApi'

/** FR-TRF-004: header, both movements and the actor. 404 if outside the user's branches. */
export function TransferDetailPage() {
  const transferId = Number(useParams().transferId)
  const transfer = useQuery({
    queryKey: transferKeys.detail(transferId),
    queryFn: ({ signal }) => fetchTransfer(transferId, signal),
    enabled: Number.isInteger(transferId),
  })

  if (transfer.isPending) return <LoadingState label="Cargando transferencia…" />
  if (transfer.isError) return <ErrorState error={transfer.error} onRetry={() => transfer.refetch()} />
  const data = transfer.data

  return (
    <section className="page page-form">
      <ModuleSurface
        breadcrumb={[{ to: '/inventory/movements', label: 'Movimientos' }]}
        eyebrow="Transferencia"
        icon="transfer"
        title={`Transferencia #${data.id}`}
        meta={
          <>
            <span className="chip">
              <Icon name="user" />
              {data.actor.fullName}
            </span>
            <span className="chip">
              <Icon name="clock" />
              {formatDateTime(data.createdAt)}
            </span>
          </>
        }
      >
        <div className="module-section">
          <p className="device-name">
            {data.product.name} <span className="mono muted small">{data.product.sku}</span>
          </p>
          <div className="route-card">
            <div className="route-end">
              <p className="eyebrow">Origen</p>
              <strong>{data.source.name}</strong>
              <span className="route-balance">
                Saldo después <b>{data.sourceBalanceAfter}</b>
              </span>
            </div>
            <div className="route-arrow" aria-label={`${data.quantity} unidades`}>
              <span className="qty-move">{data.quantity} u.</span>
              <Icon name="arrowRight" size={22} />
            </div>
            <div className="route-end">
              <p className="eyebrow">Destino</p>
              <strong>{data.destination.name}</strong>
              <span className="route-balance">
                Saldo después <b>{data.destinationBalanceAfter}</b>
              </span>
            </div>
          </div>
          <dl className="details subsection">
            <dt>Motivo</dt>
            <dd>{data.reason ?? '—'}</dd>
            <dt>Operación</dt>
            <dd className="mono wrap-anywhere">{data.operationId}</dd>
          </dl>
        </div>

        <div className="card-header">
          <div className="card-heading">
            <span className="card-heading-icon tone-muted" aria-hidden="true">
              <Icon name="history" size={18} />
            </span>
            <div>
              <h2>Movimientos</h2>
              <p className="card-subtitle">Ambos tramos se registraron en la misma transacción.</p>
            </div>
          </div>
        </div>
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Sucursal</th>
                  <th scope="col">Tipo</th>
                  <th scope="col" className="numeric">
                    Cantidad
                  </th>
                  <th scope="col" className="numeric">
                    Saldo
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.movements.map((movement) => (
                  <tr key={movement.id}>
                    <td className="cell-title">{movement.branch.name}</td>
                    <td data-label="Tipo">{MOVEMENT_LABELS[movement.type]}</td>
                    <td data-label="Cantidad" className="numeric">
                      <span className={`delta ${movement.quantityDelta < 0 ? 'delta-out' : 'delta-in'}`}>{formatSigned(movement.quantityDelta)}</span>
                    </td>
                    <td data-label="Saldo" className="numeric">
                      {movement.balanceBefore} → {movement.balanceAfter}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
      </ModuleSurface>
    </section>
  )
}
