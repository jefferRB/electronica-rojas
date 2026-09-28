import { Suspense, useCallback, useRef, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { describeError } from '../shared/api/describeError'
import { Alert } from '../shared/ui/Alert'
import { Icon } from '../shared/ui/Icon'
import { LoadingState } from '../shared/ui/States'
import { useLogout, useSession } from '../features/auth/session'
import { BranchSelector } from '../features/branches/BranchSelector'
import { SelectedBranchProvider } from '../features/branches/SelectedBranchProvider'
import { BottomNav, MobileDrawer } from './MobileNav'
import { MoreMenu } from './MoreMenu'
import { bottomNav, primaryNav, secondaryNav } from './navigation'
import { UserMenu } from './UserMenu'

/**
 * Authenticated layout (FR-NAV-001): a floating dark bar with the brand, the daily modules, "Más",
 * the branch selector and the account menu. Below 1200 px the modules move into a drawer ("Menú");
 * on phones the branch selector sits under the bar and a bottom bar keeps the daily modules within
 * thumb reach. Only what the role can use is listed (hiding is UX only; the API authorizes every call).
 */
export function AppLayout() {
  const { data: session } = useSession()
  const logout = useLogout()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const openerRef = useRef<HTMLButtonElement | null>(null)
  const closeDrawer = useCallback(() => setDrawerOpen(false), [])

  if (!session) return null
  const role = session.user.role
  const primary = primaryNav(role)
  const secondary = secondaryNav(role)

  function openDrawer(button: HTMLButtonElement) {
    openerRef.current = button
    setDrawerOpen(true)
  }

  function signOut() {
    logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })
  }

  return (
    <SelectedBranchProvider userId={session.user.id} branches={session.branches}>
      <div className="app-shell">
        <a className="skip-link" href="#main">
          Saltar al contenido
        </a>
        <header className="app-header">
          <div className="app-bar">
            <NavLink to="/" className="brand" aria-label="Electrónica Rojas, inicio">
              <span className="brand-mark" aria-hidden="true">
                <Icon name="cpu" />
              </span>
              <span className="brand-text">
                <span className="brand-name">Electrónica Rojas</span>
                <span className="brand-tagline">Servicio técnico</span>
              </span>
            </NavLink>

            <nav className="main-nav" aria-label="Principal">
              {primary.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className="nav-link">
                  <Icon name={item.icon} />
                  {item.label}
                </NavLink>
              ))}
              <MoreMenu links={secondary} />
            </nav>

            <div className="app-bar-tools">
              <BranchSelector />
              <span className="bar-divider desktop-only" aria-hidden="true" />
              <div className="nav-user">
                <UserMenu user={session.user} loggingOut={logout.isPending} onLogout={signOut} />
              </div>
              <button
                type="button"
                className="menu-button"
                aria-expanded={drawerOpen}
                aria-haspopup="dialog"
                onClick={(event) => openDrawer(event.currentTarget)}
              >
                <Icon name="menu" />
                Menú
              </button>
            </div>
          </div>
          <div className="context-strip">
            <BranchSelector />
          </div>
        </header>

        <main id="main" className="app-main" tabIndex={-1}>
          {logout.isError && <Alert tone="error">{describeError(logout.error)}</Alert>}
          <Suspense fallback={<LoadingState label="Cargando pantalla…" />}>
            <Outlet />
          </Suspense>
        </main>

        <BottomNav items={bottomNav(role)} menuOpen={drawerOpen} onMenu={openDrawer} />
        <MobileDrawer
          open={drawerOpen}
          onClose={closeDrawer}
          returnFocusTo={openerRef}
          user={session.user}
          primary={primary}
          secondary={secondary}
          loggingOut={logout.isPending}
          onLogout={signOut}
        />
      </div>
    </SelectedBranchProvider>
  )
}
