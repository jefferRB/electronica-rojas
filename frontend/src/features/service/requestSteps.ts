import { crDate, crTime, longDate } from './crTime'
import type { ServiceRequestDetail, Visit } from './serviceApi'

export interface JourneyStep {
  label: string
  done: boolean
}

/** Pure: which stages a request has passed (request and visit are separate records, BR-SRV-002). */
export function journeySteps(request: ServiceRequestDetail): JourneyStep[] {
  const visits = request.visits
  const live = visits.filter((visit) => visit.status !== 'CANCELLED')
  return [
    { label: 'Recibida', done: true },
    { label: 'En revisión', done: request.status !== 'PENDING' },
    { label: 'Cliente asociado', done: request.customer !== null },
    { label: 'Visita propuesta', done: live.length > 0 },
    { label: 'Visita confirmada', done: live.some((visit) => visit.status !== 'PROPOSED') },
    { label: 'Realizada', done: visits.some((visit) => visit.status === 'COMPLETED') },
  ]
}

/** What the collaborator should do next, in plain words; null when nothing is pending. */
export function nextStep(request: ServiceRequestDetail, activeVisit: Visit | undefined): { title: string; text: string } | null {
  if (request.status === 'REJECTED' || request.status === 'CANCELLED') return null
  if (request.status === 'PENDING') {
    return { title: 'Solicitud nueva.', text: 'Revísala: confirma que la zona se atiende y asocia la solicitud a un cliente.' }
  }
  if (!request.customer) {
    return { title: 'Falta asociar el cliente.', text: 'Busca por nombre o teléfono; sin cliente asociado no se puede programar la visita.' }
  }
  if (!activeVisit) {
    if (request.visits.some((visit) => visit.status === 'COMPLETED')) return null
    return { title: 'Lista para programar.', text: 'Elige técnico, día y hora dentro de su jornada.' }
  }
  const when = `${longDate(crDate(activeVisit.start))}, ${crTime(activeVisit.start)}`
  if (activeVisit.status === 'PROPOSED') {
    return {
      title: 'Visita propuesta, sin confirmar.',
      text: `Llama al cliente para acordar el ${when}. La hora del técnico no queda reservada hasta confirmar.`,
    }
  }
  if (activeVisit.status === 'CONFIRMED') {
    return { title: 'Visita confirmada.', text: `${activeVisit.technician.fullName} atenderá el ${when}. Está en su agenda.` }
  }
  return { title: 'Visita en curso.', text: `${activeVisit.technician.fullName} está atendiendo el servicio.` }
}

