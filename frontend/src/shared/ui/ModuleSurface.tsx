import type { ReactNode } from 'react'
import { PageHeader, type PageHeaderProps } from './PageHeader'

interface ModuleSurfaceProps extends PageHeaderProps {
  /** Full-bleed bands in reading order: navigation, filters, content, pagination (docs/design-system.md §5). */
  children?: ReactNode
  className?: string
}

/**
 * The single surface a module lives in: its header (label, title, description, actions and context)
 * is the first band, and navigation, filters, content and pagination follow as bands separated by
 * hairlines. Operation forms opened from an action unfold inside it, never as a second card.
 */
export function ModuleSurface({ children, className, ...header }: ModuleSurfaceProps) {
  return (
    <div className={className ? `card panel module-surface ${className}` : 'card panel module-surface'}>
      <PageHeader {...header} />
      {children}
    </div>
  )
}
