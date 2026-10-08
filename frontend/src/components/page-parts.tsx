import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

export function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: string
  description?: string
  actions?: ReactNode
  className?: string
}) {
  return (
    <header className={cn('flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between', className)}>
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  )
}

interface StatCardProps {
  label: string
  value: ReactNode
  hint?: string
  icon?: ReactNode
  to?: string
  tone?: string
  className?: string
}

export function StatCard({ label, value, hint, icon, to, tone, className }: StatCardProps) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted-foreground">{label}</p>
        {icon ? <span className={cn('text-muted-foreground [&_svg]:size-4', tone)}>{icon}</span> : null}
      </div>
      <p className="mt-2 text-2xl font-semibold tabular-nums">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </>
  )

  const classNameResolved = cn(
    'rounded-xl border bg-card p-4 transition-colors',
    to && 'hover:border-primary/50',
    className,
  )

  return to ? (
    <Link to={to} className={classNameResolved}>
      {body}
    </Link>
  ) : (
    <div className={classNameResolved}>{body}</div>
  )
}

export function SectionCard({
  title,
  description,
  action,
  children,
  className,
  contentClassName,
}: {
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
  className?: string
  contentClassName?: string
}) {
  return (
    <section className={cn('rounded-xl border bg-card', className)}>
      <div className="flex items-start justify-between gap-3 border-b p-4">
        <div className="space-y-0.5">
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {action}
      </div>
      <div className={cn('p-4', contentClassName)}>{children}</div>
    </section>
  )
}
