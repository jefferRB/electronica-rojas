import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError, NetworkError } from '../../shared/api/httpClient'
import { REPAIR_STATUS_LABELS } from '../../shared/i18n/labels'
import { newOperationId } from '../../shared/lib/format'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Alert } from '../../shared/ui/Alert'
import { MoneyField, TextAreaField, TextField } from '../../shared/ui/Field'
import { Switch } from '../../shared/ui/Switch'
import { formatColones } from '../../shared/lib/format'
import { MONEY_HINT, moneyToInput, parseMoney } from '../../shared/lib/money'
import type { SparePartOption } from '../inventory/inventoryApi'
import { describeStockError } from '../inventory/stockErrors'
import { StockStatusBadge } from '../inventory/StockStatusBadge'
import { consumePart, fetchPartOptions, repairKeys, returnPart, type PartUsage, type RepairOrderDetail } from './repairsApi'
import { partLineSubtotal, partQuantityError } from './repairLogic'

interface FormProps {
  order: RepairOrderDetail
  onCancel: () => void
  onDone: (message: string) => void
}

/** Detail, stock and repair lists that a part operation changes (one invalidation for all). */
function useRefreshAfterPartChange(orderId: number) {
  const queryClient = useQueryClient()
  return async (updated: RepairOrderDetail) => {
    queryClient.setQueryData(repairKeys.detail(orderId), updated)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
      queryClient.invalidateQueries({ queryKey: ['repairs', 'part-options', orderId] }),
      queryClient.invalidateQueries({ queryKey: ['repairs', 'list'] }),
    ])
  }
}

function useFocusFirstField() {
  const formRef = useRef<HTMLFormElement>(null)
  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('input, select, textarea')?.focus()
  }, [])
  return formRef
}

/**
 * B.2 + BR-REP-014: search a spare part of the order's branch, see its stock and price, choose the
 * quantity and whether it is charged. Management may set another price or charging decision for
 * this line (the catalog does not change); the technician records the catalog defaults. One
 * operationId per intent: kept after a network error (the retry is not applied twice), renewed when
 * the server answers or the user changes what they are recording.
 */
export function ConsumePartForm({ order, onCancel, onDone }: FormProps) {
  const queryClient = useQueryClient()
  const refresh = useRefreshAfterPartChange(order.id)
  const formRef = useFocusFirstField()
  const searchId = useId()
  const [search, setSearch] = useState('')
  const debounced = useDebouncedValue(search.trim())
  const [picked, setPicked] = useState<SparePartOption | null>(null)
  const [quantity, setQuantity] = useState('1')
  const [note, setNote] = useState('')
  const [priceText, setPriceText] = useState('')
  const [chargeable, setChargeable] = useState(true)
  const operationId = useRef(newOperationId())
  const renew = () => (operationId.current = newOperationId())
  const canOverride = order.actions.canOverridePartPricing

  const options = useQuery({
    queryKey: repairKeys.partOptions(order.id, debounced),
    queryFn: ({ signal }) => fetchPartOptions(order.id, debounced, signal),
    enabled: picked === null,
  })

  const quantityError = picked ? partQuantityError(quantity, picked.quantity) : undefined
  const price = parseMoney(priceText)
  const unitPrice = price.ok ? price.value : null
  const effectivePrice = unitPrice ?? picked?.salePrice ?? null
  const subtotal = picked && !quantityError ? partLineSubtotal(Number(quantity), effectivePrice, chargeable) : null
  // Only what departs from the catalog is sent; the server checks again who may do it.
  const priceChanged = picked !== null && price.ok && unitPrice !== picked.salePrice && unitPrice !== null
  const chargeChanged = picked !== null && chargeable !== picked.chargeableByDefault

  function pick(item: SparePartOption) {
    renew()
    setPicked(item)
    setQuantity('1')
    setPriceText(moneyToInput(item.salePrice))
    setChargeable(item.chargeableByDefault)
  }

  const mutation = useMutation({
    mutationFn: () =>
      consumePart(order.id, {
        operationId: operationId.current,
        productId: picked!.product.id,
        quantity: Number(quantity),
        note: note.trim() || undefined,
        unitPrice: canOverride && priceChanged ? (unitPrice ?? undefined) : undefined,
        chargeable: canOverride && chargeChanged ? chargeable : undefined,
      }),
    onSuccess: async (updated) => {
      renew()
      await refresh(updated)
      onDone(
        `Repuesto registrado: ${quantity} × ${picked!.product.sku}${chargeable ? '' : ' (sin cargo)'}. Las existencias de ${order.branch.name} ya se descontaron.`,
      )
    },
    onError: async (error) => {
      if (!(error instanceof NetworkError)) renew()
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: ['repairs', 'part-options', order.id] })
        await queryClient.invalidateQueries({ queryKey: repairKeys.detail(order.id) })
      }
    },
  })
  const errors = fieldErrorsOf(mutation.error)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <form ref={formRef} className="card card-highlight" onSubmit={submit} noValidate aria-label="Registrar repuesto">
      <h2>Registrar repuesto</h2>
      <p className="muted small">
        Se descuenta de las existencias de <strong>{order.branch.name}</strong> y queda en el historial del inventario con
        el código de la orden. Solo se muestran repuestos activos.
      </p>
      {mutation.isError && (
        <Alert tone="error">
          {describeStockError(mutation.error)}
          {mutation.error instanceof NetworkError && ' Puedes reintentar: no se registrará dos veces.'}
        </Alert>
      )}
      {picked ? (
        <div className="field">
          <span className="field-label">Repuesto</span>
          <div className="picked">
            <span>
              <strong>{picked.product.sku}</strong> · {picked.product.name} · {picked.quantity} disponible{picked.quantity === 1 ? '' : 's'}
            </span>
            <button
              type="button"
              className="button button-secondary button-small"
              disabled={mutation.isPending}
              onClick={() => {
                renew()
                setPicked(null)
              }}
            >
              Cambiar
            </button>
          </div>
        </div>
      ) : (
        <div className="field product-picker">
          <label htmlFor={searchId}>Buscar repuesto por SKU o nombre</label>
          <input
            id={searchId}
            type="search"
            value={search}
            autoComplete="off"
            placeholder="Ejemplo: correa, BOMBA-02"
            aria-invalid={errors.productId ? true : undefined}
            onChange={(event) => setSearch(event.target.value)}
          />
          {errors.productId && <p className="field-error">{errors.productId}</p>}
          {options.isFetching && <p className="field-hint">Buscando…</p>}
          {options.isError && <p className="field-error">{describeError(options.error)}</p>}
          {options.data && (
            <ul className="picker-results" aria-label="Repuestos de la sucursal">
              {options.data.content.length === 0 && <li className="muted">Sin repuestos activos con ese texto.</li>}
              {options.data.content.map((item) => (
                <li key={item.product.id}>
                  <button type="button" disabled={item.quantity === 0} onClick={() => pick(item)}>
                    <strong>{item.product.sku}</strong> · {item.product.name} · {item.quantity} en existencia{' '}
                    <StockStatusBadge status={item.stockStatus} />
                    <span className="picker-price">{item.salePrice === null ? 'Sin precio' : formatColones(item.salePrice)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {picked && (
        <>
          <div className="form-grid">
            <TextField
              label="Cantidad utilizada"
              type="number"
              inputMode="numeric"
              min={1}
              max={Math.min(picked.quantity, 1000)}
              value={quantity}
              error={errors.quantity ?? quantityError}
              onChange={(event) => {
                renew()
                setQuantity(event.target.value)
              }}
            />
            {canOverride ? (
              <MoneyField
                label="Precio por unidad"
                value={priceText}
                hint={
                  picked.salePrice === null
                    ? 'El catálogo no tiene precio: escríbelo para esta orden o déjalo por definir.'
                    : `Catálogo: ${formatColones(picked.salePrice)}. Cambiarlo aquí solo afecta esta orden.`
                }
                error={!price.ok ? MONEY_HINT : errors.unitPrice}
                onChange={(event) => {
                  renew()
                  setPriceText(event.target.value)
                }}
              />
            ) : (
              <div className="field">
                <span className="field-label">Precio por unidad</span>
                <p className="static-value">{picked.salePrice === null ? 'Por definir' : formatColones(picked.salePrice)}</p>
                <p className="field-hint">Precio del catálogo. Solo gestión puede ajustarlo para una orden.</p>
              </div>
            )}
            <TextField
              label="Nota (opcional)"
              maxLength={300}
              value={note}
              error={errors.note}
              onChange={(event) => {
                renew()
                setNote(event.target.value)
              }}
            />
          </div>
          {canOverride ? (
            <Switch
              label="Cobrar al cliente"
              checked={chargeable}
              stateLabels={['Se cobra', 'Sin cargo']}
              description="Apágalo para garantía, cortesía, error del taller o un repuesto incluido en otra tarifa. El repuesto igual se descuenta del inventario y queda en el historial."
              onChange={(value) => {
                renew()
                setChargeable(value)
              }}
            />
          ) : (
            <p className="field-hint">
              {chargeable ? 'Se cobra al cliente' : 'Sin cargo al cliente'} según el catálogo. Solo gestión puede cambiarlo.
            </p>
          )}
          <div className="line-subtotal" aria-live="polite">
            <span>Subtotal de esta línea</span>
            <strong>
              {!chargeable ? 'Sin cargo' : subtotal === null ? (quantityError ? '—' : 'Precio por definir') : formatColones(subtotal)}
            </strong>
          </div>
        </>
      )}
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={mutation.isPending || !picked || quantityError !== undefined || !price.ok}
        >
          {mutation.isPending ? 'Registrando…' : 'Registrar consumo'}
        </button>
        <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

/** B.4: give back units of a consumed part (partial or complete) with a mandatory reason. */
export function ReturnPartForm({ order, part, onCancel, onDone }: FormProps & { part: PartUsage }) {
  const refresh = useRefreshAfterPartChange(order.id)
  const queryClient = useQueryClient()
  const formRef = useFocusFirstField()
  const [quantity, setQuantity] = useState(String(part.remainingQuantity))
  const [reason, setReason] = useState('')
  const operationId = useRef(newOperationId())
  const renew = () => (operationId.current = newOperationId())
  const closed = !['RECEIVED', 'DIAGNOSING', 'AWAITING_APPROVAL', 'APPROVED', 'IN_REPAIR'].includes(order.status)
  const quantityError = partQuantityError(quantity, part.remainingQuantity)

  const mutation = useMutation({
    mutationFn: () => returnPart(order.id, part.id, { operationId: operationId.current, quantity: Number(quantity), reason: reason.trim() }),
    onSuccess: async (updated) => {
      renew()
      await refresh(updated)
      onDone(`Corrección registrada: ${quantity} × ${part.product.sku} de vuelta en las existencias de ${part.branch.name}.`)
    },
    onError: async (error) => {
      if (!(error instanceof NetworkError)) renew()
      if (error instanceof ApiError && error.status === 409) await queryClient.invalidateQueries({ queryKey: repairKeys.detail(order.id) })
    },
  })
  const errors = fieldErrorsOf(mutation.error)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <form ref={formRef} className="card card-highlight" onSubmit={submit} noValidate aria-label="Corregir repuesto">
      <h2>Corregir repuesto</h2>
      <p>
        <strong>{part.product.sku}</strong> · {part.product.name}: se registraron {part.quantity}; siguen contando como utilizadas{' '}
        {part.remainingQuantity}. Las unidades devueltas vuelven a {part.branch.name}; el registro original se conserva.
      </p>
      {closed && (
        <Alert tone="info">
          La orden está «{REPAIR_STATUS_LABELS[order.status]}». La corrección queda auditada como posterior al cierre y no cambia
          su historial de estados.
        </Alert>
      )}
      {mutation.isError && (
        <Alert tone="error">
          {describeError(mutation.error)}
          {mutation.error instanceof NetworkError && ' Puedes reintentar: no se aplicará dos veces.'}
        </Alert>
      )}
      <div className="form-grid">
        <TextField
          label="Unidades a devolver"
          type="number"
          inputMode="numeric"
          min={1}
          max={part.remainingQuantity}
          value={quantity}
          error={errors.quantity ?? quantityError}
          onChange={(event) => {
            renew()
            setQuantity(event.target.value)
          }}
        />
      </div>
      <TextAreaField
        label="Motivo de la corrección"
        rows={2}
        maxLength={300}
        value={reason}
        error={errors.reason}
        onChange={(event) => {
          renew()
          setReason(event.target.value)
        }}
      />
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={mutation.isPending || quantityError !== undefined || reason.trim() === ''}
        >
          {mutation.isPending ? 'Guardando…' : 'Registrar devolución'}
        </button>
        <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
