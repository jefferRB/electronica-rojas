import type { CustomerCandidate, CustomerMatch, CustomerRef, NewCustomer } from './customersApi'
import { formatPhone, optional } from './customersApi'

/** Minimum letters of the name, or digits of the phone, before searching (as the server). */
export const MIN_LOOKUP_CHARS = 3

/**
 * What the collaborator typed or chose in the customer picker. The identity of an existing
 * customer lives only in `selected`; editing the name or phone clears it (see `editDraft`).
 */
export interface CustomerDraft {
  name: string
  phone: string
  selected: CustomerRef | null
  /** null = automatic: register as new when the search found nobody and the data is complete. */
  registerNew: boolean | null
  /** The user confirmed a different person after a POSSIBLE_DUPLICATE_CUSTOMER answer. */
  confirmedNewPerson: boolean
  email: string
  address: string
  internalNotes: string
}

export const EMPTY_DRAFT: CustomerDraft = {
  name: '',
  phone: '',
  selected: null,
  registerNew: null,
  confirmedNewPerson: false,
  email: '',
  address: '',
  internalNotes: '',
}

/** Who the order (or request) is for: an existing customer, or a new record typed inline. */
export type CustomerChoice = { kind: 'existing'; match: CustomerRef; phone: string } | { kind: 'new'; customer: NewCustomer }

/** "María  Pérez" → "maria perez": the same folding the server uses for search_name. */
export function foldName(text: string): string {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()
}

export const phoneDigits = (phone: string) => phone.replace(/\D/g, '')

/**
 * The terms worth sending to the server, or null when neither field reaches the minimum
 * (typing "an" or "88" must not fire a request).
 */
export function lookupTerms(draft: Pick<CustomerDraft, 'name' | 'phone'>): { name: string; phone: string } | null {
  const name = foldName(draft.name).replace(/ /g, '').length >= MIN_LOOKUP_CHARS ? draft.name.trim() : ''
  const phone = phoneDigits(draft.phone).length >= MIN_LOOKUP_CHARS ? draft.phone.trim() : ''
  return name || phone ? { name, phone } : null
}

/**
 * A plausible complete phone: 8 Costa Rican digits (optionally with 506) or an international
 * number with + / 00. Only a UI hint: the server's PhoneNumbers is the authority.
 */
export function looksLikeCompletePhone(phone: string): boolean {
  const trimmed = phone.trim()
  const digits = phoneDigits(trimmed)
  if (trimmed.startsWith('+') || trimmed.startsWith('00')) {
    const international = trimmed.startsWith('00') ? digits.slice(2) : digits
    return international.startsWith('506') ? international.length === 11 : international.length >= 8 && international.length <= 15
  }
  return digits.length === 8 || (digits.length === 11 && digits.startsWith('506'))
}

export function hasNewCustomerData(draft: CustomerDraft): boolean {
  return draft.name.trim() !== '' && looksLikeCompletePhone(draft.phone)
}

/**
 * Whether the draft will register a new customer. Automatic (registerNew null) means: only when
 * the search finished with no candidates and name and phone are complete; with candidates the
 * user must pick one or tick "Registrar como cliente nuevo" explicitly.
 */
export function willRegisterNew(draft: CustomerDraft, candidates: CustomerCandidate[] | undefined): boolean {
  if (draft.selected) return false
  if (draft.registerNew !== null) return draft.registerNew && hasNewCustomerData(draft)
  return candidates !== undefined && candidates.length === 0 && hasNewCustomerData(draft)
}

/** The customer part of the request, or null while it is still undecided. */
export function draftToChoice(draft: CustomerDraft, candidates: CustomerCandidate[] | undefined): CustomerChoice | null {
  if (draft.selected) return { kind: 'existing', match: draft.selected, phone: draft.phone.trim() }
  if (!willRegisterNew(draft, candidates)) return null
  return {
    kind: 'new',
    customer: {
      fullName: draft.name.trim(),
      phone: draft.phone.trim(),
      email: optional(draft.email),
      address: optional(draft.address),
      internalNotes: optional(draft.internalNotes),
      allowDuplicatePhone: draft.confirmedNewPerson || undefined,
    },
  }
}

/**
 * Applies a typed change. Changing the name or phone after choosing someone invalidates the
 * choice, so an order can never stay linked to a person the fields no longer describe.
 */
export function editDraft(draft: CustomerDraft, change: Partial<Pick<CustomerDraft, 'name' | 'phone'>>): CustomerDraft {
  const identityChanged =
    (change.name !== undefined && change.name !== draft.name) || (change.phone !== undefined && change.phone !== draft.phone)
  return identityChanged ? { ...draft, ...change, selected: null, confirmedNewPerson: false } : { ...draft, ...change }
}

/** Fills the fields from a chosen customer. A customer of another branch keeps the typed phone (the proof). */
export function selectCandidate(draft: CustomerDraft, candidate: CustomerRef & { phone?: string | null }): CustomerDraft {
  return {
    ...draft,
    name: candidate.fullName,
    phone: candidate.phone ? formatPhone(candidate.phone) : draft.phone,
    selected: { id: candidate.id, fullName: candidate.fullName, inScope: candidate.inScope },
    registerNew: null,
    confirmedNewPerson: false,
  }
}

/** Matches listed in a POSSIBLE_DUPLICATE_CUSTOMER 409 body, if any. */
export function duplicateMatches(properties: Record<string, unknown>): CustomerMatch[] {
  if (properties.code !== 'POSSIBLE_DUPLICATE_CUSTOMER' || !Array.isArray(properties.matches)) return []
  return properties.matches.filter(
    (match): match is CustomerMatch =>
      typeof match === 'object' && match !== null && typeof (match as CustomerMatch).id === 'number',
  )
}
