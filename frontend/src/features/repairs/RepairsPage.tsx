import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { REPAIR_STATUS_LABELS, type RepairStatus } from '../../shared/i18n/labels'
import { formatDateTime } from '../../shared/lib/format'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Avatar } from '../../shared/ui/Avatar'
import { SearchField, SelectField, TextField } from '../../shared/ui/Field'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { Pagination } from '../../shared/ui/Pagination'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { permissions } from '../auth/permissions'
import { useSession } from '../auth/session'
import { fetchRepairOrders, repairKeys, type RepairFilters } from './repairsApi'
import { RepairStatusBadge } from './RepairStatusBadge'

/** Status shortcuts, in workshop order; "Todas" clears the filter. */
const STATUS_TABS: RepairStatus[] = [
  'RECEIVED',
  'DIAGNOSING',
  'AWAITING_APPROVAL',
  'APPROVED',
  'IN_REPAIR',
  'READY_FOR_PICKUP',
  'DELIVERED',
  'CANCELLED',
  'UNREPAIRABLE',
]

/**
 * FR-REP-002: repair orders of the user's branches, newest first. Filters live in the URL so a
 * filtered list can be shared or reloaded (and the dashboard opens it with the same filter).
 * Technicians only receive their assigned orders.
 */
export function RepairsPage() {
  const { data: session } = useSession()
  const [params, setParams] = useSearchParams()
  const [searchText, setSearchText] = useState(params.get('q') ?? '')
  const search = useDebouncedValue(searchText.trim())
  const filters: RepairFilters = {
    branchId: params.get('branch') ? Number(params.get('branch')) : undefined,
    status: (params.get('status') ?? '') as RepairStatus | '',
    search,
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    page: Number(params.get('page') ?? 0),
  }
  const orders = useQuery({
    queryKey: repairKeys.list(filters),
    queryFn: ({ signal }) => fetchRepairOrders(filters, signal),
    placeholderData: keepPreviousData,
  })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  const role = session?.user.role
  const branches = session?.branches ?? []
  const activeFilters = [params.get('branch'), filters.from, filters.to].filter(Boolean).length
  const filtered = Boolean(filters.status || search || activeFilters)

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Taller"
        icon="wrench"
        title="Reparaciones"
        description={
          role === 'TECHNICIAN'
            ? 'Órdenes que tienes asignadas, de la más reciente a la más antigua.'
            : 'Equipos recibidos en tus sucursales: etapa, técnico responsable y entrega.'
        }
        actions={
          role &&
          permissions.receiveRepairs(role) && (
            <Link className="button button-primary" to="/repairs/new">
              <Icon name="plus" />
              Recibir equipo
            </Link>
          )
        }
      >
        <div className="tabs tabs-line" role="group" aria-label="Filtrar por estado">
          <button type="button" className={filters.status === '' ? 'active' : undefined} aria-pressed={filters.status === ''} onClick={() => setFilter('status', '')}>
            Todas
          </button>
          {STATUS_TABS.map((status) => (
            <button
              key={status}
              type="button"
              className={filters.status === status ? 'active' : undefined}
              aria-pressed={filters.status === status}
              onClick={() => setFilter('status', status)}
            >
              {REPAIR_STATUS_LABELS[status]}
            </button>
          ))}
        </div>

        <FilterBar
          activeCount={activeFilters}
          canClear={Boolean(searchText || activeFilters)}
          onClear={() => {
            setSearchText('')
            const next = new URLSearchParams()
            if (filters.status) next.set('status', filters.status)
            setParams(next, { replace: true })
          }}
          search={
            <SearchField
              label="Buscar"
              placeholder="Orden, cliente, marca o serie"
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
          <TextField label="Recibida desde" type="date" value={filters.from} onChange={(event) => setFilter('from', event.target.value)} />
          <TextField label="Recibida hasta" type="date" value={filters.to} onChange={(event) => setFilter('to', event.target.value)} />
        </FilterBar>

        {orders.isPending && <LoadingState label="Cargando órdenes…" />}
        {orders.isError && <ErrorState error={orders.error} onRetry={() => orders.refetch()} />}
        {orders.data?.content.length === 0 &&
          (filtered ? (
            <EmptyState icon="search" title="Ninguna orden coincide con los filtros">
              Prueba con otro estado, amplía las fechas o busca por el código de la orden, el cliente o la serie.
            </EmptyState>
          ) : (
            <EmptyState
              icon="wrench"
              title={role === 'TECHNICIAN' ? 'No tienes órdenes asignadas' : 'Todavía no hay equipos recibidos'}
              action={
                role &&
                permissions.receiveRepairs(role) && (
                  <Link className="button button-primary" to="/repairs/new">
                    <Icon name="plus" />
                    Recibir el primer equipo
                  </Link>
                )
              }
            >
              {role === 'TECHNICIAN'
                ? 'Cuando gestión te asigne una orden aparecerá aquí.'
                : 'Cada equipo que llega al mostrador se registra con una orden y su historial.'}
            </EmptyState>
          ))}
        {orders.data && orders.data.content.length > 0 && (
          <>
            <div className="table-scroll">
              <table className="data-table repairs-table">
                <thead>
                  <tr>
                    <th scope="col">Orden</th>
                    <th scope="col">Cliente</th>
                    <th scope="col">Equipo</th>
                    <th scope="col">Técnico</th>
                    <th scope="col">Sucursal</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.data.content.map((order) => (
                    <tr key={order.id}>
                      <td className="cell-title">
                        <span className="order-cell">
                          <Link className="order-code" to={`/repairs/${order.id}`}>
                            {order.orderCode}
                          </Link>
                          <span className="cell-sub">{formatDateTime(order.receivedAt)}</span>
                        </span>
                      </td>
                      <td data-label="Cliente">
                        <span className="person">
                          <Avatar name={order.customer.fullName} size="sm" />
                          <span className="person-text">{order.customer.fullName}</span>
                        </span>
                      </td>
                      <td data-label="Equipo">
                        <span>
                          <span className="cell-primary">{order.device.type}</span>
                          <span className="cell-sub">
                            {order.device.brand}
                            {order.device.model ? ` ${order.device.model}` : ''}
                          </span>
                        </span>
                      </td>
                      <td data-label="Técnico">{order.technician?.fullName ?? <span className="muted">Sin asignar</span>}</td>
                      <td data-label="Sucursal">
                        <span className="chip">{order.branch.code}</span>
                      </td>
                      <td data-label="Estado" className="cell-status">
                        <span className="status-stack">
                          <RepairStatusBadge status={order.status} />
                          {(order.status === 'CANCELLED' || order.status === 'UNREPAIRABLE') && order.inCustody && (
                            <span className="custody-flag">
                              <Icon name="alert" size={14} />
                              Pendiente de devolver
                            </span>
                          )}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={orders.data.page}
              totalPages={orders.data.totalPages}
              totalElements={orders.data.totalElements}
              noun="órdenes"
              label="Paginación de órdenes"
              onChange={(page) => setFilter('page', String(page))}
            />
          </>
        )}
      </ModuleSurface>
    </section>
  )
}
