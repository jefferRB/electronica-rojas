import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchSession, login, logout } from './authApi'

export const SESSION_QUERY_KEY = ['session'] as const

export function useSession() {
  return useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: fetchSession,
    staleTime: 60_000,
    retry: false,
  })
}

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => login(email, password),
    // Drop anything cached for a previous user, then load the new session.
    onSuccess: async () => {
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0] })
      await queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY })
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    // FR-AUTH-002: clear every cached private response, even if the request failed.
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(SESSION_QUERY_KEY, null)
    },
  })
}
