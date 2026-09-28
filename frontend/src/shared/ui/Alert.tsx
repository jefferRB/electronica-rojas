import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

type Tone = 'error' | 'success' | 'info' | 'warn'

interface AlertProps {
  tone: Tone
  children: ReactNode
  /** Optional short heading (what happened) before the explanation of what to do. */
  title?: string
}

const ICONS: Record<Tone, IconName> = { error: 'alert', success: 'checkCircle', info: 'info', warn: 'alert' }

/** Status message: text carries the meaning, the icon and color only reinforce it (FR-NAV-002). */
export function Alert({ tone, children, title }: AlertProps) {
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon name={ICONS[tone]} size={20} />
      <div className="alert-body">
        {title && <strong>{title} </strong>}
        {children}
      </div>
    </div>
  )
}
