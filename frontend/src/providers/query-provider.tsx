import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

import { ApiRequestError } from '@/api/client'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Never retry auth, validation or permission failures: they will not resolve themselves.
        if (error instanceof ApiRequestError && error.status >= 400 && error.status < 500) {
          return false
        }
        return failureCount < 2
      },
    },
    mutations: { retry: false },
  },
})

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => queryClient)
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

export { queryClient }
