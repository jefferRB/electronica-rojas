import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { fieldErrorsOf } from '../../shared/api/describeError'
import { describeAuditEvent } from '../../shared/i18n/audit'
import { AUDIT_ACTION_LABELS } from '../../shared/i18n/labels'
import { formatDateTime } from '../../shared/lib/format'
import { Avatar } from '../../shared/ui/Avatar'
import { FilterBar } from '../../shared/ui/FilterBar'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { SelectField, TextField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { useSession } from '../auth/session'
import { branchKeys, fetchBranches, type BranchSummary } from '../branches/branchesApi'
import { fetchAuditEvents } from './auditApi'

/**
 * FR-AUD-001: read-only trail in Spanish. ADMIN sees every branch; managers only theirs. The
 * branch list offered here is a convenience: the server applies the scope to every query.
 */
export function AuditPage() {
  const { data: session } = useSession()
  const isAdmin = session?.user.role === 'ADMIN'
  const [params, setParams] = useSearchParams()
  const filters = {
    action: params.get('action') ?? '',
    branchId: params.get('branch') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    page: Number(params.get('page') ?? 0),
    size: 20,
  }
  const events = useQuery({
    queryKey: ['audit', filters],
    queryFn: ({ signal }) => fetchAuditEvents(filters, signal),
    placeholderData: keepPreviousData,
  })
  const allBranches = useQuery({ queryKey: branchKeys.all, queryFn: ({ signal }) => fetchBranches(signal), enabled: isAdmin })
  const branchOptions: BranchSummary[] = isAdmin ? (allBranches.data ?? []) : (session?.branches ?? [])
  const errors = fieldErrorsOf(events.error)

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
        icon="audit"
        title="Auditoría"
        description="Quién hizo qué, dónde y cuándo. Registro de solo lectura; horas de Costa Rica."
      >
        <FilterBar
          activeCount={[filters.action, filters.branchId, filters.from, filters.to].filter(Boolean).length}
          canClear={Boolean(filters.action || filters.branchId || filters.from || filters.to)}
          onClear={() => setParams(new URLSearchParams(), { replace: true })}
        >
          <SelectField label="Acción" value={filters.action} onChange={(event) => setFilter('action', event.target.value)}>
            <option value="">Todas</option>
            {Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </SelectField>
          <SelectField label="Sucursal" value={filters.branchId} onChange={(event) => setFilter('branch', event.target.value)}>
            <option value="">{isAdmin ? 'Todas' : 'Todas mis sucursales'}</option>
            {branchOptions.map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.code} · {branch.name}
              </option>
            ))}
          </SelectField>
          <TextField label="Desde" type="date" value={filters.from} onChange={(event) => setFilter('from', event.target.value)} />
          <TextField
            label="Hasta"
            type="date"
            value={filters.to}
            min={filters.from || undefined}
            error={errors.to}
            onChange={(event) => setFilter('to', event.target.value)}
          />
        </FilterBar>
        {events.isPending && <LoadingState label="Cargando auditoría…" />}
        {events.isError && !errors.to && <ErrorState error={events.error} onRetry={() => events.refetch()} />}
        {events.data?.content.length === 0 && (
          <EmptyState icon="audit" title="Sin eventos para estos filtros">
            Amplía el rango de fechas o quita el filtro de acción o sucursal.
          </EmptyState>
        )}
        {events.data && events.data.content.length > 0 && (
          <>
            <div className="table-scroll">
            <table className="data-table audit-table">
              <thead>
                <tr>
                  <th scope="col">Acción</th>
                  <th scope="col">Colaborador</th>
                  <th scope="col">Fecha</th>
                </tr>
              </thead>
              <tbody>
                {events.data.content.map((event) => {
                  const text = describeAuditEvent(event)
                  return (
                    <tr key={event.id}>
                      <td className="cell-title audit-what">
                        <span className="cell-primary">{text.title}</span>
                        <span className="audit-description">{text.description}</span>
                        {text.technical && (
                          <details className="technical">
                            <summary>Texto técnico original</summary>
                            <code>{text.technical}</code>
                          </details>
                        )}
                      </td>
                      <td data-label="Colaborador">
                        <span className="person">
                          <Avatar name={event.actorName ?? 'Sistema'} size="sm" />
                          <span className="person-text">{event.actorName ?? 'Sistema'}</span>
                        </span>
                      </td>
                      <td data-label="Fecha" className="nowrap">
                        {formatDateTime(event.occurredAt)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            </div>
            <Pagination
              page={events.data.page}
              totalPages={events.data.totalPages}
              totalElements={events.data.totalElements}
              noun="eventos"
              label="Paginación de auditoría"
              onChange={(next) => setFilter('page', String(next))}
            />
          </>
        )}
      </ModuleSurface>
    </section>
  )
}
