import jsQR from 'jsqr'
import { describe, expect, it } from 'vitest'
import { QUIET_ZONE, qrFileName, qrMatrix, qrPath, qrPixels, qrSvgDocument } from './qr'

const URL = 'http://localhost:5173/solicitar/servicio-a-domicilio'

describe('portal QR code', () => {
  it('encodes exactly the portal URL (decoded back with an independent reader)', () => {
    const { data, width } = qrPixels(qrMatrix(URL), 6)
    const decoded = jsQR(data, width, width)
    expect(decoded?.data).toBe(URL)
  })

  it('is deterministic and changes when the address changes', () => {
    expect(qrPath(qrMatrix(URL))).toBe(qrPath(qrMatrix(URL)))
    expect(qrPath(qrMatrix(`${URL}-2`))).not.toBe(qrPath(qrMatrix(URL)))
  })

  it('keeps the quiet zone white around the code', () => {
    const matrix = qrMatrix(URL)
    const { data, width } = qrPixels(matrix, 1)
    const band = QUIET_ZONE * width
    // First QUIET_ZONE rows are all white (R channel 255).
    for (let pixel = 0; pixel < band; pixel++) expect(data[pixel * 4]).toBe(255)
    expect(width).toBe(matrix.size + QUIET_ZONE * 2)
  })

  it('exports a standalone printable SVG and readable file names', () => {
    const svg = qrSvgDocument(qrMatrix(URL))
    expect(svg).toMatch(/^<svg xmlns="http:\/\/www.w3.org\/2000\/svg"/)
    expect(svg).toContain('fill="#ffffff"')
    expect(qrFileName('servicio-a-domicilio', 'png')).toBe('electronica-rojas-solicitudes-servicio-a-domicilio.png')
  })
})
