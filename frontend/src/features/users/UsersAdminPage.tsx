import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { SelectField, TextField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { PasswordField } from '../../shared/ui/PasswordField'
import { useDisclosure } from '../../shared/ui/useDisclosure'
import { ROLE_LABELS } from '../../shared/i18n/labels'
import { ROLES, type Role } from '../auth/authApi'
import { branchKeys, fetchBranches, type Branch } from '../branches/branchesApi'
import { BranchCheckboxes } from './BranchCheckboxes'
import { createUser, fetchUsers, resetPassword, updateUser, userKeys, type User } from './usersApi'

/** FR-AUTH-004 / FR-BRH-002: accounts, roles and branch assignments. No public sign-up. */
export function UsersAdminPage() {
  const [page, setPage] = useState(0)
  const [editingId, setEditingId] = useState<number | null>(null)
  const users = useQuery({
    queryKey: userKeys.page(page),
    queryFn: ({ signal }) => fetchUsers(page, signal),
    placeholderData: keepPreviousData,
  })
  const branches = useQuery({ queryKey: branchKeys.all, queryFn: ({ signal }) => fetchBranches(signal) })
  const branchList = branches.data ?? []
  // Always edit the freshest copy from the list, so the version is current after each save.
  const editing = users.data?.content.find((user) => user.id === editingId) ?? null
  const create = useDisclosure()
  const [created, setCreated] = useState<string | null>(null)

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Administración"
        icon="userCog"
        title="Usuarios"
        description="Colaboradores internos, su rol y las sucursales que pueden operar."
        actions={
          <>
            {/* One form at a time: an open edit is never lost by starting a new account. */}
            <button
              type="button"
              className="button button-primary"
              {...create.triggerProps}
              disabled={editingId !== null}
              title={editingId !== null ? 'Termina o cancela la edición en curso' : undefined}
              onClick={() => {
                setCreated(null)
                create.triggerProps.onClick()
              }}
            >
              <Icon name="plus" />
              Agregar usuario
            </button>
          </>
        }
      >
        {created && <Alert tone="success">Usuario {created} creado.</Alert>}
        {create.open && (
          <div {...create.panelProps}>
            <CreateUserForm
              branches={branchList}
              onCancel={create.hide}
              onCreated={(email) => {
                setCreated(email)
                create.hide()
              }}
            />
          </div>
        )}
        {editing && <EditUserPanel key={editing.id} user={editing} branches={branchList} onDone={() => setEditingId(null)} />}

        {users.isPending && <LoadingState label="Cargando usuarios…" />}
        {users.isError && <ErrorState error={users.error} onRetry={() => users.refetch()} />}
        {users.data && (
          <>
            <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Colaborador</th>
                  <th scope="col">Rol</th>
                  <th scope="col">Sucursales</th>
                  <th scope="col">Estado</th>
                  <th scope="col">
                    <span className="visually-hidden">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.data.content.map((user) => (
                  <tr key={user.id}>
                    <td className="cell-title">
                      <span className="person">
                        <Avatar name={user.fullName} />
                        <span className="person-text">
                          <span className="cell-primary">{user.fullName}</span>
                          <span className="cell-sub wrap-anywhere">{user.email}</span>
                        </span>
                      </span>
                    </td>
                    <td data-label="Rol">
                      <span className="chip">{ROLE_LABELS[user.role]}</span>
                    </td>
                    <td data-label="Sucursales">
                      {user.role === 'ADMIN' ? (
                        <span className="muted">Todas</span>
                      ) : user.branches.length === 0 ? (
                        '—'
                      ) : (
                        <span className="chip-list">
                          {user.branches.map((branch) => (
                            <span key={branch.id} className="chip">
                              {branch.code}
                            </span>
                          ))}
                        </span>
                      )}
                    </td>
                    <td data-label="Estado">
                      <span className={`badge ${user.active ? 'badge-ok' : 'badge-muted'}`}>
                        {user.active ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td className="actions">
                      <button
                        type="button"
                        className="button button-secondary"
                        disabled={create.open || editingId !== null}
                        title={create.open ? 'Cierra primero el formulario de alta' : undefined}
                        onClick={() => {
                          setCreated(null)
                          setEditingId(user.id)
                        }}
                      >
                        Editar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            <Pagination
              page={users.data.page}
              totalPages={users.data.totalPages}
              totalElements={users.data.totalElements}
              noun="usuarios"
              label="Paginación de usuarios"
              onChange={setPage}
            />
          </>
        )}
      </ModuleSurface>
    </section>
  )
}

function CreateUserForm({
  branches,
  onCancel,
  onCreated,
}: {
  branches: Branch[]
  onCancel: () => void
  onCreated: (email: string) => void
}) {
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<Role>('BRANCH_MANAGER')
  const [password, setPassword] = useState('')
  const [branchIds, setBranchIds] = useState<number[]>([])
  // On a validation error the values stay and the form stays open; on success it closes.
  const create = useMutation({
    mutationFn: createUser,
    onSuccess: async (user) => {
      await queryClient.invalidateQueries({ queryKey: userKeys.all })
      onCreated(user.email)
    },
  })
  const errors = fieldErrorsOf(create.error)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate({
      email: email.trim(),
      fullName: fullName.trim(),
      role,
      password,
      branchIds: role === 'ADMIN' ? [] : branchIds,
    })
  }

  return (
    <form className="card card-highlight" onSubmit={handleSubmit} noValidate>
      <h2>Nuevo usuario</h2>
      {create.isError && <Alert tone="error">{describeError(create.error)}</Alert>}
      <div className="form-grid">
        <TextField label="Nombre completo" value={fullName} maxLength={120} error={errors.fullName} onChange={(e) => setFullName(e.target.value)} />
        <TextField label="Correo electrónico" type="email" autoComplete="off" value={email} maxLength={254} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <SelectField label="Rol" value={role} error={errors.role} onChange={(e) => setRole(e.target.value as Role)}>
          {ROLES.map((value) => (
            <option key={value} value={value}>
              {ROLE_LABELS[value]}
            </option>
          ))}
        </SelectField>
        <PasswordField
          label="Contraseña inicial"
          autoComplete="new-password"
          showRequirements
          value={password}
          error={errors.password}
          onChange={setPassword}
        />
      </div>
      <BranchCheckboxes branches={branches} selected={branchIds} onChange={setBranchIds} error={errors.branchIds} disabled={role === 'ADMIN'} />
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={create.isPending || !email || !fullName || !password}>
          {create.isPending ? 'Guardando…' : 'Crear usuario'}
        </button>
        <button type="button" className="button button-secondary" disabled={create.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
      <p className="field-hint">Comparte la contraseña inicial por un canal seguro.</p>
    </form>
  )
}

function EditUserPanel({ user, branches, onDone }: { user: User; branches: Branch[]; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState(user.fullName)
  const [role, setRole] = useState<Role>(user.role)
  const [active, setActive] = useState(user.active)
  const [branchIds, setBranchIds] = useState<number[]>(user.branches.map((branch) => branch.id))
  const [newPassword, setNewPassword] = useState('')

  const update = useMutation({
    mutationFn: () =>
      updateUser(user.id, {
        fullName: fullName.trim(),
        role,
        active,
        branchIds: role === 'ADMIN' ? [] : branchIds,
        version: user.version,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: userKeys.all })
      onDone()
    },
  })
  const reset = useMutation({
    mutationFn: () => resetPassword(user.id, newPassword, user.version),
    onSuccess: async () => {
      setNewPassword('')
      await queryClient.invalidateQueries({ queryKey: userKeys.all })
    },
  })
  const errors = fieldErrorsOf(update.error)
  const resetErrors = fieldErrorsOf(reset.error)

  return (
    <div className="card card-highlight">
      <h2>Editar {user.email}</h2>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          update.mutate()
        }}
        noValidate
      >
        {update.isError && <Alert tone="error">{describeError(update.error)}</Alert>}
        <div className="form-grid">
          <TextField label="Nombre completo" value={fullName} maxLength={120} error={errors.fullName} onChange={(e) => setFullName(e.target.value)} />
          <SelectField label="Rol" value={role} error={errors.role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((value) => (
              <option key={value} value={value}>
                {ROLE_LABELS[value]}
              </option>
            ))}
          </SelectField>
          <label className="checkbox">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Cuenta activa
          </label>
        </div>
        <BranchCheckboxes branches={branches} selected={branchIds} onChange={setBranchIds} error={errors.branchIds} disabled={role === 'ADMIN'} />
        <p className="field-hint">Cambiar el rol o desactivar la cuenta cierra las sesiones abiertas de este usuario.</p>
        <div className="form-actions">
          <button type="submit" className="button button-primary" disabled={update.isPending}>
            {update.isPending ? 'Guardando…' : 'Guardar cambios'}
          </button>
          <button type="button" className="button button-secondary" onClick={onDone}>
            Cancelar
          </button>
        </div>
      </form>

      <form
        className="subsection"
        onSubmit={(event) => {
          event.preventDefault()
          reset.mutate()
        }}
        noValidate
      >
        <h3>Restablecer contraseña</h3>
        {reset.isError && <Alert tone="error">{describeError(reset.error)}</Alert>}
        {reset.isSuccess && <Alert tone="success">Contraseña restablecida. Las sesiones abiertas del usuario se cerraron.</Alert>}
        <div className="form-grid">
          <PasswordField
            label="Nueva contraseña"
            autoComplete="new-password"
            showRequirements
            value={newPassword}
            error={resetErrors.newPassword}
            onChange={setNewPassword}
          />
        </div>
        <div className="form-actions">
          <button type="submit" className="button button-secondary" disabled={reset.isPending || !newPassword}>
            Restablecer
          </button>
        </div>
      </form>
    </div>
  )
}
