/** Initials for avatars: "Sofía Jiménez Rojas" → "SR"; a single word → its first two letters. */
export function initials(name: string): string {
  const words = name
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .split(/\s+/)
    .filter(Boolean)
  if (words.length === 0) return '?'
  const first = words[0][0]
  const last = words.length > 1 ? words[words.length - 1][0] : (words[0][1] ?? '')
  return (first + last).toUpperCase()
}
