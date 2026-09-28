import qrcode from 'qrcode-generator'

/**
 * QR codes for the portal link, generated in the browser (no web service: the business URL is never
 * sent to a third party). Deterministic: the same text always gives the same modules.
 * Error correction "M" (15 %) survives smudges on printed paper without making the code dense.
 */
export interface QrMatrix {
  size: number
  isDark: (row: number, col: number) => boolean
}

/** Quiet zone the QR specification requires around the code, in modules. */
export const QUIET_ZONE = 4

export function qrMatrix(text: string): QrMatrix {
  const code = qrcode(0, 'M')
  code.addData(text, 'Byte')
  code.make()
  return { size: code.getModuleCount(), isDark: (row, col) => code.isDark(row, col) }
}

/** One SVG path with a 1x1 square per dark module, offset by the quiet zone. */
export function qrPath(matrix: QrMatrix): string {
  const parts: string[] = []
  for (let row = 0; row < matrix.size; row++) {
    for (let col = 0; col < matrix.size; col++) {
      if (matrix.isDark(row, col)) parts.push(`M${col + QUIET_ZONE} ${row + QUIET_ZONE}h1v1h-1z`)
    }
  }
  return parts.join('')
}

/** Standalone SVG document for printing: scalable, black on white, quiet zone included. */
export function qrSvgDocument(matrix: QrMatrix): string {
  const extent = matrix.size + QUIET_ZONE * 2
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${extent} ${extent}" width="${extent * 10}" height="${extent * 10}" shape-rendering="crispEdges">` +
    `<rect width="${extent}" height="${extent}" fill="#ffffff"/><path fill="#000000" d="${qrPath(matrix)}"/></svg>`
  )
}

/**
 * RGBA pixels of the code (quiet zone included) at `scale` pixels per module: what a PNG export
 * draws, and what the tests decode to check that the code carries the exact URL.
 */
export function qrPixels(matrix: QrMatrix, scale: number): { data: Uint8ClampedArray<ArrayBuffer>; width: number } {
  const width = (matrix.size + QUIET_ZONE * 2) * scale
  const data = new Uint8ClampedArray(width * width * 4).fill(255)
  for (let row = 0; row < matrix.size; row++) {
    for (let col = 0; col < matrix.size; col++) {
      if (!matrix.isDark(row, col)) continue
      for (let y = 0; y < scale; y++) {
        for (let x = 0; x < scale; x++) {
          const offset = (((row + QUIET_ZONE) * scale + y) * width + (col + QUIET_ZONE) * scale + x) * 4
          data[offset] = 0
          data[offset + 1] = 0
          data[offset + 2] = 0
        }
      }
    }
  }
  return { data, width }
}

/** PNG of the code via a canvas (about 1 000 px wide, enough for print). */
export function qrPngBlob(matrix: QrMatrix): Promise<Blob | null> {
  const scale = Math.max(8, Math.floor(1000 / (matrix.size + QUIET_ZONE * 2)))
  const { data, width } = qrPixels(matrix, scale)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = width
  const context = canvas.getContext('2d')
  if (!context) return Promise.resolve(null)
  context.putImageData(new ImageData(data, width, width), 0, 0)
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
}

/** Saves a Blob with a readable name (no server round trip). */
export function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Give the browser a moment to start the download before releasing the object URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function qrFileName(slug: string, extension: 'png' | 'svg'): string {
  return `electronica-rojas-solicitudes-${slug}.${extension}`
}
