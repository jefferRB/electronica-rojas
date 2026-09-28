import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import {
  draftToChoice,
  editDraft,
  EMPTY_DRAFT,
  lookupTerms,
  selectCandidate,
  type CustomerChoice,
  type CustomerDraft,
} from './customerDraft'
import { customerKeys, fetchCustomerLookup, type CustomerCandidate, type CustomerRef } from './customersApi'

export const LOOKUP_DEBOUNCE_MS = 300

export interface CustomerPickerState {
  draft: CustomerDraft
  setDraft: (draft: CustomerDraft) => void
  edit: (change: Partial<Pick<CustomerDraft, 'name' | 'phone'>>) => void
  select: (candidate: CustomerRef & { phone?: string | null }) => void
  clearSelection: () => void
  /** Candidates for the current (debounced) terms; undefined while there is nothing to show. */
  candidates: CustomerCandidate[] | undefined
  moreInScope: number
  /** True once the results correspond to what is typed now (no pending debounce or request). */
  settled: boolean
  isSearching: boolean
  error: unknown
  /** The customer part of the request, or null while undecided. */
  choice: CustomerChoice | null
}

/**
 * State of the reusable customer picker (Phase 3.1): the typed name/phone, the chosen customer and
 * the incremental lookup.
 *
 * Stale answers can never replace newer ones: each search is its own TanStack Query keyed by the
 * terms, the component only reads the query of the current key, and the previous request is
 * aborted through its AbortSignal when its key stops being observed.
 */
export function useCustomerPicker(initial: CustomerDraft = EMPTY_DRAFT): CustomerPickerState {
  const [draft, setDraft] = useState(initial)
  const terms = draft.selected ? null : lookupTerms(draft)
  const termsKey = terms ? JSON.stringify([terms.name, terms.phone]) : ''
  const debouncedKey = useDebouncedValue(termsKey, LOOKUP_DEBOUNCE_MS)
  const [name, phone] = debouncedKey ? (JSON.parse(debouncedKey) as [string, string]) : ['', '']
  const searching = debouncedKey !== '' && !draft.selected

  const lookup = useQuery({
    queryKey: customerKeys.lookup(name, phone),
    queryFn: ({ signal }) => fetchCustomerLookup(name, phone, signal),
    enabled: searching,
    staleTime: 30_000,
    retry: false,
  })

  const settled = searching && debouncedKey === termsKey && lookup.isSuccess && !lookup.isFetching
  const candidates = searching ? lookup.data?.candidates : undefined

  return {
    draft,
    setDraft,
    edit: (change) => setDraft((current) => editDraft(current, change)),
    select: (candidate) => setDraft((current) => selectCandidate(current, candidate)),
    clearSelection: () => setDraft((current) => ({ ...current, selected: null, registerNew: null })),
    candidates,
    moreInScope: searching ? (lookup.data?.moreInScope ?? 0) : 0,
    settled,
    isSearching: searching && lookup.isFetching,
    error: searching ? lookup.error : null,
    choice: draftToChoice(draft, settled ? candidates : undefined),
  }
}
