import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { authApi } from '@/api/endpoints'
import { onUnauthorized, tokenStore } from '@/api/client'
import type { AuthResponse, UserResponse } from '@/types/api'

interface AuthContextValue {
  user: UserResponse | null
  isAuthenticated: boolean
  isBootstrapping: boolean
  login: (email: string, password: string, deviceName?: string) => Promise<UserResponse>
  register: (input: {
    email: string
    password: string
    fullName: string
    timezone?: string
  }) => Promise<UserResponse>
  logout: (options?: { allDevices?: boolean }) => Promise<void>
  refreshUser: () => Promise<UserResponse | null>
  setUser: (user: UserResponse) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** Passwords must be at least 10 characters with a letter and a digit, matching the server rule. */
export function passwordProblem(password: string): string | null {
  if (password.length < 10) return 'Use at least 10 characters.'
  if (!/[a-zA-Z]/.test(password)) return 'Include at least one letter.'
  if (!/\d/.test(password)) return 'Include at least one digit.'
  if (password.length > 72) return 'Keep it under 72 characters.'
  return null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUserState] = useState<UserResponse | null>(null)
  const [isBootstrapping, setBootstrapping] = useState(() => Boolean(tokenStore.access))

  const clearSession = useCallback(() => {
    tokenStore.clear()
    setUserState(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    onUnauthorized(clearSession)
  }, [clearSession])

  const { data, isLoading, isError } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.me(),
    enabled: Boolean(tokenStore.access),
    retry: false,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (!tokenStore.access) {
      setBootstrapping(false)
      return
    }
    if (isLoading) return
    if (isError || !data) {
      clearSession()
    } else {
      setUserState(data)
    }
    setBootstrapping(false)
  }, [data, isLoading, isError, clearSession])

  const storeSession = useCallback(
    (session: AuthResponse) => {
      tokenStore.set(session.accessToken, session.refreshToken)
      setUserState(session.user)
      queryClient.setQueryData(['auth', 'me'], session.user)
    },
    [queryClient],
  )

  const loginMutation = useMutation({
    mutationFn: (input: { email: string; password: string; deviceName?: string }) =>
      authApi.login({
        email: input.email,
        password: input.password,
        deviceName: input.deviceName,
      }),
  })

  const registerMutation = useMutation({
    mutationFn: (input: {
      email: string
      password: string
      fullName: string
      timezone?: string
    }) =>
      authApi.register({
        email: input.email,
        password: input.password,
        fullName: input.fullName,
        timezone: input.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
      }),
  })

  const logoutMutation = useMutation({
    mutationFn: (options?: { allDevices?: boolean }) =>
      authApi.logout({
        refreshToken: tokenStore.refresh ?? undefined,
        allDevices: options?.allDevices ?? false,
      }),
  })

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: Boolean(user),
      isBootstrapping,
      login: async (email, password, deviceName) => {
        const session = await loginMutation.mutateAsync({ email, password, deviceName })
        storeSession(session)
        return session.user
      },
      register: async (input) => {
        const session = await registerMutation.mutateAsync(input)
        storeSession(session)
        return session.user
      },
      logout: async (options) => {
        try {
          await logoutMutation.mutateAsync(options)
        } catch {
          // A failed logout must still clear local credentials; the server keeps the session until
          // it expires, and the user has explicitly asked to be signed out here.
        } finally {
          clearSession()
        }
      },
      refreshUser: async () => {
        if (!tokenStore.access) return null
        try {
          const fresh = await authApi.me()
          setUserState(fresh)
          queryClient.setQueryData(['auth', 'me'], fresh)
          return fresh
        } catch {
          clearSession()
          return null
        }
      },
      setUser: setUserState,
    }),
    [user, isBootstrapping, loginMutation, registerMutation, logoutMutation, storeSession, clearSession, queryClient],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
