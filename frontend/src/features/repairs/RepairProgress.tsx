import { REPAIR_STATUS_LABELS, type RepairStatus } from '../../shared/i18n/labels'
import { Icon } from '../../shared/ui/Icon'
import type { StatusChange } from './repairsApi'

/** The usual path of a repair; the quote stages share one step. */
const STEPS: { label: string; statuses: RepairStatus[] }[] = [
  { label: 'Recibida', statuses: ['RECEIVED'] },
  { label: 'Diagnóstico', statuses: ['DIAGNOSING'] },
  { label: 'Cotización', statuses: ['AWAITING_APPROVAL', 'APPROVED'] },
  { label: 'Reparación', statuses: ['IN_REPAIR'] },
  { label: 'Lista', statuses: ['READY_FOR_PICKUP'] },
  { label: 'Entregada', statuses: ['DELIVERED'] },
]

/**
 * Where the order is in the workshop cycle, read from its immutable history: completed steps, the
 * current one and the ones never reached (a quote is optional). Cancelled or unrepairable orders show
 * the step where they stopped. The status badge above remains the authoritative text.
 */
export function RepairProgress({ status, history }: { status: RepairStatus; history: StatusChange[] }) {
  const reached = new Set<RepairStatus>(history.map((change) => change.toStatus))
  reached.add('RECEIVED')
  const stopped = status === 'CANCELLED' || status === 'UNREPAIRABLE' || history.some((change) => change.toStatus === 'CANCELLED' || change.toStatus === 'UNREPAIRABLE')
  const currentIndex = STEPS.findIndex((step) => step.statuses.includes(status))

  return (
    <ol className="progress" aria-label="Avance de la orden">
      {STEPS.map((step, index) => {
        const done = step.statuses.some((value) => reached.has(value))
        const current = index === currentIndex
        const state = current ? 'current' : done ? 'done' : 'todo'
        return (
          <li key={step.label} className={`progress-step progress-${state}`} aria-current={current ? 'step' : undefined}>
            <span className="progress-dot" aria-hidden="true">
              {state === 'done' ? <Icon name="check" size={14} /> : index + 1}
            </span>
            <span className="progress-label">
              {step.label}
              <span className="visually-hidden">{current ? ' (etapa actual)' : done ? ' (completada)' : ' (pendiente)'}</span>
            </span>
          </li>
        )
      })}
      {stopped && (
        <li className="progress-step progress-stopped">
          <span className="progress-dot" aria-hidden="true">
            <Icon name="close" size={14} />
          </span>
          <span className="progress-label">
            {REPAIR_STATUS_LABELS[history.some((change) => change.toStatus === 'UNREPAIRABLE') ? 'UNREPAIRABLE' : 'CANCELLED']}
          </span>
        </li>
      )}
    </ol>
  )
}
