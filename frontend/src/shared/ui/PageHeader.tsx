import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Icon, type IconName } from './Icon'

interface Crumb {
  to: string
  label: string
}

export interface PageHeaderProps {
  /** Small uppercase label that places the screen inside its module. */
  eyebrow?: string
  icon?: IconName
  title: ReactNode
  description?: ReactNode
  /** Main action(s) of the screen, aligned right on wide screens. */
  actions?: ReactNode
  /** Parent screens, shown above the title on detail pages. */
  breadcrumb?: Crumb[]
  /** Status badges or key facts below the title. */
  meta?: ReactNode
  titleClassName?: string
}

/** Shared header of every module screen: context label, title, short description and main action. */
export function PageHeader({ eyebrow, icon, title, description, actions, breadcrumb, meta, titleClassName }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-header-main">
        {breadcrumb && breadcrumb.length > 0 && (
          <nav aria-label="Ruta de navegación">
            <ol className="breadcrumb">
              {breadcrumb.map((crumb) => (
                <li key={crumb.to} className="breadcrumb-item">
                  <Link to={crumb.to}>{crumb.label}</Link>
                  <Icon name="chevronRight" size={14} />
                </li>
              ))}
            </ol>
          </nav>
        )}
        {eyebrow && (
          <p className="eyebrow">
            {icon && <Icon name={icon} />}
            {eyebrow}
          </p>
        )}
        <h1 className={titleClassName}>{title}</h1>
        {description && <p className="page-lede">{description}</p>}
        {meta && <div className="page-header-meta">{meta}</div>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}
