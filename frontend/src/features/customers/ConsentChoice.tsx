import { CHANNEL_LABELS, CONSENT_TEXT, CONSENT_TEXT_VERSION, type ContactChannel } from '../../shared/i18n/consent'

/**
 * C.1 at registration: the collaborator reads the text to the customer and ticks only the channels
 * the customer accepts. Nothing is ticked by default; an e-mail or phone alone is not consent.
 */
export function ConsentChoice({
  channels,
  hasEmail,
  disabled,
  onChange,
}: {
  channels: ContactChannel[]
  hasEmail: boolean
  disabled?: boolean
  onChange: (channels: ContactChannel[]) => void
}) {
  function toggle(channel: ContactChannel, checked: boolean) {
    onChange(checked ? [...channels.filter((value) => value !== channel), channel] : channels.filter((value) => value !== channel))
  }
  return (
    <fieldset className="field consent-choice">
      <legend>Avisos al cliente (opcional)</legend>
      <p className="muted small">
        Lee al cliente: «{CONSENT_TEXT}» <span className="mono">({CONSENT_TEXT_VERSION})</span>
      </p>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={channels.includes('EMAIL') && hasEmail}
          disabled={disabled || !hasEmail}
          onChange={(event) => toggle('EMAIL', event.target.checked)}
        />{' '}
        Acepta avisos por {CHANNEL_LABELS.EMAIL.toLowerCase()}
        {!hasEmail && <span className="muted small"> (registra primero su correo)</span>}
      </label>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={channels.includes('WHATSAPP')}
          disabled={disabled}
          onChange={(event) => toggle('WHATSAPP', event.target.checked)}
        />{' '}
        Acepta avisos por WhatsApp <span className="muted small">(se registra; el envío por WhatsApp aún no está disponible)</span>
      </label>
    </fieldset>
  )
}
