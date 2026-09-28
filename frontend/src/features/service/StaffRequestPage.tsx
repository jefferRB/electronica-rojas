import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError, NetworkError } from '../../shared/api/httpClient'
import { PROVINCE_LABELS, WINDOW_LABELS, type PreferredWindow, type Province } from '../../shared/i18n/serviceLabels'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { SelectField, TextAreaField, TextField } from '../../shared/ui/Field'
import { useSelectedBranch } from '../branches/selectedBranch'
import { CustomerPicker } from '../customers/CustomerPicker'
import { duplicateMatches } from '../customers/customerDraft'
import { useCustomerPicker } from '../customers/useCustomerPicker'
import { crToday } from './crTime'
import { createStaffRequest, serviceKeys } from './serviceApi'

interface Details {
  province: Province | ''
  canton: string
  district: string
  addressLine: string
  deviceType: string
  brand: string
  model: string
  problemDescription: string
  preferredDate: string
  preferredWindow: PreferredWindow
  additionalNotes: string
}

const EMPTY: Details = {
  province: '',
  canton: '',
  district: '',
  addressLine: '',
  deviceType: '',
  brand: '',
  model: '',
  problemDescription: '',
  preferredDate: '',
  preferredWindow: 'ANY',
  additionalNotes: '',
}

const optional = (value: string) => (value.trim() ? value.trim() : undefined)

/**
 * A home-service request taken by phone or at the counter. The same customer picker as the
 * workshop reception: the customer is found or registered in the same transaction.
 */
export function StaffRequestPage() {
  const { selected } = useSelectedBranch()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const picker = useCustomerPicker()
  const [details, setDetails] = useState<Details>(EMPTY)
  const submissionId = useRef(newOperationId())

  const create = useMutation({
    mutationFn: (confirmedNewPerson: boolean) => {
      const choice = picker.choice
      return createStaffRequest({
        submissionId: submissionId.current,
        branchId: selected!.id,
        customerId: choice?.kind === 'existing' ? choice.match.id : undefined,
        registerCustomer: choice?.kind === 'new' ? true : undefined,
        confirmedNewPerson: confirmedNewPerson || picker.draft.confirmedNewPerson || undefined,
        contactName: picker.draft.name.trim(),
        contactPhone: picker.draft.phone.trim(),
        contactEmail: optional(picker.draft.email),
        province: details.province as Province,
        canton: details.canton.trim(),
        district: optional(details.district),
        addressLine: details.addressLine.trim(),
        deviceType: details.deviceType.trim(),
        brand: optional(details.brand),
        model: optional(details.model),
        problemDescription: details.problemDescription.trim(),
        preferredDate: optional(details.preferredDate),
        preferredWindow: details.preferredWindow,
        additionalNotes: optional(details.additionalNotes),
        notificationsConsent: false,
      })
    },
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
      navigate(`/service-requests/${created.id}`)
    },
    onError: (error) => {
      if (!(error instanceof NetworkError)) submissionId.current = newOperationId()
    },
  })
  const errors = fieldErrorsOf(create.error)
  const duplicates = create.error instanceof ApiError ? duplicateMatches(create.error.properties) : []
  const set = (key: keyof Details) => (event: { target: { value: string } }) => setDetails((current) => ({ ...current, [key]: event.target.value }))
  const ready =
    picker.choice && details.province && details.canton.trim() && details.addressLine.trim() && details.deviceType.trim() && details.problemDescription.trim()

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    create.mutate(false)
  }

  if (!selected) return <Alert tone="info">Selecciona en el encabezado la sucursal que atenderá la solicitud.</Alert>

  return (
    <section className="page page-form">
      <ModuleSurface
        breadcrumb={[{ to: '/service-requests', label: 'Solicitudes a domicilio' }]}
        eyebrow="Servicio a domicilio"
        icon="phone"
        title="Registrar solicitud"
        description="Para pedidos por teléfono o en mostrador. Queda en revisión; la visita se programa después."
        meta={
          <span className="chip">
            <Icon name="building" />
            Sucursal que atiende: {selected.name}
          </span>
        }
      >

        <form className="module-form" onSubmit={submit} noValidate>
          <div className="module-section step-card">
            <div className="step-header">
              <span className="step-number" aria-hidden="true">
                1
              </span>
              <div>
                <h2>Cliente</h2>
                <p>Busca por nombre o teléfono; si no existe, se registra con los mismos datos.</p>
              </div>
            </div>
            <div className="picker-zone">
              <CustomerPicker picker={picker} errors={errors} disabled={create.isPending} />
            </div>
            {duplicates.length > 0 && (
              <div role="alert">
                <Alert tone="warn" title="Ya hay clientes con ese teléfono o nombre.">
                  Elige uno o confirma que es otra persona.
                </Alert>
                <ul className="match-list">
                  {duplicates.map((match) => (
                    <li key={match.id}>
                      <span>{match.fullName}</span>
                      <button type="button" className="button button-secondary button-small" onClick={() => { create.reset(); picker.select(match) }}>
                        Usar este cliente
                      </button>
                    </li>
                  ))}
                </ul>
                <button type="button" className="button button-secondary" disabled={create.isPending} onClick={() => create.mutate(true)}>
                  Es otra persona: registrar cliente nuevo
                </button>
              </div>
            )}
          </div>

          <div className="module-section step-card">
            <div className="step-header">
              <span className="step-number" aria-hidden="true">
                2
              </span>
              <div>
                <h2>Dirección y equipo</h2>
                <p>Dónde será el servicio y qué equipo revisar.</p>
              </div>
            </div>
            <h3 className="form-section-title">Dirección</h3>
            <div className="form-grid">
              <SelectField label="Provincia" value={details.province} error={errors.province} onChange={set('province')}>
                <option value="">Selecciona</option>
                {(Object.keys(PROVINCE_LABELS) as Province[]).map((province) => (
                  <option key={province} value={province}>
                    {PROVINCE_LABELS[province]}
                  </option>
                ))}
              </SelectField>
              <TextField label="Cantón" value={details.canton} maxLength={80} error={errors.canton} onChange={set('canton')} />
              <TextField label="Distrito (opcional)" value={details.district} maxLength={80} onChange={set('district')} />
            </div>
            <TextAreaField label="Dirección exacta" rows={2} maxLength={300} value={details.addressLine} error={errors.addressLine} onChange={set('addressLine')} />
            <h3 className="form-section-title">Equipo y preferencia</h3>
            <div className="form-grid">
              <TextField label="Tipo de electrodoméstico" value={details.deviceType} maxLength={60} error={errors.deviceType} onChange={set('deviceType')} />
              <TextField label="Marca (opcional)" value={details.brand} maxLength={60} onChange={set('brand')} />
              <TextField label="Modelo (opcional)" value={details.model} maxLength={80} onChange={set('model')} />
            </div>
            <TextAreaField label="Problema reportado" maxLength={1000} value={details.problemDescription} error={errors.problemDescription} onChange={set('problemDescription')} />
            <div className="form-grid">
              <TextField label="Fecha preferida (opcional)" type="date" min={crToday()} value={details.preferredDate} error={errors.preferredDate} onChange={set('preferredDate')} />
              <SelectField label="Horario preferido" value={details.preferredWindow} onChange={set('preferredWindow')}>
                {(Object.keys(WINDOW_LABELS) as PreferredWindow[]).map((window) => (
                  <option key={window} value={window}>
                    {WINDOW_LABELS[window]}
                  </option>
                ))}
              </SelectField>
            </div>
            <TextAreaField label="Observaciones (opcional)" rows={2} maxLength={500} value={details.additionalNotes} onChange={set('additionalNotes')} />

            {create.isError && duplicates.length === 0 && <Alert tone="error">{describeError(create.error)}</Alert>}
            <div className="form-actions">
              <button type="submit" className="button button-primary" disabled={create.isPending || !ready}>
                {create.isPending ? 'Registrando…' : 'Registrar solicitud'}
              </button>
              <Link className="button button-secondary" to="/service-requests">
                Cancelar
              </Link>
            </div>
          </div>
        </form>
      </ModuleSurface>
    </section>
  )
}
