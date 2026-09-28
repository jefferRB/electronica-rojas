/**
 * Spanish (Costa Rica) labels for every stable code the API returns. The backend keeps English
 * identifiers (they are part of the contract and of stored history); only this module turns them
 * into user-facing text, so no component hard-codes translations.
 */

export type Role = 'ADMIN' | 'BRANCH_MANAGER' | 'RECEPTIONIST' | 'TECHNICIAN'
export type ProductKind = 'MERCHANDISE' | 'SPARE_PART'
export type MovementType =
  | 'RECEIPT'
  | 'ISSUE'
  | 'ADJUSTMENT_IN'
  | 'ADJUSTMENT_OUT'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'OUT_FOR_REPAIR'
  | 'RETURN_FROM_REPAIR'
export type StockStatus = 'OUT_OF_STOCK' | 'LOW' | 'NORMAL'
export type RepairStatus =
  | 'RECEIVED'
  | 'DIAGNOSING'
  | 'AWAITING_APPROVAL'
  | 'APPROVED'
  | 'IN_REPAIR'
  | 'READY_FOR_PICKUP'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'UNREPAIRABLE'
export type Resolution = 'REPAIRED' | 'CANCELLED' | 'UNREPAIRABLE'
export type QuoteStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type DecisionMethod = 'IN_PERSON' | 'PHONE' | 'EMAIL' | 'MESSAGE'

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrador',
  BRANCH_MANAGER: 'Encargado de sucursal',
  RECEPTIONIST: 'Recepcionista',
  TECHNICIAN: 'Técnico',
}

export const KIND_LABELS: Record<ProductKind, string> = {
  MERCHANDISE: 'Producto de venta',
  SPARE_PART: 'Repuesto',
}

export const MOVEMENT_LABELS: Record<MovementType, string> = {
  RECEIPT: 'Entrada de inventario',
  ISSUE: 'Salida de inventario',
  ADJUSTMENT_IN: 'Ajuste positivo',
  ADJUSTMENT_OUT: 'Ajuste negativo',
  TRANSFER_OUT: 'Transferencia enviada',
  TRANSFER_IN: 'Transferencia recibida',
  OUT_FOR_REPAIR: 'Consumo en reparación',
  RETURN_FROM_REPAIR: 'Devolución de reparación',
}

export const STOCK_STATUS_LABELS: Record<StockStatus, string> = {
  OUT_OF_STOCK: 'Sin existencias',
  LOW: 'Bajo mínimo',
  NORMAL: 'Existencias normales',
}

/** CSS badge variant per stock status (text always accompanies the color). */
export const STOCK_STATUS_TONES: Record<StockStatus, 'error' | 'warn' | 'ok'> = {
  OUT_OF_STOCK: 'error',
  LOW: 'warn',
  NORMAL: 'ok',
}

export const REPAIR_STATUS_LABELS: Record<RepairStatus, string> = {
  RECEIVED: 'Recibida',
  DIAGNOSING: 'En diagnóstico',
  AWAITING_APPROVAL: 'Esperando aprobación',
  APPROVED: 'Aprobada',
  IN_REPAIR: 'En reparación',
  READY_FOR_PICKUP: 'Lista para entrega',
  DELIVERED: 'Entregada',
  CANCELLED: 'Cancelada',
  UNREPAIRABLE: 'Sin reparación posible',
}

/** Badge variant per repair status (text always accompanies the color). */
export const REPAIR_STATUS_TONES: Record<RepairStatus, 'ok' | 'warn' | 'error' | 'info' | 'muted'> = {
  RECEIVED: 'info',
  DIAGNOSING: 'info',
  AWAITING_APPROVAL: 'warn',
  APPROVED: 'info',
  IN_REPAIR: 'info',
  READY_FOR_PICKUP: 'ok',
  DELIVERED: 'muted',
  CANCELLED: 'error',
  UNREPAIRABLE: 'error',
}

/** Button text for a manual transition: the action, not the resulting state. */
export const REPAIR_TRANSITION_ACTIONS: Record<RepairStatus, string> = {
  RECEIVED: 'Volver a recibida',
  DIAGNOSING: 'Iniciar diagnóstico',
  AWAITING_APPROVAL: 'Solicitar aprobación',
  APPROVED: 'Aprobar',
  IN_REPAIR: 'Iniciar reparación',
  READY_FOR_PICKUP: 'Marcar lista para entrega',
  DELIVERED: 'Registrar entrega al cliente',
  CANCELLED: 'Cancelar orden',
  UNREPAIRABLE: 'Declarar sin reparación posible',
}

export const RESOLUTION_LABELS: Record<Resolution, string> = {
  REPAIRED: 'Reparado',
  CANCELLED: 'Cancelado',
  UNREPAIRABLE: 'Sin reparación posible',
}

export const QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  PENDING: 'Pendiente de decisión',
  APPROVED: 'Aprobada por el cliente',
  REJECTED: 'Rechazada por el cliente',
}

export const QUOTE_STATUS_TONES: Record<QuoteStatus, 'warn' | 'ok' | 'error'> = {
  PENDING: 'warn',
  APPROVED: 'ok',
  REJECTED: 'error',
}

export const DECISION_METHOD_LABELS: Record<DecisionMethod, string> = {
  IN_PERSON: 'En persona',
  PHONE: 'Por teléfono',
  EMAIL: 'Por correo electrónico',
  MESSAGE: 'Por mensaje',
}

export const AUDIT_ACTION_LABELS: Record<string, string> = {
  BRANCH_CREATED: 'Sucursal registrada',
  BRANCH_UPDATED: 'Sucursal actualizada',
  USER_CREATED: 'Usuario registrado',
  USER_UPDATED: 'Usuario actualizado',
  USER_PASSWORD_RESET: 'Contraseña restablecida',
  PRODUCT_CREATED: 'Producto registrado',
  PRODUCT_UPDATED: 'Producto actualizado',
  PRODUCT_PRICING_CHANGED: 'Precio o costo de producto modificado',
  STOCK_MINIMUM_CHANGED: 'Mínimo de existencias modificado',
  STOCK_MOVEMENT_RECORDED: 'Movimiento de inventario',
  STOCK_TRANSFER_COMPLETED: 'Transferencia completada',
  CUSTOMER_CREATED: 'Cliente registrado',
  CUSTOMER_UPDATED: 'Cliente actualizado',
  CUSTOMER_CONSENT_RECORDED: 'Preferencia de avisos del cliente',
  REPAIR_ORDER_RECEIVED: 'Equipo recibido para reparación',
  REPAIR_STATUS_CHANGED: 'Cambio de estado de reparación',
  REPAIR_TECHNICIAN_ASSIGNED: 'Técnico asignado',
  REPAIR_DIAGNOSIS_UPDATED: 'Diagnóstico actualizado',
  REPAIR_QUOTE_CREATED: 'Cotización emitida',
  REPAIR_QUOTE_DECIDED: 'Decisión sobre cotización',
  REPAIR_PART_CONSUMED: 'Repuesto utilizado en reparación',
  REPAIR_PART_RETURNED: 'Corrección de repuesto',
  SERVICE_REQUEST_SUBMITTED: 'Solicitud a domicilio recibida',
  SERVICE_REQUEST_UPDATED: 'Solicitud a domicilio actualizada',
  SERVICE_VISIT_UPDATED: 'Visita a domicilio actualizada',
  TECHNICIAN_SCHEDULE_UPDATED: 'Horario de técnico actualizado',
  SERVICE_SETTINGS_UPDATED: 'Parámetros de visitas actualizados',
  PUBLIC_PORTAL_UPDATED: 'Portal público actualizado',
  PUBLIC_PORTAL_SLUG_CHANGED: 'Dirección del portal público cambiada',
  NOTIFICATION_RETRY_REQUESTED: 'Reintento de aviso solicitado',
}

export const AUDIT_ENTITY_LABELS: Record<string, string> = {
  BRANCH: 'Sucursales',
  USER: 'Usuarios',
  PRODUCT: 'Productos',
  BRANCH_STOCK: 'Mínimos de existencias',
  STOCK_MOVEMENT: 'Movimientos de inventario',
  STOCK_TRANSFER: 'Transferencias',
  CUSTOMER: 'Clientes',
  CUSTOMER_CONSENT: 'Preferencias de avisos',
  REPAIR_ORDER: 'Órdenes de reparación',
  REPAIR_QUOTE: 'Cotizaciones',
  REPAIR_PART: 'Repuestos utilizados',
  REPAIR_PART_RETURN: 'Correcciones de repuestos',
  SERVICE_REQUEST: 'Solicitudes a domicilio',
  SERVICE_VISIT: 'Visitas a domicilio',
  TECHNICIAN_SCHEDULE: 'Horarios de técnicos',
  SERVICE_SETTINGS: 'Parámetros de visitas',
  PUBLIC_PORTAL: 'Portal público',
  NOTIFICATION: 'Notificaciones',
}

/** Label for an audit action code; unknown future codes are shown as-is rather than hidden. */
export function auditActionLabel(action: string): string {
  return AUDIT_ACTION_LABELS[action] ?? action
}

/** "1 unidad" / "4 unidades". */
export function units(quantity: number): string {
  return `${quantity} ${Math.abs(quantity) === 1 ? 'unidad' : 'unidades'}`
}
