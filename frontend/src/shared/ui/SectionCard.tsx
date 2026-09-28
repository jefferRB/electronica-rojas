import type { HTMLAttributes, ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

interface SectionCardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  title: ReactNode
  subtitle?: ReactNode
  icon?: IconName
  iconTone?: 'primary' | 'accent' | 'warn' | 'muted'
  actions?: ReactNode
  /** Heading level inside the page (h2 by default). */
  level?: 2 | 3
  children?: ReactNode
}

/** Card with a consistent header (icon, title, subtitle, actions): the unit that groups each section. */
export function SectionCard({
  title,
  subtitle,
  icon,
  iconTone = 'primary',
  actions,
  level = 2,
  children,
  className,
  ...rest
}: SectionCardProps) {
  const Heading = level === 2 ? 'h2' : 'h3'
  return (
    <section className={className ? `card ${className}` : 'card'} {...rest}>
      <div className="card-header">
        <div className="card-heading">
          {icon && (
            <span className={`card-heading-icon tone-${iconTone}`} aria-hidden="true">
              <Icon name={icon} size={18} />
            </span>
          )}
          <div>
            <Heading>{title}</Heading>
            {subtitle && <p className="card-subtitle">{subtitle}</p>}
          </div>
        </div>
        {actions && <div className="row-actions">{actions}</div>}
      </div>
      {children}
    </section>
  )
}
