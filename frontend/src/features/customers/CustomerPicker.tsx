import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { describeError } from '../../shared/api/describeError'
import { TextAreaField, TextField } from '../../shared/ui/Field'
import { hasNewCustomerData, willRegisterNew } from './customerDraft'
import { formatPhone, type CustomerCandidate } from './customersApi'
import type { CustomerPickerState } from './useCustomerPicker'

interface CustomerPickerProps {
  picker: CustomerPickerState
  /** Field errors of the last save, keyed by server field name. */
  errors?: Record<string, string>
  /** Prefix of the server field names of the inline customer, e.g. "newCustomer.". */
  errorPrefix?: string
  disabled?: boolean
}

/**
 * Reusable customer picker (Phase 3.1): name and phone are typed directly; from 3 letters or
 * digits the server suggests matching customers of the user's branches. Choosing one links its
 * id; editing a field afterwards unlinks it. With no match, the same fields register a new
 * customer when the form is saved (in the same server transaction as the order).
 *
 * Accessibility: both inputs follow the ARIA 1.2 combobox pattern over one shared listbox
 * (arrow keys, Enter, Escape; click outside closes; results count announced politely).
 */
export function CustomerPicker({ picker, errors = {}, errorPrefix = '', disabled }: CustomerPickerProps) {
  const { draft, candidates } = picker
  const listboxId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const options = candidates ?? []
  const expanded = open && !draft.selected && options.length > 0

  // Clicking anywhere outside the picker closes the list.
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent | TouchEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('touchstart', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('touchstart', close)
    }
  }, [open])

  const activeIndex = active < options.length ? active : -1

  function choose(candidate: CustomerCandidate) {
    picker.select(candidate)
    setOpen(false)
    setActive(-1)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      if (expanded) event.preventDefault()
      setOpen(false)
    } else if (event.key === 'ArrowDown' && options.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActive((activeIndex + 1) % options.length)
    } else if (event.key === 'ArrowUp' && options.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActive(activeIndex <= 0 ? options.length - 1 : activeIndex - 1)
    } else if (event.key === 'Enter' && expanded && activeIndex >= 0) {
      // Enter picks the highlighted customer instead of submitting the form.
      event.preventDefault()
      choose(options[activeIndex])
    }
  }

  const comboboxProps = {
    role: 'combobox',
    'aria-autocomplete': 'list' as const,
    'aria-expanded': expanded,
    'aria-controls': listboxId,
    'aria-activedescendant': expanded && activeIndex >= 0 ? `${listboxId}-${activeIndex}` : undefined,
    autoComplete: 'off',
    disabled,
    onKeyDown,
    onFocus: () => setOpen(true),
  }
  const error = (key: string) => errors[`${errorPrefix}${key}`] ?? errors[key]
  const registering = willRegisterNew(draft, picker.settled ? candidates : undefined)
  const registerChecked = draft.registerNew ?? registering
  const showNewOption = !draft.selected && (draft.name.trim() !== '' || draft.phone.trim() !== '')

  return (
    <div className="customer-picker" ref={containerRef}>
      <div className="form-grid customer-picker-fields">
        <TextField
          label="Nombre del cliente"
          value={draft.name}
          maxLength={160}
          error={error('fullName')}
          {...comboboxProps}
          onChange={(event) => {
            picker.edit({ name: event.target.value })
            setOpen(true)
            setActive(-1)
          }}
        />
        <TextField
          label="Teléfono del cliente"
          type="tel"
          inputMode="tel"
          value={draft.phone}
          maxLength={30}
          hint="8 dígitos de Costa Rica o número internacional con +."
          error={error('phone') ?? error('customerPhone')}
          {...comboboxProps}
          onChange={(event) => {
            picker.edit({ phone: event.target.value })
            setOpen(true)
            setActive(-1)
          }}
        />
      </div>

      {expanded && (
        <ul className="picker-results customer-options" role="listbox" id={listboxId} aria-label="Clientes encontrados">
          {options.map((candidate, index) => (
            <li
              key={candidate.id}
              id={`${listboxId}-${index}`}
              role="option"
              aria-selected={index === activeIndex}
              className={index === activeIndex ? 'active' : undefined}
              // mousedown would blur the input first; keep focus and choose on click.
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(candidate)}
            >
              <strong>{candidate.fullName}</strong>
              {candidate.inScope ? (
                <span className="muted small">
                  {' '}
                  · {candidate.phone ? formatPhone(candidate.phone) : ''}
                  {candidate.registeredBranch ? ` · ${candidate.registeredBranch.name}` : ''}
                </span>
              ) : (
                <span className="muted small"> · cliente de otra sucursal (coincide el teléfono)</span>
              )}
            </li>
          ))}
        </ul>
      )}

      <p className="visually-hidden" aria-live="polite">
        {!draft.selected && picker.settled
          ? options.length === 0
            ? 'No se encontraron clientes.'
            : `${options.length} ${options.length === 1 ? 'cliente encontrado' : 'clientes encontrados'}.`
          : ''}
      </p>
      {picker.isSearching && <p className="field-hint">Buscando clientes…</p>}
      {picker.error != null && <p className="field-error">{describeError(picker.error)}</p>}
      {picker.moreInScope > 0 && !draft.selected && (
        <p className="field-hint">Hay {picker.moreInScope} coincidencias más: escribe más datos para acotar.</p>
      )}

      {draft.selected ? (
        <div className="picked">
          <span className="status-line">
            <span className="badge badge-ok">Cliente existente</span>
            <strong>{draft.selected.fullName}</strong>
            {!draft.selected.inScope && <span className="muted small">de otra sucursal, verificado por teléfono</span>}
          </span>
          <button
            type="button"
            className="button button-secondary button-small"
            disabled={disabled}
            onClick={() => {
              picker.clearSelection()
              setOpen(true)
            }}
          >
            Cambiar cliente
          </button>
        </div>
      ) : (
        showNewOption && (
          <div className="subsection-tight">
            {picker.settled && options.length === 0 && <p className="muted small">No hay clientes con esos datos en tus sucursales.</p>}
            <label className="checkbox">
              <input
                type="checkbox"
                checked={registerChecked}
                disabled={disabled}
                onChange={(event) => picker.setDraft({ ...draft, registerNew: event.target.checked })}
              />{' '}
              Registrar como cliente nuevo
            </label>
            {registerChecked && !hasNewCustomerData(draft) && (
              <p className="field-hint">Para registrarlo, escribe el nombre y un teléfono completo.</p>
            )}
            {registerChecked && (
              <details className="optional-fields">
                <summary>Datos adicionales (opcional)</summary>
                <div className="form-grid">
                  <TextField
                    label="Correo electrónico"
                    type="email"
                    value={draft.email}
                    maxLength={254}
                    autoComplete="off"
                    error={error('email')}
                    onChange={(event) => picker.setDraft({ ...draft, email: event.target.value })}
                  />
                  <TextField
                    label="Dirección"
                    value={draft.address}
                    maxLength={300}
                    autoComplete="off"
                    error={error('address')}
                    onChange={(event) => picker.setDraft({ ...draft, address: event.target.value })}
                  />
                </div>
                <TextAreaField
                  label="Notas internas"
                  rows={2}
                  maxLength={1000}
                  value={draft.internalNotes}
                  hint="Solo para el personal. No anotes datos sensibles."
                  error={error('internalNotes')}
                  onChange={(event) => picker.setDraft({ ...draft, internalNotes: event.target.value })}
                />
              </details>
            )}
          </div>
        )
      )}
    </div>
  )
}
