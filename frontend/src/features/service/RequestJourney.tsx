import { REQUEST_STATUS_LABELS } from '../../shared/i18n/serviceLabels'
import { Icon } from '../../shared/ui/Icon'
import { journeySteps } from './requestSteps'
import type { ServiceRequestDetail } from './serviceApi'

export function RequestJourney({ request }: { request: ServiceRequestDetail }) {
  const steps = journeySteps(request)
  const stopped = request.status === 'REJECTED' || request.status === 'CANCELLED'
  const current = stopped ? -1 : steps.findIndex((step) => !step.done)
  return (
    <ol className="progress" aria-label="Avance de la solicitud">
      {steps.map((step, index) => {
        const state = index === current ? 'current' : step.done ? 'done' : 'todo'
        return (
          <li key={step.label} className={`progress-step progress-${state}`} aria-current={index === current ? 'step' : undefined}>
            <span className="progress-dot" aria-hidden="true">
              {state === 'done' ? <Icon name="check" size={14} /> : index + 1}
            </span>
            <span className="progress-label">
              {step.label}
              <span className="visually-hidden">{state === 'current' ? ' (siguiente paso)' : state === 'done' ? ' (completado)' : ' (pendiente)'}</span>
            </span>
          </li>
        )
      })}
      {stopped && (
        <li className="progress-step progress-stopped">
          <span className="progress-dot" aria-hidden="true">
            <Icon name="close" size={14} />
          </span>
          <span className="progress-label">{REQUEST_STATUS_LABELS[request.status]}</span>
        </li>
      )}
    </ol>
  )
}
