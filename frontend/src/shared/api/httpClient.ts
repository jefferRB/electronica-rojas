/**
 * Single entry point for HTTP calls to the backend API (ER-ARCH-001 section 9).
 *
 * - Relative URLs only: Vite proxies /api in development; production serves frontend and
 *   backend from the same origin, so the session cookie is sent with `same-origin`.
 * - CSRF (double-submit): mutating requests copy the XSRF-TOKEN cookie into the
 *   X-XSRF-TOKEN header. If the cookie is missing (first visit, or just rotated by
 *   login/logout) it is fetched from /api/v1/auth/csrf first.
 * - Errors are RFC 9457 ProblemDetail bodies turned into ApiError.
 */

export interface FieldError {
  field: string
  /** Stable code (constraint name or business code), translated in shared/i18n/errors.ts. */
  code?: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: FieldError[]
  /** Extra ProblemDetail members, e.g. `available` on an insufficient-stock 409. */
  readonly properties: Record<string, unknown>

  constructor(status: number, message: string, fieldErrors: FieldError[] = [], properties: Record<string, unknown> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
    this.properties = properties
  }
}

export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('No se pudo conectar con el servidor', { cause })
    this.name = 'NetworkError'
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  const match = document.cookie.split('; ').find((part) => part.startsWith(prefix))
  return match ? decodeURIComponent(match.slice(prefix.length)) : null
}

async function csrfToken(): Promise<string> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await send('/api/v1/auth/csrf', { method: 'GET' })
    token = readCookie(CSRF_COOKIE)
  }
  if (!token) throw new ApiError(0, 'No se obtuvo el token CSRF')
  return token
}

async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(path, { ...init, credentials: 'same-origin' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new NetworkError(error)
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes('json')) {
    try {
      const problem = (await response.json()) as { detail?: string; errors?: FieldError[] } & Record<string, unknown>
      return new ApiError(response.status, problem.detail ?? `HTTP ${response.status}`, problem.errors ?? [], problem)
    } catch {
      // fall through to the generic error
    }
  }
  return new ApiError(response.status, `HTTP ${response.status}`)
}

interface RequestOptions {
  json?: unknown
  form?: Record<string, string>
  signal?: AbortSignal
}

async function request<T>(method: 'GET' | 'POST' | 'PUT', path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: BodyInit | undefined

  if (method !== 'GET') {
    headers[CSRF_HEADER] = await csrfToken()
  }
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  } else if (options.form) {
    body = new URLSearchParams(options.form)
  }

  const response = await send(path, { method, headers, body, signal: options.signal })
  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const http = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>('GET', path, { signal }),
  post: <T>(path: string, json?: unknown) => request<T>('POST', path, { json }),
  put: <T>(path: string, json: unknown) => request<T>('PUT', path, { json }),
  postForm: <T>(path: string, form: Record<string, string>) => request<T>('POST', path, { form }),
}
