import { Suspense } from 'react'
import { NavLink, Outlet } from 'react-router'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { LoadingState } from '../../shared/ui/States'
import { useSelectedBranch } from '../branches/selectedBranch'

/**
 * Inventory area: stock, history and catalog sections, scoped to the branch in the header selector.
 * The module is one surface; each section renders its bands (heading, filters, table) inside it.
 */
export function InventoryLayout() {
  const { selected } = useSelectedBranch()

  return (
    <section className="page">
      <ModuleSurface
        eyebrow="Inventario"
        icon="box"
        title="Inventario"
        description="Existencias físicas por sucursal, alertas de mínimo y el historial inmutable de movimientos."
        meta={
          selected && (
            <span className="chip context-chip">
              <Icon name="building" />
              Sucursal {selected.code} · {selected.name}
            </span>
          )
        }
      >
        <nav className="tabs tabs-line section-tabs" aria-label="Secciones de inventario">
          <NavLink to="/inventory" end>
            <Icon name="box" />
            Sucursal seleccionada
          </NavLink>
          <NavLink to="/inventory/overview">
            <Icon name="building" />
            Todas las sucursales
          </NavLink>
          <NavLink to="/inventory/movements">
            <Icon name="history" />
            Movimientos
          </NavLink>
          <NavLink to="/inventory/catalog">
            <Icon name="tag" />
            Catálogo
          </NavLink>
        </nav>
        {!selected && <Alert tone="info">No tienes sucursales asignadas. Solicita acceso a un administrador.</Alert>}
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </ModuleSurface>
    </section>
  )
}
