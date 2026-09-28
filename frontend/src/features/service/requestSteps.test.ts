import { describe, expect, it } from 'vitest'
import { journeySteps, nextStep } from './requestSteps'
import type { ServiceRequestDetail, Visit } from './serviceApi'

const visit = (status: Visit['status']) =>
  ({ id: 1, status, start: '2026-09-28T14:30:00Z', technician: { id: 3, fullName: 'Diego Araya' } }) as Visit

const request = (overrides: Partial<ServiceRequestDetail>) =>
  ({ status: 'UNDER_REVIEW', customer: null, visits: [], ...overrides }) as ServiceRequestDetail

describe('home-service request journey', () => {
  it('marks the stages a request has passed', () => {
    const steps = journeySteps(request({ customer: { id: 1, fullName: 'Ana' }, visits: [visit('PROPOSED')] }))
    expect(steps.map((step) => step.done)).toEqual([true, true, true, true, false, false])
  })

  it('does not count a cancelled visit as proposed', () => {
    expect(journeySteps(request({ visits: [visit('CANCELLED')] }))[3].done).toBe(false)
  })

  it('tells the collaborator the next step, and that a proposal reserves nothing', () => {
    expect(nextStep(request({ status: 'PENDING' }), undefined)?.title).toBe('Solicitud nueva.')
    expect(nextStep(request({}), undefined)?.title).toBe('Falta asociar el cliente.')
    const customer = { id: 1, fullName: 'Ana' }
    expect(nextStep(request({ customer }), undefined)?.title).toBe('Lista para programar.')
    expect(nextStep(request({ customer }), visit('PROPOSED'))?.text).toContain('no queda reservada')
    expect(nextStep(request({ customer }), visit('CONFIRMED'))?.text).toContain('Diego Araya')
    expect(nextStep(request({ status: 'REJECTED' }), undefined)).toBeNull()
  })
})
