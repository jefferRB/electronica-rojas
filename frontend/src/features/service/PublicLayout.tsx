import type { ReactNode } from 'react'
import { Icon } from '../../shared/ui/Icon'

/** Customer-facing pages (no session): brand header and a centered content column. */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <main className="public-screen">
      <header className="public-top">
        <span className="brand">
          <span className="brand-mark" aria-hidden="true">
            <Icon name="cpu" />
          </span>
          <span className="brand-text">
            <span className="brand-name">Electrónica Rojas</span>
            <span className="brand-tagline">Reparación de electrodomésticos</span>
          </span>
        </span>
      </header>
      {children}
    </main>
  )
}
