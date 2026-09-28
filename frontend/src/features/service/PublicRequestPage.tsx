import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { CONSENT_TEXT, CONSENT_TEXT_VERSION } from '../../shared/i18n/consent'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError, NetworkError } from '../../shared/api/httpClient'
import { PROVINCE_LABELS, WINDOW_LABELS, type PreferredWindow, type Province } from '../../shared/i18n/serviceLabels'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { SelectField, TextAreaField, TextField } from '../../shared/ui/Field'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { fetchPublicPortal, portalKeys, portalPath, type PublicPortal, type PublicPortalRules } from './portalApi'
import { submitPublicRequest, type PublicReceipt } from './serviceApi'
import { PublicLayout } from './PublicLayout'
import { describeServiceDays } from './serviceDays'

/** "Otro": the customer writes the type when the published options do not fit. */
const OTHER_TYPE = '__other__'

interface FormState {
  branchId: string
  contactName: string
  contactPhone: string
  contactEmail: string
  province: Province | ''
  canton: string
  district: string
  addressLine: string
  deviceChoice: string
  deviceType: string
  brand: string
  model: string
  problemDescription: string
  preferredDate: string
  preferredWindow: PreferredWindow
  additionalNotes: string
  contactConsent: boolean
  emailNotifications: boolean
  whatsappNotifications: boolean
  website: string
}

function emptyForm(portal: PublicPortal): FormState {
  const rules = portal.rules
  return {
    // With one branch there is nothing to choose.
    branchId: portal.branches.length === 1 ? String(portal.branches[0].id) : '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    province: rules?.servedProvinces.length === 1 ? rules.servedProvinces[0] : '',
    canton: '',
    district: '',
    addressLine: '',
    deviceChoice: '',
    deviceType: '',
    brand: '',
    model: '',
    problemDescription: '',
    preferredDate: '',
    preferredWindow: 'ANY',
    additionalNotes: '',
    contactConsent: false,
    emailNotifications: false,
    whatsappNotifications: false,
    website: '',
  }
}

const optional = (value: string) => (value.trim() ? value.trim() : undefined)

/** ISO weekday (1 = Monday) of a "YYYY-MM-DD" calendar date. */
function isoWeekday(date: string): number {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay()
  return day === 0 ? 7 : day
}

/**
 * The address before slugs existed (/solicitar-servicio): opens the current portal, so links and
 * bookmarks shared earlier keep working.
 */
export function LegacyPortalRedirect() {
  const portal = useQuery({ queryKey: portalKeys.public(null), queryFn: ({ signal }) => fetchPublicPortal(null, signal) })
  if (portal.data) return <Navigate to={portalPath(portal.data.slug)} replace />
  return (
    <PublicLayout>
      {portal.isPending && <LoadingState label="Abriendo el formulario…" />}
      {portal.isError && <ErrorState error={portal.error} onRetry={() => portal.refetch()} />}
    </PublicLayout>
  )
}

/**
 * FR-SRV-001/002, BR-SRV-009: the public page to request a home repair, opened from the shared link
 * or QR (no account). It says plainly that this is a request: the time is only confirmed later.
 */
export function PublicRequestPage() {
  const { slug = '' } = useParams()
  const navigate = useNavigate()
  const portal = useQuery({
    queryKey: portalKeys.public(slug),
    queryFn: ({ signal }) => fetchPublicPortal(slug, signal),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  // A previous address still works; the browser then shows the current one.
  useEffect(() => {
    if (portal.data && portal.data.slug !== slug) navigate(portalPath(portal.data.slug), { replace: true })
  }, [portal.data, slug, navigate])

  if (portal.isPending) {
    return (
      <PublicLayout>
        <LoadingState label="Cargando el formulario…" />
      </PublicLayout>
    )
  }
  if (portal.isError) {
    const missing = portal.error instanceof ApiError && portal.error.status === 404
    return (
      <PublicLayout>
        {missing ? (
          <section className="card public-card">
            <h1>No encontramos este formulario</h1>
            <p>Revisa el enlace o el código QR que te compartieron, o comunícate directamente con el negocio.</p>
          </section>
        ) : (
          <ErrorState error={portal.error} onRetry={() => portal.refetch()} />
        )}
      </PublicLayout>
    )
  }
  if (!portal.data.accepting || !portal.data.rules) {
    return (
      <PublicLayout>
        <section className="card public-card public-paused">
          <span className="empty-state-icon" aria-hidden="true">
            <Icon name="clock" size={28} />
          </span>
          <h1>Por ahora no recibimos solicitudes en línea</h1>
          <p>El formulario de reparaciones a domicilio está pausado temporalmente. Para coordinar una visita, comunícate directamente con el negocio.</p>
        </section>
      </PublicLayout>
    )
  }
  return <RequestForm key={portal.data.slug} portal={portal.data} rules={portal.data.rules} />
}

function RequestForm({ portal, rules }: { portal: PublicPortal; rules: PublicPortalRules }) {
  const [form, setForm] = useState<FormState>(() => emptyForm(portal))
  const [receipt, setReceipt] = useState<PublicReceipt | null>(null)
  // One id per filled form, kept on network retries so a double submit creates one request.
  const submissionId = useRef(newOperationId())
  const typed = rules.serviceTypes.length > 0
  const deviceType = typed && form.deviceChoice !== OTHER_TYPE ? form.deviceChoice : form.deviceType

  const submit = useMutation({
    mutationFn: () =>
      submitPublicRequest({
        submissionId: submissionId.current,
        branchId: Number(form.branchId),
        contactName: form.contactName.trim(),
        contactPhone: form.contactPhone.trim(),
        contactEmail: optional(form.contactEmail),
        province: form.province as Province,
        canton: form.canton.trim(),
        district: optional(form.district),
        addressLine: form.addressLine.trim(),
        deviceType: deviceType.trim(),
        brand: optional(form.brand),
        model: optional(form.model),
        problemDescription: form.problemDescription.trim(),
        preferredDate: rules.allowPreferredDate ? optional(form.preferredDate) : undefined,
        preferredWindow: rules.allowPreferredWindow ? form.preferredWindow : 'ANY',
        additionalNotes: optional(form.additionalNotes),
        contactConsent: form.contactConsent,
        emailNotifications: form.emailNotifications && form.contactEmail.trim() !== '',
        whatsappNotifications: form.whatsappNotifications,
        consentTextVersion: form.emailNotifications || form.whatsappNotifications ? CONSENT_TEXT_VERSION : undefined,
        website: form.website,
      }),
    onSuccess: setReceipt,
    onError: (error) => {
      if (!(error instanceof NetworkError)) submissionId.current = newOperationId()
    },
  })
  const errors = fieldErrorsOf(submit.error)
  const set = (key: keyof FormState) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const dateError =
    form.preferredDate && !rules.serviceDays.includes(isoWeekday(form.preferredDate))
      ? `Ese día no hacemos visitas; atendemos ${describeServiceDays(rules.serviceDays)}.`
      : undefined

  const ready =
    form.branchId &&
    form.contactName.trim() &&
    form.contactPhone.trim() &&
    form.province &&
    form.canton.trim() &&
    form.addressLine.trim() &&
    deviceType.trim() &&
    form.problemDescription.trim().length >= 10 &&
    form.contactConsent &&
    !dateError

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submit.mutate()
  }

  if (receipt) {
    return (
      <PublicLayout>
        <section className="card public-card">
          <div className="public-success">
            <span className="empty-state-icon" aria-hidden="true">
              <Icon name="checkCircle" size={28} />
            </span>
            <h1>Recibimos tu solicitud</h1>
          </div>
          {portal.successMessage && <p className="portal-message">{portal.successMessage}</p>}
          <p>
            <strong>Todavía no es una cita confirmada.</strong> Tu solicitud será revisada y el horario quedará confirmado
            cuando el equipo la apruebe y te contacte.
          </p>
          {receipt.requestCode && (
            <p>
              Número de solicitud: <strong className="mono">{receipt.requestCode}</strong>
            </p>
          )}
          <p>
            Guarda este enlace para consultar el estado:{' '}
            <Link to={`/solicitud/${receipt.publicRef}`}>ver el estado de mi solicitud</Link>
          </p>
        </section>
      </PublicLayout>
    )
  }

  return (
    <PublicLayout>
      <form className="card public-card" onSubmit={handleSubmit} noValidate>
        <h1>Solicitar reparación a domicilio</h1>
        {portal.welcomeMessage && <p className="portal-message">{portal.welcomeMessage}</p>}
        <Alert tone="info">
          Esto es una <strong>solicitud</strong>: el horario que indiques es una preferencia. Tu solicitud será revisada y el
          horario quedará confirmado cuando el equipo la apruebe.
        </Alert>
        {submit.isError && (
          <Alert tone="error">
            {describeError(submit.error)}
            {submit.error instanceof NetworkError && ' Puedes reintentar: no se duplicará tu solicitud.'}
          </Alert>
        )}

        <fieldset className="subsection-tight">
          <legend className="field-label">Tus datos</legend>
          <div className="form-grid">
            <TextField label="Nombre completo" autoComplete="name" value={form.contactName} maxLength={160} error={errors.contactName} onChange={set('contactName')} />
            <TextField
              label="Teléfono"
              type="tel"
              autoComplete="tel"
              value={form.contactPhone}
              maxLength={30}
              hint="8 dígitos, por ejemplo 8888-7777."
              error={errors.contactPhone}
              onChange={set('contactPhone')}
            />
            <TextField label="Correo electrónico (opcional)" type="email" autoComplete="email" value={form.contactEmail} maxLength={254} error={errors.contactEmail} onChange={set('contactEmail')} />
          </div>
        </fieldset>

        <fieldset className="subsection-tight">
          <legend className="field-label">Dónde será el servicio</legend>
          <div className="form-grid">
            {portal.branches.length > 1 && (
              <SelectField label="Sucursal más cercana" value={form.branchId} error={errors.branchId} onChange={set('branchId')}>
                <option value="">Selecciona una sucursal</option>
                {portal.branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </SelectField>
            )}
            <SelectField label="Provincia" value={form.province} error={errors.province} onChange={set('province')}>
              <option value="">Selecciona</option>
              {rules.servedProvinces.map((province) => (
                <option key={province} value={province}>
                  {PROVINCE_LABELS[province]}
                </option>
              ))}
            </SelectField>
            <TextField label="Cantón" value={form.canton} maxLength={80} error={errors.canton} onChange={set('canton')} />
            <TextField label="Distrito (opcional)" value={form.district} maxLength={80} error={errors.district} onChange={set('district')} />
          </div>
          <TextAreaField
            label="Dirección exacta"
            rows={2}
            maxLength={300}
            hint="Señas para llegar: calle, color de la casa, puntos de referencia."
            value={form.addressLine}
            error={errors.addressLine}
            onChange={set('addressLine')}
          />
          {portal.branches.length === 1 && <p className="field-hint">Atiende: {portal.branches[0].name}.</p>}
        </fieldset>

        <fieldset className="subsection-tight">
          <legend className="field-label">El equipo</legend>
          <div className="form-grid">
            {typed ? (
              <SelectField label="Tipo de electrodoméstico" value={form.deviceChoice} error={errors.deviceType} onChange={set('deviceChoice')}>
                <option value="">Selecciona</option>
                {rules.serviceTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
                <option value={OTHER_TYPE}>Otro</option>
              </SelectField>
            ) : (
              <TextField label="Tipo de electrodoméstico" placeholder="Refrigeradora, lavadora…" value={form.deviceType} maxLength={60} error={errors.deviceType} onChange={set('deviceType')} />
            )}
            {typed && form.deviceChoice === OTHER_TYPE && (
              <TextField label="¿Qué equipo es?" value={form.deviceType} maxLength={60} error={errors.deviceType} onChange={set('deviceType')} />
            )}
            <TextField label="Marca (si la sabes)" value={form.brand} maxLength={60} error={errors.brand} onChange={set('brand')} />
            <TextField label="Modelo (si lo sabes)" value={form.model} maxLength={80} error={errors.model} onChange={set('model')} />
          </div>
          <TextAreaField
            label="¿Qué problema tiene?"
            maxLength={1000}
            hint="Al menos 10 caracteres."
            value={form.problemDescription}
            error={errors.problemDescription}
            onChange={set('problemDescription')}
          />
        </fieldset>

        {(rules.allowPreferredDate || rules.allowPreferredWindow) && (
          <fieldset className="subsection-tight">
            <legend className="field-label">Cuándo prefieres la visita</legend>
            <div className="form-grid">
              {rules.allowPreferredDate && (
                <TextField
                  label="Fecha preferida (opcional)"
                  type="date"
                  min={rules.earliestDate}
                  max={rules.latestDate}
                  value={form.preferredDate}
                  hint={`Visitas ${describeServiceDays(rules.serviceDays)}. Es una preferencia, no una reserva.`}
                  error={dateError ?? errors.preferredDate}
                  onChange={set('preferredDate')}
                />
              )}
              {rules.allowPreferredWindow && (
                <SelectField label="Horario preferido" value={form.preferredWindow} onChange={set('preferredWindow')}>
                  {(Object.keys(WINDOW_LABELS) as PreferredWindow[]).map((window) => (
                    <option key={window} value={window}>
                      {WINDOW_LABELS[window]}
                    </option>
                  ))}
                </SelectField>
              )}
            </div>
          </fieldset>
        )}
        <TextAreaField label="Observaciones (opcional)" rows={2} maxLength={500} value={form.additionalNotes} onChange={set('additionalNotes')} />

        {/* Honeypot: hidden from people and screen readers; bots tend to fill it. */}
        <div className="honeypot" aria-hidden="true">
          <label htmlFor="website">No completar</label>
          <input id="website" tabIndex={-1} autoComplete="off" value={form.website} onChange={set('website')} />
        </div>

        <label className="checkbox">
          <input
            type="checkbox"
            checked={form.contactConsent}
            onChange={(event) => setForm((current) => ({ ...current, contactConsent: event.target.checked }))}
          />{' '}
          Acepto que me contacten por teléfono o correo para coordinar esta solicitud.
        </label>
        {errors.contactConsent && <p className="field-error">{errors.contactConsent}</p>}
        <fieldset className="field consent-choice">
          <legend>Avisos sobre tu solicitud (opcional)</legend>
          <p className="muted small">{CONSENT_TEXT}</p>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={form.emailNotifications && form.contactEmail.trim() !== ''}
              disabled={form.contactEmail.trim() === ''}
              onChange={(event) => setForm((current) => ({ ...current, emailNotifications: event.target.checked }))}
            />{' '}
            Quiero recibir avisos por correo electrónico
            {form.contactEmail.trim() === '' && <span className="muted small"> (escribe tu correo arriba)</span>}
          </label>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={form.whatsappNotifications}
              onChange={(event) => setForm((current) => ({ ...current, whatsappNotifications: event.target.checked }))}
            />{' '}
            Quiero recibir avisos por WhatsApp
          </label>
          {errors.consentTextVersion && <p className="field-error">{errors.consentTextVersion}</p>}
        </fieldset>
        <p className="muted small">
          Usamos tus datos solo para atender esta solicitud. No los compartimos con terceros.
        </p>

        <button type="submit" className="button button-primary button-block" disabled={submit.isPending || !ready}>
          {submit.isPending ? 'Enviando…' : 'Enviar solicitud'}
        </button>
      </form>
    </PublicLayout>
  )
}
