import type { Role } from '../features/auth/authApi'
import { permissions } from '../features/auth/permissions'
import type { IconName } from '../shared/ui/Icon'

export interface NavItem {
  to: string
  label: string
  icon: IconName
  /** Short label for the phone bottom bar. */
  short?: string
  /** Exact match (the home route). */
  end?: boolean
}

/**
 * Single source of the menu per role (FR-NAV-001, ER-FS-001 §23): the desktop bar, the drawer and the
 * phone bottom bar all read it. Hiding is UX only; the API authorizes every call.
 */
export function primaryNav(role: Role): NavItem[] {
  const items: (NavItem | false)[] = [
    { to: '/', label: 'Inicio', icon: 'home', end: true },
    permissions.readRepairs(role) && { to: '/repairs', label: 'Reparaciones', icon: 'wrench', short: 'Taller' },
    permissions.handleServiceRequests(role) && { to: '/service-requests', label: 'A domicilio', icon: 'van', short: 'Domicilio' },
    permissions.handleServiceRequests(role) && { to: '/agenda', label: 'Agenda', icon: 'calendar' },
    permissions.ownVisits(role) && { to: '/my-visits', label: 'Mis visitas', icon: 'calendar', short: 'Visitas' },
    permissions.manageCustomers(role) && { to: '/customers', label: 'Clientes', icon: 'users' },
    permissions.readInventory(role) && { to: '/inventory', label: 'Inventario', icon: 'box' },
  ]
  return items.filter((item): item is NavItem => item !== false)
}

/** Less frequent modules, grouped under "Más" (only those the role can open). */
export function secondaryNav(role: Role): NavItem[] {
  const items: (NavItem | false)[] = [
    permissions.transfer(role) && { to: '/transfers', label: 'Transferencias', icon: 'transfer' },
    permissions.readNotifications(role) && { to: '/notifications', label: 'Notificaciones', icon: 'bell' },
    permissions.readAudit(role) && { to: '/audit', label: 'Auditoría', icon: 'audit' },
    permissions.administer(role) && { to: '/admin/branches', label: 'Sucursales', icon: 'building' },
    permissions.administer(role) && { to: '/admin/users', label: 'Usuarios', icon: 'userCog' },
  ]
  return items.filter((item): item is NavItem => item !== false)
}

/** Phone bottom bar: the first four daily modules; everything else lives in the drawer. */
export function bottomNav(role: Role): NavItem[] {
  return primaryNav(role).slice(0, 4)
}
