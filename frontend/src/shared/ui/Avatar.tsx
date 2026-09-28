import { initials } from '../lib/initials'

/** Soft background / strong text pairs; every pair passes 4.5:1. */
const TONES = [
  ['#eaf1fe', '#1d4ed8'],
  ['#e6f4f2', '#0b5f58'],
  ['#f1ecfe', '#5b3fc4'],
  ['#fdf3e3', '#8a4f06'],
  ['#fdecee', '#a51d31'],
  ['#e8f6ee', '#146c36'],
  ['#eef1f5', '#3d4a5c'],
] as const

function toneOf(name: string) {
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return TONES[hash % TONES.length]
}

/** Initials avatar: the same person always gets the same color. Decorative; the name is shown beside it. */
export function Avatar({ name, size }: { name: string; size?: 'sm' | 'lg' }) {
  const [bg, fg] = toneOf(name)
  return (
    <span
      className={size ? `avatar avatar-${size}` : 'avatar'}
      aria-hidden="true"
      style={{ ['--avatar-bg' as string]: bg, ['--avatar-fg' as string]: fg }}
    >
      {initials(name)}
    </span>
  )
}
