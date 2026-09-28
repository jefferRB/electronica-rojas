import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ApiError } from '../../shared/api/httpClient'
import { describeError } from '../../shared/api/describeError'
import { PUBLIC_STATUS_LABELS, WINDOW_LABELS } from '../../shared/i18n/serviceLabels'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { LoadingState } from '../../shared/ui/States'
import { PublicLayout } from './PublicLayout'
import { longDate, shortTime } from './crTime'
import { fetchPublicStatus } from './serviceApi'

/** The customer's view of one request: coarse status and, once confirmed, the visit time. */
export function PublicStatusPage() {
  const ref = useParams().ref ?? ''
  const status = useQuery({
    queryKey: ['public', 'status', ref],
    queryFn: ({ signal }) => fetchPublicStatus(ref, signal),
    retry: false,
  })

  return (
    <PublicLayout>
      <section className="card public-card">
        <h1>Estado de tu solicitud</h1>
        {status.isPending && <LoadingState />}
        {status.isError && (
          <Alert tone="error">
            {status.error instanceof ApiError && (status.error.status === 404 || status.error.status === 400)
              ? 'No encontramos una solicitud con este enlace. Revisa que esté completo.'
              : describeError(status.error)}
          </Alert>
        )}
        {status.data && (
          <>
            <Alert tone={status.data.status === 'NOT_ACCEPTED' || status.data.status === 'CANCELLED' ? 'error' : 'info'}>
              <strong>{PUBLIC_STATUS_LABELS[status.data.status].title}.</strong> {PUBLIC_STATUS_LABELS[status.data.status].detail}
            </Alert>
            <dl className="details">
              <dt>Solicitud</dt>
              <dd className="mono">{status.data.requestCode}</dd>
              <dt>Equipo</dt>
              <dd>{status.data.deviceType}</dd>
              <dt>Sucursal</dt>
              <dd>{status.data.branchName}</dd>
              {status.data.visitDate ? (
                <>
                  <dt>Visita</dt>
                  <dd>
                    {longDate(status.data.visitDate)}, de {shortTime(status.data.visitFrom)} a {shortTime(status.data.visitTo)}
                  </dd>
                </>
              ) : (
                <>
                  <dt>Preferencia</dt>
                  <dd>
                    {status.data.preferredDate ? `${longDate(status.data.preferredDate)}, ` : ''}
                    {WINDOW_LABELS[status.data.preferredWindow].toLowerCase()} (sin confirmar)
                  </dd>
                </>
              )}
              <dt>Enviada</dt>
              <dd>{formatDateTime(status.data.submittedAt)}</dd>
            </dl>
          </>
        )}
        <p className="muted small">
          <Link to="/solicitar-servicio">Hacer otra solicitud</Link>
        </p>
      </section>
    </PublicLayout>
  )
}
