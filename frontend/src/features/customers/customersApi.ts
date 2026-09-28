import type { Page } from '../../shared/api/page'
import { http } from '../../shared/api/httpClient'
import { CONSENT_TEXT_VERSION, type ConsentSource, type ContactChannel } from '../../shared/i18n/consent'
import type { BranchSummary } from '../branches/branchesApi'

/** Mirrors CustomerDtos.CustomerResponse. `phone` is E.164 (+50688887777). */
export interface Customer {
  id: number
  fullName: string
  phone: string
  email: string | null
  address: string | null
  internalNotes: string | null
  registeredBranch: BranchSummary
  createdAt: string
  updatedAt: string
  version: number
}

/** The minimal identity every match or candidate has (enough to link an order). */
export interface CustomerRef {
  id: number
  fullName: string
  /** False for a customer of another branch, known only through an exact phone match. */
  inScope: boolean
}

/** Mirrors CustomerDtos.CustomerMatch: a possible duplicate reported by the server. */
export interface CustomerMatch extends CustomerRef {
  matchedBy: 'PHONE' | 'NAME' | 'PHONE_AND_NAME'
}

/**
 * Mirrors CustomerDtos.CustomerCandidate (incremental lookup). Contact data is null for a
 * customer of another branch: the phone the user typed is the proof used at reception.
 */
export interface CustomerCandidate extends CustomerRef {
  phone: string | null
  email: string | null
  registeredBranch: BranchSummary | null
  nameMatch: boolean
  phoneMatch: boolean
}

export interface CustomerLookup {
  candidates: CustomerCandidate[]
  /** How many more in-scope customers match: ask to keep typing rather than paging. */
  moreInScope: number
}

/** Mirrors CustomerDtos.NewCustomer. */
export interface NewCustomer {
  fullName: string
  phone: string
  email?: string
  address?: string
  internalNotes?: string
  /** Explicit confirmation after a POSSIBLE_DUPLICATE_CUSTOMER answer. */
  allowDuplicatePhone?: boolean
  /** Channels the customer explicitly accepted now; absent = no consent (never implied). */
  consent?: ConsentGrant
}

export interface ConsentGrant {
  channels: ContactChannel[]
  source: Exclude<ConsentSource, 'PUBLIC_FORM'>
  textVersion: string
}

/** Mirrors CustomerDtos.ConsentStatement. */
export interface ConsentStatement {
  id: number
  channel: ContactChannel
  granted: boolean
  source: ConsentSource
  textVersion: string | null
  reference: string | null
  statedAt: string
  recordedBy: { id: number; fullName: string }
  recordedAt: string
}

export interface CustomerConsents {
  customerId: number
  hasEmail: boolean
  currentTextVersion: string
  channels: { channel: ContactChannel; latest: ConsentStatement | null }[]
  history: ConsentStatement[]
}

export interface CustomerUpdate {
  fullName: string
  phone: string
  email?: string
  address?: string
  internalNotes?: string
  version: number
}

/** Raw form state shared by the customer forms (strings as typed). */
export interface CustomerFormValues {
  fullName: string
  phone: string
  email: string
  address: string
  internalNotes: string
}

export const EMPTY_CUSTOMER: CustomerFormValues = { fullName: '', phone: '', email: '', address: '', internalNotes: '' }

export const customerKeys = {
  all: ['customers'] as const,
  search: (search: string, page: number) => ['customers', 'search', search, page] as const,
  detail: (id: number) => ['customers', 'detail', id] as const,
  lookup: (name: string, phone: string) => ['customers', 'lookup', name, phone] as const,
  consents: (id: number) => ['customers', 'consents', id] as const,
}

export const fetchCustomers = (search: string, page: number, signal?: AbortSignal) => {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  if (search.trim()) params.set('search', search.trim())
  return http.get<Page<Customer>>(`/api/v1/customers?${params}`, signal)
}

export const fetchCustomer = (id: number, signal?: AbortSignal) => http.get<Customer>(`/api/v1/customers/${id}`, signal)

/** Incremental search (name words and/or phone fragment), within the caller's scope. */
export function fetchCustomerLookup(name: string, phone: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ size: '8' })
  if (name) params.set('name', name)
  if (phone) params.set('phone', phone)
  return http.get<CustomerLookup>(`/api/v1/customers/lookup?${params}`, signal)
}

export const createCustomer = (branchId: number, customer: NewCustomer) =>
  http.post<Customer>('/api/v1/customers', { branchId, customer })

export const fetchConsents = (customerId: number, signal?: AbortSignal) =>
  http.get<CustomerConsents>(`/api/v1/customers/${customerId}/consents`, signal)

export const recordConsent = (
  customerId: number,
  input: { channel: ContactChannel; granted: boolean; source: Exclude<ConsentSource, 'PUBLIC_FORM'>; textVersion?: string },
) => http.post<CustomerConsents>(`/api/v1/customers/${customerId}/consents`, input)

/**
 * Adds the channels the customer accepted to a new customer (reception or customer form). No
 * channel checked = no consent at all; an unavailable channel (no e-mail) is dropped.
 */
export function withConsent(customer: NewCustomer, channels: ContactChannel[], source: ConsentGrant['source'] = 'IN_PERSON'): NewCustomer {
  const usable = channels.filter((channel) => channel !== 'EMAIL' || Boolean(customer.email?.trim()))
  return usable.length === 0 ? customer : { ...customer, consent: { channels: usable, source, textVersion: CONSENT_TEXT_VERSION } }
}

export const updateCustomer = (id: number, update: CustomerUpdate) => http.put<Customer>(`/api/v1/customers/${id}`, update)

/** "+50688887777" → "8888-7777"; other countries keep their international form. */
export function formatPhone(phone: string): string {
  const match = /^\+506(\d{4})(\d{4})$/.exec(phone)
  return match ? `${match[1]}-${match[2]}` : phone
}

/** Empty optional fields are sent as absent rather than "". */
export function optional(value: string): string | undefined {
  const trimmed = value.trim()
  return trimmed ? trimmed : undefined
}
