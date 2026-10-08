import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="flex flex-col justify-center px-6 py-12 sm:px-10">
        <div className="mx-auto w-full max-w-sm">
          <Link to="/" className="mb-8 inline-flex items-center gap-2">
            <span className="grid size-9 place-items-center rounded-xl bg-primary/15 text-base font-bold text-primary">
              L
            </span>
            <span className="text-lg font-semibold tracking-tight">LIFEOS</span>
          </Link>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {subtitle ? <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p> : null}
          <div className="mt-8">{children}</div>
          {footer ? <div className="mt-6 text-sm text-muted-foreground">{footer}</div> : null}
        </div>
      </div>

      <div className="hidden border-l bg-card/40 lg:flex lg:flex-col lg:justify-center lg:px-14">
        <div className="max-w-md space-y-6">
          <h2 className="text-xl font-semibold">
            One operating system for tasks, goals, habits, health, money and learning.
          </h2>
          <ul className="space-y-4 text-sm text-muted-foreground">
            <li className="flex gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
              Plans your day from real deadlines, dependencies and available hours — the assistant only
              narrates the schedule it computed.
            </li>
            <li className="flex gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
              Scores six life dimensions and excludes the ones you have no data for, instead of
              penalising a new account.
            </li>
            <li className="flex gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
              Answers from your own documents with citations, and runs fully offline on the built-in
              heuristic provider when no API key is configured.
            </li>
            <li className="flex gap-3">
              <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />
              Exports everything you own as a single JSON archive, and deletes it all on request.
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}

export function FormError({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
      {message}
    </p>
  )
}

export function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return <p className="mt-1 text-xs text-destructive">{message}</p>
}
