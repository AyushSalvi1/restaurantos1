import { cn } from '@/lib/utils'
import type {
  BudgetPeriod,
  FocusMode,
  GoalStatus,
  GraphNodeType,
  HabitFrequency,
  InsightSeverity,
  LearningStatus,
  NotificationCategory,
  Priority,
  ResourceType,
  TaskStatus,
  TransactionType,
} from '@/types/api'

type Tone = 'default' | 'success' | 'warning' | 'destructive' | 'secondary' | 'outline' | 'info'

const toneMap: Record<string, Tone> = {
  TODO: 'secondary',
  IN_PROGRESS: 'info',
  COMPLETED: 'success',
  CANCELLED: 'outline',

  LOW: 'secondary',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'destructive',

  ACTIVE: 'info',
  ACHIEVED: 'success',
  PAUSED: 'warning',
  ARCHIVED: 'outline',

  GREAT: 'success',
  GOOD: 'success',
  NEUTRAL: 'info',
  DIFFICULT: 'destructive',

  WARNING: 'warning',
  INFO: 'info',

  PENDING: 'secondary',
  PROCESSING: 'info',
  READY: 'success',
  FAILED: 'destructive',

  INCOME: 'success',
  EXPENSE: 'destructive',

  WEEKLY: 'info',
  MONTHLY: 'info',
  YEARLY: 'info',

  DAILY: 'info',
  POMODORO_25_5: 'info',
  POMODORO_50_10: 'info',
  DEEP_WORK: 'default',
  CUSTOM: 'secondary',

  DISABLED: 'destructive',
  DELETED: 'outline',
  ADMIN: 'warning',
  USER: 'secondary',
}

/**
 * Neutral grey for values that carry no semantic colour mapping.
 *
 * Several enum constants are shared across domains (`LOW` is both a priority and a mood,
 * `ACTIVE` a goal status and a learning status). One map has to serve them all, so a domain-neutral
 * tone wins where two domains disagree.
 */
export function toneFor(value?: string | null): Tone {
  if (!value) return 'outline'
  return toneMap[value] ?? 'outline'
}

export function toneClasses(tone: Tone): string {
  switch (tone) {
    case 'success':
      return 'border-transparent bg-success/15 text-success'
    case 'warning':
      return 'border-transparent bg-warning/15 text-warning'
    case 'destructive':
      return 'border-transparent bg-destructive/15 text-destructive'
    case 'info':
      return 'border-transparent bg-sky-400/15 text-sky-300'
    case 'default':
      return 'border-transparent bg-primary/15 text-primary'
    case 'secondary':
      return 'border-transparent bg-secondary text-secondary-foreground'
    default:
      return 'border-border text-muted-foreground'
  }
}

export function StatusBadge({
  value,
  label,
  className,
}: {
  value?: string | null
  label?: string
  className?: string
}) {
  if (!value) return null
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        toneClasses(toneFor(value)),
        className,
      )}
    >
      {label ?? value.replace(/_/g, ' ').toLowerCase()}
    </span>
  )
}

/** Per-colour bar fills, keyed by domain so a task bar and a goal bar read the same way. */
export const progressColors: Record<string, string> = {
  productivity: 'bg-sky-400',
  learning: 'bg-violet-400',
  goals: 'bg-emerald-400',
  habits: 'bg-amber-400',
  finance: 'bg-teal-400',
  planning: 'bg-rose-400',
}

export function progressColor(key?: string): string {
  if (!key) return 'bg-primary'
  return progressColors[key.toLowerCase()] ?? 'bg-primary'
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}

export type {
  BudgetPeriod,
  FocusMode,
  GoalStatus,
  GraphNodeType,
  HabitFrequency,
  InsightSeverity,
  LearningStatus,
  NotificationCategory,
  Priority,
  ResourceType,
  TaskStatus,
  TransactionType,
}
