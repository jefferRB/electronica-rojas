import { Fragment } from 'react'
import { REPAIR_STATUS_LABELS } from '../../shared/i18n/labels'
import { formatColones, formatDateTime } from '../../shared/lib/format'
import { SectionCard } from '../../shared/ui/SectionCard'
import type { PartUsage, PartsSummary, RepairOrderDetail } from './repairsApi'

const OPEN_STATUSES = ['RECEIVED', 'DIAGNOSING', 'AWAITING_APPROVAL', 'APPROVED', 'IN_REPAIR']

/** What the line adds to the chargeable subtotal, said in words when it is not a plain amount. */
function ChargeCell({ part }: { part: PartUsage }) {
  if (!part.chargeable) {
    return (
      <>
        <span className="badge badge-muted">Sin cargo</span>
        <span className="cell-sub">No suma al subtotal</span>
      </>
    )
  }
  if (part.chargedAmount === null) {
    return <span className="badge badge-warn">Precio por definir</span>
  }
  return <strong>{formatColones(part.chargedAmount)}</strong>
}

/** Totals of the parts still in use; the cost appears only when the API sent it (allowed roles). */
function PartsTotals({ summary }: { summary: PartsSummary }) {
  if (summary.linesInUse === 0) return null
  return (
    <div className="parts-totals" aria-label="Totales de repuestos">
      <div className="parts-total">
        <span className="parts-total-label">Repuestos utilizados</span>
        <span className="parts-total-value">
          {summary.unitsInUse} {summary.unitsInUse === 1 ? 'unidad' : 'unidades'}
        </span>
        {summary.unitsWithoutCharge > 0 && <span className="cell-sub">{summary.unitsWithoutCharge} sin cargo</span>}
      </div>
      <div className="parts-total parts-total-main">
        <span className="parts-total-label">Subtotal cobrable</span>
        <span className="parts-total-value">{formatColones(summary.chargeableSubtotal)}</span>
        {summary.unpricedLines > 0 && (
          <span className="cell-sub">
            Sin incluir {summary.unpricedLines} {summary.unpricedLines === 1 ? 'línea sin precio' : 'líneas sin precio'}
          </span>
        )}
      </div>
      {summary.totalCost !== undefined && summary.totalCost !== null && (
        <div className="parts-total parts-total-internal">
          <span className="parts-total-label">Costo interno</span>
          <span className="parts-total-value">{formatColones(summary.totalCost)}</span>
          <span className="cell-sub">
            {summary.uncostedLines > 0 ? `${summary.uncostedLines} sin costo registrado · ` : ''}Solo gestión
          </span>
        </div>
      )}
    </div>
  )
}

/**
 * B.5 + BR-REP-014: spare parts used in the order with the price each line was recorded with.
 * Corrections appear under their line, so the history reads "used 3, returned 1" instead of
 * silently showing 2; the subtotal counts only the units still in use and only charged lines.
 */
export function RepairPartsCard({
  order,
  disabled,
  onReturn,
}: {
  order: RepairOrderDetail
  disabled: boolean
  onReturn: (part: PartUsage) => void
}) {
  const { parts, actions, partsSummary } = order
  return (
    <SectionCard
      title="Repuestos utilizados"
      icon="package"
      iconTone="accent"
      subtitle={`Descontados de las existencias de ${order.branch.name}. Cada línea conserva el precio con el que se registró.`}
    >
      {parts.length === 0 ? (
        <p className="muted small">
          Sin repuestos registrados.
          {order.status !== 'IN_REPAIR' && ' Los repuestos se registran mientras la orden está «En reparación».'}
        </p>
      ) : (
        <>
          {partsSummary && <PartsTotals summary={partsSummary} />}
          <div className="table-scroll">
            <table className="data-table parts-table">
              <thead>
                <tr>
                  <th scope="col">Repuesto</th>
                  <th scope="col" className="numeric">
                    Cantidad
                  </th>
                  <th scope="col" className="numeric">
                    Precio unitario
                  </th>
                  <th scope="col" className="numeric">
                    Cobro
                  </th>
                  <th scope="col">Registro</th>
                  {actions.canReturnParts && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {parts.map((part) => (
                  <Fragment key={part.id}>
                    <tr>
                      <td data-label="Repuesto" className="cell-title">
                        <span className="mono">{part.product.sku}</span> · {part.product.name}
                        {part.note && <div className="muted small">Nota: {part.note}</div>}
                      </td>
                      <td data-label="Cantidad" className="numeric">
                        {part.quantity}
                        {part.returnedQuantity > 0 && (
                          <div className="muted small">
                            {part.returnedQuantity} devuelta{part.returnedQuantity === 1 ? '' : 's'} · {part.remainingQuantity} en uso
                          </div>
                        )}
                      </td>
                      <td data-label="Precio unitario" className="numeric">
                        {part.unitPrice === null ? <span className="muted">Sin precio</span> : formatColones(part.unitPrice)}
                        {part.priceOverridden && <span className="cell-sub">Ajustado para esta orden</span>}
                        {part.unitCost !== undefined && part.unitCost !== null && (
                          <span className="cell-sub">Costo {formatColones(part.unitCost)}</span>
                        )}
                      </td>
                      <td data-label="Cobro" className="numeric">
                        <ChargeCell part={part} />
                      </td>
                      <td data-label="Registro">
                        <span className="cell-stack">
                          {part.recordedBy.fullName}
                          <span className="muted small">{formatDateTime(part.recordedAt)}</span>
                        </span>
                      </td>
                      {actions.canReturnParts && (
                        <td data-label="Acción">
                          {part.remainingQuantity > 0 && (
                            <button type="button" className="button button-secondary button-small" disabled={disabled} onClick={() => onReturn(part)}>
                              Corregir
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                    {part.returns.map((ret) => (
                      <tr key={`r${ret.id}`} className="row-correction">
                        <td data-label="Corrección">
                          <span className="badge badge-info">Devolución</span> {ret.reason}
                          {!OPEN_STATUSES.includes(ret.orderStatus) && (
                            <div className="muted small">Registrada con la orden en «{REPAIR_STATUS_LABELS[ret.orderStatus]}».</div>
                          )}
                        </td>
                        <td data-label="Cantidad" className="numeric positive">
                          −{ret.quantity} en uso
                        </td>
                        <td aria-hidden="true" />
                        <td aria-hidden="true" />
                        <td data-label="Registro">
                          <span className="cell-stack">
                            {ret.recordedBy.fullName}
                            <span className="muted small">{formatDateTime(ret.recordedAt)}</span>
                          </span>
                        </td>
                        {actions.canReturnParts && <td aria-hidden="true" />}
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </SectionCard>
  )
}
