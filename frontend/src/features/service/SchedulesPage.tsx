import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { WEEKDAY_LABELS } from '../../shared/i18n/serviceLabels'
import { Alert } from '../../shared/ui/Alert'
import { Icon } from '../../shared/ui/Icon'
import { ModuleSurface } from '../../shared/ui/ModuleSurface'
import { TextField } from '../../shared/ui/Field'
import { useDisclosure } from '../../shared/ui/useDisclosure'
import { useSelectedBranch } from '../branches/selectedBranch'
import { shortTime } from './crTime'
import {
  fetchSchedules,
  fetchServiceSettings,
  saveServiceSettings,
  saveWeek,
  serviceKeys,
  type ShiftInput,
  type TechnicianSchedule,
} from './serviceApi'

/**
 * Working hours per technician (one shift per weekday, optional break) and the branch's visit
 * defaults. Configuration of the selected branch; days a technician works at another branch are
 * shown but not editable here.
 */
export function SchedulesPage() {
  const { selected } = useSelectedBranch()
  const schedules = useQuery({
    queryKey: selected ? serviceKeys.schedules(selected.id) : ['service', 'schedules', 'none'],
    queryFn: ({ signal }) => fetchSchedules(selected!.id, signal),
    enabled: selected !== null,
  })
  const [editingId, setEditingId] = useState<number | null>(null)
  const [saved, setSaved] = useState<string | null>(null)

  if (!selected) return <Alert tone="info">Selecciona una sucursal en el encabezado.</Alert>

  return (
    <section className="page">
      <ModuleSurface
        breadcrumb={[{ to: '/agenda', label: 'Agenda' }]}
        eyebrow="Servicio a domicilio"
        icon="clock"
        title="Horarios de técnicos"
        description="Jornada por día, descanso y parámetros de visita. Cambiar un horario no mueve visitas ya confirmadas."
        meta={
          <span className="chip">
            <Icon name="building" />
            {selected.name}
          </span>
        }
      >
        {saved && <Alert tone="success">{saved}</Alert>}
        <SettingsCard branchId={selected.id} onSaved={() => setSaved('Parámetros de visitas guardados.')} />

        <section className="module-section" aria-labelledby="schedules-technicians">
          <div className="module-section-title">
            <h2 id="schedules-technicians">Técnicos</h2>
          </div>
          {schedules.isPending && <p>Cargando horarios…</p>}
          {schedules.isError && <Alert tone="error">{describeError(schedules.error)}</Alert>}
          {schedules.data?.length === 0 && <p className="muted">No hay técnicos activos en esta sucursal.</p>}
          {schedules.data?.map((schedule) => (
            <div key={schedule.technician.id} className="subsection-tight">
              <div className="card-title-row">
                <h3>{schedule.technician.fullName}</h3>
                <button
                  type="button"
                  className="button button-secondary button-small"
                  disabled={editingId !== null}
                  onClick={() => {
                    setSaved(null)
                    setEditingId(schedule.technician.id)
                  }}
                >
                  Editar horario
                </button>
              </div>
              {editingId === schedule.technician.id ? (
                <WeekForm
                  schedule={schedule}
                  branchId={selected.id}
                  onCancel={() => setEditingId(null)}
                  onSaved={() => {
                    setEditingId(null)
                    setSaved(`Horario de ${schedule.technician.fullName} guardado.`)
                  }}
                />
              ) : (
                <WeekSummary schedule={schedule} />
              )}
            </div>
          ))}
        </section>
      </ModuleSurface>
    </section>
  )
}

function WeekSummary({ schedule }: { schedule: TechnicianSchedule }) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            {WEEKDAY_LABELS.map((day) => (
              <th key={day} scope="col">
                {day}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr>
            {WEEKDAY_LABELS.map((day, index) => {
              const shift = schedule.shifts.find((candidate) => candidate.dayOfWeek === index + 1)
              return (
                <td key={day} data-label={day}>
                  {shift ? (
                    <>
                      {shortTime(shift.start)}–{shortTime(shift.end)}
                      {shift.breakStart && (
                        <span className="muted small">
                          {' '}
                          (descanso {shortTime(shift.breakStart)}–{shortTime(shift.breakEnd)})
                        </span>
                      )}
                      <span className="muted small"> · {shift.branch.code}</span>
                    </>
                  ) : (
                    <span className="muted">Libre</span>
                  )}
                </td>
              )
            })}
          </tr>
        </tbody>
      </table>
    </div>
  )
}

interface DayRow {
  works: boolean
  start: string
  end: string
  breakStart: string
  breakEnd: string
  /** Branch of an existing shift at another branch: shown, not editable here. */
  otherBranch: string | null
}

function WeekForm({ schedule, branchId, onCancel, onSaved }: { schedule: TechnicianSchedule; branchId: number; onCancel: () => void; onSaved: () => void }) {
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<DayRow[]>(() =>
    WEEKDAY_LABELS.map((_, index) => {
      const shift = schedule.shifts.find((candidate) => candidate.dayOfWeek === index + 1)
      const foreign = shift && shift.branch.id !== branchId
      return {
        works: shift !== undefined,
        start: shortTime(shift?.start) || '08:00',
        end: shortTime(shift?.end) || '17:00',
        breakStart: shortTime(shift?.breakStart),
        breakEnd: shortTime(shift?.breakEnd),
        otherBranch: foreign ? shift.branch.name : null,
      }
    }),
  )
  const save = useMutation({
    mutationFn: () =>
      saveWeek(
        schedule.technician.id,
        rows.flatMap((row, index): ShiftInput[] =>
          // Days at other branches are kept by the server; only this branch's days are sent.
          row.works && row.otherBranch === null
            ? [
                {
                  dayOfWeek: index + 1,
                  branchId,
                  start: row.start,
                  end: row.end,
                  breakStart: row.breakStart || undefined,
                  breakEnd: row.breakEnd || undefined,
                },
              ]
            : [],
        ),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
      onSaved()
    },
  })
  const update = (index: number, change: Partial<DayRow>) => setRows((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)))

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form className="card card-highlight" onSubmit={submit} noValidate aria-label={`Horario de ${schedule.technician.fullName}`}>
      {save.isError && <Alert tone="error">{describeError(save.error)}</Alert>}
      {fieldErrorsOf(save.error).shifts && <p className="field-error">{fieldErrorsOf(save.error).shifts}</p>}
      {rows.map((row, index) => (
        <fieldset key={WEEKDAY_LABELS[index]} className="shift-row">
          <legend className="visually-hidden">{WEEKDAY_LABELS[index]}</legend>
          <label className="checkbox shift-day">
            <input type="checkbox" checked={row.works} disabled={row.otherBranch !== null} onChange={(event) => update(index, { works: event.target.checked })} />{' '}
            {WEEKDAY_LABELS[index]}
          </label>
          {row.otherBranch ? (
            <span className="muted small">Trabaja en {row.otherBranch}</span>
          ) : (
            row.works && (
              <div className="shift-times">
                <TextField label="Entrada" type="time" value={row.start} onChange={(event) => update(index, { start: event.target.value })} />
                <TextField label="Salida" type="time" value={row.end} onChange={(event) => update(index, { end: event.target.value })} />
                <TextField label="Descanso desde" type="time" value={row.breakStart} onChange={(event) => update(index, { breakStart: event.target.value })} />
                <TextField label="hasta" type="time" value={row.breakEnd} onChange={(event) => update(index, { breakEnd: event.target.value })} />
              </div>
            )
          )}
        </fieldset>
      ))}
      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar horario'}
        </button>
        <button type="button" className="button button-secondary" disabled={save.isPending} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

function SettingsCard({ branchId, onSaved }: { branchId: number; onSaved: () => void }) {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: serviceKeys.settings(branchId), queryFn: ({ signal }) => fetchServiceSettings(branchId, signal) })
  const edit = useDisclosure()
  const [duration, setDuration] = useState('')
  const [buffer, setBuffer] = useState('')
  const save = useMutation({
    mutationFn: () => saveServiceSettings(branchId, { defaultVisitMinutes: Number(duration), bufferMinutes: Number(buffer) }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: serviceKeys.all })
      edit.hide()
      onSaved()
    },
  })
  const errors = fieldErrorsOf(save.error)

  return (
    <section className="module-section module-section-tonal" aria-labelledby="schedules-settings">
      <div className="module-section-title">
        <h2 id="schedules-settings">Parámetros de visitas</h2>
        <button
          type="button"
          className="button button-secondary button-small"
          {...edit.triggerProps}
          onClick={() => {
            setDuration(String(settings.data?.defaultVisitMinutes ?? 90))
            setBuffer(String(settings.data?.bufferMinutes ?? 30))
            edit.triggerProps.onClick()
          }}
        >
          Editar
        </button>
      </div>
      {settings.data && (
        <p>
          Duración estimada de una visita: <strong>{settings.data.defaultVisitMinutes} min</strong> · margen entre visitas (traslado):{' '}
          <strong>{settings.data.bufferMinutes} min</strong>
        </p>
      )}
      {edit.open && (
        <div {...edit.panelProps}>
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              save.mutate()
            }}
          >
            {save.isError && <Alert tone="error">{describeError(save.error)}</Alert>}
            <div className="form-grid">
              <TextField label="Duración estimada (min)" type="number" min={15} max={480} step={15} value={duration} error={errors.defaultVisitMinutes} onChange={(event) => setDuration(event.target.value)} />
              <TextField label="Margen entre visitas (min)" type="number" min={0} max={240} step={5} value={buffer} error={errors.bufferMinutes} onChange={(event) => setBuffer(event.target.value)} />
            </div>
            <div className="form-actions">
              <button type="submit" className="button button-primary" disabled={save.isPending}>
                Guardar
              </button>
              <button type="button" className="button button-secondary" onClick={edit.hide}>
                Cancelar
              </button>
            </div>
          </form>
        </div>
      )}
    </section>
  )
}
