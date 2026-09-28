import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { SectionCard } from '../../shared/ui/SectionCard'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { Pagination } from '../../shared/ui/Pagination'
import { useDisclosure } from '../../shared/ui/useDisclosure'
import { fetchRepairOrders, repairKeys } from '../repairs/repairsApi'
import { RepairStatusBadge } from '../repairs/RepairStatusBadge'
import { CustomerServiceHistory } from '../service/CustomerServiceHistory'
import { CustomerConsentsCard } from './CustomerConsentsCard'
import { CustomerFields } from './CustomerFields'
import {
  customerKeys,
  fetchCustomer,
  formatPhone,
  optional,
  updateCustomer,
  type Customer,
  type CustomerFormValues,
} from './customersApi'

/** FR-CUS-001: customer data, edit (optimistic version) and repair history. */
export function CustomerDetailPage() {
  const customerId = Number(useParams().customerId)
  const customer = useQuery({
    queryKey: customerKeys.detail(customerId),
    queryFn: ({ signal }) => fetchCustomer(customerId, signal),
  })
  const edit = useDisclosure()
  const [saved, setSaved] = useState(false)

  if (customer.isPending) return <LoadingState label="Cargando cliente…" />
  if (customer.isError) return <ErrorState error={customer.error} onRetry={() => customer.refetch()} />
  const data = customer.data

  return (
    <section className="page">
      <ModuleSurface
        breadcrumb={[{ to: '/customers', label: 'Clientes' }]}
        eyebrow="Ficha del cliente"
        icon="user"
        title={
          <span className="title-with-avatar">
            <Avatar name={data.fullName} size="lg" />
            <span>{data.fullName}</span>
          </span>
        }
        meta={
          <>
            <span className="chip">
              <Icon name="phone" />
              {formatPhone(data.phone)}
            </span>
            <span className="chip">
              <Icon name="building" />
              Registrado en {data.registeredBranch.name}
            </span>
          </>
        }
        actions={
          <>
            <Link className="button button-secondary" to={`/repairs/new?customerId=${data.id}`}>
              <Icon name="wrench" />
              Recibir equipo
            </Link>
            <button
              type="button"
              className="button button-primary"
              {...edit.triggerProps}
              onClick={() => {
                setSaved(false)
                edit.triggerProps.onClick()
              }}
            >
              <Icon name="edit" />
              Editar datos
            </button>
          </>
        }
      >

        {saved && <Alert tone="success">Datos del cliente actualizados.</Alert>}
        {edit.open && (
          <div {...edit.panelProps}>
            <EditCustomerForm
              key={data.version}
              customer={data}
              onCancel={edit.hide}
              onSaved={() => {
                setSaved(true)
                edit.hide()
              }}
            />
          </div>
        )}

        <div className="split">
          <div className="stack-lg">
            <RepairHistory customerId={data.id} />
            <CustomerServiceHistory customerId={data.id} />
          </div>
          <aside className="stack-lg" aria-label="Contacto y avisos">
            <SectionCard title="Datos de contacto" icon="user">
              <dl className="details details-stacked">
                <dt>Teléfono</dt>
                <dd>{formatPhone(data.phone)}</dd>
                <dt>Correo</dt>
                <dd>{data.email ?? '—'}</dd>
                <dt>Dirección</dt>
                <dd>{data.address ?? '—'}</dd>
                <dt>Notas internas</dt>
                <dd className="text-block">{data.internalNotes ?? '—'}</dd>
                <dt>Registrado en</dt>
                <dd>
                  {data.registeredBranch.name} · {formatDateTime(data.createdAt)}
                </dd>
                <dt>Última actualización</dt>
                <dd>{formatDateTime(data.updatedAt)}</dd>
              </dl>
            </SectionCard>
            <CustomerConsentsCard customerId={data.id} />
          </aside>
        </div>
      </ModuleSurface>
    </section>
  )
}

function EditCustomerForm({ customer, onCancel, onSaved }: { customer: Customer; onCancel: () => void; onSaved: () => void }) {
  const queryClient = useQueryClient()
  const [values, setValues] = useState<CustomerFormValues>({
    fullName: customer.fullName,
    phone: formatPhone(customer.phone),
    email: customer.email ?? '',
    address: customer.address ?? '',
    internalNotes: customer.internalNotes ?? '',
  })
  const mutation = useMutation({
    mutationFn: () =>
      updateCustomer(customer.id, {
        fullName: values.fullName.trim(),
        phone: values.phone.trim(),
        email: optional(values.email),
        address: optional(values.address),
        internalNotes: optional(values.internalNotes),
        version: customer.version,
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(customerKeys.detail(customer.id), updated)
      await queryClient.invalidateQueries({ queryKey: customerKeys.all })
      onSaved()
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <form className="card card-highlight" onSubmit={handleSubmit} noValidate>
      <h2>Editar cliente</h2>
      {mutation.isError && <Alert tone="error">{describeError(mutation.error)}</Alert>}
      <CustomerFields values={values} errors={fieldErrorsOf(mutation.error)} onChange={setValues} />
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={mutation.isPending || !values.fullName.trim() || !values.phone.trim()}>
          {mutation.isPending ? 'Guardando…' : 'Guardar cambios'}
        </button>
        <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

/** Every order of this customer in the caller's branches (one customer, many repairs). */
function RepairHistory({ customerId }: { customerId: number }) {
  const [page, setPage] = useState(0)
  const filters = { customerId, page }
  const orders = useQuery({
    queryKey: repairKeys.list(filters),
    queryFn: ({ signal }) => fetchRepairOrders(filters, signal),
    placeholderData: keepPreviousData,
  })

  return (
    <SectionCard
      className="panel"
      title="Reparaciones"
      icon="wrench"
      subtitle={orders.data ? `${orders.data.totalElements} ${orders.data.totalElements === 1 ? 'orden' : 'órdenes'} en tus sucursales` : undefined}
    >
      {orders.isPending && <LoadingState label="Cargando reparaciones…" />}
      {orders.isError && <Alert tone="error">{describeError(orders.error)}</Alert>}
      {orders.data?.content.length === 0 && (
        <EmptyState compact icon="wrench" title="Sin reparaciones">
          Este cliente no tiene órdenes en tus sucursales.
        </EmptyState>
      )}
      {orders.data && orders.data.content.length > 0 && (
        <>
          <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Orden</th>
                <th scope="col">Recibida</th>
                <th scope="col">Equipo</th>
                <th scope="col">Sucursal</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              {orders.data.content.map((order) => (
                <tr key={order.id}>
                  <td className="cell-title">
                    <Link className="order-code" to={`/repairs/${order.id}`}>
                      {order.orderCode}
                    </Link>
                  </td>
                  <td data-label="Recibida">{formatDateTime(order.receivedAt)}</td>
                  <td data-label="Equipo">
                    {order.device.type} {order.device.brand} {order.device.model ?? ''}
                  </td>
                  <td data-label="Sucursal">{order.branch.name}</td>
                  <td data-label="Estado">
                    <RepairStatusBadge status={order.status} />
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
            label="Paginación del historial de reparaciones"
            onChange={setPage}
          />
        </>
      )}
    </SectionCard>
  )
}
