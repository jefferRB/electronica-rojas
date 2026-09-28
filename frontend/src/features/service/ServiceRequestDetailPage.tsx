import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { describeError } from '../../shared/api/describeError'
import { ApiError } from '../../shared/api/httpClient'
import { EVENT_LABELS, PROVINCE_LABELS, WINDOW_LABELS } from '../../shared/i18n/serviceLabels'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { SectionCard } from '../../shared/ui/SectionCard'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { RequestJourney } from './RequestJourney'
import { nextStep } from './requestSteps'
import { SelectField } from '../../shared/ui/Field'
import { useSession } from '../auth/session'
import { CustomerPicker } from '../customers/CustomerPicker'
import { duplicateMatches, EMPTY_DRAFT } from '../customers/customerDraft'
import { formatPhone } from '../customers/customersApi'
import { useCustomerPicker } from '../customers/useCustomerPicker'
import { longDate } from './crTime'
import { ReasonForm } from './ReasonForm'
import { ScheduleVisitForm } from './ScheduleVisitForm'
import { RequestStatusBadge } from './ServiceBadges'
import {
  cancelRequest,
  cancelVisit,
  changeRequestBranch,
  confirmVisit,
  fetchServiceRequest,
  linkCustomer,
  rejectRequest,
  serviceKeys,
  startReview,
  type ServiceRequestDetail,
  type Visit,
} from './serviceApi'
import { VisitCard } from './VisitCard'
import { NotificationStatusList } from '../notifications/NotificationStatusList'

type Panel =
  | { kind: 'customer' }
  | { kind: 'schedule' }
  | { kind: 'reschedule'; visit: Visit }
  | { kind: 'cancelVisit'; visit: Visit }
  | { kind: 'branch' }
  | { kind: 'reject' }
  | { kind: 'cancel' }

/** FR-SRV-003: one request with its visits and timeline; one action form open at a time. */
export function ServiceRequestDetailPage() {
  const requestId = Number(useParams().requestId)
  const queryClient = useQueryClient()
  const request = useQuery({
    queryKey: serviceKeys.request(requestId),
    queryFn: ({ signal }) => fetchServiceRequest(requestId, signal),
  })
  const [panel, setPanel] = useState<Panel | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [moved, setMoved] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)

  // Visit cards further down open their forms here, above the fold: bring the form into view and
  // put the keyboard focus on its first field, or the button seems to do nothing.
  useEffect(() => {
    if (!panel) return
    const container = panelRef.current
    container?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
    container?.querySelector<HTMLElement>('input, select, textarea')?.focus({ preventScroll: true })
  }, [panel])

  async function refresh(message: string) {
    await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
    setPanel(null)
    setDone(message)
  }

  const action = useMutation({
    mutationFn: (run: () => Promise<unknown>) => run(),
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) await queryClient.invalidateQueries({ queryKey: serviceKeys.request(requestId) })
    },
  })

  if (moved) {
    return (
      <Alert tone="success">
        La solicitud pasó a otra sucursal. <Link to="/service-requests">Volver a las solicitudes</Link>
      </Alert>
    )
  }
  if (request.isPending) return <LoadingState label="Cargando solicitud…" />
  if (request.isError) return <ErrorState error={request.error} onRetry={() => request.refetch()} />
  const data = request.data
  const { actions } = data
  const activeVisit = data.visits.find((visit) => visit.status === 'PROPOSED' || visit.status === 'CONFIRMED' || visit.status === 'IN_PROGRESS')
  const busy = panel !== null || action.isPending

  function open(next: Panel) {
    setDone(null)
    action.reset()
    setPanel(next)
  }

  const step = nextStep(data, activeVisit)

  return (
    <section className="page">
      <ModuleSurface
        breadcrumb={[{ to: '/service-requests', label: 'Solicitudes a domicilio' }]}
        eyebrow="Solicitud a domicilio"
        icon="van"
        title={data.requestCode}
        titleClassName="order-title mono"
        meta={
          <>
            <RequestStatusBadge status={data.status} />
            <span className="chip">
              <Icon name={data.channel === 'PUBLIC_FORM' ? 'send' : 'user'} />
              {data.channel === 'PUBLIC_FORM' ? 'Formulario público' : `Registrada por ${data.createdBy?.fullName ?? 'el personal'}`}
            </span>
            <span className="chip">
              <Icon name="clock" />
              {formatDateTime(data.createdAt)}
            </span>
            <span className="chip">
              <Icon name="building" />
              {data.branch.name}
            </span>
          </>
        }
      >

        {done && <Alert tone="success">{done}</Alert>}
        {action.isError && !panel && <Alert tone="error">{describeError(action.error)}</Alert>}
        {step && !done && (
          <Alert tone={activeVisit?.status === 'PROPOSED' ? 'warn' : 'info'} title={step.title}>
            {step.text}
          </Alert>
        )}

        {/* Where the request stands and what can be done next, read together. */}
        <div className="record-summary">
          <RequestJourney request={data} />
          {(actions.canStartReview || actions.canLinkCustomer || actions.canSchedule || actions.canChangeBranch || actions.canReject || actions.canCancel) && (
            <div className="card action-panel" role="group" aria-label="Acciones de la solicitud">
              <div className="action-panel-label">
                <Icon name="spark" size={16} />
                Acciones disponibles
              </div>
              <div className="action-bar">
                {actions.canStartReview && (
                  <button type="button" className="button button-secondary" disabled={busy} onClick={() => action.mutate(async () => { await startReview(data.id); await refresh('Solicitud en revisión.') })}>
                    <Icon name="eye" />
                    Marcar en revisión
                  </button>
                )}
                {actions.canLinkCustomer && (
                  <button type="button" className="button button-primary" disabled={busy} onClick={() => open({ kind: 'customer' })}>
                    <Icon name="user" />
                    Asociar cliente
                  </button>
                )}
                {actions.canSchedule && (
                  <button type="button" className="button button-primary" disabled={busy} onClick={() => open({ kind: 'schedule' })}>
                    <Icon name="calendar" />
                    Programar visita
                  </button>
                )}
                {actions.canChangeBranch && (
                  <button type="button" className="button button-secondary" disabled={busy} onClick={() => open({ kind: 'branch' })}>
                    <Icon name="building" />
                    Cambiar sucursal
                  </button>
                )}
                {(actions.canReject || actions.canCancel) && (actions.canStartReview || actions.canLinkCustomer || actions.canSchedule || actions.canChangeBranch) && (
                  <span className="action-separator" aria-hidden="true" />
                )}
                {actions.canReject && (
                  <button type="button" className="button button-danger" disabled={busy} onClick={() => open({ kind: 'reject' })}>
                    Rechazar
                  </button>
                )}
                {actions.canCancel && (
                  <button type="button" className="button button-danger" disabled={busy} onClick={() => open({ kind: 'cancel' })}>
                    Cancelar solicitud
                  </button>
                )}
              </div>
            </div>
          )}
        </div>

        <div ref={panelRef} className="operation-slot">
        {panel?.kind === 'customer' && <LinkCustomerPanel request={data} onCancel={() => setPanel(null)} onDone={() => refresh('Cliente asociado.')} />}
        {panel?.kind === 'schedule' && (
          <ScheduleVisitForm
            branchId={data.branch.id}
            requestId={data.id}
            preferredDate={data.preferredDate}
            onCancel={() => setPanel(null)}
            onDone={(visit) => refresh(visit.status === 'CONFIRMED' ? 'Visita confirmada.' : 'Visita propuesta. Confírmala cuando el cliente acepte el horario.')}
          />
        )}
        {panel?.kind === 'reschedule' && (
          <ScheduleVisitForm
            branchId={data.branch.id}
            requestId={data.id}
            visit={panel.visit}
            onCancel={() => setPanel(null)}
            onDone={() => refresh('Visita reprogramada. El horario anterior queda en el historial.')}
          />
        )}
        {panel?.kind === 'cancelVisit' && (
          <ReasonForm
            title="Cancelar visita"
            explanation="La hora del técnico queda libre y la solicitud vuelve a revisión para programar otra visita."
            confirmLabel="Cancelar visita"
            pending={action.isPending}
            error={action.error}
            onCancel={() => setPanel(null)}
            onSubmit={(reason) => action.mutate(async () => { await cancelVisit(panel.visit.id, reason); await refresh('Visita cancelada.') })}
          />
        )}
        {panel?.kind === 'branch' && <BranchPanel request={data} onCancel={() => setPanel(null)} onMoved={(stillVisible) => (stillVisible ? refresh('Sucursal cambiada.') : setMoved(true))} />}
        {panel?.kind === 'reject' && (
          <ReasonForm
            title="Rechazar solicitud"
            explanation="El cliente verá que no se pudo atender; el motivo es interno."
            confirmLabel="Rechazar"
            pending={action.isPending}
            error={action.error}
            onCancel={() => setPanel(null)}
            onSubmit={(reason) => action.mutate(async () => { await rejectRequest(data.id, reason); await refresh('Solicitud rechazada.') })}
          />
        )}
        {panel?.kind === 'cancel' && (
          <ReasonForm
            title="Cancelar solicitud"
            explanation="Si hay una visita programada, se cancela y la hora del técnico queda libre."
            confirmLabel="Cancelar solicitud"
            pending={action.isPending}
            error={action.error}
            onCancel={() => setPanel(null)}
            onSubmit={(reason) => action.mutate(async () => { await cancelRequest(data.id, reason); await refresh('Solicitud cancelada.') })}
          />
        )}
        </div>

        <div className="split">
          <div className="stack-lg">
            <SectionCard title="Equipo y problema" icon="device">
              <div className="device-summary">
                <div>
                  <span className="device-name">
                    {data.deviceType} {data.brand ?? ''} {data.model ?? ''}
                  </span>
                  <span className="muted small">
                    Preferencia: {data.preferredDate ? `${longDate(data.preferredDate)}, ` : 'sin fecha, '}
                    {WINDOW_LABELS[data.preferredWindow].toLowerCase()}
                  </span>
                </div>
              </div>
              <div className="fact-grid">
                <div className="fact fact-accent">
                  <h3>Problema descrito</h3>
                  <p className="text-block">{data.problemDescription}</p>
                </div>
                <div className="fact">
                  <h3>Observaciones</h3>
                  <p className="text-block">{data.additionalNotes ?? 'Sin observaciones'}</p>
                </div>
                {data.decisionReason && (
                  <div className="fact">
                    <h3>Motivo de la decisión</h3>
                    <p className="text-block">{data.decisionReason}</p>
                  </div>
                )}
              </div>
            </SectionCard>

            <SectionCard title="Visitas" icon="calendar" iconTone="accent" subtitle="La hora solo existe en la visita; reprogramar conserva el horario anterior en el historial.">
              {data.visits.length === 0 ? (
                <EmptyState compact icon="calendar" title="Sin visitas programadas">
                  {actions.canSchedule ? 'Usa «Programar visita» para proponer o confirmar un horario.' : 'La visita se programa después de asociar el cliente.'}
                </EmptyState>
              ) : (
                <div className="stack">
                  {data.visits.map((visit) => (
                    <div key={visit.id} className={`visit-block visit-${visit.status.toLowerCase()}`}>
                      <VisitCard visit={visit} />
                      <div className="action-bar">
                        {visit.actions.canConfirm && (
                          <button type="button" className="button button-primary button-small" disabled={busy} onClick={() => action.mutate(async () => { await confirmVisit(visit.id); await refresh('Visita confirmada.') })}>
                            <Icon name="checkCircle" />
                            Confirmar con el cliente
                          </button>
                        )}
                        {visit.actions.canReschedule && (
                          <button type="button" className="button button-secondary button-small" disabled={busy} onClick={() => open({ kind: 'reschedule', visit })}>
                            Reprogramar
                          </button>
                        )}
                        {visit.actions.canCancel && (
                          <button type="button" className="button button-danger button-small" disabled={busy} onClick={() => open({ kind: 'cancelVisit', visit })}>
                            Cancelar visita
                          </button>
                        )}
                        <Link className="button button-ghost button-small" to={`/visits/${visit.id}`}>
                          Ver visita
                          <Icon name="arrowRight" />
                        </Link>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </SectionCard>

            <SectionCard title="Historial" icon="history" iconTone="muted" subtitle="Cada decisión queda registrada con su autor y motivo.">
              <ol className="timeline">
                {[...data.history].reverse().map((event, index) => (
                  <li key={index}>
                    <strong>{EVENT_LABELS[event.type] ?? event.type}</strong>
                    <div className="muted small">
                      {event.actor?.fullName ?? 'Formulario público'} · {formatDateTime(event.occurredAt)}
                    </div>
                    {event.type === 'VISIT_RESCHEDULED' && event.details && (
                      <div className="small">
                        De {formatDateTime(String(event.details.previousStart))} a {formatDateTime(String(event.details.start))}
                      </div>
                    )}
                    {event.reason && <div className="small">Motivo: {event.reason}</div>}
                  </li>
                ))}
              </ol>
            </SectionCard>
          </div>

          <aside className="stack-lg" aria-label="Contacto y avisos">
            <SectionCard title="Contacto y dirección" icon="user">
              <div className="person person-block">
                <Avatar name={data.contactName} size="lg" />
                <span className="person-text">
                  <span className="strong">{data.contactName}</span>
                  <span className="contact-line">
                    <Icon name="phone" size={15} />
                    <a href={`tel:${data.contactPhone}`}>{formatPhone(data.contactPhone)}</a>
                  </span>
                  <span className="contact-line">
                    <Icon name="mail" size={15} />
                    {data.contactEmail ? <a href={`mailto:${data.contactEmail}`}>{data.contactEmail}</a> : <span className="muted">Sin correo</span>}
                  </span>
                </span>
              </div>
              <dl className="details details-stacked subsection">
                <dt>Cliente asociado</dt>
                <dd>{data.customer ? <Link to={`/customers/${data.customer.id}`}>{data.customer.fullName}</Link> : <span className="muted">Sin asociar</span>}</dd>
                <dt>Zona</dt>
                <dd>
                  {data.district ? `${data.district}, ` : ''}
                  {data.canton}, {PROVINCE_LABELS[data.province]}
                </dd>
                <dt>Dirección</dt>
                <dd className="text-block">{data.addressLine}</dd>
                <dt>Avisos</dt>
                <dd>
                  {data.emailConsent || data.whatsappConsent
                    ? `Aceptó avisos en el formulario por ${[data.emailConsent && 'correo', data.whatsappConsent && 'WhatsApp'].filter(Boolean).join(' y ')}`
                    : data.notificationsConsent
                      ? 'Pidió avisos sin elegir canal (formulario anterior): confirma el canal en la ficha del cliente'
                      : 'No pidió avisos'}
                  {(data.emailConsent || data.whatsappConsent) && data.customer && (
                    <div className="muted small">Se registró en la ficha del cliente si sus datos de contacto coinciden.</div>
                  )}
                </dd>
              </dl>
            </SectionCard>

            {data.notifications.length > 0 && (
              <SectionCard title="Avisos al cliente" icon="bell" iconTone="warn">
                <NotificationStatusList notifications={data.notifications} emptyText="" />
              </SectionCard>
            )}
          </aside>
        </div>
      </ModuleSurface>
    </section>
  )
}

/** Identity resolution for a request: search with the request's name and phone prefilled. */
function LinkCustomerPanel({ request, onCancel, onDone }: { request: ServiceRequestDetail; onCancel: () => void; onDone: () => void }) {
  const picker = useCustomerPicker({ ...EMPTY_DRAFT, name: request.contactName, phone: formatPhone(request.contactPhone) })
  const link = useMutation({
    mutationFn: (confirmedNewPerson: boolean) =>
      linkCustomer(
        request.id,
        picker.choice?.kind === 'existing' ? { customerId: picker.choice.match.id } : { registerNew: true, confirmedNewPerson },
      ),
    onSuccess: onDone,
  })
  const duplicates = link.error instanceof ApiError ? duplicateMatches(link.error.properties) : []

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    link.mutate(false)
  }

  return (
    <form className="card card-highlight" onSubmit={submit} noValidate aria-label="Asociar cliente">
      <h2>Asociar cliente</h2>
      <p className="muted small">
        Busca si la persona ya es cliente. Si no aparece, se registrará con el nombre y teléfono de la solicitud.
      </p>
      {link.isError && duplicates.length === 0 && <Alert tone="error">{describeError(link.error)}</Alert>}
      <CustomerPicker picker={picker} disabled={link.isPending} />
      {duplicates.length > 0 && (
        <div className="subsection" role="alert">
          <p>
            <strong>Ya hay clientes con ese teléfono o nombre.</strong> Elige uno o confirma que es otra persona.
          </p>
          <ul className="match-list">
            {duplicates.map((match) => (
              <li key={match.id}>
                <span>{match.fullName}</span>
                <button type="button" className="button button-secondary button-small" onClick={() => { link.reset(); picker.select(match) }}>
                  Usar este cliente
                </button>
              </li>
            ))}
          </ul>
          <button type="button" className="button button-secondary" disabled={link.isPending} onClick={() => link.mutate(true)}>
            Es otra persona: registrar cliente nuevo
          </button>
        </div>
      )}
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={link.isPending || !picker.choice}>
          {link.isPending ? 'Guardando…' : picker.choice?.kind === 'existing' ? 'Asociar este cliente' : 'Registrar y asociar'}
        </button>
        <button type="button" className="button button-secondary" disabled={link.isPending} onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}

function BranchPanel({ request, onCancel, onMoved }: { request: ServiceRequestDetail; onCancel: () => void; onMoved: (stillVisible: boolean) => void }) {
  const { data: session } = useSession()
  const [branchId, setBranchId] = useState('')
  const change = useMutation({
    mutationFn: () => changeRequestBranch(request.id, Number(branchId)),
    onSuccess: (detail) => onMoved(detail !== undefined),
  })
  const options = (session?.branches ?? []).filter((branch) => branch.id !== request.branch.id)

  return (
    <form
      className="card card-highlight"
      noValidate
      aria-label="Cambiar sucursal"
      onSubmit={(event) => {
        event.preventDefault()
        change.mutate()
      }}
    >
      <h2>Cambiar sucursal</h2>
      {change.isError && <Alert tone="error">{describeError(change.error)}</Alert>}
      <SelectField label="Nueva sucursal" value={branchId} onChange={(event) => setBranchId(event.target.value)}>
        <option value="">Selecciona</option>
        {options.map((branch) => (
          <option key={branch.id} value={branch.id}>
            {branch.name}
          </option>
        ))}
      </SelectField>
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={change.isPending || !branchId}>
          Cambiar
        </button>
        <button type="button" className="button button-secondary" onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}
