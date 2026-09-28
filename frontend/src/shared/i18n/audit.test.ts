import { describe, expect, it } from 'vitest'
import { describeAuditEvent } from './audit'

describe('describeAuditEvent', () => {
  it('describes a recorded transfer in Spanish with branch names', () => {
    const text = describeAuditEvent({
      action: 'STOCK_TRANSFER_COMPLETED',
      summary: 'Transfer of 4 x CMP-100 from SJ-01 to AL-01 (received, balance 4)',
      detailsSource: 'RECORDED',
      details: {
        direction: 'RECEIVED',
        quantity: 4,
        sku: 'CMP-100',
        sourceBranchCode: 'SJ-01',
        sourceBranchName: 'San José Centro',
        destinationBranchCode: 'AL-01',
        destinationBranchName: 'Alajuela Centro',
        balanceAfter: 4,
      },
    })

    expect(text.title).toBe('Transferencia completada')
    expect(text.description).toBe(
      'Transferencia de 4 unidades del producto CMP-100 desde San José Centro hacia Alajuela Centro. Existencias finales en Alajuela Centro: 4.',
    )
    expect(text.technical).toBeUndefined()
  })

  it('uses branch codes for legacy events and keeps the original text available', () => {
    const text = describeAuditEvent({
      action: 'STOCK_MOVEMENT_RECORDED',
      summary: 'RECEIPT 10 x CMP-100 at SJ-01 (balance 0 -> 10)',
      detailsSource: 'LEGACY',
      details: { movementType: 'RECEIPT', quantity: 10, sku: 'CMP-100', branchCode: 'SJ-01', balanceBefore: 0, balanceAfter: 10 },
    })

    expect(text.title).toBe('Movimiento de inventario')
    expect(text.description).toBe('Entrada de inventario de 10 unidades del producto CMP-100 en SJ-01. Existencias: 0 → 10.')
    expect(text.technical).toBe('RECEIPT 10 x CMP-100 at SJ-01 (balance 0 -> 10)')
  })

  it('falls back to a generic Spanish sentence without inventing data', () => {
    const text = describeAuditEvent({ action: 'PRODUCT_CREATED', summary: 'weird old text', detailsSource: 'NONE', details: {} })

    expect(text.title).toBe('Producto registrado')
    expect(text.description).toContain('Registro anterior sin detalle estructurado')
    expect(text.technical).toBe('weird old text')
  })

  it('describes price changes with only what changed and that history keeps its prices', () => {
    const text = describeAuditEvent({
      action: 'PRODUCT_PRICING_CHANGED',
      summary: 'Pricing of product MOT-001 changed',
      detailsSource: 'RECORDED',
      details: { sku: 'MOT-001', salePriceBefore: 12500, salePriceAfter: 18000, unitCostBefore: 8000, unitCostAfter: 8000 },
    })

    expect(text.title).toBe('Precio o costo de producto modificado')
    expect(text.description).toMatch(/^Producto MOT-001: precio de venta .*12.500,00 → .*18.000,00\. Aplica a operaciones nuevas/)
    expect(text.description).not.toContain('costo')
  })

  it('says when the public portal was paused and when a part was used without charge', () => {
    const portal = describeAuditEvent({
      action: 'PUBLIC_PORTAL_UPDATED',
      summary: 'x',
      detailsSource: 'RECORDED',
      details: { enabled: false, enabledChanged: true, changedFields: ['enabled', 'welcomeMessage'] },
    })
    expect(portal.description).toBe(
      'Se desactivó el portal público: el enlace sigue activo pero no recibe solicitudes. Cambios en: mensaje de bienvenida.',
    )

    const part = describeAuditEvent({
      action: 'REPAIR_PART_CONSUMED',
      summary: 'x',
      detailsSource: 'RECORDED',
      details: { quantity: 1, sku: 'MOT-001', orderCode: 'OR-2026-000001', branchName: 'San José Centro', chargeable: false },
    })
    expect(part.description).toContain('Sin cargo al cliente.')
  })

  it('shows unknown future action codes as-is instead of hiding them', () => {
    expect(describeAuditEvent({ action: 'SOMETHING_NEW', summary: 'x', detailsSource: 'NONE', details: {} }).title).toBe('SOMETHING_NEW')
  })
})
