import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { NetworkError } from '../../shared/api/httpClient'
import { OUTCOME_LABELS, type VisitOutcome } from '../../shared/i18n/serviceLabels'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { VisitStatusBadge } from './ServiceBadges'
import { SelectField, TextAreaField, TextField } from '../../shared/ui/Field'
import { useSession } from '../auth/session'
import { fetchRepairOrders, repairKeys } from '../repairs/repairsApi'
import { completeVisit, fetchVisit, linkRepairOrder, serviceKeys, startVisit, type Visit } from './serviceApi'
import { VisitCard } from './VisitCard'

/** One visit: the technician starts it and records the result; the counter links the workshop order. */
export function VisitDetailPage() {
  const visitId = Number(useParams().visitId)
  const { data: session } = useSession()
  const queryClient = useQueryClient()
  const visit = useQuery({ queryKey: serviceKeys.visit(visitId), queryFn: ({ signal }) => fetchVisit(visitId, signal) })
  const [panel, setPanel] = useState<'complete' | 'repair' | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const start = useMutation({
    mutationFn: () => startVisit(visitId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
      setDone('Visita iniciada.')
    },
  })

  async function finished(message: string) {
    await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
    setPanel(null)
    setDone(message)
  }

  if (visit.isPending) return <LoadingState label="Cargando visita…" />
  if (visit.isError) return <ErrorState error={visit.error} onRetry={() => visit.refetch()} />
  const data = visit.data
  const isTechnician = session?.user.role === 'TECHNICIAN'

  return (
    <section className="page">
      <ModuleSurface
        breadcrumb={[isTechnician ? { to: '/my-visits', label: 'Mis visitas' } : { to: '/agenda', label: 'Agenda' }]}
        eyebrow={`Visita · solicitud ${data.requestCode}`}
        icon="van"
        title={`${data.deviceType} · ${data.canton}`}
        meta={<VisitStatusBadge status={data.status} />}
        actions={
          !isTechnician && (
            <Link className="button button-secondary" to={`/service-requests/${data.requestId}`}>
              Ver solicitud
              <Icon name="arrowRight" />
            </Link>
          )
        }
      >

        {done && <Alert tone="success">{done}</Alert>}
        {start.isError && <Alert tone="error">{describeError(start.error)}</Alert>}

        {(data.actions.canStart || data.actions.canComplete || data.actions.canLinkRepairOrder) && (
        <div className="card action-panel" role="group" aria-label="Acciones de la visita">
          <div className="action-panel-label">
            <Icon name="spark" size={16} />
            Acciones disponibles
          </div>
          <div className="action-bar">
          {data.actions.canStart && (
            <button type="button" className="button button-primary" disabled={start.isPending || panel !== null} onClick={() => start.mutate()}>
              Iniciar visita
            </button>
          )}
          {data.actions.canComplete && (
            <button type="button" className="button button-primary" disabled={panel !== null} onClick={() => { setDone(null); setPanel('complete') }}>
              Registrar resultado
            </button>
          )}
          {data.actions.canLinkRepairOrder && (
            <button type="button" className="button button-primary" disabled={panel !== null} onClick={() => { setDone(null); setPanel('repair') }}>
              Pasar a taller
            </button>
          )}
          </div>
        </div>
        )}

        {panel === 'complete' && <CompleteForm visit={data} onCancel={() => setPanel(null)} onDone={() => finished('Resultado registrado.')} />}
        {panel === 'repair' && <RepairLinkForm visit={data} onCancel={() => setPanel(null)} onDone={() => finished('Orden de taller vinculada.')} />}

        <div className="module-section">
          <VisitCard visit={data} showRequestLink />
          {isTechnician && !data.contactPhone && (
            <p className="muted small">El teléfono y la dirección solo se muestran mientras la visita está pendiente o en curso.</p>
          )}
        </div>
      </ModuleSurface>
    </section>
  )
}

function CompleteForm({ visit, onCancel, onDone }: { visit: Visit; onCancel: () => void; onDone: () => void }) {
  const [outcome, setOutcome] = useState<VisitOutcome | ''>('')
  const [notes, setNotes] = useState('')
  const complete = useMutation({ mutationFn: () => completeVisit(visit.id, outcome as VisitOutcome, notes.trim() || undefined), onSuccess: onDone })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    complete.mutate()
  }

  return (
    <form className="card card-highlight" onSubmit={submit} noValidate aria-label="Registrar resultado">
      <h2>Resultado de la visita</h2>
      {complete.isError && <Alert tone="error">{describeError(complete.error)}</Alert>}
      <SelectField label="Resultado" value={outcome} onChange={(event) => setOutcome(event.target.value as VisitOutcome)}>
        <option value="">Selecciona</option>
        {(Object.keys(OUTCOME_LABELS) as VisitOutcome[]).map((value) => (
          <option key={value} value={value}>
            {OUTCOME_LABELS[value]}
          </option>
        ))}
      </SelectField>
      <TextAreaField label="Trabajo realizado u observaciones" maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} />
      {outcome === 'NEEDS_WORKSHOP' && (
        <p className="muted small">La recepción creará o vinculará la orden de taller cuando el equipo llegue, sin duplicar al cliente.</p>
      )}
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={complete.isPending || !outcome}>
          {complete.isPending ? 'Guardando…' : 'Guardar resultado'}
        </button>
        <button type="button" className="button button-secondary" disabled={complete.isPending} onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}

/** Opens a workshop order for the same customer, or links one of that customer's open orders. */
function RepairLinkForm({ visit, onCancel, onDone }: { visit: Visit; onCancel: () => void; onDone: () => void }) {
  const [mode, setMode] = useState<'new' | 'existing'>('new')
  const [existingId, setExistingId] = useState('')
  const [condition, setCondition] = useState('')
  const [accessories, setAccessories] = useState('')
  const operationId = useRef(newOperationId())
  const customerOrders = useQuery({
    queryKey: repairKeys.list({ customerId: visit.customer?.id, page: 0 }),
    queryFn: ({ signal }) => fetchRepairOrders({ customerId: visit.customer?.id, page: 0 }, signal),
    enabled: mode === 'existing' && visit.customer !== null,
  })
  const link = useMutation({
    mutationFn: () =>
      mode === 'existing'
        ? linkRepairOrder(visit.id, { existingOrderId: Number(existingId) })
        : linkRepairOrder(visit.id, { operationId: operationId.current, physicalCondition: condition.trim(), accessories: accessories.trim() || undefined }),
    onSuccess: onDone,
    onError: (error) => {
      if (!(error instanceof NetworkError)) operationId.current = newOperationId()
    },
  })
  const errors = fieldErrorsOf(link.error)

  return (
    <form
      className="card card-highlight"
      noValidate
      aria-label="Pasar a taller"
      onSubmit={(event) => {
        event.preventDefault()
        link.mutate()
      }}
    >
      <h2>Pasar el equipo al taller</h2>
      {link.isError && <Alert tone="error">{describeError(link.error)}</Alert>}
      <div className="status-line">
        <label className="checkbox">
          <input type="radio" name="mode" checked={mode === 'new'} onChange={() => setMode('new')} /> Crear orden nueva
        </label>
        <label className="checkbox">
          <input type="radio" name="mode" checked={mode === 'existing'} onChange={() => setMode('existing')} /> Vincular una orden existente del cliente
        </label>
      </div>
      {mode === 'new' ? (
        <div className="form-grid">
          <TextAreaField label="Estado físico al recibir" rows={2} maxLength={1000} value={condition} error={errors.physicalCondition} onChange={(event) => setCondition(event.target.value)} />
          <TextField label="Accesorios (opcional)" maxLength={500} value={accessories} onChange={(event) => setAccessories(event.target.value)} />
        </div>
      ) : (
        <SelectField label="Orden" value={existingId} onChange={(event) => setExistingId(event.target.value)}>
          <option value="">{customerOrders.isPending ? 'Cargando…' : 'Selecciona una orden'}</option>
          {customerOrders.data?.content.map((order) => (
            <option key={order.id} value={order.id}>
              {order.orderCode} · {order.device.type} {order.device.brand}
            </option>
          ))}
        </SelectField>
      )}
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={link.isPending || (mode === 'new' ? !condition.trim() : !existingId)}
        >
          {link.isPending ? 'Guardando…' : mode === 'new' ? 'Crear orden de taller' : 'Vincular orden'}
        </button>
        <button type="button" className="button button-secondary" disabled={link.isPending} onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}
