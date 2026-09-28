import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { describeError } from '../../shared/api/describeError'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { SectionCard } from '../../shared/ui/SectionCard'
import { EmptyState, LoadingState } from '../../shared/ui/States'
import { crDate, crTime, longDate } from './crTime'
import { RequestStatusBadge, VisitStatusBadge } from './ServiceBadges'
import { fetchServiceRequests, serviceKeys } from './serviceApi'

/** A customer's home-service requests and visits, next to their workshop repairs. */
export function CustomerServiceHistory({ customerId }: { customerId: number }) {
  const filters = { customerId, page: 0 }
  const requests = useQuery({
    queryKey: serviceKeys.requests(filters),
    queryFn: ({ signal }) => fetchServiceRequests(filters, signal),
  })

  return (
    <SectionCard
      className="panel"
      title="Servicios a domicilio"
      icon="van"
      iconTone="accent"
      subtitle={requests.data ? `${requests.data.totalElements} ${requests.data.totalElements === 1 ? 'solicitud' : 'solicitudes'} en tus sucursales` : undefined}
    >
      {requests.isPending && <LoadingState />}
      {requests.isError && <Alert tone="error">{describeError(requests.error)}</Alert>}
      {requests.data?.content.length === 0 && (
        <EmptyState compact icon="van" title="Sin solicitudes a domicilio">
          Este cliente no tiene solicitudes en tus sucursales.
        </EmptyState>
      )}
      {requests.data && requests.data.content.length > 0 && (
        <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">Solicitud</th>
              <th scope="col">Recibida</th>
              <th scope="col">Equipo</th>
              <th scope="col">Visita</th>
              <th scope="col">Estado</th>
            </tr>
          </thead>
          <tbody>
            {requests.data.content.map((request) => (
              <tr key={request.id}>
                <td className="cell-title">
                  <Link className="order-code" to={`/service-requests/${request.id}`}>
                    {request.requestCode}
                  </Link>
                </td>
                <td data-label="Recibida">{formatDateTime(request.createdAt)}</td>
                <td data-label="Equipo">{request.deviceType}</td>
                <td data-label="Visita">
                  {request.activeVisit ? (
                    <>
                      {longDate(crDate(request.activeVisit.start))}, {crTime(request.activeVisit.start)}{' '}
                      <VisitStatusBadge status={request.activeVisit.status} />
                    </>
                  ) : (
                    '—'
                  )}
                </td>
                <td data-label="Estado">
                  <RequestStatusBadge status={request.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}
    </SectionCard>
  )
}
