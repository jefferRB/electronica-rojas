import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { NetworkError } from '../../shared/api/httpClient'
import { fieldErrorsOf } from '../../shared/api/describeError'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { SelectField, TextField } from '../../shared/ui/Field'
import { useSession } from '../auth/session'
import { branchKeys, fetchBranches, type BranchSummary } from '../branches/branchesApi'
import { useSelectedBranch } from '../branches/selectedBranch'
import { fetchProduct, inventoryKeys, type ProductSummary } from '../inventory/inventoryApi'
import { ProductPicker } from '../inventory/ProductPicker'
import { describeStockError } from '../inventory/stockErrors'
import { createTransfer, type Transfer } from './transfersApi'

/**
 * FR-TRF-001..005: choose origin, destination, product and quantity, review a summary with the
 * current balances, then confirm. Disabling the button is only UX; the backend guarantees that
 * the operationId is applied once (DATA-003).
 */
export function TransferPage() {
  const { data: session } = useSession()
  const { selected } = useSelectedBranch()
  const queryClient = useQueryClient()
  const isAdmin = session?.user.role === 'ADMIN'

  const [sourceId, setSourceId] = useState<number | null>(selected?.id ?? null)
  const [destinationId, setDestinationId] = useState<number | null>(null)
  const [product, setProduct] = useState<ProductSummary | null>(null)
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  const [reviewing, setReviewing] = useState(false)
  const operationId = useRef(newOperationId())
  const [result, setResult] = useState<Transfer | null>(null)

  // Admins may send to any active branch; other roles only between their own branches.
  const allBranches = useQuery({
    queryKey: branchKeys.all,
    queryFn: ({ signal }) => fetchBranches(signal),
    enabled: isAdmin,
  })
  const sources: BranchSummary[] = session?.branches ?? []
  const destinations: BranchSummary[] = (
    isAdmin ? (allBranches.data ?? []).filter((branch) => branch.active) : sources
  ).filter((branch) => branch.id !== sourceId)

  const detail = useQuery({
    queryKey: product ? inventoryKeys.product(product.id) : ['products', 'detail', 'none'],
    queryFn: ({ signal }) => fetchProduct(product!.id, signal),
    enabled: product !== null,
  })
  const balanceAt = (branchId: number | null) =>
    detail.data?.stock.find((entry) => entry.branch.id === branchId)?.quantity

  const transfer = useMutation({
    mutationFn: createTransfer,
    onSuccess: async (created) => {
      setResult(created)
      operationId.current = newOperationId()
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
        queryClient.invalidateQueries({ queryKey: ['products', 'detail'] }),
      ])
    },
  })
  const errors = fieldErrorsOf(transfer.error)

  /** A changed field is a new intent: new operationId, back to editing. */
  function changed<T>(setter: (value: T) => void) {
    return (value: T) => {
      operationId.current = newOperationId()
      transfer.reset()
      setReviewing(false)
      setter(value)
    }
  }

  function reset() {
    setResult(null)
    setProduct(null)
    setQuantity('')
    setReason('')
    setReviewing(false)
    transfer.reset()
  }

  if (!session) return null
  const source = sources.find((branch) => branch.id === sourceId) ?? null
  const destination = destinations.find((branch) => branch.id === destinationId) ?? null
  const units = Number(quantity)
  const complete = source && destination && product && Number.isInteger(units) && units > 0
  const available = balanceAt(sourceId)
  // A branch that never had the product has 0 units (no stock row yet).
  const destinationBalance = balanceAt(destinationId) ?? 0

  const step = result ? 3 : reviewing && complete ? 2 : 1

  const header = {
    eyebrow: 'Inventario',
    icon: 'transfer',
    title: 'Transferir entre sucursales',
    description: 'Descuenta el origen y suma el destino en una sola operación; un reintento nunca se aplica dos veces.',
  } as const
  // The steps are a band of the module, right under its header: progress and form read as one unit.
  const stepper = (
    <ol className="stepper card-steps" aria-label="Pasos de la transferencia">
      {['Seleccionar', 'Revisar', 'Confirmar'].map((label, index) => (
        <li
          key={label}
          className={index + 1 < step || result ? 'step-done' : undefined}
          aria-current={index + 1 === step && !result ? 'step' : undefined}
        >
          <span className="step-text">{label}</span>
        </li>
      ))}
    </ol>
  )

  if (result) {
    return (
      <section className="page page-form">
        <ModuleSurface {...header}>
          {stepper}
          <div className="module-section">
            <Alert tone="success" title="Transferencia realizada.">
              Transferencia #{result.id}: {result.quantity} × {result.product.sku} de {result.source.name} a {result.destination.name}.
            </Alert>
            <div className="route-card">
              <div className="route-end">
                <p className="eyebrow">Origen</p>
                <strong>{result.source.name}</strong>
                <span className="route-balance">
                  Nuevo saldo <b>{result.sourceBalanceAfter}</b>
                </span>
              </div>
              <div className="route-arrow" aria-hidden="true">
                <span className="qty-move">−{result.quantity}</span>
                <Icon name="arrowRight" size={22} />
              </div>
              <div className="route-end">
                <p className="eyebrow">Destino</p>
                <strong>{result.destination.name}</strong>
                <span className="route-balance">
                  Nuevo saldo <b>{result.destinationBalanceAfter}</b>
                </span>
              </div>
            </div>
            <p className="muted small subsection-tight">
              Operación <span className="mono wrap-anywhere">{result.operationId}</span>
            </p>
            <div className="form-actions">
              <Link className="button button-secondary" to={`/transfers/${result.id}`}>
                Ver detalle y movimientos
              </Link>
              <button type="button" className="button button-primary" onClick={reset}>
                <Icon name="plus" />
                Nueva transferencia
              </button>
          </div>
          </div>
        </ModuleSurface>
      </section>
    )
  }

  return (
    <section className="page page-form">
      <ModuleSurface {...header}>
        {stepper}
        <div className="module-section">
          <div className="module-section-title">
            <h2>Origen, destino y producto</h2>
          </div>
          {sources.length === 0 && <Alert tone="info">No tienes sucursales autorizadas para transferir.</Alert>}
          <div className="form-grid">
            <SelectField
              label="Sucursal de origen"
              value={sourceId ?? ''}
              error={errors.sourceBranchId}
              onChange={(event) => changed(setSourceId)(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">Selecciona…</option>
              {sources.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.code} · {branch.name}
                </option>
              ))}
            </SelectField>
            <SelectField
              label="Sucursal de destino"
              value={destinationId ?? ''}
              error={errors.destinationBranchId}
              onChange={(event) => changed(setDestinationId)(event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">Selecciona…</option>
              {destinations.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.code} · {branch.name}
                </option>
              ))}
            </SelectField>
          </div>
          <ProductPicker label="Producto" value={product} onChange={changed(setProduct)} error={errors.productId} />
          {product && source && (
            <p className="field-hint">
              Existencias actuales en {source.name}: <strong>{available ?? '…'}</strong>
            </p>
          )}
          <div className="form-grid">
            <TextField
              label="Cantidad (unidades)"
              type="number"
              inputMode="numeric"
              min={1}
              step={1}
              value={quantity}
              error={errors.quantity}
              onChange={(event) => changed(setQuantity)(event.target.value)}
            />
            <TextField
              label="Motivo (opcional)"
              maxLength={300}
              value={reason}
              error={errors.reason}
              onChange={(event) => changed(setReason)(event.target.value)}
            />
          </div>
          {!reviewing && (
            <div className="form-actions">
              <button type="button" className="button button-primary" disabled={!complete} onClick={() => setReviewing(true)}>
                Revisar transferencia
                <Icon name="arrowRight" />
              </button>
            </div>
          )}
        </div>

        {reviewing && complete && (
          <div className="card card-highlight">
            <h2>Revisa y confirma</h2>
            {transfer.isError && (
              <Alert tone="error">
                {describeStockError(transfer.error)}
                {transfer.error instanceof NetworkError && ' Puedes reintentar: no se aplicará dos veces.'}
              </Alert>
            )}
            <p className="device-name">
              {product.name} <span className="mono muted small">{product.sku}</span>
            </p>
            <div className="route-card" aria-label="Saldos antes y después">
              <div className="route-end">
                <p className="eyebrow">Origen</p>
                <strong>{source.name}</strong>
                <span className="route-balance">
                  {available ?? '?'} → <b>{available !== undefined ? available - units : '?'}</b>
                </span>
              </div>
              <div className="route-arrow">
                <span className="qty-move">{units} u.</span>
                <Icon name="arrowRight" size={22} />
              </div>
              <div className="route-end">
                <p className="eyebrow">Destino</p>
                <strong>{destination.name}</strong>
                <span className="route-balance">
                  {destinationBalance} → <b>{destinationBalance + units}</b>
                </span>
              </div>
            </div>
            {reason.trim() && <p className="small subsection-tight">Motivo: {reason.trim()}</p>}
            {available !== undefined && available < units && (
              <Alert tone="warn">El origen muestra menos unidades de las solicitadas; el servidor rechazará la operación.</Alert>
            )}
            <div className="form-actions">
              <button
                type="button"
                className="button button-primary"
                disabled={transfer.isPending}
                onClick={() =>
                  transfer.mutate({
                    operationId: operationId.current,
                    sourceBranchId: source.id,
                    destinationBranchId: destination.id,
                    productId: product.id,
                    quantity: units,
                    reason: reason.trim(),
                  })
                }
              >
                {transfer.isPending ? 'Transfiriendo…' : transfer.error instanceof NetworkError ? 'Reintentar' : 'Confirmar transferencia'}
              </button>
              <button type="button" className="button button-secondary" disabled={transfer.isPending} onClick={() => setReviewing(false)}>
                Editar
              </button>
            </div>
          </div>
        )}
      </ModuleSurface>
    </section>
  )
}
