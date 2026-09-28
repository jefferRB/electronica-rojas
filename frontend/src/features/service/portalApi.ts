import { http } from '../../shared/api/httpClient'
import type { Province } from '../../shared/i18n/serviceLabels'
import type { PersonRef, PublicBranch } from './serviceApi'

/** Mirrors ServiceDtos.PublicPortalRules: dates are local Costa Rica days computed by the server. */
export interface PublicPortalRules {
  allowPreferredDate: boolean
  allowPreferredWindow: boolean
  earliestDate: string
  latestDate: string
  /** ISO weekdays, 1 = Monday. */
  serviceDays: number[]
  servedProvinces: Province[]
  serviceTypes: string[]
}

/**
 * Mirrors ServiceDtos.PublicPortal: only what the anonymous page renders. `slug` is the current one,
 * also when the page was opened through a previous address. While paused, `accepting` is false and
 * there are no rules or branches.
 */
export interface PublicPortal {
  slug: string
  accepting: boolean
  welcomeMessage: string | null
  successMessage: string | null
  rules: PublicPortalRules | null
  branches: PublicBranch[]
}

/** Mirrors ServiceDtos.PortalSettingsView (ADMIN). */
export interface PortalSettings {
  enabled: boolean
  slug: string
  allowPreferredDate: boolean
  allowPreferredWindow: boolean
  minNoticeDays: number
  maxDaysAhead: number
  serviceDays: number[]
  servedProvinces: Province[]
  serviceTypes: string[]
  welcomeMessage: string | null
  successMessage: string | null
  previousSlugs: string[]
  updatedAt: string
  updatedBy: PersonRef | null
  version: number
}

export type PortalSettingsInput = Omit<PortalSettings, 'slug' | 'previousSlugs' | 'updatedAt' | 'updatedBy'>

/** Limits shared with the server (ServiceDtos.PortalSettingsRequest). */
export const PORTAL_LIMITS = {
  welcomeMessage: 300,
  successMessage: 500,
  serviceType: 60,
  serviceTypes: 20,
  minNoticeDays: 30,
  maxDaysAhead: 180,
  slugMin: 3,
  slugMax: 60,
} as const

export const portalKeys = {
  settings: ['portal', 'settings'] as const,
  public: (slug: string | null) => ['public', 'portal', slug ?? ''] as const,
}

/** `slug` null = the current portal (links made before slugs existed). 404 for an unknown slug. */
export const fetchPublicPortal = (slug: string | null, signal?: AbortSignal) =>
  http.get<PublicPortal>(slug ? `/api/v1/public/portal/${encodeURIComponent(slug)}` : '/api/v1/public/portal', signal)

export const fetchPortalSettings = (signal?: AbortSignal) => http.get<PortalSettings>('/api/v1/portal-settings', signal)

export const updatePortalSettings = (input: PortalSettingsInput) =>
  http.put<PortalSettings>('/api/v1/portal-settings', input)

export const changePortalSlug = (slug: string, version: number) =>
  http.put<PortalSettings>('/api/v1/portal-settings/slug', { slug, version })

/** The public path of the portal; the full URL uses the site's own origin (no hard-coded domain). */
export function portalPath(slug: string): string {
  return `/solicitar/${slug}`
}

export function portalUrl(slug: string, origin: string = window.location.origin): string {
  return `${origin}${portalPath(slug)}`
}
