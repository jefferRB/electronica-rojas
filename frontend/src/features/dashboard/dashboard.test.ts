import { describe, expect, it } from 'vitest'
import { greeting, groupIndicators, indicatorView, priorities } from './dashboardApi'

describe('indicatorView (D.1/D.2)', () => {
  it('opens each list with the same status and branch the count used', () => {
    expect(indicatorView('REPAIRS_READY', 4, '2026-09-28')?.to).toBe('/repairs?status=READY_FOR_PICKUP&branch=4')
    expect(indicatorView('REQUESTS_PENDING', 4, '2026-09-28')?.to).toBe('/service-requests?status=PENDING&branch=4')
    expect(indicatorView('VISITS_TODAY', 4, '2026-09-28')?.to).toBe('/agenda?view=day&date=2026-09-28&branch=4')
    // The stock screen works on the selected branch, which is the dashboard's branch.
    expect(indicatorView('STOCK_OUT', 4, '2026-09-28')?.to).toBe('/inventory?stock=OUT_OF_STOCK')
  })

  it('uses the all-branches views in the consolidated dashboard', () => {
    expect(indicatorView('REPAIRS_RECEIVED', null, '2026-09-28')?.to).toBe('/repairs?status=RECEIVED')
    expect(indicatorView('VISITS_TODAY', null, '2026-09-28')?.to).toBe('/agenda?view=day&date=2026-09-28&branch=all')
    expect(indicatorView('STOCK_LOW', null, '2026-09-28')?.to).toBe('/inventory/overview?stock=LOW')
  })

  it('gives technicians their own lists and ignores unknown keys', () => {
    expect(indicatorView('MY_REPAIRS_IN_REPAIR', null, '2026-09-28')).toMatchObject({ label: 'En reparación', to: '/repairs?status=IN_REPAIR' })
    expect(indicatorView('MY_VISITS_TODAY', null, '2026-09-28')?.to).toBe('/my-visits')
    expect(indicatorView('SOMETHING_NEW', 1, '2026-09-28')).toBeNull()
  })
})

describe('dashboard grouping and priorities (premium home)', () => {
  const indicators = [
    { key: 'REPAIRS_RECEIVED', count: 2 },
    { key: 'REPAIRS_READY', count: 1 },
    { key: 'REQUESTS_PENDING', count: 0 },
    { key: 'VISITS_TODAY', count: 3 },
    { key: 'STOCK_OUT', count: 4 },
    { key: 'STOCK_LOW', count: 1 },
    { key: 'SOMETHING_NEW', count: 9 },
  ]

  it('groups indicators by use and drops unknown keys', () => {
    const groups = groupIndicators(indicators, 4, '2026-09-28')
    expect(groups.workshop?.map((item) => item.key)).toEqual(['REPAIRS_RECEIVED', 'REPAIRS_READY'])
    expect(groups.home?.map((item) => item.key)).toEqual(['REQUESTS_PENDING'])
    expect(groups.agenda?.[0]).toMatchObject({ key: 'VISITS_TODAY', count: 3 })
    expect(groups.stock?.map((item) => item.key)).toEqual(['STOCK_OUT', 'STOCK_LOW'])
    expect(Object.values(groups).flat().some((item) => item?.key === 'SOMETHING_NEW')).toBe(false)
  })

  it('lists only non-zero indicators that need action, urgent first', () => {
    expect(priorities(indicators, 4, '2026-09-28').map((item) => item.key)).toEqual([
      'REPAIRS_READY',
      'STOCK_OUT',
      'REPAIRS_RECEIVED',
      'STOCK_LOW',
    ])
  })

  it('greets by the Costa Rica hour', () => {
    expect(greeting(8)).toBe('Buenos días')
    expect(greeting(14)).toBe('Buenas tardes')
    expect(greeting(21)).toBe('Buenas noches')
  })
})
