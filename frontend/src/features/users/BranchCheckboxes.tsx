import type { Branch } from '../branches/branchesApi'

interface Props {
  branches: Branch[]
  selected: number[]
  onChange: (ids: number[]) => void
  error?: string
  disabled?: boolean
}

/** Branch assignment picker (UserBranch). Only active branches can be assigned. */
export function BranchCheckboxes({ branches, selected, onChange, error, disabled }: Props) {
  const active = branches.filter((branch) => branch.active)

  function toggle(id: number, checked: boolean) {
    onChange(checked ? [...selected, id] : selected.filter((value) => value !== id))
  }

  return (
    <fieldset className="field checkbox-group" disabled={disabled} aria-invalid={error ? true : undefined}>
      <legend>Sucursales autorizadas</legend>
      {disabled && <p className="field-hint">Los administradores acceden a todas las sucursales.</p>}
      {!disabled && active.length === 0 && <p className="field-hint">No hay sucursales activas. Crea una primero.</p>}
      {!disabled &&
        active.map((branch) => (
          <label key={branch.id} className="checkbox">
            <input
              type="checkbox"
              checked={selected.includes(branch.id)}
              onChange={(event) => toggle(branch.id, event.target.checked)}
            />
            {branch.code} · {branch.name}
          </label>
        ))}
      {error && <p className="field-error">{error}</p>}
    </fieldset>
  )
}
