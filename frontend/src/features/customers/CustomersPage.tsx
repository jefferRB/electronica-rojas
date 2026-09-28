import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError } from '../../shared/api/httpClient'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { SearchField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { useDisclosure } from '../../shared/ui/useDisclosure'
import { useSelectedBranch } from '../branches/selectedBranch'
import type { ContactChannel } from '../../shared/i18n/consent'
import { ConsentChoice } from './ConsentChoice'
import { CustomerFields } from './CustomerFields'
import { duplicateMatches } from './customerDraft'
import {
  createCustomer,
  withConsent,
  customerKeys,
  EMPTY_CUSTOMER,
  fetchCustomers,
  formatPhone,
  optional,
  type Customer,
  type CustomerFormValues,
} from './customersApi'

/** FR-CUS-001: customers of the user's branches, search by name, email or phone digits. */
export function CustomersPage() {
  const [searchText, setSearchText] = useState('')
  const search = useDebouncedValue(searchText.trim())
  const [page, setPage] = useState(0)
  const customers = useQuery({
    queryKey: customerKeys.search(search, page),
    queryFn: ({ signal }) => fetchCustomers(search, page, signal),
    placeholderData: keepPreviousData,
  })
  const create = useDisclosure()
  const [created, setCreated] = useState<Customer | null>(null)

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Clientes"
        icon="users"
        title="Clientes"
        description="Personas y empresas registradas o atendidas en tus sucursales, con su historial de reparaciones y visitas."
        actions={
          <button
            type="button"
            className="button button-primary"
            {...create.triggerProps}
            onClick={() => {
              setCreated(null)
              create.triggerProps.onClick()
            }}
          >
            <Icon name="plus" />
            Agregar cliente
          </button>
        }
      >
        {created && (
          <Alert tone="success">
            Cliente <Link to={`/customers/${created.id}`}>{created.fullName}</Link> registrado.
          </Alert>
        )}
        {create.open && (
          <div {...create.panelProps}>
            <CreateCustomerForm
              onCancel={create.hide}
              onCreated={(customer) => {
                setCreated(customer)
                create.hide()
              }}
            />
          </div>
        )}

        <FilterBar
          canClear={searchText !== ''}
          onClear={() => {
            setSearchText('')
            setPage(0)
          }}
          search={
            <SearchField
              label="Buscar"
              placeholder="Nombre, correo o teléfono (mínimo 4 dígitos)"
              value={searchText}
              onChange={(event) => {
                setSearchText(event.target.value)
                setPage(0)
              }}
            />
          }
        />
        {customers.isPending && <LoadingState label="Cargando clientes…" />}
        {customers.isError && <ErrorState error={customers.error} onRetry={() => customers.refetch()} />}
        {customers.data?.content.length === 0 &&
          (search ? (
            <EmptyState icon="search" title="Ningún cliente coincide con la búsqueda">
              Prueba con parte del nombre, el correo o al menos cuatro dígitos del teléfono.
            </EmptyState>
          ) : (
            <EmptyState icon="users" title="Todavía no hay clientes registrados">
              Se registran al recibir un equipo o con «Agregar cliente».
            </EmptyState>
          ))}
        {customers.data && customers.data.content.length > 0 && (
          <>
            <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Teléfono</th>
                  <th scope="col">Correo</th>
                  <th scope="col">Sucursal de registro</th>
                </tr>
              </thead>
              <tbody>
                {customers.data.content.map((customer) => (
                  <tr key={customer.id}>
                    <td className="cell-title">
                      <span className="person">
                        <Avatar name={customer.fullName} />
                        <Link className="person-text" to={`/customers/${customer.id}`}>
                          {customer.fullName}
                        </Link>
                      </span>
                    </td>
                    <td data-label="Teléfono" className="nowrap">
                      {formatPhone(customer.phone)}
                    </td>
                    <td data-label="Correo">{customer.email ?? <span className="muted">Sin correo</span>}</td>
                    <td data-label="Sucursal">
                      <span className="chip">{customer.registeredBranch.name}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            <Pagination
              page={customers.data.page}
              totalPages={customers.data.totalPages}
              totalElements={customers.data.totalElements}
              noun="clientes"
              label="Paginación de clientes"
              onChange={setPage}
            />
          </>
        )}
      </ModuleSurface>
    </section>
  )
}

function CreateCustomerForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (customer: Customer) => void }) {
  const queryClient = useQueryClient()
  const { selected } = useSelectedBranch()
  const [values, setValues] = useState<CustomerFormValues>(EMPTY_CUSTOMER)
  const [consentChannels, setConsentChannels] = useState<ContactChannel[]>([])
  const mutation = useMutation({
    mutationFn: (allowDuplicatePhone: boolean) =>
      createCustomer(
        selected!.id,
        withConsent(
          {
            fullName: values.fullName.trim(),
            phone: values.phone.trim(),
            email: optional(values.email),
            address: optional(values.address),
            internalNotes: optional(values.internalNotes),
            allowDuplicatePhone,
          },
          consentChannels,
        ),
      ),
    onSuccess: async (customer) => {
      await queryClient.invalidateQueries({ queryKey: customerKeys.all })
      onCreated(customer)
    },
  })
  const errors = fieldErrorsOf(mutation.error)
  const duplicates = mutation.error instanceof ApiError ? duplicateMatches(mutation.error.properties) : []

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate(false)
  }

  return (
    <form className="card card-highlight" onSubmit={handleSubmit} noValidate>
      <h2>Nuevo cliente</h2>
      {selected ? (
        <p className="muted small">Se registrará en {selected.name}.</p>
      ) : (
        <Alert tone="info">Selecciona una sucursal en el encabezado para registrar clientes.</Alert>
      )}
      {mutation.isError && <Alert tone="error">{describeError(mutation.error)}</Alert>}
      {duplicates.length > 0 && (
        <div className="subsection">
          <p>Clientes con el mismo teléfono:</p>
          <ul className="match-list">
            {duplicates.map((match) => (
              <li key={match.id}>
                <span>{match.fullName}</span>
                {match.inScope ? (
                  <Link to={`/customers/${match.id}`}>Ver cliente</Link>
                ) : (
                  <span className="muted small">Registrado en otra sucursal</span>
                )}
              </li>
            ))}
          </ul>
          <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={() => mutation.mutate(true)}>
            Es otra persona: registrar de todos modos
          </button>
        </div>
      )}
      <CustomerFields values={values} errors={errors} errorPrefix="customer." onChange={setValues} />
      <ConsentChoice channels={consentChannels} hasEmail={values.email.trim() !== ''} disabled={mutation.isPending} onChange={setConsentChannels} />
      <div className="form-actions">
        <button
          type="submit"
          className="button button-primary"
          disabled={mutation.isPending || !selected || !values.fullName.trim() || !values.phone.trim()}
        >
          {mutation.isPending ? 'Guardando…' : 'Registrar cliente'}
        </button>
        <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
