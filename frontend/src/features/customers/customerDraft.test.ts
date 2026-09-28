import { describe, expect, it } from 'vitest'
import {
  draftToChoice,
  editDraft,
  EMPTY_DRAFT,
  foldName,
  looksLikeCompletePhone,
  lookupTerms,
  selectCandidate,
  willRegisterNew,
  type CustomerDraft,
} from './customerDraft'
import type { CustomerCandidate } from './customersApi'

const ana: CustomerCandidate = {
  id: 7,
  fullName: 'Ana Pérez',
  phone: '+50688887777',
  email: null,
  registeredBranch: { id: 1, code: 'SJ-01', name: 'San José' },
  inScope: true,
  nameMatch: true,
  phoneMatch: false,
}

const draft = (change: Partial<CustomerDraft>): CustomerDraft => ({ ...EMPTY_DRAFT, ...change })

describe('lookupTerms', () => {
  it('waits for 3 letters of the name or 3 digits of the phone', () => {
    expect(lookupTerms({ name: 'an', phone: '88' })).toBeNull()
    expect(lookupTerms({ name: ' a n a ', phone: '' })).toEqual({ name: 'a n a', phone: '' })
    expect(lookupTerms({ name: 'an', phone: '8-8-8' })).toEqual({ name: '', phone: '8-8-8' })
  })

  it('folds case, accents and spaces like the server', () => {
    expect(foldName('  ÁNA   María ')).toBe('ana maria')
  })
})

describe('looksLikeCompletePhone', () => {
  it.each(['8888-7777', '+506 8888 7777', '50688887777', '+1 305 555 0100', '001 305 555 0100'])('accepts %s', (phone) => {
    expect(looksLikeCompletePhone(phone)).toBe(true)
  })

  it.each(['888', '8888-777', '+506 8888 777', ''])('rejects %s', (phone) => {
    expect(looksLikeCompletePhone(phone)).toBe(false)
  })
})

describe('customer choice', () => {
  it('registers a new customer automatically only when the search found nobody and data is complete', () => {
    const typed = draft({ name: 'Marta Solís', phone: '2222-3333' })
    expect(willRegisterNew(typed, undefined)).toBe(false) // search not settled yet
    expect(willRegisterNew(typed, [ana])).toBe(false) // there are candidates: choose or tick explicitly
    expect(draftToChoice(typed, [])).toEqual({
      kind: 'new',
      customer: { fullName: 'Marta Solís', phone: '2222-3333', allowDuplicatePhone: undefined },
    })
    expect(willRegisterNew(draft({ name: 'Marta', phone: '2222' }), [])).toBe(false) // incomplete phone
  })

  it('respects an explicit choice to register even when there were candidates', () => {
    const explicit = draft({ name: 'Ana Pérez', phone: '8888-7777', registerNew: true })
    expect(draftToChoice(explicit, [ana])?.kind).toBe('new')
    expect(draftToChoice({ ...explicit, registerNew: false }, [])).toBeNull()
  })

  it('links the selected customer by id and fills the fields', () => {
    const chosen = selectCandidate(draft({ name: 'ana', phone: '' }), ana)
    expect(chosen.name).toBe('Ana Pérez')
    expect(chosen.phone).toBe('8888-7777')
    expect(draftToChoice(chosen, [ana])).toEqual({
      kind: 'existing',
      match: { id: 7, fullName: 'Ana Pérez', inScope: true },
      phone: '8888-7777',
    })
  })

  it('keeps the typed phone as proof for a customer of another branch', () => {
    const other = { ...ana, id: 9, phone: null, inScope: false }
    const chosen = selectCandidate(draft({ name: '', phone: '2222 3333' }), other)
    expect(chosen.phone).toBe('2222 3333')
    expect(draftToChoice(chosen, [other])).toMatchObject({ kind: 'existing', phone: '2222 3333', match: { inScope: false } })
  })

  it('unlinks the customer when the name or phone is edited afterwards', () => {
    const chosen = selectCandidate(EMPTY_DRAFT, ana)
    expect(editDraft(chosen, { name: 'Ana Pérez Solís' }).selected).toBeNull()
    expect(editDraft(chosen, { phone: '8888-7778' }).selected).toBeNull()
    expect(editDraft(chosen, { name: 'Ana Pérez' }).selected).not.toBeNull() // no real change
  })

  it('sends the duplicate confirmation only when the user gave it', () => {
    const confirmed = draft({ name: 'Ana Pérez', phone: '8888-7777', registerNew: true, confirmedNewPerson: true })
    expect(draftToChoice(confirmed, [])).toMatchObject({ kind: 'new', customer: { allowDuplicatePhone: true } })
  })
})
