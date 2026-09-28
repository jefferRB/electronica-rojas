import { useEffect, useId, useRef, useState } from 'react'
import { ROLE_LABELS } from '../shared/i18n/labels'
import { Avatar } from '../shared/ui/Avatar'
import { Icon } from '../shared/ui/Icon'
import type { Session } from '../features/auth/authApi'
import { ThemePicker } from './ThemePicker'

interface UserMenuProps {
  user: Session['user']
  loggingOut: boolean
  onLogout: () => void
}

/**
 * Desktop account menu: who is signed in, the palette and "Cerrar sesión". Disclosure pattern like
 * "Más": aria-expanded/aria-controls, Escape returns the focus to the button, click outside closes.
 */
export function UserMenu({ user, loggingOut, onLogout }: UserMenuProps) {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    function onPointer(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="user-menu" ref={containerRef}>
      <button
        ref={buttonRef}
        type="button"
        className="user-button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
      >
        <Avatar name={user.fullName} />
        <span className="user-box">
          <span className="user-name truncate">{user.fullName}</span>
          <span className="user-role">{ROLE_LABELS[user.role]}</span>
        </span>
        <span className="visually-hidden">: menú de la cuenta</span>
        <Icon name="chevronDown" size={16} />
      </button>
      {open && (
        <div id={panelId} className="popover popover-right">
          <div className="user-summary">
            <Avatar name={user.fullName} />
            <span className="user-box">
              <span className="user-name">{user.fullName}</span>
              <span className="user-role">
                {ROLE_LABELS[user.role]} · {user.email}
              </span>
            </span>
          </div>
          <div className="menu-divider" />
          <ThemePicker />
          <div className="menu-divider" />
          <button type="button" className="menu-item" disabled={loggingOut} onClick={onLogout}>
            <Icon name="logout" />
            {loggingOut ? 'Saliendo…' : 'Cerrar sesión'}
          </button>
        </div>
      )}
    </div>
  )
}
