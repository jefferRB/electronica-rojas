import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { PROVINCE_LABELS, WEEKDAY_LABELS, type Province } from '../../shared/i18n/serviceLabels'
import { formatDateTime } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { TextAreaField, TextField } from '../../shared/ui/Field'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { Switch } from '../../shared/ui/Switch'
import {
  changePortalSlug,
  fetchPortalSettings,
  PORTAL_LIMITS,
  portalKeys,
  portalPath,
  portalUrl,
  updatePortalSettings,
  type PortalSettings,
} from './portalApi'
import { downloadBlob, qrFileName, qrMatrix, qrPngBlob, qrSvgDocument } from './qr'
import { QrCode } from './QrCode'
import { slugify, slugProblem } from './slug'

const PROVINCES = Object.keys(PROVINCE_LABELS) as Province[]

/** The editable part of the settings, as the form holds it (numbers as typed). */
interface Draft {
  enabled: boolean
  allowPreferredDate: boolean
  allowPreferredWindow: boolean
  minNoticeDays: string
  maxDaysAhead: string
  serviceDays: number[]
  servedProvinces: Province[]
  serviceTypes: string[]
  welcomeMessage: string
  successMessage: string
}

function toDraft(settings: PortalSettings): Draft {
  return {
    enabled: settings.enabled,
    allowPreferredDate: settings.allowPreferredDate,
    allowPreferredWindow: settings.allowPreferredWindow,
    minNoticeDays: String(settings.minNoticeDays),
    maxDaysAhead: String(settings.maxDaysAhead),
    serviceDays: [...settings.serviceDays],
    servedProvinces: [...settings.servedProvinces],
    serviceTypes: [...settings.serviceTypes],
    welcomeMessage: settings.welcomeMessage ?? '',
    successMessage: settings.successMessage ?? '',
  }
}

const whole = (text: string) => (/^\d{1,3}$/.test(text.trim()) ? Number(text.trim()) : NaN)

/** Problems the server would also report, shown before sending (the server still decides). */
function draftProblems(draft: Draft): Partial<Record<keyof Draft, string>> {
  const problems: Partial<Record<keyof Draft, string>> = {}
  const min = whole(draft.minNoticeDays)
  const max = whole(draft.maxDaysAhead)
  if (Number.isNaN(min) || min > PORTAL_LIMITS.minNoticeDays) problems.minNoticeDays = 'Entre 0 y 30 días.'
  if (Number.isNaN(max) || max < 1 || max > PORTAL_LIMITS.maxDaysAhead) problems.maxDaysAhead = 'Entre 1 y 180 días.'
  else if (!Number.isNaN(min) && max < min) problems.maxDaysAhead = 'No puede ser menor que la anticipación mínima.'
  if (draft.serviceDays.length === 0) problems.serviceDays = 'Elige al menos un día.'
  if (draft.servedProvinces.length === 0) problems.servedProvinces = 'Elige al menos una provincia.'
  if (/[<>]/.test(draft.welcomeMessage)) problems.welcomeMessage = 'Solo texto: sin los signos < y >.'
  if (/[<>]/.test(draft.successMessage)) problems.successMessage = 'Solo texto: sin los signos < y >.'
  return problems
}

/**
 * BR-SRV-009: the shareable public request page. Link and QR together (what the business prints or
 * posts), then the switch, the request rules and the messages, saved with one button. Changing the
 * address is a separate, confirmed act. Only ADMIN reaches this page; the API enforces it too.
 */
export function PortalSettingsPage() {
  const settings = useQuery({ queryKey: portalKeys.settings, queryFn: ({ signal }) => fetchPortalSettings(signal) })
  const url = settings.data ? portalUrl(settings.data.slug) : null

  return (
    <section className="page">
      <ModuleSurface
        breadcrumb={[{ to: '/service-requests', label: 'A domicilio' }]}
        eyebrow="Servicio a domicilio"
        icon="link"
        title="Configuración del portal"
        description="El enlace y el código QR con los que tus clientes solicitan una visita. Cada envío llega a la bandeja como solicitud; ninguno es una cita hasta que el equipo la confirma."
        actions={
          url && (
            <a className="button button-secondary" href={url} target="_blank" rel="noopener noreferrer">
              <Icon name="eye" />
              Ver página pública
            </a>
          )
        }
      >
        {settings.isPending && <LoadingState label="Cargando la configuración del portal…" />}
        {settings.isError && <ErrorState error={settings.error} onRetry={() => settings.refetch()} />}
        {settings.data && <PortalSettingsEditor settings={settings.data} />}
      </ModuleSurface>
    </section>
  )
}

function PortalSettingsEditor({ settings }: { settings: PortalSettings }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<Draft>(() => toDraft(settings))
  const [saved, setSaved] = useState<Draft>(() => toDraft(settings))
  // Version the draft was based on; a slug change made here moves it forward.
  const [version, setVersion] = useState(settings.version)
  const problems = draftProblems(draft)
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)
  const valid = Object.keys(problems).length === 0

  const save = useMutation({
    mutationFn: () =>
      updatePortalSettings({
        enabled: draft.enabled,
        allowPreferredDate: draft.allowPreferredDate,
        allowPreferredWindow: draft.allowPreferredWindow,
        minNoticeDays: whole(draft.minNoticeDays),
        maxDaysAhead: whole(draft.maxDaysAhead),
        serviceDays: draft.serviceDays,
        servedProvinces: draft.servedProvinces,
        serviceTypes: draft.serviceTypes,
        welcomeMessage: draft.welcomeMessage.trim() || null,
        successMessage: draft.successMessage.trim() || null,
        version,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(portalKeys.settings, updated)
      setDraft(toDraft(updated))
      setSaved(toDraft(updated))
      setVersion(updated.version)
    },
  })
  const serverErrors = fieldErrorsOf(save.error)
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    save.reset()
    setDraft((current) => ({ ...current, [key]: value }))
  }
  const toggle = <T,>(list: T[], item: T, order: T[]) =>
    list.includes(item) ? list.filter((value) => value !== item) : order.filter((value) => value === item || list.includes(value))

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (valid && dirty) save.mutate()
  }

  return (
    <form className="portal-settings" onSubmit={handleSubmit} noValidate aria-label="Configuración del portal público">
      <section className="portal-section portal-status" aria-labelledby="portal-status-title">
        <div className="portal-section-heading">
          <h2 id="portal-status-title">Solicitudes a domicilio públicas</h2>
          <p className="card-subtitle">Pausar el portal no cambia el enlace ni borra solicitudes.</p>
        </div>
        <Switch
          label="Aceptar solicitudes desde el portal público"
          checked={draft.enabled}
          stateLabels={['Activo', 'Desactivado']}
          description={
            draft.enabled
              ? 'Los clientes pueden enviar solicitudes desde el enlace y el código QR.'
              : 'El enlace y el QR siguen funcionando: la página explica que las solicitudes están pausadas y no acepta nuevas.'
          }
          onChange={(value) => set('enabled', value)}
        />
      </section>

      <PortalShare settings={settings} onSlugChanged={(updated) => setVersion(updated.version)} />

      <section className="portal-section" aria-labelledby="portal-rules-title">
        <div className="portal-section-heading">
          <h2 id="portal-rules-title">Reglas de las solicitudes</h2>
          <p className="card-subtitle">
            Lo que el cliente indica es una preferencia. El horario queda confirmado cuando el equipo aprueba la solicitud y
            programa la visita.
          </p>
        </div>
        <div className="portal-rules">
          <Switch
            label="El cliente puede proponer una fecha"
            checked={draft.allowPreferredDate}
            onChange={(value) => set('allowPreferredDate', value)}
          />
          {draft.allowPreferredDate && (
            <div className="form-grid">
              <TextField
                label="Anticipación mínima (días)"
                type="number"
                inputMode="numeric"
                min={0}
                max={PORTAL_LIMITS.minNoticeDays}
                value={draft.minNoticeDays}
                hint="0 permite pedir para hoy."
                error={problems.minNoticeDays ?? serverErrors.minNoticeDays}
                onChange={(event) => set('minNoticeDays', event.target.value)}
              />
              <TextField
                label="Máximo de días hacia adelante"
                type="number"
                inputMode="numeric"
                min={1}
                max={PORTAL_LIMITS.maxDaysAhead}
                value={draft.maxDaysAhead}
                error={problems.maxDaysAhead ?? serverErrors.maxDaysAhead}
                onChange={(event) => set('maxDaysAhead', event.target.value)}
              />
            </div>
          )}
          <fieldset className="field">
            <legend className="field-label">Días de atención</legend>
            <p className="field-hint">Una fecha preferida solo puede caer en estos días.</p>
            <div className="toggle-chips">
              {WEEKDAY_LABELS.map((label, index) => {
                const day = index + 1
                return (
                  <label key={day} className="toggle-chip">
                    <input
                      type="checkbox"
                      checked={draft.serviceDays.includes(day)}
                      onChange={() => set('serviceDays', toggle(draft.serviceDays, day, [1, 2, 3, 4, 5, 6, 7]))}
                    />
                    <span>{label}</span>
                  </label>
                )
              })}
            </div>
            {(problems.serviceDays ?? serverErrors.serviceDays) && (
              <p className="field-error">{problems.serviceDays ?? serverErrors.serviceDays}</p>
            )}
          </fieldset>
          <Switch
            label="El cliente puede proponer un horario (mañana o tarde)"
            checked={draft.allowPreferredWindow}
            onChange={(value) => set('allowPreferredWindow', value)}
          />
        </div>
      </section>

      <section className="portal-section" aria-labelledby="portal-zones-title">
        <div className="portal-section-heading">
          <h2 id="portal-zones-title">Zonas atendidas</h2>
          <p className="card-subtitle">El formulario solo ofrece estas provincias.</p>
        </div>
        <fieldset className="field">
          <legend className="visually-hidden">Provincias atendidas</legend>
          <div className="toggle-chips">
            {PROVINCES.map((province) => (
              <label key={province} className="toggle-chip">
                <input
                  type="checkbox"
                  checked={draft.servedProvinces.includes(province)}
                  onChange={() => set('servedProvinces', toggle(draft.servedProvinces, province, PROVINCES))}
                />
                <span>{PROVINCE_LABELS[province]}</span>
              </label>
            ))}
          </div>
          {(problems.servedProvinces ?? serverErrors.servedProvinces) && (
            <p className="field-error">{problems.servedProvinces ?? serverErrors.servedProvinces}</p>
          )}
        </fieldset>
      </section>

      <section className="portal-section" aria-labelledby="portal-types-title">
        <div className="portal-section-heading">
          <h2 id="portal-types-title">Tipos de equipo publicados</h2>
          <p className="card-subtitle">
            Opciones que el cliente elige en el formulario; siempre puede escribir «Otro». Sin tipos, escribe el tipo libremente.
          </p>
        </div>
        <ServiceTypesEditor value={draft.serviceTypes} onChange={(value) => set('serviceTypes', value)} />
      </section>

      <section className="portal-section" aria-labelledby="portal-messages-title">
        <div className="portal-section-heading">
          <h2 id="portal-messages-title">Mensajes</h2>
          <p className="card-subtitle">Opcionales y en texto simple. Sin mensaje se usa un texto neutro.</p>
        </div>
        <div className="form-grid form-grid-2">
          <TextAreaField
            label="Mensaje de bienvenida"
            rows={3}
            maxLength={PORTAL_LIMITS.welcomeMessage}
            placeholder="Contanos qué equipo necesitás reparar y nos pondremos en contacto contigo."
            hint={`${draft.welcomeMessage.length}/${PORTAL_LIMITS.welcomeMessage} caracteres.`}
            value={draft.welcomeMessage}
            error={problems.welcomeMessage ?? serverErrors.welcomeMessage}
            onChange={(event) => set('welcomeMessage', event.target.value)}
          />
          <TextAreaField
            label="Mensaje después de enviar la solicitud"
            rows={3}
            maxLength={PORTAL_LIMITS.successMessage}
            placeholder="Recibimos tu solicitud. Nuestro equipo la revisará y te contactará para confirmar la visita."
            hint={`${draft.successMessage.length}/${PORTAL_LIMITS.successMessage} caracteres. Siempre se aclara que aún no es una cita confirmada.`}
            value={draft.successMessage}
            error={problems.successMessage ?? serverErrors.successMessage}
            onChange={(event) => set('successMessage', event.target.value)}
          />
        </div>
      </section>

      {save.isError && <Alert tone="error">{describeError(save.error)}</Alert>}
      {save.isSuccess && !dirty && <Alert tone="success">Cambios guardados. El portal ya los muestra.</Alert>}

      <div className={`submit-bar portal-submit${dirty ? '' : ' is-idle'}`}>
        <span className="submit-status" role="status">
          {dirty ? (
            <>
              <Icon name="edit" size={16} />
              Cambios sin guardar
            </>
          ) : (
            <>
              <Icon name="check" size={16} />
              {settings.updatedBy
                ? `Guardado · ${settings.updatedBy.fullName}, ${formatDateTime(settings.updatedAt)}`
                : 'Sin cambios pendientes'}
            </>
          )}
        </span>
        <div className="form-actions">
          <button
            type="button"
            className="button button-secondary"
            disabled={!dirty || save.isPending}
            onClick={() => {
              save.reset()
              setDraft(saved)
            }}
          >
            Descartar
          </button>
          <button type="submit" className="button button-primary" disabled={!dirty || !valid || save.isPending}>
            {save.isPending ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </div>
      </div>
    </form>
  )
}

/** Copies through a temporary selection; false when the browser refuses. */
function copyWithSelection(text: string): boolean {
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    area.remove()
  }
}

/** Link + QR, side by side on wide screens: what the business shares. */
function PortalShare({ settings, onSlugChanged }: { settings: PortalSettings; onSlugChanged: (updated: PortalSettings) => void }) {
  const url = portalUrl(settings.slug)
  const [copied, setCopied] = useState<'ok' | 'failed' | null>(null)
  const timer = useRef<number | undefined>(undefined)
  useEffect(() => () => window.clearTimeout(timer.current), [])

  async function copy() {
    let ok = false
    try {
      await navigator.clipboard.writeText(url)
      ok = true
    } catch {
      // Async Clipboard API denied (embedded browsers, missing permission): the classic copy command
      // still works from this click in most browsers.
      ok = copyWithSelection(url)
    }
    setCopied(ok ? 'ok' : 'failed')
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setCopied(null), 2500)
  }

  async function downloadPng() {
    const blob = await qrPngBlob(qrMatrix(url))
    if (blob) downloadBlob(blob, qrFileName(settings.slug, 'png'))
  }

  function downloadSvg() {
    downloadBlob(new Blob([qrSvgDocument(qrMatrix(url))], { type: 'image/svg+xml' }), qrFileName(settings.slug, 'svg'))
  }

  return (
    <section className="portal-section" aria-labelledby="portal-share-title">
      <div className="portal-section-heading">
        <h2 id="portal-share-title">Comparte tu portal</h2>
        <p className="card-subtitle">Publícalo en tu sitio web, redes sociales o mensajes, o imprime el código QR en el local.</p>
      </div>
      <div className="portal-share">
        <div className="share-card">
          <h3>
            <Icon name="link" size={18} />
            Tu enlace de solicitudes
          </h3>
          <div className="share-link">
            <p className="share-url mono" data-testid="portal-url">
              {url}
            </p>
            <div className="share-actions">
              <button type="button" className="button button-primary" onClick={copy}>
                <Icon name={copied === 'ok' ? 'check' : 'copy'} />
                {copied === 'ok' ? 'Copiado' : 'Copiar'}
              </button>
              <a className="button button-secondary" href={url} target="_blank" rel="noopener noreferrer">
                <Icon name="external" />
                Abrir
              </a>
            </div>
          </div>
          <p className="visually-hidden" role="status">
            {copied === 'ok' ? 'Enlace copiado al portapapeles.' : ''}
          </p>
          {copied === 'failed' && (
            <p className="field-error">No se pudo copiar automáticamente. Selecciona el enlace y cópialo a mano.</p>
          )}
          <p className="field-hint">
            El enlace no es secreto: cualquier persona con él puede enviar una solicitud, y cada una la revisa tu equipo.
          </p>
          <SlugEditor settings={settings} onChanged={onSlugChanged} />
        </div>

        <div className="share-card qr-card">
          <h3>
            <Icon name="qr" size={18} />
            Código QR
          </h3>
          <div className="qr-frame">
            <QrCode text={url} label={`Código QR que abre ${url}`} />
          </div>
          <p className="qr-caption">Escanéalo para abrir el formulario</p>
          <div className="share-actions">
            <button type="button" className="button button-secondary" onClick={downloadPng}>
              <Icon name="download" />
              Descargar PNG
            </button>
            <button type="button" className="button button-ghost" onClick={downloadSvg}>
              <Icon name="download" />
              SVG para imprenta
            </button>
          </div>
        </div>
      </div>
    </section>
  )
}

/** Changing the address: warn, explain, confirm. The previous slug keeps redirecting to the new one. */
function SlugEditor({ settings, onChanged }: { settings: PortalSettings; onChanged: (updated: PortalSettings) => void }) {
  const queryClient = useQueryClient()
  const inputId = useId()
  const [open, setOpen] = useState(false)
  const [slug, setSlug] = useState(settings.slug)
  const [understood, setUnderstood] = useState(false)
  const normalized = slug.trim().toLowerCase()
  const problem = normalized === settings.slug ? null : slugProblem(normalized)
  const change = useMutation({
    mutationFn: () => changePortalSlug(normalized, settings.version),
    onSuccess: (updated) => {
      queryClient.setQueryData(portalKeys.settings, updated)
      onChanged(updated)
      setOpen(false)
      setUnderstood(false)
    },
  })
  const errors = fieldErrorsOf(change.error)

  if (!open) {
    return (
      <div className="slug-summary">
        <p className="small">
          Dirección: <span className="mono">{portalPath(settings.slug)}</span>
        </p>
        <button
          type="button"
          className="button button-ghost button-small"
          onClick={() => {
            change.reset()
            setSlug(settings.slug)
            setOpen(true)
          }}
        >
          <Icon name="edit" size={16} />
          Cambiar dirección
        </button>
        {change.isSuccess && <Alert tone="success">Dirección actualizada. Descarga de nuevo el código QR.</Alert>}
        {settings.previousSlugs.length > 0 && (
          <p className="field-hint">
            Direcciones anteriores que siguen llevando al portal:{' '}
            {settings.previousSlugs.map((previous, index) => (
              <span key={previous}>
                {index > 0 && ', '}
                <span className="mono">{portalPath(previous)}</span>
              </span>
            ))}
          </p>
        )}
      </div>
    )
  }

  return (
    <div className="slug-editor" role="group" aria-labelledby={`${inputId}-title`}>
      <p className="form-section-title" id={`${inputId}-title`}>
        Cambiar la dirección del enlace
      </p>
      <div className="field">
        <label htmlFor={inputId}>Dirección</label>
        <div className="input-prefix">
          <span className="mono" aria-hidden="true">
            /solicitar/
          </span>
          <input
            id={inputId}
            value={slug}
            maxLength={PORTAL_LIMITS.slugMax}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={problem || errors.slug ? true : undefined}
            aria-describedby={`${inputId}-help`}
            onChange={(event) => {
              change.reset()
              setSlug(event.target.value)
            }}
          />
        </div>
        <p className={problem || errors.slug ? 'field-error' : 'field-hint'} id={`${inputId}-help`}>
          {problem ?? errors.slug ?? `Nuevo enlace: ${portalUrl(normalized || settings.slug)}`}
        </p>
        {slugify(slug) !== normalized && slugify(slug).length >= 3 && (
          <button type="button" className="button button-ghost button-small" onClick={() => setSlug(slugify(slug))}>
            Usar «{slugify(slug)}»
          </button>
        )}
      </div>
      {normalized !== settings.slug && !problem && (
        <Alert tone="warn" title="Cambiará el enlace que compartes.">
          Los enlaces y códigos QR que ya imprimiste o publicaste usan <span className="mono">{portalPath(settings.slug)}</span>.
          Esa dirección seguirá llevando al portal, pero conviene actualizar los materiales con el nuevo QR.
        </Alert>
      )}
      {change.isError && !errors.slug && <Alert tone="error">{describeError(change.error)}</Alert>}
      <label className="checkbox">
        <input type="checkbox" checked={understood} onChange={(event) => setUnderstood(event.target.checked)} />
        Entiendo que el enlace del portal cambiará.
      </label>
      <div className="form-actions">
        <button
          type="button"
          className="button button-primary"
          disabled={!understood || problem !== null || normalized === settings.slug || change.isPending}
          onClick={() => change.mutate()}
        >
          {change.isPending ? 'Cambiando…' : 'Confirmar nueva dirección'}
        </button>
        <button type="button" className="button button-secondary" disabled={change.isPending} onClick={() => setOpen(false)}>
          Cancelar
        </button>
      </div>
    </div>
  )
}

/** A short list of appliance types: add, remove; no CMS. */
function ServiceTypesEditor({ value, onChange }: { value: string[]; onChange: (value: string[]) => void }) {
  const [text, setText] = useState('')
  const trimmed = text.trim()
  const duplicate = value.some((type) => type.toLowerCase() === trimmed.toLowerCase())
  const full = value.length >= PORTAL_LIMITS.serviceTypes

  function add() {
    if (!trimmed || duplicate || full || /[<>]/.test(trimmed)) return
    onChange([...value, trimmed])
    setText('')
  }

  return (
    <div className="service-types">
      {value.length > 0 ? (
        <ul className="chip-list" aria-label="Tipos publicados">
          {value.map((type) => (
            <li key={type} className="chip removable-chip">
              {type}
              <button
                type="button"
                className="chip-remove"
                aria-label={`Quitar ${type}`}
                onClick={() => onChange(value.filter((item) => item !== type))}
              >
                <Icon name="close" size={14} />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted small">Sin tipos publicados: el cliente escribe el tipo de equipo.</p>
      )}
      <div className="inline-add">
        <TextField
          label="Agregar tipo de equipo"
          placeholder="Lavadora, refrigeradora, horno…"
          maxLength={PORTAL_LIMITS.serviceType}
          value={text}
          error={duplicate && trimmed ? 'Ese tipo ya está en la lista.' : full ? 'Máximo 20 tipos.' : undefined}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              add()
            }
          }}
        />
        <button type="button" className="button button-secondary" disabled={!trimmed || duplicate || full} onClick={add}>
          <Icon name="plus" />
          Agregar
        </button>
      </div>
    </div>
  )
}
