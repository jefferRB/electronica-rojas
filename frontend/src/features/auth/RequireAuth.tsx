import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { describeError } from '../../shared/api/describeError'
import { Alert } from '../../shared/ui/Alert'
import { EmptyState, LoadingState } from '../../shared/ui/States'
import type { Role } from './authApi'
import { permissions } from './permissions'
import { useSession } from './session'

/**
 * Route guard for the authenticated area. This only improves UX: every API call is still
 * authorized by the backend (SEC-003, ARCH-FE-001).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const session = useSession()

  if (session.isPending) return <LoadingState label="Cargando sesión…" />
  if (session.isError) {
    return (
      <main className="centered">
        <Alert tone="error">{describeError(session.error)}</Alert>
      </main>
    )
  }
  if (!session.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

/** Shows a 403 view instead of the page when the role is not allowed (FR-AUTH-003). */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const session = useSession()
  if (session.data?.user.role !== role) {
    return (
      <AccessDenied />
    )
  }
  return children
}

/** Like RequireRole, for capabilities shared by several roles (see permissions.ts). */
export function RequirePermission({ allowed, children }: { allowed: keyof typeof permissions; children: ReactNode }) {
  const session = useSession()
  if (!session.data || !permissions[allowed](session.data.user.role)) {
    return (
      <AccessDenied />
    )
  }
  return children
}

/** 403 view (FR-AUTH-003): says why and offers a way back; no data of the section is loaded. */
function AccessDenied() {
  return (
    <section className="page">
      <div className="card">
        <EmptyState
          icon="shield"
          title="Acceso denegado"
          action={
            <Link className="button button-primary" to="/">
              Volver al inicio
            </Link>
          }
        >
          Tu rol no permite abrir esta sección. Si la necesitas para tu trabajo, pide a un administrador que revise tus permisos.
        </EmptyState>
      </div>
    </section>
  )
}
