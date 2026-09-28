// WCAG 2.2 contrast check of the design tokens (docs/design-system.md, "Accesibilidad").
// Reads src/styles/tokens.css, resolves every palette (default + data-theme overrides) and checks the
// text/background pairs the components use: 4.5:1 for text, 3:1 for focus rings, control borders and
// status marks. Exits with 1 when a pair fails, so it can run in CI.
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const css = readFileSync(fileURLToPath(new URL('../src/styles/tokens.css', import.meta.url)), 'utf8')

function block(selector) {
  const start = css.indexOf(`${selector} {`)
  if (start < 0) return {}
  const body = css.slice(start, css.indexOf('\n}', start))
  const vars = {}
  for (const match of body.matchAll(/(--[\w-]+):\s*([^;]+);/g)) vars[match[1]] = match[2].trim()
  return vars
}

const base = block(':root')
const palettes = {
  electric: base,
  petroleum: { ...base, ...block(":root[data-theme='petroleum']") },
  indigo: { ...base, ...block(":root[data-theme='indigo']") },
}

function resolve(vars, name) {
  let value = vars[name]
  for (let depth = 0; value && value.startsWith('var(') && depth < 5; depth++) value = vars[value.slice(4, -1).trim()]
  if (!value || !/^#[0-9a-f]{6}$/i.test(value)) throw new Error(`${name} is not a plain hex color: ${value}`)
  return value
}

function luminance(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function ratio(a, b) {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (light + 0.05) / (dark + 0.05)
}

const TEXT = 4.5
const UI = 3
const pairs = [
  ['--c-text', '--c-bg', TEXT, 'Texto principal sobre fondo'],
  ['--c-text', '--c-surface-2', TEXT, 'Texto principal sobre superficie secundaria'],
  ['--c-text-2', '--c-surface', TEXT, 'Texto secundario sobre tarjeta'],
  ['--c-text-2', '--c-bg', TEXT, 'Texto secundario sobre fondo'],
  ['--c-text-2', '--c-surface-2', TEXT, 'Texto secundario sobre superficie secundaria'],
  ['--c-primary', '--c-surface', TEXT, 'Enlaces sobre tarjeta'],
  ['--c-primary', '--c-bg', TEXT, 'Enlaces sobre fondo'],
  ['--c-on-primary', '--c-primary', TEXT, 'Botón principal'],
  ['--c-on-primary', '--c-primary-hover', TEXT, 'Botón principal (hover)'],
  ['--c-primary-soft-text', '--c-primary-soft', TEXT, 'Pestaña activa / insignia informativa'],
  ['--c-primary-soft-text', '--c-surface', TEXT, 'Pestaña activa sobre blanco'],
  ['--c-accent-soft-text', '--c-accent-soft', TEXT, 'Acento sobre acento suave'],
  ['--c-accent-soft-text', '--c-bg', TEXT, 'Etiqueta de sección (eyebrow)'],
  ['--c-ok-text', '--c-ok-soft', TEXT, 'Insignia de éxito'],
  ['--c-warn-text', '--c-warn-soft', TEXT, 'Insignia de advertencia'],
  ['--c-error-text', '--c-error-soft', TEXT, 'Insignia de error'],
  ['--c-error-text', '--c-surface', TEXT, 'Botón de peligro / error de campo'],
  ['--c-muted-text', '--c-muted-soft', TEXT, 'Insignia neutra'],
  ['--c-nav-text', '--c-nav', TEXT, 'Navegación: texto'],
  ['--c-nav-muted', '--c-nav', TEXT, 'Navegación: enlaces inactivos'],
  ['--c-nav-indicator', '--c-nav', TEXT, 'Navegación: etiqueta del inicio'],
  ['--c-nav-focus', '--c-nav', UI, 'Navegación: anillo de foco'],
  ['--c-focus', '--c-surface', UI, 'Anillo de foco sobre tarjeta'],
  ['--c-focus', '--c-bg', UI, 'Anillo de foco sobre fondo'],
  ['--c-border-control', '--c-surface', UI, 'Borde de campos de formulario'],
  ['--c-error', '--c-surface', UI, 'Borde de campo con error'],
  ['--c-warn', '--c-surface', UI, 'Marca de advertencia (regla, borde discontinuo)'],
  ['--c-ok', '--c-surface', UI, 'Marca de éxito'],
]

let failures = 0
for (const [palette, vars] of Object.entries(palettes)) {
  console.log(`\nPaleta ${palette}`)
  const hero = [...(vars['--g-hero'] ?? '').matchAll(/#[0-9a-f]{6}/gi)].map((m) => m[0])
  const checks = [...pairs, ...hero.flatMap((stop) => [
    ['--c-nav-text', stop, TEXT, `Inicio: texto sobre degradado ${stop}`],
    ['--c-nav-muted', stop, TEXT, `Inicio: texto secundario sobre degradado ${stop}`],
    ['--c-nav-indicator', stop, TEXT, `Inicio: etiqueta sobre degradado ${stop}`],
  ])]
  for (const [fg, bg, min, label] of checks) {
    const a = resolve(vars, fg)
    const b = bg.startsWith('#') ? bg : resolve(vars, bg)
    const value = ratio(a, b)
    const ok = value >= min
    if (!ok) failures++
    console.log(`${ok ? '  ok  ' : '  FAIL'} ${value.toFixed(2).padStart(5)}:1 (mín. ${min}) ${label}`)
  }
}
console.log(failures === 0 ? '\nTodas las combinaciones cumplen WCAG 2.2 AA.' : `\n${failures} combinaciones no cumplen.`)
process.exit(failures === 0 ? 0 : 1)
