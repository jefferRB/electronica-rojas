import { useEffect, useId, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { Icon, type IconName } from '../shared/ui/Icon'

export interface MenuLink {
  to: string
  label: string
  icon?: IconName
}

/**
 * D.3: secondary modules behind one "Más" button so the main bar keeps the daily ones in one line.
 * Disclosure pattern: the button exposes aria-expanded/aria-controls; Escape closes and returns the
 * focus to the button; a click outside or choosing a link closes it. The button is highlighted when the
 * current page is one of its links, so the user always sees where they are. The popover is rendered
 * above the page content (z-index layer "dropdown"), never behind cards.
 */
export function MoreMenu({ links }: { links: MenuLink[] }) {
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const location = useLocation()

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

  if (links.length === 0) return null
  const current = links.some((link) => location.pathname.startsWith(link.to))

  return (
    <div className="nav-more" ref={containerRef}>
      <button
        ref={buttonRef}
        type="button"
        className={current ? 'active' : undefined}
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((value) => !value)}
      >
        <Icon name="more" />
        Más
        <Icon name="chevronDown" size={16} className="chevron" />
      </button>
      {open && (
        <ul id={menuId} className="popover nav-more-menu">
          {links.map((link) => (
            <li key={link.to}>
              <NavLink
                to={link.to}
                className={({ isActive }) => (isActive ? 'menu-item active' : 'menu-item')}
                onClick={() => setOpen(false)}
              >
                {link.icon && <Icon name={link.icon} />}
                {link.label}
              </NavLink>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
