import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../../shared/api/httpClient'
import { Alert } from '../../shared/ui/Alert'
import { crDate, longDate } from './crTime'
import { fetchVisitForOrder, serviceKeys } from './serviceApi'

/** On a workshop order: the home visit it came from, if any (the API answers 404 otherwise). */
export function OrderOriginNotice({ orderId }: { orderId: number }) {
  const origin = useQuery({
    queryKey: serviceKeys.byOrder(orderId),
    queryFn: ({ signal }) => fetchVisitForOrder(orderId, signal),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })
  if (!origin.data) return null
  return (
    <Alert tone="info" title="Viene de una visita a domicilio.">
      Visita del {longDate(crDate(origin.data.start))}, solicitud{' '}
      <Link className="mono" to={`/visits/${origin.data.id}`}>
        {origin.data.requestCode}
      </Link>
      .
    </Alert>
  )
}
