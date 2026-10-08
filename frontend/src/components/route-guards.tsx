import { Building2, Loader2, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/stores/auth'

function FullPageSpinner() {
  return (
    <div className="grid min-h-screen place-items-center">
      <div className="flex flex-col items-center gap-3 text-muted-foreground">
        <span className="grid size-10 place-items-center rounded-xl bg-primary/15 text-primary">
          <Building2 className="size-5" aria-hidden />
        </span>
        <Loader2 className="size-5 animate-spin" aria-hidden />
        <p className="text-sm">Loading LIFEOS…</p>
      </div>
    </div>
  )
}

/** Requires a session. Onboarding is pushed here too, so a new account lands on the wizard. */
export function RequireAuth() {
  const { isAuthenticated, isBootstrapping, user } = useAuth()
  const location = useLocation()

  if (isBootstrapping) return <FullPageSpinner />
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user && !user.onboardingCompleted && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />
  }
  return <Outlet />
}

/** Requires a session and the ADMIN role; the API enforces the same rule server-side. */
export function RequireAdmin() {
  const { user } = useAuth()
  if (user && user.role !== 'ADMIN') return <Navigate to="/app" replace />
  return <Outlet />
}

export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { isAuthenticated, isBootstrapping } = useAuth()
  if (isBootstrapping) return <FullPageSpinner />
  if (isAuthenticated) return <Navigate to="/app" replace />
  return <>{children}</>
}

export function ErrorPage({
  title = 'Something went wrong',
  message,
  icon: Icon,
  action,
}: {
  title?: string
  message?: string
  icon?: LucideIcon
  action?: ReactNode
}) {
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 py-20 text-center">
      {Icon ? <Icon className="size-10 text-muted-foreground" aria-hidden /> : null}
      <h1 className="text-xl font-semibold">{title}</h1>
      {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
      {action ?? (
        <Button asChild>
          <Link to="/app">Back to dashboard</Link>
        </Button>
      )}
    </div>
  )
}
