import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { ApiError, NetworkError } from '../../shared/api/httpClient'
import { newOperationId } from '../../shared/lib/format'
import { Alert } from '../../shared/ui/Alert'
import { SelectField, TextField } from '../../shared/ui/Field'
import { addDays, crDate, crTime, crToday, crToInstant, shortTime } from './crTime'
import {
  fetchAvailability,
  fetchServiceTechnicians,
  rescheduleVisit,
  scheduleVisit,
  serviceKeys,
  type Visit,
} from './serviceApi'

interface ScheduleVisitFormProps {
  branchId: number
  requestId: number
  /** Present when moving an existing visit. */
  visit?: Visit
  preferredDate?: string | null
  onDone: (visit: Visit) => void
  onCancel: () => void
}

/**
 * Propose / confirm a visit, or move one. Free start times come from the server (shift, break,
 * confirmed visits and margin); the server re-checks everything when saving, so a slot taken
 * meanwhile is answered with a clear conflict and fresh suggestions.
 */
export function ScheduleVisitForm({ branchId, requestId, visit, preferredDate, onDone, onCancel }: ScheduleVisitFormProps) {
  const queryClient = useQueryClient()
  const tomorrow = addDays(crToday(), 1)
  const [technicianId, setTechnicianId] = useState(visit ? String(visit.technician.id) : '')
  const [date, setDate] = useState(visit ? crDate(visit.start) : preferredDate && preferredDate >= tomorrow ? preferredDate : tomorrow)
  const [time, setTime] = useState(visit ? crTime(visit.start) : '')
  const [duration, setDuration] = useState(
    visit ? String(Math.round((Date.parse(visit.end) - Date.parse(visit.start)) / 60000)) : '',
  )
  const [confirm, setConfirm] = useState(false)
  const [reason, setReason] = useState('')
  const operationId = useRef(newOperationId())

  const technicians = useQuery({
    queryKey: serviceKeys.technicians(branchId),
    queryFn: ({ signal }) => fetchServiceTechnicians(branchId, signal),
  })
  const durationValue = duration ? Number(duration) : undefined
  const availability = useQuery({
    queryKey: serviceKeys.availability(Number(technicianId), date, durationValue),
    queryFn: ({ signal }) => fetchAvailability(Number(technicianId), date, durationValue, signal),
    enabled: technicianId !== '' && date !== '',
  })

  const save = useMutation({
    mutationFn: () => {
      const start = crToInstant(date, time)
      return visit
        ? rescheduleVisit(visit.id, { technicianId: Number(technicianId), start, durationMinutes: durationValue, reason: reason.trim() || undefined })
        : scheduleVisit(requestId, { operationId: operationId.current, technicianId: Number(technicianId), start, durationMinutes: durationValue, confirm })
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
      onDone(saved)
    },
    onError: async (error) => {
      if (!(error instanceof NetworkError)) operationId.current = newOperationId()
      // A conflict means the agenda changed: refresh the suggestions.
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: ['service', 'availability'] })
      }
    },
  })
  const errors = fieldErrorsOf(save.error)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    save.mutate()
  }

  const shift = availability.data?.shift
  return (
    <form className="card card-highlight" onSubmit={submit} noValidate aria-label={visit ? 'Reprogramar visita' : 'Programar visita'}>
      <h2>{visit ? 'Reprogramar visita' : 'Programar visita'}</h2>
      {save.isError && <Alert tone="error">{describeError(save.error)}</Alert>}
      <div className="form-grid">
        <SelectField label="Técnico" value={technicianId} error={errors.technicianId} onChange={(event) => setTechnicianId(event.target.value)}>
          <option value="">{technicians.isPending ? 'Cargando…' : 'Selecciona un técnico'}</option>
          {technicians.data?.map((technician) => (
            <option key={technician.id} value={technician.id}>
              {technician.fullName}
            </option>
          ))}
        </SelectField>
        <TextField label="Fecha" type="date" min={crToday()} value={date} onChange={(event) => setDate(event.target.value)} />
        <TextField
          label="Hora de inicio"
          type="time"
          step={300}
          value={time}
          error={errors.start}
          onChange={(event) => setTime(event.target.value)}
        />
        <TextField
          label="Duración estimada (min)"
          type="number"
          min={15}
          max={480}
          step={15}
          placeholder={availability.data ? String(availability.data.durationMinutes) : 'Según la sucursal'}
          value={duration}
          error={errors.durationMinutes}
          onChange={(event) => setDuration(event.target.value)}
        />
      </div>
      {technicians.data?.length === 0 && <Alert tone="info">No hay técnicos activos en esta sucursal.</Alert>}

      {technicianId && availability.data && (
        <div className="subsection-tight">
          {shift ? (
            <p className="muted small">
              Jornada: {shortTime(shift.start)}–{shortTime(shift.end)}
              {shift.breakStart && ` (descanso ${shortTime(shift.breakStart)}–${shortTime(shift.breakEnd)})`} · margen entre visitas{' '}
              {availability.data.bufferMinutes} min
            </p>
          ) : (
            <p className="muted small">El técnico no trabaja ese día.</p>
          )}
          {shift && availability.data.freeStarts.length === 0 && <p className="muted small">No quedan horarios libres ese día.</p>}
          {availability.data.freeStarts.length > 0 && (
            <div className="slot-list" role="group" aria-label="Horarios libres">
              {availability.data.freeStarts.map((start) => (
                <button
                  key={start}
                  type="button"
                  className={`button button-small ${shortTime(start) === time ? 'button-primary' : 'button-secondary'}`}
                  aria-pressed={shortTime(start) === time}
                  onClick={() => setTime(shortTime(start))}
                >
                  {shortTime(start)}
                </button>
              ))}
            </div>
          )}
          {availability.data.confirmed.length > 0 && (
            <p className="muted small">
              Ocupado:{' '}
              {availability.data.confirmed.map((busy) => `${crTime(busy.start)}–${crTime(busy.end)} (${busy.requestCode})`).join(', ')}
            </p>
          )}
        </div>
      )}

      {visit ? (
        <TextField label="Motivo del cambio (opcional)" value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />
      ) : (
        <label className="checkbox">
          <input type="checkbox" checked={confirm} onChange={(event) => setConfirm(event.target.checked)} /> El cliente ya aceptó este
          horario (confirmar ahora). Si no, queda como propuesta y no reserva la hora del técnico.
        </label>
      )}

      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={save.isPending || !technicianId || !date || !time}>
          {save.isPending ? 'Guardando…' : visit ? 'Guardar nuevo horario' : confirm ? 'Confirmar visita' : 'Proponer visita'}
        </button>
        <button type="button" className="button button-secondary" disabled={save.isPending} onClick={onCancel}>
          Volver
        </button>
      </div>
    </form>
  )
}
