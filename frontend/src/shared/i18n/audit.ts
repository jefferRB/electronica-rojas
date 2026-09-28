import {
  CHANNEL_LABELS,
  CONSENT_SOURCE_LABELS,
  NOTIFICATION_EVENT_LABELS,
  type ConsentSource,
  type ContactChannel,
  type NotificationEventType,
} from './consent'
import { formatColones, formatDateTime } from '../lib/format'
import {
  auditActionLabel,
  DECISION_METHOD_LABELS,
  KIND_LABELS,
  MOVEMENT_LABELS,
  REPAIR_STATUS_LABELS,
  ROLE_LABELS,
  units,
} from './labels'
import type { DecisionMethod, MovementType, ProductKind, RepairStatus, Role } from './labels'
import { EVENT_LABELS, OUTCOME_LABELS, type ServiceEventType, type VisitOutcome } from './serviceLabels'

/** The fields of an audit event this module needs (see features/audit/auditApi.ts). */
export interface AuditEventLike {
  action: string
  summary: string
  details: Record<string, unknown>
  detailsSource: 'RECORDED' | 'LEGACY' | 'NONE'
}

export interface AuditText {
  title: string
  description: string
  /** Original technical text, shown on demand for events recorded before structured details. */
  technical?: string
}

type Details = Record<string, unknown>

const str = (details: Details, key: string) => (typeof details[key] === 'string' ? (details[key] as string) : undefined)
const num = (details: Details, key: string) => (typeof details[key] === 'number' ? (details[key] as number) : undefined)
const bool = (details: Details, key: string) => (typeof details[key] === 'boolean' ? (details[key] as boolean) : undefined)

/** "San José Centro" when the name was recorded, otherwise the branch code ("SJ-01"). */
function branch(details: Details, prefix = ''): string {
  const name = str(details, `${prefix ? prefix + 'B' : 'b'}ranchName`)
  const code = str(details, `${prefix ? prefix + 'B' : 'b'}ranchCode`)
  return name ?? code ?? 'una sucursal'
}

function product(details: Details): string {
  const sku = str(details, 'sku')
  const name = str(details, 'productName')
  return sku ? `${sku}${name ? ` (${name})` : ''}` : 'un producto'
}

function activeText(details: Details): string {
  const active = bool(details, 'active')
  return active === undefined ? '' : active ? ' Estado: activo.' : ' Estado: inactivo.'
}

function order(details: Details): string {
  const code = str(details, 'orderCode')
  return code ? `la orden ${code}` : 'una orden de reparación'
}

function repairStatus(details: Details, key: string): string | undefined {
  const status = str(details, key) as RepairStatus | undefined
  return status ? (REPAIR_STATUS_LABELS[status] ?? status) : undefined
}

/** Amount recorded as a decimal string ("25000.50") to keep BigDecimal exactness. */
function amount(details: Details): string | undefined {
  const value = str(details, 'amount')
  return value !== undefined && !Number.isNaN(Number(value)) ? formatColones(Number(value)) : undefined
}

/** A money detail (JSON number or decimal string) in colones; "sin definir" when absent. */
function colones(details: Details, key: string): string {
  const value = details[key]
  const parsed = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN
  return Number.isNaN(parsed) ? 'sin definir' : formatColones(parsed)
}

const PORTAL_FIELD_LABELS: Record<string, string> = {
  enabled: 'activación',
  allowPreferredDate: 'fecha preferida',
  allowPreferredWindow: 'horario preferido',
  minNoticeDays: 'anticipación mínima',
  maxDaysAhead: 'días hacia adelante',
  serviceDays: 'días de atención',
  servedProvinces: 'zonas atendidas',
  serviceTypes: 'tipos de equipo',
  welcomeMessage: 'mensaje de bienvenida',
  successMessage: 'mensaje de confirmación',
}

/** "4 mar, 09:00" in Costa Rica for an ISO instant recorded in details. */
function when(details: Details, key: string): string | undefined {
  const value = str(details, key)
  return value ? formatDateTime(value) : undefined
}

function serviceEvent(details: Details): string | undefined {
  const event = str(details, 'event') as ServiceEventType | undefined
  const code = str(details, 'requestCode')
  if (!event || !code) return undefined
  const label = EVENT_LABELS[event] ?? event
  switch (event) {
    case 'VISIT_PROPOSED':
    case 'VISIT_CONFIRMED':
      return `${label} para la solicitud ${code}: ${when(details, 'start') ?? ''}${
        str(details, 'technicianName') ? ` con ${str(details, 'technicianName')}` : ''
      }.`
    case 'VISIT_RESCHEDULED':
      return `${label} (${code}): de ${when(details, 'previousStart') ?? '—'} a ${when(details, 'start') ?? '—'}${
        str(details, 'technicianName') ? `, técnico ${str(details, 'technicianName')}` : ''
      }.`
    case 'VISIT_COMPLETED': {
      const outcome = str(details, 'outcome') as VisitOutcome | undefined
      return `${label} (${code})${outcome ? `: ${OUTCOME_LABELS[outcome]?.toLowerCase() ?? outcome}` : ''}.`
    }
    case 'REPAIR_ORDER_LINKED':
      return `${label}: la solicitud ${code} pasó al taller con la orden ${str(details, 'orderCode') ?? ''}.`
    case 'BRANCH_CHANGED':
      return `${label}: la solicitud ${code} pasó de ${str(details, 'previousBranchName') ?? str(details, 'previousBranchCode') ?? '—'} a ${branch(details)}.`
    default:
      return `${label} (${code}).`
  }
}

type Describer = (details: Details) => string | undefined

const DESCRIBERS: Record<string, Describer> = {
  BRANCH_CREATED: (d) => `Se registró la sucursal ${branch(d)}.`,
  BRANCH_UPDATED: (d) => `Se actualizó la sucursal ${branch(d)}.${activeText(d)}`,
  USER_CREATED: (d) => {
    if (bool(d, 'bootstrap')) return 'Se creó el administrador inicial del sistema.'
    const role = str(d, 'role') as Role | undefined
    const count = num(d, 'branchCount')
    return `Se registró una cuenta${role ? ` con rol ${ROLE_LABELS[role] ?? role}` : ''}${
      count !== undefined && role !== 'ADMIN' ? ` y ${count} ${count === 1 ? 'sucursal asignada' : 'sucursales asignadas'}` : ''
    }.`
  },
  USER_UPDATED: (d) => {
    const role = str(d, 'role') as Role | undefined
    return `Se actualizó una cuenta${role ? `: rol ${ROLE_LABELS[role] ?? role}` : ''}.${activeText(d)}`
  },
  USER_PASSWORD_RESET: () => 'Un administrador restableció la contraseña de una cuenta. Sus sesiones abiertas se cerraron.',
  PRODUCT_CREATED: (d) => {
    const kind = str(d, 'kind') as ProductKind | undefined
    return `Se registró el producto ${product(d)}${kind ? ` como ${KIND_LABELS[kind]?.toLowerCase() ?? kind}` : ''}.`
  },
  PRODUCT_UPDATED: (d) => `Se actualizó el producto ${product(d)}.${activeText(d)}`,
  STOCK_MINIMUM_CHANGED: (d) => {
    const before = num(d, 'previousMinimum')
    const after = num(d, 'newMinimum')
    if (before === undefined || after === undefined) return undefined
    return `El mínimo del producto ${product(d)} en ${branch(d)} cambió de ${before} a ${after}.`
  },
  STOCK_MOVEMENT_RECORDED: (d) => {
    const type = str(d, 'movementType') as MovementType | undefined
    const quantity = num(d, 'quantity')
    const before = num(d, 'balanceBefore')
    const after = num(d, 'balanceAfter')
    if (!type || quantity === undefined) return undefined
    const balance = before !== undefined && after !== undefined ? ` Existencias: ${before} → ${after}.` : ''
    const label = bool(d, 'initialStock') ? 'Existencias iniciales' : (MOVEMENT_LABELS[type] ?? type)
    return `${label} de ${units(quantity)} del producto ${product(d)} en ${branch(d)}.${balance}`
  },
  PRODUCT_PRICING_CHANGED: (d) => {
    // Only what changed: "precio de venta ₡12 500,00 → ₡18 000,00".
    const parts: string[] = []
    const change = (label: string, before: string, after: string) => {
      if (colones(d, before) !== colones(d, after)) parts.push(`${label} ${colones(d, before)} → ${colones(d, after)}`)
    }
    change('precio de venta', 'salePriceBefore', 'salePriceAfter')
    change('costo', 'unitCostBefore', 'unitCostAfter')
    const before = bool(d, 'chargeableByDefaultBefore')
    const after = bool(d, 'chargeableByDefaultAfter')
    if (before !== undefined && after !== undefined && before !== after) {
      parts.push(after ? 'ahora se cobra por defecto' : 'ahora no se cobra por defecto')
    }
    return `Producto ${product(d)}: ${parts.join('; ')}. Aplica a operaciones nuevas; las reparaciones anteriores conservan su precio.`
  },
  PUBLIC_PORTAL_UPDATED: (d) => {
    const fields = Array.isArray(d.changedFields) ? (d.changedFields as string[]) : []
    const switched = bool(d, 'enabledChanged')
      ? bool(d, 'enabled')
        ? 'Se activó el portal público: vuelve a recibir solicitudes. '
        : 'Se desactivó el portal público: el enlace sigue activo pero no recibe solicitudes. '
      : ''
    const others = fields.filter((field) => field !== 'enabled').map((field) => PORTAL_FIELD_LABELS[field] ?? field)
    return `${switched}${others.length > 0 ? `Cambios en: ${others.join(', ')}.` : ''}`.trim() || undefined
  },
  PUBLIC_PORTAL_SLUG_CHANGED: (d) =>
    `La dirección del portal cambió de /solicitar/${str(d, 'previousSlug') ?? '—'} a /solicitar/${str(d, 'newSlug') ?? '—'}. La anterior sigue llevando al portal.`,
  STOCK_TRANSFER_COMPLETED: (d) => {
    const quantity = num(d, 'quantity')
    if (quantity === undefined) return undefined
    const direction = str(d, 'direction')
    const balanceAfter = num(d, 'balanceAfter')
    const where = direction === 'SENT' ? branch(d, 'source') : branch(d, 'destination')
    const balance = balanceAfter !== undefined ? ` Existencias finales en ${where}: ${balanceAfter}.` : ''
    return `Transferencia de ${units(quantity)} del producto ${product(d)} desde ${branch(d, 'source')} hacia ${branch(
      d,
      'destination',
    )}.${balance}`
  },
  // Customer events carry no personal data by design (BR-CUS-001): only where they were registered.
  CUSTOMER_CREATED: (d) => `Se registró un cliente en ${branch(d)}.`,
  CUSTOMER_UPDATED: () => 'Se actualizaron los datos de un cliente.',
  CUSTOMER_CONSENT_RECORDED: (d) => {
    const channel = str(d, 'channel') as ContactChannel | undefined
    const source = str(d, 'source') as ConsentSource | undefined
    const granted = bool(d, 'granted')
    if (!channel || granted === undefined) return undefined
    return `Un cliente ${granted ? 'aceptó' : 'retiró'} los avisos por ${CHANNEL_LABELS[channel]?.toLowerCase() ?? channel}${
      source ? ` (${CONSENT_SOURCE_LABELS[source]?.toLowerCase() ?? source})` : ''
    }${str(d, 'textVersion') ? `, texto ${str(d, 'textVersion')}` : ''}.`
  },
  NOTIFICATION_RETRY_REQUESTED: (d) => {
    const event = str(d, 'eventType') as NotificationEventType | undefined
    return `Se pidió reintentar el aviso «${event ? (NOTIFICATION_EVENT_LABELS[event] ?? event) : 'aviso'}»${
      str(d, 'reference') ? ` de ${str(d, 'reference')}` : ''
    } (${num(d, 'attempts') ?? 0} intentos previos).`
  },
  REPAIR_ORDER_RECEIVED: (d) => {
    const device = [str(d, 'deviceType'), str(d, 'brand')].filter(Boolean).join(' ')
    return `Se recibió ${device ? `un equipo (${device})` : 'un equipo'} con ${order(d)} en ${branch(d)}.`
  },
  REPAIR_STATUS_CHANGED: (d) => {
    const from = repairStatus(d, 'fromStatus')
    const to = repairStatus(d, 'toStatus')
    if (!to) return undefined
    return `${order(d)[0].toUpperCase()}${order(d).slice(1)} pasó ${from ? `de «${from}» ` : ''}a «${to}».`
  },
  REPAIR_TECHNICIAN_ASSIGNED: (d) => {
    const technician = str(d, 'technicianName')
    const previous = str(d, 'previousTechnicianName')
    if (!technician) return undefined
    return `Se asignó ${order(d)} a ${technician}${previous ? ` (antes: ${previous})` : ''}.`
  },
  REPAIR_DIAGNOSIS_UPDATED: (d) => `Se actualizó el diagnóstico de ${order(d)}.`,
  REPAIR_QUOTE_CREATED: (d) => {
    const total = amount(d)
    return `Se emitió una cotización${total ? ` de ${total}` : ''} para ${order(d)}.`
  },
  SERVICE_REQUEST_SUBMITTED: (d) =>
    `Se recibió la solicitud a domicilio ${str(d, 'requestCode') ?? ''} para ${branch(d)}${
      str(d, 'channel') === 'STAFF' ? ' (registrada por el personal)' : ' (formulario público)'
    }.`,
  SERVICE_REQUEST_UPDATED: serviceEvent,
  SERVICE_VISIT_UPDATED: serviceEvent,
  TECHNICIAN_SCHEDULE_UPDATED: (d) =>
    `Se actualizó el horario de ${str(d, 'technicianName') ?? 'un técnico'} (${num(d, 'workingDays') ?? 0} días laborales).`,
  SERVICE_SETTINGS_UPDATED: (d) =>
    `Visitas en ${branch(d)}: duración estimada ${num(d, 'defaultVisitMinutes') ?? '—'} min, margen ${num(d, 'bufferMinutes') ?? '—'} min.`,
  REPAIR_PART_CONSUMED: (d) => {
    const quantity = num(d, 'quantity')
    if (quantity === undefined) return undefined
    const before = num(d, 'balanceBefore')
    const after = num(d, 'balanceAfter')
    const balance = before !== undefined && after !== undefined ? ` Existencias: ${before} → ${after}.` : ''
    const charge =
      bool(d, 'chargeable') === false
        ? ' Sin cargo al cliente.'
        : bool(d, 'priceOverridden')
          ? ` Precio ajustado para esta orden: ${colones(d, 'unitPrice')} por unidad (catálogo: ${colones(d, 'catalogPrice')}).`
          : ''
    return `Se utilizaron ${units(quantity)} del repuesto ${product(d)} en ${order(d)} (${branch(d)}).${balance}${charge}`
  },
  REPAIR_PART_RETURNED: (d) => {
    const quantity = num(d, 'quantity')
    if (quantity === undefined) return undefined
    const closed = bool(d, 'orderClosed') ? ` con la orden ya cerrada («${repairStatus(d, 'orderStatus') ?? ''}»)` : ''
    const remaining = num(d, 'remainingQuantity')
    return `Se devolvieron al inventario ${units(quantity)} del repuesto ${product(d)} de ${order(d)}${closed}.${
      remaining !== undefined ? ` Siguen contando como utilizadas: ${remaining}.` : ''
    }`
  },
  REPAIR_QUOTE_DECIDED: (d) => {
    const decision = str(d, 'decision')
    const method = str(d, 'method') as DecisionMethod | undefined
    if (decision !== 'APPROVED' && decision !== 'REJECTED') return undefined
    const total = amount(d)
    return `El cliente ${decision === 'APPROVED' ? 'aprobó' : 'rechazó'} la cotización${total ? ` de ${total}` : ''} de ${order(
      d,
    )}${method ? ` (${DECISION_METHOD_LABELS[method]?.toLowerCase() ?? method})` : ''}.`
  },
}

/**
 * Spanish title and description for an audit event. Events without usable data (unknown legacy
 * formats) get a generic sentence plus the original technical text; nothing is invented.
 */
export function describeAuditEvent(event: AuditEventLike): AuditText {
  const title = auditActionLabel(event.action)
  const described = event.detailsSource !== 'NONE' ? DESCRIBERS[event.action]?.(event.details) : undefined
  if (described) {
    return event.detailsSource === 'LEGACY' ? { title, description: described, technical: event.summary } : { title, description: described }
  }
  return {
    title,
    description: `${title}. Registro anterior sin detalle estructurado; consulta el texto técnico original.`,
    technical: event.summary,
  }
}
