import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { describeError } from '../../shared/api/describeError'
import {
  CHANNEL_LABELS,
  NOTIFICATION_EVENT_LABELS,
  NOTIFICATION_STATE_LABELS,
  NOTIFICATION_STATE_TONES,
  describeNotificationState,
  type NotificationState,
} from '../../shared/i18n/consent'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { Pagination } from '../../shared/ui/Pagination'
import { useSession } from '../auth/session'
import { useSelectedBranch } from '../branches/selectedBranch'
import {
  fetchDevInbox,
  fetchNotification,
  fetchNotifications,
  notificationKeys,
  retryNotification,
  type NotificationRow,
} from './notificationsApi'

const OUTCOME_LABELS = { SENT: 'Enviado', RETRY: 'Falló, se reintentará', FAILED: 'Falló', SKIPPED: 'No enviado' } as const

/**
 * C.4: operational view of customer notices for ADMIN and branch managers. States, attempts and
 * errors are visible; message bodies and full addresses are not (the development inbox, for ADMIN
 * only, exists solely while no real provider is configured).
 */
export function NotificationsPage() {
  const { data: session } = useSession()
  const isAdmin = session?.user.role === 'ADMIN'
  const { selected } = useSelectedBranch()
  const [params, setParams] = useSearchParams()
  const [tab, setTab] = useState<'list' | 'inbox'>('list')
  const [openId, setOpenId] = useState<number | null>(null)
  const filters = {
    status: (params.get('status') ?? '') as NotificationState | '',
    branchId: params.get('all') === '1' ? undefined : selected?.id,
    page: Number(params.get('page') ?? 0),
  }
  const list = useQuery({
    queryKey: notificationKeys.list(filters),
    queryFn: ({ signal }) => fetchNotifications(filters, signal),
    placeholderData: keepPreviousData,
    enabled: tab === 'list',
  })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Administración y control"
        icon="bell"
        title="Notificaciones"
        description="Avisos a clientes (equipo listo, visitas). Se envían después de guardar la operación y solo con consentimiento; el estado del aviso no cambia el de la orden."
      >
        {isAdmin && (
          <div className="tabs tabs-line section-tabs" role="tablist" aria-label="Vista">
            <button type="button" role="tab" aria-selected={tab === 'list'} className={tab === 'list' ? 'active' : ''} onClick={() => setTab('list')}>
              <Icon name="send" />
              Envíos
            </button>
            <button type="button" role="tab" aria-selected={tab === 'inbox'} className={tab === 'inbox' ? 'active' : ''} onClick={() => setTab('inbox')}>
              <Icon name="inbox" />
              Bandeja de desarrollo (simulada)
            </button>
          </div>
        )}
        {tab === 'inbox' ? (
          <DevInbox />
        ) : (
          <>
            <div className="tabs tabs-line" role="group" aria-label="Estado del aviso">
              <button type="button" className={filters.status === '' ? 'active' : undefined} aria-pressed={filters.status === ''} onClick={() => setFilter('status', '')}>
                Todos
              </button>
              {(Object.keys(NOTIFICATION_STATE_LABELS) as NotificationState[]).map((value) => (
                <button
                  key={value}
                  type="button"
                  className={filters.status === value ? 'active' : undefined}
                  aria-pressed={filters.status === value}
                  onClick={() => setFilter('status', value)}
                >
                  <span className={`status-dot tone-${NOTIFICATION_STATE_TONES[value]}`} aria-hidden="true" />
                  {NOTIFICATION_STATE_LABELS[value]}
                </button>
              ))}
            </div>
            <div className="panel-toolbar">
              <p className="muted small no-margin">
                {params.get('all') === '1' ? 'Avisos de todas tus sucursales.' : selected ? `Avisos de ${selected.name}.` : 'Avisos de tus sucursales.'}
              </p>
              <label className="checkbox">
                <input type="checkbox" checked={params.get('all') === '1'} onChange={(event) => setFilter('all', event.target.checked ? '1' : '')} /> Todas
                mis sucursales
              </label>
            </div>
            {list.isError && <ErrorState error={list.error} onRetry={() => list.refetch()} />}
            {list.isPending && <LoadingState label="Cargando avisos…" />}
            {list.data?.content.length === 0 && (
              <EmptyState icon="bell" title="No hay avisos con estos filtros">
                Los avisos se crean al marcar un equipo como listo o al confirmar, reprogramar o cancelar una visita.
              </EmptyState>
            )}
            {list.data && list.data.content.length > 0 && (
              <>
                <div className="table-scroll">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Fecha</th>
                      <th scope="col">Aviso</th>
                      <th scope="col">Referencia</th>
                      <th scope="col">Destino</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Intentos</th>
                      <th scope="col">Detalle</th>
                    </tr>
                  </thead>
                  <tbody>
                    {list.data.content.map((row) => (
                      <Fragment key={row.id}>
                        <tr>
                          <td data-label="Fecha" className="nowrap">
                            {formatDateTime(row.createdAt)}
                          </td>
                          <td className="cell-title">
                            <span className="cell-primary">{NOTIFICATION_EVENT_LABELS[row.eventType]}</span>
                            <span className="cell-sub">{CHANNEL_LABELS[row.channel]}</span>
                          </td>
                          <td data-label="Referencia" className="mono">
                            <ReferenceLink row={row} />
                          </td>
                          <td data-label="Destino">{row.recipientHint ?? '—'}</td>
                          <td data-label="Estado" className="cell-status">
                            <span className={`badge badge-${NOTIFICATION_STATE_TONES[row.status]}`}>{describeNotificationState(row.status, row.skipReason)}</span>
                            {row.lastError && row.status !== 'SENT' && <div className="muted small">Último error: {row.lastError}</div>}
                            {row.status === 'PENDING' && row.attempts > 0 && (
                              <div className="muted small">Próximo intento: {formatDateTime(row.nextAttemptAt)}</div>
                            )}
                          </td>
                          <td data-label="Intentos" className="numeric">
                            {row.attempts} / {row.maxAttempts}
                          </td>
                          <td data-label="Detalle">
                            <button type="button" className="button button-secondary button-small" aria-expanded={openId === row.id} onClick={() => setOpenId(openId === row.id ? null : row.id)}>
                              {openId === row.id ? 'Ocultar' : 'Ver intentos'}
                            </button>
                          </td>
                        </tr>
                        {openId === row.id && (
                          <tr className="row-correction">
                            <td colSpan={7}>
                              <NotificationAttempts id={row.id} />
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
                </div>
                <Pagination
                  page={filters.page}
                  totalPages={list.data.totalPages}
                  totalElements={list.data.totalElements}
                  noun="notificaciones"
                  label="Páginas de notificaciones"
                  onChange={(page) => setFilter('page', String(page))}
                />
              </>
            )}
          </>
        )}
      </ModuleSurface>
    </section>
  )
}

function ReferenceLink({ row }: { row: NotificationRow }) {
  if (!row.reference) return <>—</>
  return row.subjectType === 'REPAIR_ORDER' ? <Link to={`/repairs/${row.subjectId}`}>{row.reference}</Link> : <Link to={`/visits/${row.subjectId}`}>{row.reference}</Link>
}

function NotificationAttempts({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const detail = useQuery({ queryKey: notificationKeys.detail(id), queryFn: ({ signal }) => fetchNotification(id, signal) })
  const retry = useMutation({
    mutationFn: () => retryNotification(id),
    onSuccess: async (updated) => {
      queryClient.setQueryData(notificationKeys.detail(id), updated)
      await queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] })
    },
  })
  if (detail.isPending) return <LoadingState label="Cargando intentos…" />
  if (detail.isError) return <Alert tone="error">{describeError(detail.error)}</Alert>
  const { message, attempts } = detail.data
  return (
    <div>
      {retry.isError && <Alert tone="error">{describeError(retry.error)}</Alert>}
      {retry.isSuccess && <Alert tone="success">Reintento programado: el trabajador lo enviará en su próxima pasada.</Alert>}
      {attempts.length === 0 ? (
        <p className="muted small">Todavía sin intentos.</p>
      ) : (
        <ol className="timeline">
          {attempts.map((attempt) => (
            <li key={attempt.attemptNumber + attempt.startedAt}>
              <strong>
                Intento {attempt.attemptNumber}: {OUTCOME_LABELS[attempt.outcome]}
              </strong>
              <div className="muted small">
                {formatDateTime(attempt.finishedAt)}
                {attempt.errorCode && ` · ${attempt.errorCode}`}
                {attempt.errorMessage && ` · ${attempt.errorMessage}`}
              </div>
            </li>
          ))}
        </ol>
      )}
      {message.status === 'FAILED' && (
        <button type="button" className="button button-secondary button-small" disabled={retry.isPending} onClick={() => retry.mutate()}>
          {retry.isPending ? 'Programando…' : 'Reintentar envío'}
        </button>
      )}
    </div>
  )
}

function DevInbox() {
  const inbox = useQuery({ queryKey: notificationKeys.inbox, queryFn: ({ signal }) => fetchDevInbox(signal) })
  return (
    <div className="module-section dev-inbox">
      <Alert tone="warn" title="Simulación: estos correos NO se enviaron.">
        Modo de desarrollo: los mensajes no salen del servidor. Aquí se ve exactamente lo que recibiría el cliente (últimos 100,
        se borran al reiniciar). Con un proveedor real configurado esta bandeja desaparece.
      </Alert>
      {inbox.isPending && <LoadingState />}
      {inbox.isError && <ErrorState error={inbox.error} />}
      {inbox.data?.length === 0 && (
        <EmptyState icon="inbox" title="Sin mensajes simulados todavía">
          Aparecen cuando el trabajador procesa un aviso con consentimiento.
        </EmptyState>
      )}
      <ul className="quote-list">
        {inbox.data?.map((message) => (
          <li key={message.messageId} className="dev-message">
            <div className="status-line">
              <span className="badge badge-warn badge-plain">Simulado · no enviado</span>
              <strong>{message.subject}</strong>
            </div>
            <div className="muted small">
              Para {message.to} · {formatDateTime(message.acceptedAt)}
            </div>
            <pre className="text-block mail-body">{message.body}</pre>
          </li>
        ))}
      </ul>
    </div>
  )
}
