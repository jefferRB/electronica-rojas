/**
 * Color palette preference (docs/design-system.md, "Paletas"). Purely visual and per browser:
 * stored in localStorage, no backend setting. Unknown or unavailable values fall back to the default.
 */
export const THEMES = [
  { id: 'electric', label: 'Azul eléctrico', swatch: ['#19283e', '#2563eb', '#0f766e'] },
  { id: 'petroleum', label: 'Petróleo profesional', swatch: ['#1b2a2f', '#0f766e', '#475569'] },
  { id: 'indigo', label: 'Índigo tecnológico', swatch: ['#1c1f3f', '#4f46e5', '#0e7490'] },
] as const

export type ThemeId = (typeof THEMES)[number]['id']

const STORAGE_KEY = 'electronica-rojas.theme'

function isTheme(value: unknown): value is ThemeId {
  return THEMES.some((theme) => theme.id === value)
}

export function readTheme(): ThemeId {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isTheme(stored) ? stored : 'electric'
  } catch {
    return 'electric'
  }
}

export function applyTheme(theme: ThemeId) {
  if (theme === 'electric') document.documentElement.removeAttribute('data-theme')
  else document.documentElement.setAttribute('data-theme', theme)
}

export function saveTheme(theme: ThemeId) {
  applyTheme(theme)
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // storage unavailable (private mode): the palette still applies to this page view
  }
}
