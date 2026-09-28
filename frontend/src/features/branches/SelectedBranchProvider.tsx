import { useMemo, useState, type ReactNode } from 'react'
import type { BranchSummary } from './branchesApi'
import { SelectedBranchContext } from './selectedBranch'

interface Props {
  userId: number
  branches: BranchSummary[]
  children: ReactNode
}

const storageKey = (userId: number) => `electronica-rojas.selectedBranch.${userId}`

function readStoredBranch(userId: number): number | null {
  try {
    const value = sessionStorage.getItem(storageKey(userId))
    return value ? Number(value) : null
  } catch {
    return null
  }
}

/**
 * Keeps the selected branch per user in sessionStorage. A stored id that is no longer among
 * the authorized branches (assignment removed, branch deactivated) is ignored.
 */
export function SelectedBranchProvider({ userId, branches, children }: Props) {
  const [storedId, setStoredId] = useState<number | null>(() => readStoredBranch(userId))

  const value = useMemo(() => {
    const selected = branches.find((branch) => branch.id === storedId) ?? branches[0] ?? null
    return {
      branches,
      selected,
      select: (branchId: number) => {
        setStoredId(branchId)
        try {
          sessionStorage.setItem(storageKey(userId), String(branchId))
        } catch {
          // storage unavailable (private mode): selection still works for this page view
        }
      },
    }
  }, [branches, storedId, userId])

  return <SelectedBranchContext.Provider value={value}>{children}</SelectedBranchContext.Provider>
}
