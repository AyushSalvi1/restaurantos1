const DATE_TIME = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const DATE_ONLY = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

const TIME_ONLY = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

const WEEKDAY = new Intl.DateTimeFormat(undefined, { weekday: 'short' })

/**
 * All rendering goes through the viewer's zone. The API speaks UTC, so timestamps are only meaningful
 * once they are localised, and `dayjs`-style local parsing is avoided entirely.
 */
function toDate(value: string | Date | undefined | null): Date | null {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(value?: string | null): string {
  const date = toDate(value)
  return date ? DATE_TIME.format(date) : '—'
}

export function formatDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(`${value.length === 10 ? `${value}T00:00:00` : value}`)
  return Number.isNaN(date.getTime()) ? '—' : DATE_ONLY.format(date)
}

export function formatTime(value?: string | null): string {
  const date = toDate(value)
  return date ? TIME_ONLY.format(date) : '—'
}

export function formatWeekday(value?: string | null): string {
  const date = toDate(value)
  return date ? WEEKDAY.format(date) : '—'
}

/** `HH:mm` for a `LocalTime` value such as `08:30:00`. */
export function formatClock(value?: string | null): string {
  if (!value) return '—'
  return value.slice(0, 5)
}

export function toLocalDateInput(value?: string | null): string {
  if (!value) return ''
  if (value.length === 10) return value
  const date = toDate(value)
  if (!date) return ''
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

/** Value for `<input type="datetime-local">`, which expects local wall-clock time without a zone. */
export function toLocalDateTimeInput(value?: string | null): string {
  if (!value) return ''
  const date = toDate(value)
  if (!date) return ''
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

/** Converts a `datetime-local` value back into the UTC instant the API expects. */
export function fromLocalDateTimeInput(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

export function formatRelative(value?: string | null): string {
  const date = toDate(value)
  if (!date) return '—'
  const deltaMs = date.getTime() - Date.now()
  const abs = Math.abs(deltaMs)
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 365 * 24 * 3600_000],
    ['month', 30 * 24 * 3600_000],
    ['day', 24 * 3600_000],
    ['hour', 3600_000],
    ['minute', 60_000],
  ]
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  for (const [unit, ms] of units) {
    if (abs >= ms || unit === 'minute') {
      return formatter.format(Math.round(deltaMs / ms), unit)
    }
  }
  return formatter.format(0, 'minute')
}

export function formatCurrency(value: number | null | undefined, currency = 'USD'): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(value ?? 0)
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat().format(value ?? 0)
}

export function formatPercent(value: number | null | undefined, digits = 0): string {
  return `${(value ?? 0).toFixed(digits)}%`
}

export function formatMinutes(minutes: number | null | undefined): string {
  const total = Math.max(0, Math.round(minutes ?? 0))
  if (total < 60) return `${total}m`
  const hours = Math.floor(total / 60)
  const rest = total % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

export function formatBytes(bytes: number | null | undefined): string {
  const value = bytes ?? 0
  if (value < 1024) return `${value} B`
  const units = ['KB', 'MB', 'GB']
  let scaled = value / 1024
  let index = 0
  while (scaled >= 1024 && index < units.length - 1) {
    scaled /= 1024
    index += 1
  }
  return `${scaled.toFixed(1)} ${units[index]}`
}

export function titleCase(value?: string | null): string {
  if (!value) return ''
  return value
    .toLowerCase()
    .split(/[\s_]+/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function todayIso(): string {
  return toLocalDateInput(new Date().toISOString()) ?? ''
}

export function addDaysIso(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00`)
  date.setDate(date.getDate() + days)
  return toLocalDateInput(date.toISOString()) ?? iso
}

export function currentMonthIso(): string {
  return todayIso().slice(0, 7)
}

export function startOfWeekIso(iso = todayIso()): string {
  const date = new Date(`${iso}T00:00:00`)
  const day = date.getDay()
  const diff = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + diff)
  return toLocalDateInput(date.toISOString()) ?? iso
}
