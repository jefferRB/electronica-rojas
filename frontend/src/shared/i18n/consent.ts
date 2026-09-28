/**
 * Notification consent (BR-CUS-006) and notification states in Spanish. The text below is the one
 * customers accept; its version is stored with every consent. Changing the wording requires a new
 * version here AND in NotificationConsentText.CURRENT_VERSION on the server.
 */

export type ContactChannel = 'EMAIL' | 'WHATSAPP'
export type ConsentSource = 'IN_PERSON' | 'PHONE' | 'WRITTEN' | 'PUBLIC_FORM'
export type NotificationEventType = 'REPAIR_READY_FOR_PICKUP' | 'VISIT_CONFIRMED' | 'VISIT_RESCHEDULED' | 'VISIT_CANCELLED'
export type NotificationState = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED'
export type SkipReason = 'NO_CONSENT' | 'CONSENT_WITHDRAWN' | 'NO_ADDRESS'

export const CONSENT_TEXT_VERSION = 'AVISOS-2026-09'

export const CONSENT_TEXT =
  'Acepto recibir avisos operativos sobre mis reparaciones y visitas a domicilio (equipo listo para retirar; visita ' +
  'confirmada, reprogramada o cancelada) por el canal indicado. No incluyen publicidad. Puedo retirar este permiso ' +
  'en cualquier momento en la sucursal o por teléfono.'

export const CHANNEL_LABELS: Record<ContactChannel, string> = {
  EMAIL: 'Correo electrónico',
  WHATSAPP: 'WhatsApp',
}

export const CONSENT_SOURCE_LABELS: Record<ConsentSource, string> = {
  IN_PERSON: 'En la sucursal',
  PHONE: 'Por teléfono',
  WRITTEN: 'Por escrito (correo o mensaje del cliente)',
  PUBLIC_FORM: 'Formulario público',
}

export const NOTIFICATION_EVENT_LABELS: Record<NotificationEventType, string> = {
  REPAIR_READY_FOR_PICKUP: 'Equipo listo para retirar',
  VISIT_CONFIRMED: 'Visita confirmada',
  VISIT_RESCHEDULED: 'Visita reprogramada',
  VISIT_CANCELLED: 'Visita cancelada',
}

export const NOTIFICATION_STATE_LABELS: Record<NotificationState, string> = {
  PENDING: 'Pendiente de envío',
  SENDING: 'Enviándose',
  SENT: 'Enviado',
  FAILED: 'Falló',
  SKIPPED: 'No enviado',
}

export const NOTIFICATION_STATE_TONES: Record<NotificationState, 'ok' | 'warn' | 'error' | 'info' | 'muted'> = {
  PENDING: 'info',
  SENDING: 'info',
  SENT: 'ok',
  FAILED: 'error',
  SKIPPED: 'muted',
}

export const SKIP_REASON_LABELS: Record<SkipReason, string> = {
  NO_CONSENT: 'el cliente no aceptó avisos por este canal',
  CONSENT_WITHDRAWN: 'el cliente retiró su consentimiento',
  NO_ADDRESS: 'el cliente no tiene dirección para este canal',
}

/** "Enviado", "No enviado: el cliente no aceptó avisos por este canal". */
export function describeNotificationState(state: NotificationState, reason: SkipReason | null): string {
  const label = NOTIFICATION_STATE_LABELS[state]
  return state === 'SKIPPED' && reason ? `${label}: ${SKIP_REASON_LABELS[reason]}` : label
}
