import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError } from '../../shared/api/httpClient'
import {
  DECISION_METHOD_LABELS,
  QUOTE_STATUS_LABELS,
  QUOTE_STATUS_TONES,
  REPAIR_STATUS_LABELS,
  REPAIR_TRANSITION_ACTIONS,
  RESOLUTION_LABELS,
  type DecisionMethod,
  type RepairStatus,
} from '../../shared/i18n/labels'
import { formatColones, formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { SectionCard } from '../../shared/ui/SectionCard'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { permissions } from '../auth/permissions'
import { useSession } from '../auth/session'
import { SelectField, TextAreaField, TextField } from '../../shared/ui/Field'
import { formatPhone } from '../customers/customersApi'
import { parseColones } from './repairLogic'
import {
  assignTechnician,
  changeStatus,
  createQuote,
  decideQuote,
  fetchRepairOrder,
  fetchTechnicians,
  repairKeys,
  updateDiagnosis,
  type PartUsage,
  type Quote,
  type RepairOrderDetail,
} from './repairsApi'
import { ConsumePartForm, ReturnPartForm } from './PartForms'
import { RepairPartsCard } from './RepairPartsCard'
import { NotificationStatusList } from '../notifications/NotificationStatusList'
import { RepairProgress } from './RepairProgress'
import { RepairStatusBadge } from './RepairStatusBadge'
import { OrderOriginNotice } from '../service/OrderOriginNotice'

/** The one action form open at a time (B.5: no permanent forms cluttering the detail). */
type Panel =
  | { kind: 'transition'; toStatus: RepairStatus; reasonRequired: boolean }
  | { kind: 'assign' }
  | { kind: 'diagnosis' }
  | { kind: 'quote' }
  | { kind: 'decide'; quote: Quote }
  | { kind: 'consumePart' }
  | { kind: 'returnPart'; part: PartUsage }

/** FR-REP-003..005: order detail, timeline, technical work, quotes and the allowed actions. */
export function RepairDetailPage() {
  const orderId = Number(useParams().orderId)
  const location = useLocation()
  const justReceived = (location.state as { received?: boolean } | null)?.received === true
  const order = useQuery({
    queryKey: repairKeys.detail(orderId),
    queryFn: ({ signal }) => fetchRepairOrder(orderId, signal),
  })
  const [panel, setPanel] = useState<Panel | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const role = useSession().data?.user.role

  if (order.isPending) return <LoadingState label="Cargando orden…" />
  if (order.isError) return <ErrorState error={order.error} onRetry={() => order.refetch()} />
  const data = order.data
  const pendingQuote = data.quotes.find((quote) => quote.status === 'PENDING')

  function open(next: Panel) {
    setDone(null)
    setPanel(next)
  }

  function finished(message: string) {
    setPanel(null)
    setDone(message)
  }

  const busy = panel !== null
  const canPrint = role !== undefined && permissions.receiveRepairs(role)

  return (
    <section className="page repair-detail">
      <ModuleSurface
        breadcrumb={[{ to: '/repairs', label: 'Reparaciones' }]}
        eyebrow="Orden de reparación"
        icon="wrench"
        title={data.orderCode}
        titleClassName="order-title mono"
        meta={
          <>
            <RepairStatusBadge status={data.status} />
            {data.resolution && <span className="chip">Resultado: {RESOLUTION_LABELS[data.resolution]}</span>}
            <span className="chip">
              <Icon name={data.inCustody ? 'building' : 'checkCircle'} />
              {data.inCustody ? `En custodia en ${data.branch.name}` : `Entregado el ${formatDateTime(data.deliveredAt)}`}
            </span>
          </>
        }
        actions={
          canPrint && (
            <Link className="button button-secondary" to={`/repairs/${data.id}/receipt`}>
              <Icon name="printer" />
              Comprobante de recepción
            </Link>
          )
        }
      >

        {justReceived && !done && (
          <Alert tone="success" title="Recepción registrada.">
            Entrega al cliente el código {data.orderCode}
            {canPrint && (
              <>
                {' '}
                o <Link to={`/repairs/${data.id}/receipt`}>imprime el comprobante</Link>
              </>
            )}
            .
          </Alert>
        )}
        {done && <Alert tone="success">{done}</Alert>}
        {(data.status === 'CANCELLED' || data.status === 'UNREPAIRABLE') && data.inCustody && (
          <Alert tone="warn" title="Equipo pendiente de devolver.">
            La orden terminó sin reparación, pero el aparato sigue en {data.branch.name}. Registra la entrega cuando el cliente lo
            retire.
          </Alert>
        )}

        {/* Where the order stands and what can be done next, read together. */}
        <div className="record-summary">
          <RepairProgress status={data.status} history={data.history} />
          <OrderActionsBar order={data} pendingQuote={pendingQuote} disabled={busy} onOpen={open} />
        </div>
        {panel?.kind === 'consumePart' && <ConsumePartForm order={data} onCancel={() => setPanel(null)} onDone={finished} />}
        {panel?.kind === 'returnPart' && (
          <ReturnPartForm key={panel.part.id} order={data} part={panel.part} onCancel={() => setPanel(null)} onDone={finished} />
        )}
        {panel && panel.kind !== 'consumePart' && panel.kind !== 'returnPart' && (
          <ActionPanel key={panel.kind === 'transition' ? panel.toStatus : panel.kind} order={data} panel={panel} onCancel={() => setPanel(null)} onDone={finished} />
        )}

        <div className="split">
          <div className="stack-lg">
            <SectionCard title="Equipo y falla reportada" icon="device" subtitle="Datos registrados al recibir; no se modifican.">
              <div className="device-summary">
                <div>
                  <span className="device-name">
                    {data.device.type} · {data.device.brand} {data.device.model ?? ''}
                  </span>
                  <span className="muted small">
                    Serie: <span className="mono">{data.device.serialNumber ?? 'sin registrar'}</span>
                  </span>
                </div>
              </div>
              <div className="fact-grid">
                <div className="fact fact-accent">
                  <h3>Falla reportada por el cliente</h3>
                  <p className="text-block">{data.reportedFault}</p>
                </div>
                <div className="fact">
                  <h3>Estado físico al recibir</h3>
                  <p className="text-block">{data.physicalCondition}</p>
                </div>
                <div className="fact">
                  <h3>Accesorios entregados</h3>
                  <p className="text-block">{data.accessories ?? 'Ninguno'}</p>
                </div>
              </div>
            </SectionCard>

            <SectionCard title="Trabajo técnico" icon="cpu" iconTone="accent">
              <dl className="details">
                <dt>Técnico</dt>
                <dd>
                  {data.technician ? (
                    <span className="person">
                      <Avatar name={data.technician.fullName} size="sm" />
                      {data.technician.fullName}
                    </span>
                  ) : (
                    <span className="muted">Sin asignar</span>
                  )}
                </dd>
                <dt>Diagnóstico</dt>
                <dd className="text-block">{data.diagnosis ?? <span className="muted">Pendiente</span>}</dd>
                {data.diagnosisUpdatedBy && (
                  <>
                    <dt>Actualizado</dt>
                    <dd>
                      {data.diagnosisUpdatedBy.fullName} · {formatDateTime(data.diagnosisUpdatedAt)}
                    </dd>
                  </>
                )}
              </dl>
            </SectionCard>

            <SectionCard title="Cotización y autorización" icon="tag" subtitle="Montos en colones; no es una factura.">
              {data.quotes.length === 0 ? (
                <EmptyState compact icon="tag" title="Sin cotizaciones">
                  El técnico emite una cotización durante el diagnóstico cuando la reparación necesita la aprobación del cliente.
                </EmptyState>
              ) : (
                <ul className="quote-list">
                  {data.quotes.map((quote) => (
                    <li key={quote.id}>
                      <div className="status-line">
                        <span className="amount">{formatColones(quote.amount)}</span>
                        <span className={`badge badge-${QUOTE_STATUS_TONES[quote.status]}`}>{QUOTE_STATUS_LABELS[quote.status]}</span>
                      </div>
                      <p className="text-block">{quote.description}</p>
                      <p className="muted small">
                        Emitida por {quote.createdBy.fullName} · {formatDateTime(quote.createdAt)}
                        {quote.decidedBy && (
                          <>
                            <br />
                            Decisión registrada por {quote.decidedBy.fullName} · {formatDateTime(quote.decidedAt)}
                            {quote.decisionMethod && ` · ${DECISION_METHOD_LABELS[quote.decisionMethod]}`}
                            {quote.decisionNote && ` · ${quote.decisionNote}`}
                          </>
                        )}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
              {data.partsSummary && data.partsSummary.linesInUse > 0 && (
                <p className="quote-parts-note">
                  Repuestos cobrables registrados: <strong>{formatColones(data.partsSummary.chargeableSubtotal)}</strong>. Se muestran
                  aparte y no se suman a la cotización: la cotización es el monto que aprobó el cliente.
                </p>
              )}
            </SectionCard>

            <RepairPartsCard order={data} disabled={busy} onReturn={(part) => open({ kind: 'returnPart', part })} />

            <SectionCard title="Historial de estados" icon="history" iconTone="muted" subtitle="Registro inmutable: quién cambió qué y cuándo.">
              <ol className="timeline">
                {[...data.history].reverse().map((change, index) => (
                  <li key={index}>
                    <strong>
                      {change.fromStatus
                        ? `${REPAIR_STATUS_LABELS[change.fromStatus]} → ${REPAIR_STATUS_LABELS[change.toStatus]}`
                        : 'Equipo recibido'}
                    </strong>
                    <div className="muted small">
                      {change.actor.fullName} · {formatDateTime(change.changedAt)}
                    </div>
                    {change.reason && <div className="small">Motivo: {change.reason}</div>}
                  </li>
                ))}
              </ol>
            </SectionCard>
          </div>

          <aside className="stack-lg" aria-label="Cliente, recepción y avisos">
            <SectionCard title="Cliente" icon="user" level={2}>
              <div className="person person-block">
                <Avatar name={data.customer.fullName} size="lg" />
                <span className="person-text">
                  {data.customer.phone !== null ? (
                    <Link className="strong" to={`/customers/${data.customer.id}`}>
                      {data.customer.fullName}
                    </Link>
                  ) : (
                    <span className="strong">{data.customer.fullName}</span>
                  )}
                  {data.customer.phone !== null && (
                    <>
                      <span className="contact-line">
                        <Icon name="phone" size={15} />
                        <a href={`tel:${data.customer.phone}`}>{formatPhone(data.customer.phone)}</a>
                      </span>
                      <span className="contact-line">
                        <Icon name="mail" size={15} />
                        {data.customer.email ? <a href={`mailto:${data.customer.email}`}>{data.customer.email}</a> : <span className="muted">Sin correo</span>}
                      </span>
                    </>
                  )}
                </span>
              </div>
            </SectionCard>

            <SectionCard title="Recepción y entrega" icon="clipboard" iconTone="accent">
              <dl className="details details-stacked">
                <dt>Sucursal</dt>
                <dd>{data.branch.name}</dd>
                <dt>Recibido por</dt>
                <dd>
                  {data.receivedBy.fullName}
                  <span className="cell-sub muted small">{formatDateTime(data.receivedAt)}</span>
                </dd>
                <dt>Entrega</dt>
                <dd>
                  {data.deliveredBy ? (
                    <>
                      {data.deliveredBy.fullName}
                      <span className="cell-sub muted small">{formatDateTime(data.deliveredAt)}</span>
                    </>
                  ) : (
                    <span className="muted">Pendiente</span>
                  )}
                </dd>
              </dl>
            </SectionCard>

            {(data.notifications.length > 0 || data.status === 'READY_FOR_PICKUP') && (
              <SectionCard title="Avisos al cliente" icon="bell" iconTone="warn" subtitle="Independientes del estado de la orden.">
                <NotificationStatusList notifications={data.notifications} emptyText="Sin avisos registrados para esta orden." />
                {data.notifications.some((notice) => notice.status === 'SKIPPED' || notice.status === 'FAILED') && (
                  <p className="muted small">El equipo sigue listo aunque el aviso no se haya enviado: contacta al cliente por teléfono.</p>
                )}
              </SectionCard>
            )}

            <OrderOriginNotice orderId={data.id} />
          </aside>
        </div>
      </ModuleSurface>
    </section>
  )
}

/** Negative transitions close the order: they are styled apart and placed last. */
const CLOSING: RepairStatus[] = ['CANCELLED', 'UNREPAIRABLE']

function OrderActionsBar({
  order,
  pendingQuote,
  disabled,
  onOpen,
}: {
  order: RepairOrderDetail
  pendingQuote: Quote | undefined
  disabled: boolean
  onOpen: (panel: Panel) => void
}) {
  const { actions } = order
  const primary: ReactNode[] = []
  const secondary: ReactNode[] = []
  const closing: ReactNode[] = []
  if (actions.canDecideQuote && pendingQuote) {
    primary.push(
      <button key="decide" type="button" className="button button-primary" disabled={disabled} onClick={() => onOpen({ kind: 'decide', quote: pendingQuote })}>
        <Icon name="checkCircle" />
        Registrar decisión del cliente
      </button>,
    )
  }
  for (const transition of actions.transitions) {
    const button = (
      <button
        key={transition.toStatus}
        type="button"
        className={`button ${CLOSING.includes(transition.toStatus) ? 'button-danger' : 'button-primary'}`}
        disabled={disabled}
        onClick={() => onOpen({ kind: 'transition', ...transition })}
      >
        {REPAIR_TRANSITION_ACTIONS[transition.toStatus]}
      </button>
    )
    if (CLOSING.includes(transition.toStatus)) closing.push(button)
    else primary.push(button)
  }
  if (actions.canAssignTechnician) {
    secondary.push(
      <button key="assign" type="button" className="button button-secondary" disabled={disabled} onClick={() => onOpen({ kind: 'assign' })}>
        <Icon name="user" />
        {order.technician ? 'Reasignar técnico' : 'Asignar técnico'}
      </button>,
    )
  }
  if (actions.canEditDiagnosis) {
    secondary.push(
      <button key="diagnosis" type="button" className="button button-secondary" disabled={disabled} onClick={() => onOpen({ kind: 'diagnosis' })}>
        <Icon name="edit" />
        {order.diagnosis ? 'Editar diagnóstico' : 'Registrar diagnóstico'}
      </button>,
    )
  }
  if (actions.canCreateQuote) {
    secondary.push(
      <button key="quote" type="button" className="button button-secondary" disabled={disabled} onClick={() => onOpen({ kind: 'quote' })}>
        <Icon name="tag" />
        Emitir cotización
      </button>,
    )
  }
  if (actions.canConsumeParts) {
    secondary.push(
      <button key="parts" type="button" className="button button-secondary" disabled={disabled} onClick={() => onOpen({ kind: 'consumePart' })}>
        <Icon name="package" />
        Registrar repuesto
      </button>,
    )
  }
  if (primary.length + secondary.length + closing.length === 0) return null
  return (
    <div className="card action-panel" role="group" aria-label="Acciones de la orden">
      <div className="action-panel-label">
        <Icon name="spark" size={16} />
        Acciones disponibles
      </div>
      <div className="action-bar">
        {primary}
        {secondary}
        {closing.length > 0 && <span className="action-separator" aria-hidden="true" />}
        {closing}
      </div>
    </div>
  )
}

function ActionPanel({
  order,
  panel,
  onCancel,
  onDone,
}: {
  order: RepairOrderDetail
  panel: Exclude<Panel, { kind: 'consumePart' } | { kind: 'returnPart' }>
  onCancel: () => void
  onDone: (message: string) => void
}) {
  const queryClient = useQueryClient()
  const formRef = useRef<HTMLFormElement>(null)
  // Move focus into the panel that just opened (keyboard and screen-reader users land on it).
  useEffect(() => {
    formRef.current?.querySelector<HTMLElement>('textarea, select, input')?.focus()
  }, [])
  const [text, setText] = useState(panel.kind === 'diagnosis' ? (order.diagnosis ?? '') : '')
  const [amount, setAmount] = useState('')
  const [technicianId, setTechnicianId] = useState(order.technician ? String(order.technician.id) : '')
  const [decision, setDecision] = useState<'APPROVED' | 'REJECTED' | ''>('')
  const [method, setMethod] = useState<DecisionMethod>('IN_PERSON')
  const [note, setNote] = useState('')

  const technicians = useQuery({
    queryKey: repairKeys.technicians(order.branch.id),
    queryFn: ({ signal }) => fetchTechnicians(order.branch.id, signal),
    enabled: panel.kind === 'assign',
  })

  const parsedAmount = parseColones(amount)
  const mutation = useMutation({
    mutationFn: (): Promise<RepairOrderDetail> => {
      switch (panel.kind) {
        case 'transition':
          return changeStatus(order.id, panel.toStatus, text.trim() || undefined)
        case 'assign':
          return assignTechnician(order.id, Number(technicianId))
        case 'diagnosis':
          return updateDiagnosis(order.id, text.trim(), order.version)
        case 'quote':
          return createQuote(order.id, parsedAmount!, text.trim())
        case 'decide':
          return decideQuote(order.id, panel.quote.id, decision as 'APPROVED' | 'REJECTED', method, note.trim() || undefined)
      }
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(repairKeys.detail(order.id), updated)
      await queryClient.invalidateQueries({ queryKey: ['repairs', 'list'] })
      onDone(successMessage(panel, updated))
    },
    onError: async (error) => {
      // A conflict means the order changed meanwhile: show its current state behind the message.
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: repairKeys.detail(order.id) })
      }
    },
  })
  const errors = fieldErrorsOf(mutation.error)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  let title: string
  let body: ReactNode
  let ready: boolean
  switch (panel.kind) {
    case 'transition':
      title = REPAIR_TRANSITION_ACTIONS[panel.toStatus]
      ready = !panel.reasonRequired || text.trim() !== ''
      body = (
        <>
          <p>
            La orden pasará de <strong>{REPAIR_STATUS_LABELS[order.status]}</strong> a{' '}
            <strong>{REPAIR_STATUS_LABELS[panel.toStatus]}</strong>.
            {panel.toStatus === 'DELIVERED' && ' Confirma que el cliente recibió el equipo y sus accesorios.'}
          </p>
          <TextAreaField
            label={panel.reasonRequired ? 'Motivo' : 'Comentario (opcional)'}
            rows={2}
            maxLength={500}
            value={text}
            error={errors.reason}
            onChange={(event) => setText(event.target.value)}
          />
        </>
      )
      break
    case 'assign':
      title = order.technician ? 'Reasignar técnico' : 'Asignar técnico'
      ready = technicianId !== ''
      body = (
        <>
          {technicians.isError && <Alert tone="error">{describeError(technicians.error)}</Alert>}
          {technicians.data?.length === 0 && <Alert tone="info">No hay técnicos activos asignados a {order.branch.name}.</Alert>}
          <SelectField label="Técnico" value={technicianId} error={errors.technicianId} onChange={(event) => setTechnicianId(event.target.value)}>
            <option value="">{technicians.isPending ? 'Cargando…' : 'Selecciona un técnico'}</option>
            {technicians.data?.map((technician) => (
              <option key={technician.id} value={technician.id}>
                {technician.fullName}
              </option>
            ))}
          </SelectField>
        </>
      )
      break
    case 'diagnosis':
      title = 'Diagnóstico'
      ready = text.trim() !== ''
      body = (
        <TextAreaField label="Diagnóstico técnico" rows={4} maxLength={2000} value={text} error={errors.diagnosis} onChange={(event) => setText(event.target.value)} />
      )
      break
    case 'quote':
      title = 'Emitir cotización'
      ready = parsedAmount !== null && text.trim() !== ''
      body = (
        <>
          <p className="muted small">Al emitirla, la orden queda esperando la aprobación del cliente. No es una factura.</p>
          <div className="form-grid">
            <TextField
              label="Monto en colones"
              inputMode="decimal"
              value={amount}
              maxLength={20}
              hint={parsedAmount !== null ? formatColones(parsedAmount) : 'Ejemplo: 25000 o 25 000,50'}
              error={errors.amount ?? (amount.trim() && parsedAmount === null ? 'Monto no válido (máximo dos decimales).' : undefined)}
              onChange={(event) => setAmount(event.target.value)}
            />
          </div>
          <TextAreaField label="Detalle del trabajo y repuestos" maxLength={1000} value={text} error={errors.description} onChange={(event) => setText(event.target.value)} />
        </>
      )
      break
    case 'decide':
      title = 'Decisión del cliente'
      ready = decision !== ''
      body = (
        <>
          <p>
            Cotización de <strong>{formatColones(panel.quote.amount)}</strong>: {panel.quote.description}
          </p>
          <fieldset className="field">
            <legend>Decisión</legend>
            <label className="checkbox">
              <input type="radio" name="decision" checked={decision === 'APPROVED'} onChange={() => setDecision('APPROVED')} /> Aprobó la
              cotización
            </label>
            <label className="checkbox">
              <input type="radio" name="decision" checked={decision === 'REJECTED'} onChange={() => setDecision('REJECTED')} /> Rechazó la
              cotización (la orden se cancela y el equipo queda para devolver)
            </label>
          </fieldset>
          <div className="form-grid">
            <SelectField label="Cómo lo comunicó" value={method} onChange={(event) => setMethod(event.target.value as DecisionMethod)}>
              {(Object.keys(DECISION_METHOD_LABELS) as DecisionMethod[]).map((value) => (
                <option key={value} value={value}>
                  {DECISION_METHOD_LABELS[value]}
                </option>
              ))}
            </SelectField>
            <TextField label="Nota (opcional)" value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />
          </div>
        </>
      )
      break
  }

  return (
    <form ref={formRef} className="card card-highlight" onSubmit={submit} noValidate aria-label={title}>
      <h2>{title}</h2>
      {mutation.isError && <Alert tone="error">{describeError(mutation.error)}</Alert>}
      {body}
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={mutation.isPending || !ready}>
          {mutation.isPending ? 'Guardando…' : 'Confirmar'}
        </button>
        <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

function successMessage(panel: Exclude<Panel, { kind: 'consumePart' } | { kind: 'returnPart' }>, order: RepairOrderDetail): string {
  switch (panel.kind) {
    case 'transition':
      return `Estado actualizado: ${REPAIR_STATUS_LABELS[order.status]}.`
    case 'assign':
      return `Técnico asignado: ${order.technician?.fullName ?? ''}.`
    case 'diagnosis':
      return 'Diagnóstico guardado.'
    case 'quote':
      return 'Cotización emitida. La orden espera la aprobación del cliente.'
    case 'decide':
      return order.status === 'APPROVED' ? 'Cotización aprobada. La reparación puede continuar.' : 'Cotización rechazada. La orden quedó cancelada.'
  }
}
