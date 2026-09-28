import { useId } from 'react'
import { Icon } from '../../shared/ui/Icon'
import { useSelectedBranch } from './selectedBranch'

/** Working branch: changes the visual context only; every API call re-authorizes it (FR-NAV-004). */
export function BranchSelector() {
  const { branches, selected, select } = useSelectedBranch()
  const id = useId()

  if (branches.length === 0) {
    return <span className="branch-selector-empty">Sin sucursales asignadas</span>
  }

  return (
    <div className="branch-selector">
      <label htmlFor={id}>
        <Icon name="building" size={16} />
        <span>Sucursal</span>
      </label>
      <select id={id} value={selected?.id ?? ''} onChange={(event) => select(Number(event.target.value))}>
        {branches.map((branch) => (
          <option key={branch.id} value={branch.id}>
            {branch.code} · {branch.name}
          </option>
        ))}
      </select>
    </div>
  )
}
