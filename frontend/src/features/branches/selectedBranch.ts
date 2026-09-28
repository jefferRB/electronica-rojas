import { createContext, useContext } from 'react'
import type { BranchSummary } from './branchesApi'

export interface SelectedBranchContextValue {
  branches: BranchSummary[]
  selected: BranchSummary | null
  select: (branchId: number) => void
}

export const SelectedBranchContext = createContext<SelectedBranchContextValue | null>(null)

/**
 * Branch chosen in the header selector. It only changes the visual context: each API call
 * still validates the branch against the user's permissions on the server (FR-NAV-004).
 */
export function useSelectedBranch(): SelectedBranchContextValue {
  const value = useContext(SelectedBranchContext)
  if (!value) throw new Error('useSelectedBranch must be used inside SelectedBranchProvider')
  return value
}
