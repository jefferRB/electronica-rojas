/** Mirrors PortalSlugs on the server (BR-SRV-009); the server always decides. */
export const RESERVED_SLUGS = new Set([
  'admin', 'administracion', 'api', 'app', 'assets', 'auth', 'config', 'configuracion', 'dashboard',
  'estado', 'inicio', 'login', 'logout', 'new', 'nuevo', 'null', 'portal', 'public', 'publico', 'settings', 'static',
  'undefined', 'www',
])

const FORMAT = /^[a-z0-9]+(-[a-z0-9]+)*$/

/** Why a slug would be refused, in Spanish, or null when it is valid. */
export function slugProblem(slug: string): string | null {
  if (slug.length < 3 || slug.length > 60 || !FORMAT.test(slug)) {
    return 'Usa entre 3 y 60 letras minúsculas, números y guiones simples (sin espacios ni tildes).'
  }
  if (RESERVED_SLUGS.has(slug)) return 'Esa palabra la usa el sistema; elige otra.'
  return null
}

/** "Electrónica Pérez" -> "electronica-perez": a suggestion the administrator still confirms. */
export function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
    .replace(/-+$/g, '')
}
