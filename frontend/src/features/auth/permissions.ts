import type { Role } from './authApi'

/**
 * What the UI offers per role (ER-FS-001 section 13). This only hides options: the backend
 * re-checks every call (SEC-003), so these helpers are never a security boundary.
 */
export const permissions = {
  readInventory: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST',
  operateInventory: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  transfer: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  manageCatalog: (role: Role) => role === 'ADMIN',
  /** Unit costs are internal (PricingPolicy); the API leaves them out for everyone else. */
  viewCosts: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  /** The public home-service portal belongs to the whole company, like the catalog. */
  managePortal: (role: Role) => role === 'ADMIN',
  readAudit: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  administer: (role: Role) => role === 'ADMIN',
  /** Customers and counter work (reception, delivery, quote decisions); technicians only see names. */
  manageCustomers: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST',
  receiveRepairs: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST',
  /** Every role works with repairs; the API limits a technician to their assigned orders. */
  /** Home-service inbox and scheduling (counter work). */
  handleServiceRequests: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST',
  /** Technicians' working hours and branch visit defaults. */
  manageSchedules: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  /** The technician's own list of visits. */
  ownVisits: (role: Role) => role === 'TECHNICIAN',
  /** Visit detail: the API limits a technician to their own visits. */
  readVisits: (role: Role) =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST' || role === 'TECHNICIAN',
  /** Customer notices: states, failures and manual retries (ADMIN all, managers their branches). */
  readNotifications: (role: Role) => role === 'ADMIN' || role === 'BRANCH_MANAGER',
  readRepairs: (role: Role) =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'RECEPTIONIST' || role === 'TECHNICIAN',
}
