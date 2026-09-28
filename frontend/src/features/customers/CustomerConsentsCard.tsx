import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fragment, useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import {
  CHANNEL_LABELS,
  CONSENT_SOURCE_LABELS,
  CONSENT_TEXT,
  CONSENT_TEXT_VERSION,
  type ConsentSource,
  type ContactChannel,
} from '../../shared/i18n/consent'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { SectionCard } from '../../shared/ui/SectionCard'
import { LoadingState } from '../../shared/ui/States'
import { SelectField } from '../../shared/ui/Field'
import { customerKeys, fetchConsents, recordConsent, type ConsentStatement } from './customersApi'

type StaffSource = Exclude<ConsentSource, 'PUBLIC_FORM'>

function describeStatement(statement: ConsentStatement): string {
  const verb = statement.granted ? 'Aceptó' : 'Retiró'
  const where = CONSENT_SOURCE_LABELS[statement.source].toLowerCase()
  return `${verb} · ${where}${statement.reference ? ` (${statement.reference})` : ''}${statement.textVersion ? ` · texto ${statement.textVersion}` : ''}`
}

/**
 * C.1: notification preferences of a customer per channel, with the full history of statements.
 * Updating them is an explicit act that records who, when, how and which text was accepted.
 */
export function CustomerConsentsCard({ customerId }: { customerId: number }) {
  const queryClient = useQueryClient()
  const consents = useQuery({
    queryKey: customerKeys.consents(customerId),
    queryFn: ({ signal }) => fetchConsents(customerId, signal),
  })
  const [open, setOpen] = useState(false)
  const [channel, setChannel] = useState<ContactChannel>('EMAIL')
  const [granted, setGranted] = useState<boolean | null>(null)
  const [source, setSource] = useState<StaffSource>('IN_PERSON')
  const [read, setRead] = useState(false)
  const [done, setDone] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      recordConsent(customerId, { channel, granted: granted!, source, textVersion: granted ? CONSENT_TEXT_VERSION : undefined }),
    onSuccess: (data) => {
      queryClient.setQueryData(customerKeys.consents(customerId), data)
      setOpen(false)
      setDone(granted ? `Registrado: acepta avisos por ${CHANNEL_LABELS[channel].toLowerCase()}.` : `Registrado: no quiere avisos por ${CHANNEL_LABELS[channel].toLowerCase()}. Los avisos pendientes de ese canal se detuvieron.`)
    },
  })
  const errors = fieldErrorsOf(mutation.error)

  function start() {
    setDone(null)
    mutation.reset()
    setGranted(null)
    setRead(false)
    setOpen(true)
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <SectionCard title="Avisos al cliente" icon="bell" iconTone="warn" subtitle="Consentimiento por canal, con su origen y fecha.">
      {consents.isPending && <LoadingState label="Cargando preferencias…" />}
      {consents.isError && <Alert tone="error">{describeError(consents.error)}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
      {consents.data && (
        <>
          <dl className="details">
            {consents.data.channels.map(({ channel: item, latest }) => (
              <Fragment key={item}>
                <dt>{CHANNEL_LABELS[item]}</dt>
                <dd>
                  {latest === null ? (
                    <span className="muted">Sin respuesta del cliente (no se envían avisos)</span>
                  ) : (
                    <>
                      <span className={`badge badge-${latest.granted ? 'ok' : 'muted'}`}>{latest.granted ? 'Acepta avisos' : 'No quiere avisos'}</span>{' '}
                      <span className="muted small">desde {formatDateTime(latest.statedAt)}</span>
                    </>
                  )}
                  {item === 'WHATSAPP' && <div className="muted small">El envío por WhatsApp todavía no está integrado.</div>}
                </dd>
              </Fragment>
            ))}
          </dl>
          {!open && (
            <button type="button" className="button button-secondary" onClick={start}>
              Actualizar preferencias
            </button>
          )}
          {open && (
            <form className="subsection" onSubmit={submit} noValidate aria-label="Actualizar preferencias de avisos">
              {mutation.isError && <Alert tone="error">{describeError(mutation.error)}</Alert>}
              <div className="form-grid">
                <SelectField label="Canal" value={channel} onChange={(event) => setChannel(event.target.value as ContactChannel)}>
                  {(Object.keys(CHANNEL_LABELS) as ContactChannel[]).map((value) => (
                    <option key={value} value={value}>
                      {CHANNEL_LABELS[value]}
                    </option>
                  ))}
                </SelectField>
                <SelectField label="Cómo lo indicó" value={source} error={errors.source} onChange={(event) => setSource(event.target.value as StaffSource)}>
                  {(['IN_PERSON', 'PHONE', 'WRITTEN'] as StaffSource[]).map((value) => (
                    <option key={value} value={value}>
                      {CONSENT_SOURCE_LABELS[value]}
                    </option>
                  ))}
                </SelectField>
              </div>
              <fieldset className="field">
                <legend>Decisión del cliente</legend>
                <label className="checkbox">
                  <input type="radio" name="consent-decision" checked={granted === true} onChange={() => setGranted(true)} /> Acepta recibir avisos
                </label>
                <label className="checkbox">
                  <input type="radio" name="consent-decision" checked={granted === false} onChange={() => setGranted(false)} /> Retira o rechaza los avisos
                </label>
              </fieldset>
              {granted && (
                <label className="checkbox">
                  <input type="checkbox" checked={read} onChange={(event) => setRead(event.target.checked)} /> El cliente aceptó este texto:
                  «{CONSENT_TEXT}» <span className="mono small">({CONSENT_TEXT_VERSION})</span>
                </label>
              )}
              {granted && channel === 'EMAIL' && !consents.data.hasEmail && (
                <Alert tone="info">Registra primero el correo del cliente en sus datos.</Alert>
              )}
              <div className="form-actions">
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={mutation.isPending || granted === null || (granted && !read) || (granted && channel === 'EMAIL' && !consents.data.hasEmail)}
                >
                  {mutation.isPending ? 'Guardando…' : 'Registrar'}
                </button>
                <button type="button" className="button button-secondary" disabled={mutation.isPending} onClick={() => setOpen(false)}>
                  Cancelar
                </button>
              </div>
            </form>
          )}
          {consents.data.history.length > 0 && (
            <details className="subsection">
              <summary>Historial ({consents.data.history.length})</summary>
              <ol className="timeline">
                {consents.data.history.map((statement) => (
                  <li key={statement.id}>
                    <strong>{CHANNEL_LABELS[statement.channel]}</strong>: {describeStatement(statement)}
                    <div className="muted small">
                      {formatDateTime(statement.statedAt)} · registrado por {statement.recordedBy.fullName}
                    </div>
                  </li>
                ))}
              </ol>
            </details>
          )}
        </>
      )}
    </SectionCard>
  )
}
