import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError, NetworkError } from '../../shared/api/httpClient'
import type { ContactChannel } from '../../shared/i18n/consent'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { Avatar } from '../../shared/ui/Avatar'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { LoadingState } from '../../shared/ui/States'
import { TextAreaField, TextField } from '../../shared/ui/Field'
import { useSelectedBranch } from '../branches/selectedBranch'
import { CustomerPicker } from '../customers/CustomerPicker'
import { draftToChoice, duplicateMatches, type CustomerChoice } from '../customers/customerDraft'
import { ConsentChoice } from '../customers/ConsentChoice'
import { customerKeys, fetchCustomer, withConsent } from '../customers/customersApi'
import { useCustomerPicker } from '../customers/useCustomerPicker'
import { receptionRequest, type DeviceInput } from './repairLogic'
import { receiveRepairOrder, repairKeys } from './repairsApi'

const EMPTY_DEVICE: DeviceInput = {
  deviceType: '',
  brand: '',
  model: '',
  serialNumber: '',
  reportedFault: '',
  physicalCondition: '',
  accessories: '',
}

/**
 * FR-REP-001 / Phase 3.1: receive an appliance. Name and phone are typed directly and the picker
 * suggests existing customers; with no match the same data registers a new customer. The device
 * data is kept while the customer is being resolved, and the whole reception (customer + order +
 * history + audit) is one server transaction.
 */
export function ReceptionPage() {
  const { selected } = useSelectedBranch()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const presetCustomerId = params.get('customerId') ? Number(params.get('customerId')) : null

  const picker = useCustomerPicker()
  const [device, setDevice] = useState<DeviceInput>(EMPTY_DEVICE)
  // Consent is asked only when a new customer is registered here (C.1); existing customers
  // manage it in their record. Nothing is checked by default.
  const [consentChannels, setConsentChannels] = useState<ContactChannel[]>([])
  const operationId = useRef(newOperationId())

  // Coming from a customer's page: that customer is chosen once it loads.
  const preset = useQuery({
    queryKey: presetCustomerId ? customerKeys.detail(presetCustomerId) : ['customers', 'detail', 'none'],
    queryFn: ({ signal }) => fetchCustomer(presetCustomerId!, signal),
    enabled: presetCustomerId !== null,
  })
  const presetApplied = useRef(false)
  const { select } = picker
  useEffect(() => {
    if (preset.data && !presetApplied.current) {
      presetApplied.current = true
      select({ id: preset.data.id, fullName: preset.data.fullName, inScope: true, phone: preset.data.phone })
    }
  }, [preset.data, select])

  const receive = useMutation({
    mutationFn: (choice: CustomerChoice) =>
      receiveRepairOrder(
        receptionRequest(
          operationId.current,
          selected!.id,
          choice.kind === 'new' ? { ...choice, customer: withConsent(choice.customer, consentChannels) } : choice,
          device,
        ),
      ),
    onSuccess: async (order) => {
      await queryClient.invalidateQueries({ queryKey: repairKeys.all })
      await queryClient.invalidateQueries({ queryKey: customerKeys.all })
      navigate(`/repairs/${order.id}`, { state: { received: true } })
    },
    onError: (error) => {
      // The server answered, so nothing was stored: the next attempt is a new intent. After a
      // network error the outcome is unknown, so the same id is kept and a retry cannot duplicate.
      if (!(error instanceof NetworkError)) operationId.current = newOperationId()
    },
  })
  const errors = fieldErrorsOf(receive.error)
  const duplicates = receive.error instanceof ApiError ? duplicateMatches(receive.error.properties) : []

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (picker.choice) receive.mutate(picker.choice)
  }

  /** After a possible-duplicate answer: the user states this is a different person. */
  function confirmNewPerson() {
    const confirmed = { ...picker.draft, registerNew: true, confirmedNewPerson: true }
    picker.setDraft(confirmed)
    const choice = draftToChoice(confirmed, [])
    if (choice) receive.mutate(choice)
  }

  const setDeviceField = (key: keyof DeviceInput) => (event: { target: { value: string } }) =>
    setDevice((current) => ({ ...current, [key]: event.target.value }))
  const deviceReady =
    device.deviceType.trim() && device.brand.trim() && device.reportedFault.trim() && device.physicalCondition.trim()

  const missing = [
    !picker.choice && 'cliente',
    !device.deviceType.trim() && 'tipo de equipo',
    !device.brand.trim() && 'marca',
    !device.reportedFault.trim() && 'falla reportada',
    !device.physicalCondition.trim() && 'estado físico',
  ].filter((item): item is string => Boolean(item))

  if (!selected) {
    return <Alert tone="info">Selecciona en el encabezado la sucursal que recibe el equipo.</Alert>
  }

  return (
    <section className="page page-form">
      <ModuleSurface
        breadcrumb={[{ to: '/repairs', label: 'Reparaciones' }]}
        eyebrow="Recepción en mostrador"
        icon="clipboard"
        title="Recibir equipo"
        description="Registra al cliente y el estado del aparato. Se genera la orden con su código y el primer registro del historial."
        meta={
          <span className="chip">
            <Icon name="building" />
            Sucursal que recibe: {selected.name}
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
                <p>Escribe el nombre o el teléfono: te sugerimos clientes ya registrados. Si no existe, se registra con los mismos datos.</p>
              </div>
            </div>
            <div className="picker-zone">
              {presetCustomerId && preset.isPending ? (
                <LoadingState label="Cargando cliente…" />
              ) : (
                <CustomerPicker picker={picker} errors={errors} errorPrefix="newCustomer." disabled={receive.isPending} />
              )}
            </div>
            {picker.choice?.kind === 'new' && (
              <ConsentChoice
                channels={consentChannels}
                hasEmail={picker.draft.email.trim() !== ''}
                disabled={receive.isPending}
                onChange={setConsentChannels}
              />
            )}
            {duplicates.length > 0 && (
              <div role="alert">
                <Alert tone="warn" title="Ya hay clientes con ese teléfono o nombre.">
                  Elige uno o confirma que es otra persona. Los datos del equipo se conservan.
                </Alert>
                <ul className="match-list">
                  {duplicates.map((match) => (
                    <li key={match.id}>
                      <span className="person">
                        <Avatar name={match.fullName} size="sm" />
                        <span className="person-text">
                          <strong>{match.fullName}</strong>
                          <span className="muted small">
                            {match.matchedBy === 'NAME' ? 'Mismo nombre' : match.matchedBy === 'PHONE' ? 'Mismo teléfono' : 'Mismo nombre y teléfono'}
                            {!match.inScope && ', de otra sucursal'}
                          </span>
                        </span>
                      </span>
                      <button
                        type="button"
                        className="button button-secondary button-small"
                        onClick={() => {
                          receive.reset()
                          picker.select(match)
                        }}
                      >
                        Usar este cliente
                      </button>
                    </li>
                  ))}
                </ul>
                <button type="button" className="button button-secondary" disabled={receive.isPending} onClick={confirmNewPerson}>
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
                <h2>Equipo</h2>
                <p>El equipo queda en custodia de la sucursal; no forma parte del inventario.</p>
              </div>
            </div>
            <div>
              <h3 className="form-section-title">Identificación</h3>
              <div className="form-grid">
                <TextField label="Tipo de equipo" placeholder="Televisor, lavadora…" value={device.deviceType} maxLength={60} error={errors.deviceType} onChange={setDeviceField('deviceType')} />
                <TextField label="Marca" value={device.brand} maxLength={60} error={errors.brand} onChange={setDeviceField('brand')} />
                <TextField label="Modelo (opcional)" value={device.model} maxLength={80} error={errors.model} onChange={setDeviceField('model')} />
                <TextField label="Número de serie (opcional)" value={device.serialNumber} maxLength={80} error={errors.serialNumber} onChange={setDeviceField('serialNumber')} />
              </div>
            </div>
            <div>
              <h3 className="form-section-title">Problema y condición al recibir</h3>
              <div className="form-grid form-grid-2">
                <div className="span-all">
                  <TextAreaField label="Falla reportada por el cliente" value={device.reportedFault} maxLength={1000} error={errors.reportedFault} onChange={setDeviceField('reportedFault')} />
                </div>
                <TextAreaField label="Estado físico al recibir" hint="Golpes, rayones, piezas faltantes…" value={device.physicalCondition} maxLength={1000} error={errors.physicalCondition} onChange={setDeviceField('physicalCondition')} />
                <TextAreaField label="Accesorios entregados (opcional)" hint="Control, cables, base, manguera…" value={device.accessories} maxLength={500} error={errors.accessories} onChange={setDeviceField('accessories')} />
              </div>
            </div>
          </div>

          {receive.isError && duplicates.length === 0 && (
            <Alert tone="error">
              {describeError(receive.error)}
              {receive.error instanceof NetworkError && ' Puedes reintentar: no se registrará dos veces.'}
            </Alert>
          )}

          <div className="submit-bar">
            <p className="submit-status" aria-live="polite">
              <Icon name={missing.length === 0 ? 'checkCircle' : 'info'} size={18} />
              {missing.length === 0 ? 'Listo para registrar.' : `Falta: ${missing.join(', ')}.`}
            </p>
            <div className="form-actions">
              <Link className="button button-secondary" to="/repairs">
                Cancelar
              </Link>
              <button type="submit" className="button button-primary" disabled={receive.isPending || !picker.choice || !deviceReady}>
                {receive.isPending ? 'Registrando…' : 'Registrar recepción'}
              </button>
            </div>
          </div>
        </form>
      </ModuleSurface>
    </section>
  )
}
