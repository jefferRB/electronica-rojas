import { useMemo } from 'react'
import { QUIET_ZONE, qrMatrix, qrPath } from './qr'

/**
 * The QR code of a URL as inline SVG: sharp at any size and printable. Always black on white with
 * its quiet zone, whatever the palette: scanners need that contrast (a deliberate exception to the
 * token-only colors of ER-DS-001).
 */
export function QrCode({ text, label }: { text: string; label: string }) {
  const matrix = useMemo(() => qrMatrix(text), [text])
  const extent = matrix.size + QUIET_ZONE * 2
  return (
    <svg
      className="qr-code"
      viewBox={`0 0 ${extent} ${extent}`}
      role="img"
      aria-label={label}
      shapeRendering="crispEdges"
      data-modules={matrix.size}
    >
      <rect width={extent} height={extent} fill="#ffffff" />
      <path d={qrPath(matrix)} fill="#000000" />
    </svg>
  )
}
