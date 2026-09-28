import { parseAmount } from '../../shared/lib/money'
import type { CustomerChoice } from '../customers/customerDraft'
import type { ReceptionInput } from './repairsApi'

/**
 * Quote amount typed in colones: the shared money convention (shared/lib/money.ts), and strictly
 * positive (BR-REP-010).
 */
export function parseColones(input: string): number | null {
  const value = parseAmount(input)
  return value !== null && value > 0 ? value : null
}

export interface DeviceInput {
  deviceType: string
  brand: string
  model: string
  serialNumber: string
  reportedFault: string
  physicalCondition: string
  accessories: string
}

const optional = (value: string) => (value.trim() ? value.trim() : undefined)

/**
 * Builds the reception request. An existing customer outside the caller's branches is sent with
 * the phone the customer just gave, which the server checks against the one on file.
 */
export function receptionRequest(
  operationId: string,
  branchId: number,
  choice: CustomerChoice,
  device: DeviceInput,
): ReceptionInput {
  const customer =
    choice.kind === 'existing'
      ? { customerId: choice.match.id, customerPhone: choice.match.inScope ? undefined : choice.phone }
      : { newCustomer: choice.customer }
  return {
    operationId,
    branchId,
    ...customer,
    deviceType: device.deviceType.trim(),
    brand: device.brand.trim(),
    model: optional(device.model),
    serialNumber: optional(device.serialNumber),
    reportedFault: device.reportedFault.trim(),
    physicalCondition: device.physicalCondition.trim(),
    accessories: optional(device.accessories),
  }
}

/** Most units of one part line (the server's limit, mirrored by a CHECK in V10). */
export const MAX_PART_QUANTITY = 1000

/**
 * Spanish error for the quantity of a part consumption or return, or undefined when valid:
 * a whole number from 1 to {@code available} (stock on the shelf, or units still counted as used).
 */
/**
 * Subtotal of a part line being recorded: price x quantity when charged; 0 when not charged (the
 * part is still used and discounted from stock); null when it is charged but has no price yet.
 */
export function partLineSubtotal(quantity: number, unitPrice: number | null, chargeable: boolean): number | null {
  if (!chargeable) return 0
  if (unitPrice === null || !Number.isInteger(quantity) || quantity < 1) return null
  // Cents as integers: 3 x 0.1 is 0.3, not 0.30000000000000004.
  return (Math.round(unitPrice * 100) * quantity) / 100
}

export function partQuantityError(input: string, available: number): string | undefined {
  const text = input.trim()
  if (!/^\d+$/.test(text)) return 'Ingresa una cantidad entera.'
  const value = Number(text)
  if (value < 1) return 'La cantidad debe ser al menos 1.'
  if (value > MAX_PART_QUANTITY) return `La cantidad máxima por registro es ${MAX_PART_QUANTITY}.`
  if (value > available) return `Solo hay ${available} disponible${available === 1 ? '' : 's'}.`
  return undefined
}
