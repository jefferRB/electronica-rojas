import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { PROVINCE_LABELS, REQUEST_STATUS_LABELS, WINDOW_LABELS, type RequestStatus } from '../../shared/i18n/serviceLabels'
import { formatDateTime } from '../../shared/lib/format'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Avatar } from '../../shared/ui/Avatar'
import { SearchField, SelectField } from '../../shared/ui/Field'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { Pagination } from '../../shared/ui/Pagination'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { permissions } from '../auth/permissions'
import { useSession } from '../auth/session'
import { crDate, crTime, longDate } from './crTime'
import { RequestStatusBadge, VisitStatusBadge } from './ServiceBadges'
import { fetchServiceRequests, serviceKeys, type RequestFilters } from './serviceApi'

const STATUS_TABS: (RequestStatus | '')[] = ['', 'PENDING', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'CANCELLED']

/** FR-SRV-003: inbox of home-service requests of the user's branches. Filters live in the URL. */
export function ServiceRequestsPage() {
  const { data: session } = useSession()
  const [params, setParams] = useSearchParams()
  const [searchText, setSearchText] = useState(params.get('q') ?? '')
  const search = useDebouncedValue(searchText.trim())
  const filters: RequestFilters = {
    status: (params.get('status') ?? '') as RequestStatus | '',
    branchId: params.get('branch') ? Number(params.get('branch')) : undefined,
    search,
    page: Number(params.get('page') ?? 0),
  }
  const requests = useQuery({
    queryKey: serviceKeys.requests(filters),
    queryFn: ({ signal }) => fetchServiceRequests(filters, signal),
    placeholderData: keepPreviousData,
  })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  const branches = session?.branches ?? []
  const filtered = Boolean(filters.status || search || params.get('branch'))

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Servicio a domicilio"
        icon="van"
        title="Solicitudes a domicilio"
        description="Del formulario público y registradas por el personal. Ninguna es una cita hasta que se confirma una visita."
        actions={
          <>
            {session && permissions.managePortal(session.user.role) && (
              <Link className="button button-ghost" to="/service-requests/portal">
                <Icon name="link" />
                Configuración del portal
              </Link>
            )}
            <Link className="button button-primary" to="/service-requests/new">
              <Icon name="plus" />
              Registrar solicitud
            </Link>
          </>
        }
      >
        <nav className="tabs tabs-line" aria-label="Estado de la solicitud">
          {STATUS_TABS.map((status) => (
            <a
              key={status || 'all'}
              href={`?status=${status}`}
              className={filters.status === status ? 'active' : undefined}
              aria-current={filters.status === status ? 'page' : undefined}
              onClick={(event) => {
                event.preventDefault()
                setFilter('status', status)
              }}
            >
              {status ? REQUEST_STATUS_LABELS[status] : 'Todas'}
            </a>
          ))}
        </nav>

        <FilterBar
          activeCount={params.get('branch') ? 1 : 0}
          canClear={Boolean(searchText || params.get('branch'))}
          onClear={() => {
            setSearchText('')
            const next = new URLSearchParams()
            if (filters.status) next.set('status', filters.status)
            setParams(next, { replace: true })
          }}
          search={
            <SearchField
              label="Buscar"
              placeholder="Código, nombre, equipo o cantón"
              value={searchText}
              onChange={(event) => {
                setSearchText(event.target.value)
                setFilter('q', event.target.value.trim())
              }}
            />
          }
        >
          {branches.length > 1 && (
            <SelectField label="Sucursal" value={params.get('branch') ?? ''} onChange={(event) => setFilter('branch', event.target.value)}>
              <option value="">Todas</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </SelectField>
          )}
        </FilterBar>

        {requests.isPending && <LoadingState label="Cargando solicitudes…" />}
        {requests.isError && <ErrorState error={requests.error} onRetry={() => requests.refetch()} />}
        {requests.data?.content.length === 0 &&
          (filtered ? (
            <EmptyState icon="search" title="Ninguna solicitud coincide">
              Cambia de pestaña o busca por código, nombre, equipo o cantón.
            </EmptyState>
          ) : (
            <EmptyState
              icon="van"
              title="No hay solicitudes a domicilio"
              action={
                <Link className="button button-secondary" to="/service-requests/new">
                  Registrar una por teléfono
                </Link>
              }
            >
              Las solicitudes del formulario público aparecen aquí para revisarlas.
            </EmptyState>
          ))}
        {requests.data && requests.data.content.length > 0 && (
          <>
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Solicitud</th>
                    <th scope="col">Cliente</th>
                    <th scope="col">Equipo y zona</th>
                    <th scope="col">Preferencia</th>
                    <th scope="col">Visita</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.data.content.map((request) => (
                    <tr key={request.id}>
                      <td className="cell-title">
                        <span className="order-cell">
                          <Link className="order-code" to={`/service-requests/${request.id}`}>
                            {request.requestCode}
                          </Link>
                          <span className="cell-sub">{formatDateTime(request.createdAt)}</span>
                        </span>
                      </td>
                      <td data-label="Cliente">
                        <span className="person">
                          <Avatar name={request.customer?.fullName ?? request.contactName} size="sm" />
                          <span className="person-text">
                            {request.customer?.fullName ?? request.contactName}
                            {!request.customer && <span className="cell-sub">Sin asociar</span>}
                          </span>
                        </span>
                      </td>
                      <td data-label="Equipo y zona">
                        <span>
                          <span className="cell-primary">{request.deviceType}</span>
                          <span className="cell-sub">
                            {request.canton}, {PROVINCE_LABELS[request.province]}
                          </span>
                        </span>
                      </td>
                      <td data-label="Preferencia">
                        <span>
                          {request.preferredDate ? longDate(request.preferredDate) : 'Sin fecha'}
                          <span className="cell-sub">{WINDOW_LABELS[request.preferredWindow]}</span>
                        </span>
                      </td>
                      <td data-label="Visita">
                        {request.activeVisit ? (
                          <span className="status-stack">
                            <span>
                              {longDate(crDate(request.activeVisit.start))}, {crTime(request.activeVisit.start)}
                              <span className="cell-sub">{request.activeVisit.technician.fullName}</span>
                            </span>
                            <VisitStatusBadge status={request.activeVisit.status} />
                          </span>
                        ) : (
                          <span className="muted">Sin programar</span>
                        )}
                      </td>
                      <td data-label="Estado" className="cell-status">
                        <RequestStatusBadge status={request.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={requests.data.page}
              totalPages={requests.data.totalPages}
              totalElements={requests.data.totalElements}
              noun="solicitudes"
              label="Paginación de solicitudes"
              onChange={(page) => setFilter('page', String(page))}
            />
          </>
        )}
      </ModuleSurface>
    </section>
  )
}
