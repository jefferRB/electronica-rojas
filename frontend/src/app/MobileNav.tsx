import { useEffect, useId, useRef, type RefObject } from 'react'
import { NavLink, useLocation } from 'react-router'
import { ROLE_LABELS } from '../shared/i18n/labels'
import { Avatar } from '../shared/ui/Avatar'
import { Icon } from '../shared/ui/Icon'
import type { Session } from '../features/auth/authApi'
import { BranchSelector } from '../features/branches/BranchSelector'
import type { NavItem } from './navigation'
import { ThemePicker } from './ThemePicker'

interface DrawerProps {
  open: boolean
  onClose: () => void
  /** The button that opened the drawer: it gets the focus back on close. */
  returnFocusTo: RefObject<HTMLButtonElement | null>
  user: Session['user']
  primary: NavItem[]
  secondary: NavItem[]
  loggingOut: boolean
  onLogout: () => void
}

const FOCUSABLE = 'a[href], button:not([disabled]), select:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Navigation drawer for tablets and phones: a modal dialog with the modules grouped by use, the branch
 * selector, the palette and "Cerrar sesión". While open the page behind cannot scroll, Tab stays
 * inside the dialog, Escape or the labelled close button closes it, and the focus returns to "Menú".
 */
export function MobileDrawer({ open, onClose, returnFocusTo, user, primary, secondary, loggingOut, onLogout }: DrawerProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const titleId = useId()
  const location = useLocation()

  // Choosing a destination closes the drawer.
  const lastPath = useRef(location.pathname)
  useEffect(() => {
    if (location.pathname !== lastPath.current) {
      lastPath.current = location.pathname
      if (open) onClose()
    }
  }, [location.pathname, open, onClose])

  useEffect(() => {
    if (!open) return
    const opener = returnFocusTo.current
    document.body.classList.add('scroll-locked')
    closeRef.current?.focus()

    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const items = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE))
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.classList.remove('scroll-locked')
      opener?.focus()
    }
  }, [open, onClose, returnFocusTo])

  if (!open) return null

  const link = (item: NavItem) => (
    <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => (isActive ? 'menu-item active' : 'menu-item')}>
      <Icon name={item.icon} />
      {item.label}
    </NavLink>
  )

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} aria-hidden="true" />
      <div ref={dialogRef} className="drawer" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <div className="drawer-header">
          <h2 id={titleId} className="drawer-title">
            Menú
          </h2>
          <button ref={closeRef} type="button" className="button button-ghost" onClick={onClose}>
            <Icon name="close" />
            Cerrar menú
          </button>
        </div>
        <div className="drawer-body">
          <div className="user-summary">
            <Avatar name={user.fullName} />
            <span className="user-box">
              <span className="user-name">{user.fullName}</span>
              <span className="user-role">{ROLE_LABELS[user.role]}</span>
            </span>
          </div>
          <BranchSelector />
          <nav className="drawer-section" aria-label="Operación">
            <p className="menu-heading">Operación</p>
            {primary.map(link)}
          </nav>
          {secondary.length > 0 && (
            <nav className="drawer-section" aria-label="Administración y control">
              <p className="menu-heading">Administración y control</p>
              {secondary.map(link)}
            </nav>
          )}
          <div className="drawer-section">
            <ThemePicker />
          </div>
          <div className="drawer-section">
            <button type="button" className="menu-item" disabled={loggingOut} onClick={onLogout}>
              <Icon name="logout" />
              {loggingOut ? 'Saliendo…' : 'Cerrar sesión'}
            </button>
          </div>
        </div>
      </div>
    </>
  )
}

/** Accessible name that always contains the visible (short) text (WCAG 2.5.3, label in name). */
function bottomLabel(item: NavItem): string {
  const visible = item.short ?? item.label
  return item.label.toLowerCase().includes(visible.toLowerCase()) ? item.label : `${visible}: ${item.label}`
}

/** Phone bottom bar: the daily modules within thumb reach, plus "Menú" for everything else. */
export function BottomNav({
  items,
  menuOpen,
  onMenu,
}: {
  items: NavItem[]
  menuOpen: boolean
  onMenu: (button: HTMLButtonElement) => void
}) {
  return (
    <nav className="bottom-nav" aria-label="Accesos principales">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end} aria-label={bottomLabel(item)}>
          <Icon name={item.icon} />
          <span>{item.short ?? item.label}</span>
        </NavLink>
      ))}
      <button type="button" aria-expanded={menuOpen} aria-haspopup="dialog" onClick={(event) => onMenu(event.currentTarget)}>
        <Icon name="menu" />
        <span>Menú</span>
      </button>
    </nav>
  )
}
