import { describe, expect, it } from 'vitest'
import { describeAuditEvent } from '../../shared/i18n/audit'
import { CONSENT_TEXT_VERSION, describeNotificationState } from '../../shared/i18n/consent'
import { describeConflict } from '../../shared/i18n/errors'
import { withConsent } from './customersApi'

describe('withConsent (C.1)', () => {
  const customer = { fullName: 'Lucía Ficticia', phone: '8800-0001', email: 'lucia@ejemplo.test' }

  it('adds nothing when no channel was accepted: contact data is never consent', () => {
    expect(withConsent(customer, [])).toEqual(customer)
  })

  it('records the accepted channels with the current text version', () => {
    expect(withConsent(customer, ['EMAIL', 'WHATSAPP']).consent).toEqual({
      channels: ['EMAIL', 'WHATSAPP'],
      source: 'IN_PERSON',
      textVersion: CONSENT_TEXT_VERSION,
    })
  })

  it('drops e-mail consent when there is no address to send to', () => {
    expect(withConsent({ ...customer, email: undefined }, ['EMAIL'])).not.toHaveProperty('consent')
    expect(withConsent({ ...customer, email: '  ' }, ['EMAIL', 'WHATSAPP']).consent?.channels).toEqual(['WHATSAPP'])
  })
})

describe('notification texts', () => {
  it('explains why a notice was not sent', () => {
    expect(describeNotificationState('SENT', null)).toBe('Enviado')
    expect(describeNotificationState('SKIPPED', 'NO_CONSENT')).toBe('No enviado: el cliente no aceptó avisos por este canal')
    expect(describeNotificationState('SKIPPED', 'CONSENT_WITHDRAWN')).toBe('No enviado: el cliente retiró su consentimiento')
  })

  it('describes consent audit events without personal data and translates new conflicts', () => {
    const event = describeAuditEvent({
      action: 'CUSTOMER_CONSENT_RECORDED',
      summary: 'Customer 4 withdrew EMAIL consent',
      detailsSource: 'RECORDED',
      details: { channel: 'EMAIL', granted: false, source: 'PHONE' },
    })
    expect(event.description).toBe('Un cliente retiró los avisos por correo electrónico (por teléfono).')
    expect(describeConflict('EMAIL_REQUIRED')).toMatch(/correo del cliente/)
    expect(describeConflict('NOTIFICATION_NOT_RETRYABLE')).toMatch(/fallaron/)
  })
})
