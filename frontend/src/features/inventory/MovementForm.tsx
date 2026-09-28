import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { NetworkError } from '../../shared/api/httpClient'
import { fieldErrorsOf } from '../../shared/api/describeError'
import { formatSigned, newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { SelectField, TextField } from '../../shared/ui/Field'
import type { BranchSummary } from '../branches/branchesApi'
import { recordMovement, STANDALONE_TYPES, type Movement, type ProductSummary, type StandaloneMovementType } from './inventoryApi'
import { MOVEMENT_LABELS } from '../../shared/i18n/labels'
import { ProductPicker } from './ProductPicker'
import { describeStockError } from './stockErrors'

interface Props {
  branch: BranchSummary
  initialProduct?: ProductSummary | null
  onClose: () => void
}

/**
 * FR-INV-004: receipt, issue or adjustment at the selected branch. One operationId per user
 * intent: kept when retrying after a network error, renewed when any field changes.
 */
export function MovementForm({ branch, initialProduct = null, onClose }: Props) {
  const queryClient = useQueryClient()
  const [product, setProduct] = useState<ProductSummary | null>(initialProduct)
  const [type, setType] = useState<StandaloneMovementType>('RECEIPT')
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  const operationId = useRef(newOperationId())
  const [result, setResult] = useState<Movement | null>(null)

  const record = useMutation({
    mutationFn: recordMovement,
    onSuccess: async (movement) => {
      setResult(movement)
      setQuantity('')
      setReason('')
      operationId.current = newOperationId()
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock', branch.id] }),
        queryClient.invalidateQueries({ queryKey: ['movements', branch.id] }),
        queryClient.invalidateQueries({ queryKey: ['products', 'detail', movement.product.id] }),
      ])
    },
  })
  const errors = fieldErrorsOf(record.error)

  /** Any change makes it a new intent, so it must not be deduplicated against the previous one. */
  function changed<T>(setter: (value: T) => void) {
    return (value: T) => {
      operationId.current = newOperationId()
      setResult(null)
      record.reset()
      setter(value)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!product) return
    record.mutate({
      operationId: operationId.current,
      branchId: branch.id,
      productId: product.id,
      type,
      quantity: Number(quantity),
      reason: reason.trim(),
    })
  }

  const networkFailure = record.error instanceof NetworkError

  return (
    <form className="card card-highlight" onSubmit={handleSubmit} noValidate>
      <div className="card-title-row">
        <h2>Registrar movimiento en {branch.code}</h2>
        <button type="button" className="button button-secondary button-small" onClick={onClose}>
          Cerrar
        </button>
      </div>
      {result && (
        <Alert tone="success">
          {MOVEMENT_LABELS[result.type]} de {Math.abs(result.quantityDelta)} × {result.product.sku} registrada. Saldo:{' '}
          {result.balanceBefore} → <strong>{result.balanceAfter}</strong> ({formatSigned(result.quantityDelta)}).
        </Alert>
      )}
      {record.isError && (
        <Alert tone="error">
          {describeStockError(record.error)}
          {networkFailure && ' Puedes reintentar: la operación no se aplicará dos veces.'}
        </Alert>
      )}
      <ProductPicker label="Producto" value={product} onChange={changed(setProduct)} error={errors.productId} />
      <div className="form-grid">
        <SelectField
          label="Tipo"
          value={type}
          error={errors.type}
          onChange={(event) => changed(setType)(event.target.value as StandaloneMovementType)}
        >
          {STANDALONE_TYPES.map((value) => (
            <option key={value} value={value}>
              {MOVEMENT_LABELS[value]}
            </option>
          ))}
        </SelectField>
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
          label="Motivo"
          hint="Ej.: compra factura 123, venta mostrador, conteo físico."
          maxLength={300}
          value={reason}
          error={errors.reason}
          onChange={(event) => changed(setReason)(event.target.value)}
        />
      </div>
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={record.isPending || !product || !(Number(quantity) > 0) || !reason.trim()}
        >
          {record.isPending ? 'Registrando…' : networkFailure ? 'Reintentar' : 'Registrar'}
        </button>
      </div>
    </form>
  )
}
