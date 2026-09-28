/**
 * Spanish labels of the home-service module. The API keeps English codes (contract and stored
 * history); only this module turns them into user-facing text.
 */

export type RequestStatus = 'PENDING' | 'UNDER_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'
export type VisitStatus = 'PROPOSED' | 'CONFIRMED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type VisitOutcome = 'RESOLVED_ON_SITE' | 'NEEDS_WORKSHOP' | 'NOT_RESOLVED'
export type Province = 'SAN_JOSE' | 'ALAJUELA' | 'CARTAGO' | 'HEREDIA' | 'GUANACASTE' | 'PUNTARENAS' | 'LIMON'
export type PreferredWindow = 'MORNING' | 'AFTERNOON' | 'ANY'
export type PublicStatus = 'RECEIVED' | 'IN_REVIEW' | 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'NOT_ACCEPTED' | 'CANCELLED'
export type ServiceEventType =
  | 'SUBMITTED'
  | 'REVIEW_STARTED'
  | 'CUSTOMER_LINKED'
  | 'BRANCH_CHANGED'
  | 'VISIT_PROPOSED'
  | 'VISIT_CONFIRMED'
  | 'VISIT_RESCHEDULED'
  | 'VISIT_STARTED'
  | 'VISIT_COMPLETED'
  | 'VISIT_CANCELLED'
  | 'REPAIR_ORDER_LINKED'
  | 'REJECTED'
  | 'CANCELLED'

type Tone = 'ok' | 'warn' | 'error' | 'info' | 'muted'

export const REQUEST_STATUS_LABELS: Record<RequestStatus, string> = {
  PENDING: 'Pendiente',
  UNDER_REVIEW: 'En revisión',
  ACCEPTED: 'Aceptada',
  REJECTED: 'Rechazada',
  CANCELLED: 'Cancelada',
}

export const REQUEST_STATUS_TONES: Record<RequestStatus, Tone> = {
  PENDING: 'warn',
  UNDER_REVIEW: 'info',
  ACCEPTED: 'ok',
  REJECTED: 'error',
  CANCELLED: 'muted',
}

export const VISIT_STATUS_LABELS: Record<VisitStatus, string> = {
  PROPOSED: 'Propuesta (sin confirmar)',
  CONFIRMED: 'Confirmada',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Completada',
  CANCELLED: 'Cancelada',
}

export const VISIT_STATUS_TONES: Record<VisitStatus, Tone> = {
  PROPOSED: 'warn',
  CONFIRMED: 'ok',
  IN_PROGRESS: 'info',
  COMPLETED: 'muted',
  CANCELLED: 'error',
}

export const OUTCOME_LABELS: Record<VisitOutcome, string> = {
  RESOLVED_ON_SITE: 'Resuelto en el domicilio',
  NEEDS_WORKSHOP: 'Requiere llevar el equipo al taller',
  NOT_RESOLVED: 'No se pudo resolver',
}

export const PROVINCE_LABELS: Record<Province, string> = {
  SAN_JOSE: 'San José',
  ALAJUELA: 'Alajuela',
  CARTAGO: 'Cartago',
  HEREDIA: 'Heredia',
  GUANACASTE: 'Guanacaste',
  PUNTARENAS: 'Puntarenas',
  LIMON: 'Limón',
}

export const WINDOW_LABELS: Record<PreferredWindow, string> = {
  MORNING: 'En la mañana',
  AFTERNOON: 'En la tarde',
  ANY: 'Cualquier horario',
}

/** What the customer reads on the public status page. */
export const PUBLIC_STATUS_LABELS: Record<PublicStatus, { title: string; detail: string }> = {
  RECEIVED: { title: 'Solicitud recibida', detail: 'Aún no está confirmada. Te contactaremos para coordinar la visita.' },
  IN_REVIEW: { title: 'En revisión', detail: 'Estamos revisando tu solicitud y la disponibilidad de nuestros técnicos.' },
  SCHEDULED: { title: 'Visita confirmada', detail: 'Tu visita quedó programada para la fecha indicada.' },
  IN_PROGRESS: { title: 'Visita en curso', detail: 'El técnico está atendiendo tu equipo.' },
  COMPLETED: { title: 'Visita realizada', detail: 'La visita se completó. Gracias por confiar en nosotros.' },
  NOT_ACCEPTED: {
    title: 'No pudimos atender la solicitud',
    detail: 'Por ahora no podemos atender esta solicitud. Puedes llamarnos para más información.',
  },
  CANCELLED: { title: 'Solicitud cancelada', detail: 'La solicitud fue cancelada.' },
}

export const EVENT_LABELS: Record<ServiceEventType, string> = {
  SUBMITTED: 'Solicitud recibida',
  REVIEW_STARTED: 'En revisión',
  CUSTOMER_LINKED: 'Cliente asociado',
  BRANCH_CHANGED: 'Sucursal cambiada',
  VISIT_PROPOSED: 'Visita propuesta',
  VISIT_CONFIRMED: 'Visita confirmada',
  VISIT_RESCHEDULED: 'Visita reprogramada',
  VISIT_STARTED: 'Visita iniciada',
  VISIT_COMPLETED: 'Visita completada',
  VISIT_CANCELLED: 'Visita cancelada',
  REPAIR_ORDER_LINKED: 'Vinculada a orden de taller',
  REJECTED: 'Solicitud rechazada',
  CANCELLED: 'Solicitud cancelada',
}

export const WEEKDAY_LABELS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const
