import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from '../shared/api/httpClient'
import { SESSION_QUERY_KEY } from '../features/auth/session'

/**
 * A 401 on any call means the server-side session is gone (expired, logged out elsewhere, or
 * revoked after a role/password change). Marking the session as absent sends the user to the
 * login page through RequireAuth.
 */
function handleUnauthorized(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    queryClient.setQueryData(SESSION_QUERY_KEY, null)
  }
}

export const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleUnauthorized }),
  mutationCache: new MutationCache({ onError: handleUnauthorized }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: true,
      // Never retry authorization or validation errors; retry network hiccups once.
      retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 1,
    },
  },
})
