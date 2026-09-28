import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { TextField } from '../../shared/ui/Field'
import { useDisclosure } from '../../shared/ui/useDisclosure'
import { SESSION_QUERY_KEY } from '../auth/session'
import { branchKeys, createBranch, fetchBranches, updateBranch, type Branch } from './branchesApi'

/** FR-BRH-001: any number of branches; deactivation instead of deletion (BR-BRH-001). */
export function BranchesAdminPage() {
  const branches = useQuery({ queryKey: branchKeys.all, queryFn: ({ signal }) => fetchBranches(signal) })
  const [editing, setEditing] = useState<number | null>(null)
  const create = useDisclosure()
  const [created, setCreated] = useState<string | null>(null)

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Administración"
        icon="building"
        title="Sucursales"
        description="Crea, edita y desactiva sucursales. Una sucursal desactivada conserva su historial."
        actions={
          <>
            {/* Only one form at a time, so an open edit is never lost by starting another operation. */}
            <button
              type="button"
              className="button button-primary"
              {...create.triggerProps}
              disabled={editing !== null}
              title={editing !== null ? 'Termina o cancela la edición en curso' : undefined}
              onClick={() => {
                setCreated(null)
                create.triggerProps.onClick()
              }}
            >
              <Icon name="plus" />
              Agregar sucursal
            </button>
          </>
        }
      >
        {created && <Alert tone="success">Sucursal {created} creada.</Alert>}
        {create.open && (
          <div {...create.panelProps}>
            <CreateBranchForm
              onCancel={create.hide}
              onCreated={(code) => {
                setCreated(code)
                create.hide()
              }}
            />
          </div>
        )}

        {branches.isPending && <LoadingState label="Cargando sucursales…" />}
        {branches.isError && <ErrorState error={branches.error} onRetry={() => branches.refetch()} />}
        {branches.data?.length === 0 && (
          <EmptyState icon="building" title="Todavía no hay sucursales">
            Usa «Agregar sucursal» para registrar la primera; los colaboradores se asignan después en Usuarios.
          </EmptyState>
        )}
        {branches.data && branches.data.length > 0 && (
          <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Sucursal</th>
                <th scope="col">Dirección</th>
                <th scope="col">Estado</th>
                <th scope="col">
                  <span className="visually-hidden">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {branches.data.map((branch) =>
                editing === branch.id ? (
                  <EditBranchRow key={branch.id} branch={branch} onDone={() => setEditing(null)} />
                ) : (
                  <tr key={branch.id}>
                    <td className="cell-title">
                      <span className="product-cell">
                        <span className="cell-primary">{branch.name}</span>
                        <span className="mono cell-sub">{branch.code}</span>
                      </span>
                    </td>
                    <td data-label="Dirección">{branch.address ?? '—'}</td>
                    <td data-label="Estado">
                      <span className={`badge ${branch.active ? 'badge-ok' : 'badge-muted'}`}>
                        {branch.active ? 'Activa' : 'Inactiva'}
                      </span>
                    </td>
                    <td className="actions">
                      <button
                        type="button"
                        className="button button-secondary"
                        disabled={create.open || editing !== null}
                        title={create.open ? 'Cierra primero el formulario de alta' : undefined}
                        onClick={() => setEditing(branch.id)}
                      >
                        Editar
                      </button>
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
          </div>
        )}
      </ModuleSurface>
    </section>
  )
}

function useInvalidateBranches() {
  const queryClient = useQueryClient()
  return async () => {
    await queryClient.invalidateQueries({ queryKey: branchKeys.all })
    // The header selector reads the branches from the session.
    await queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY })
  }
}

function CreateBranchForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (code: string) => void }) {
  const invalidate = useInvalidateBranches()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  // On a validation error the values stay and the form stays open; on success it closes.
  const create = useMutation({
    mutationFn: createBranch,
    onSuccess: async (branch) => {
      await invalidate()
      onCreated(branch.code)
    },
  })
  const errors = fieldErrorsOf(create.error)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate({ code: code.trim(), name: name.trim(), address: address.trim() })
  }

  return (
    <form className="card card-highlight" onSubmit={handleSubmit} noValidate>
      <h2>Nueva sucursal</h2>
      {create.isError && <Alert tone="error">{describeError(create.error)}</Alert>}
      <div className="form-grid">
        <TextField
          label="Código"
          hint="2 a 20 letras, números o guiones. No se puede cambiar después."
          value={code}
          maxLength={20}
          required
          error={errors.code}
          onChange={(event) => setCode(event.target.value)}
        />
        <TextField
          label="Nombre"
          value={name}
          maxLength={120}
          required
          error={errors.name}
          onChange={(event) => setName(event.target.value)}
        />
        <TextField
          label="Dirección (opcional)"
          value={address}
          maxLength={300}
          error={errors.address}
          onChange={(event) => setAddress(event.target.value)}
        />
      </div>
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={create.isPending || !code || !name}>
          {create.isPending ? 'Guardando…' : 'Crear sucursal'}
        </button>
        <button type="button" className="button button-secondary" disabled={create.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

function EditBranchRow({ branch, onDone }: { branch: Branch; onDone: () => void }) {
  const invalidate = useInvalidateBranches()
  const [name, setName] = useState(branch.name)
  const [address, setAddress] = useState(branch.address ?? '')
  const [active, setActive] = useState(branch.active)
  const update = useMutation({
    mutationFn: () => updateBranch(branch.id, { name: name.trim(), address: address.trim(), active, version: branch.version }),
    onSuccess: async () => {
      await invalidate()
      onDone()
    },
  })
  const errors = fieldErrorsOf(update.error)

  return (
    <tr className="editing-row">
      <td colSpan={5}>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            update.mutate()
          }}
          noValidate
        >
          <p>
            Editando <strong>{branch.code}</strong>
          </p>
          {update.isError && <Alert tone="error">{describeError(update.error)}</Alert>}
          <div className="form-grid">
            <TextField label="Nombre" value={name} maxLength={120} error={errors.name} onChange={(e) => setName(e.target.value)} />
            <TextField
              label="Dirección"
              value={address}
              maxLength={300}
              error={errors.address}
              onChange={(e) => setAddress(e.target.value)}
            />
            <label className="checkbox">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Sucursal activa
            </label>
          </div>
          <div className="form-actions">
            <button type="submit" className="button button-primary" disabled={update.isPending || !name.trim()}>
              {update.isPending ? 'Guardando…' : 'Guardar'}
            </button>
            <button type="button" className="button button-secondary" onClick={onDone}>
              Cancelar
            </button>
          </div>
        </form>
      </td>
    </tr>
  )
}
