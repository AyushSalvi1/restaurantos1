import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlarmClock,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  Plus,
  RefreshCw,
  Repeat,
  Trash2,
} from 'lucide-react'
import { useCallback, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { calendarApi } from '@/api/endpoints'
import { PageHeader, SectionCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { EmptyState } from '@/components/ui/empty-state'
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge, toneClasses, toneFor } from '@/lib/badges'
import {
  addDaysIso,
  formatDate,
  formatTime,
  fromLocalDateTimeInput,
  startOfWeekIso,
  titleCase,
  toLocalDateInput,
  toLocalDateTimeInput,
  todayIso,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import type { EventRequest, EventType, Occurrence } from '@/types/api'

const HOUR_HEIGHT = 56
const ALL_DAY_MINUTES = 18 * 60

const EVENT_TYPES: readonly EventType[] = ['EVENT', 'DEADLINE', 'FOCUS', 'HABIT_REMINDER']

/**
 * `toneFor` is keyed by domain vocabulary, so each event type maps onto the vocabulary entry that
 * carries its lane colour instead of inventing a second palette.
 */
const LANE_TONE_KEY: Record<EventType, string> = {
  EVENT: 'DEEP_WORK',
  DEADLINE: 'CRITICAL',
  FOCUS: 'INFO',
  HABIT_REMINDER: 'COMPLETED',
}

const NONE = '__none__'

interface PlacedEvent {
  occurrence: Occurrence
  column: number
  columns: number
  topMinutes: number
  heightMinutes: number
}

/**
 * Assigns each event to the first free column inside its overlap cluster, so simultaneous events sit
 * side by side at a fraction of the column width instead of stacking on top of each other.
 */
function layoutDay(
  occurrences: Occurrence[],
  dayFrom: number,
  visibleFromHour: number,
  visibleToHour: number,
): PlacedEvent[] {
  const windowFrom = dayFrom + visibleFromHour * 3_600_000
  const windowTo = dayFrom + visibleToHour * 3_600_000

  const items = occurrences
    .map((occurrence) => ({
      occurrence,
      start: Math.max(new Date(occurrence.startAt).getTime(), windowFrom),
      end: Math.min(new Date(occurrence.endAt).getTime(), windowTo),
    }))
    .filter((item) => item.end > item.start)
    .sort((left, right) => left.start - right.start || right.end - left.end)

  const placed: PlacedEvent[] = []
  let cluster: typeof items = []
  let clusterEnd = -Infinity

  const flush = () => {
    if (cluster.length === 0) return
    const columnEnds: number[] = []
    const assignments = cluster.map((item) => {
      let column = columnEnds.findIndex((end) => end <= item.start)
      if (column === -1) {
        column = columnEnds.length
        columnEnds.push(item.end)
      } else {
        columnEnds[column] = item.end
      }
      return { item, column }
    })
    for (const { item, column } of assignments) {
      placed.push({
        occurrence: item.occurrence,
        column,
        columns: columnEnds.length,
        topMinutes: Math.round((item.start - windowFrom) / 60_000),
        heightMinutes: Math.round((item.end - item.start) / 60_000),
      })
    }
    cluster = []
    clusterEnd = -Infinity
  }

  for (const item of items) {
    if (cluster.length > 0 && item.start >= clusterEnd) flush()
    cluster.push(item)
    clusterEnd = Math.max(clusterEnd, item.end)
  }
  flush()

  return placed
}

function startOfLocalDay(iso: string): number {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day, 0, 0, 0, 0).getTime()
}

function addMonthsIso(iso: string, delta: number): string {
  const [year, month] = iso.split('-').map(Number)
  const shifted = new Date(year, month - 1 + delta, 1)
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}-01`
}

function localDayIso(instant: string): string {
  return toLocalDateInput(instant) ?? ''
}

interface EventFormState {
  title: string
  description: string
  eventType: EventType
  startAt: string
  endAt: string
  allDay: boolean
  recurrenceRule: string
  removeRecurrence: boolean
  taskId: string
  goalId: string
  habitId: string
  location: string
  color: string
}

function defaultEventForm(startAt: string, endAt: string): EventFormState {
  return {
    title: '',
    description: '',
    eventType: 'EVENT',
    startAt,
    endAt,
    allDay: false,
    recurrenceRule: '',
    removeRecurrence: false,
    taskId: '',
    goalId: '',
    habitId: '',
    location: '',
    color: '',
  }
}

function occurrenceToForm(occurrence: Occurrence, allDay: boolean): EventFormState {
  return {
    title: occurrence.title,
    description: '',
    eventType: occurrence.eventType,
    startAt: toLocalDateTimeInput(occurrence.startAt),
    endAt: toLocalDateTimeInput(occurrence.endAt),
    allDay,
    recurrenceRule: '',
    removeRecurrence: false,
    taskId: occurrence.taskId ?? '',
    goalId: '',
    habitId: '',
    location: '',
    color: occurrence.color ?? '',
  }
}

function EventDialog({
  open,
  onOpenChange,
  form,
  setForm,
  busy,
  errorMessage,
  habitOptions,
  isRecurring,
  onSubmit,
  onDelete,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  form: EventFormState
  setForm: (updater: (current: EventFormState) => EventFormState) => void
  busy: boolean
  errorMessage?: string
  habitOptions: { id: string; name: string }[]
  isRecurring: boolean
  onSubmit: (request: EventRequest) => void
  onDelete: () => void
}) {
  const field = <K extends keyof EventFormState>(key: K, value: EventFormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      eventType: form.eventType,
      startAt: fromLocalDateTimeInput(form.startAt) ?? new Date().toISOString(),
      endAt:
        fromLocalDateTimeInput(form.endAt) ??
        new Date(Date.now() + 3_600_000).toISOString(),
      allDay: form.allDay,
      recurrenceRule: form.removeRecurrence ? '' : form.recurrenceRule.trim() || undefined,
      taskId: form.taskId.trim() || undefined,
      goalId: form.goalId.trim() || undefined,
      habitId: form.habitId || undefined,
      location: form.location.trim() || undefined,
      color: form.color.trim() || undefined,
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{form.title ? 'Edit event' : 'New event'}</DialogTitle>
          <DialogDescription>
            Recurring series are expanded by the server. Saving stores the rule; this page only asks for
            the range it should expand into.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={submit} className="space-y-4" noValidate>
          {errorMessage ? (
            <p
              role="alert"
              className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {errorMessage}
            </p>
          ) : null}

          <div className="space-y-1.5">
            <Label htmlFor="event-title">Title</Label>
            <Input
              id="event-title"
              required
              maxLength={200}
              value={form.title}
              onChange={(event) => field('title', event.target.value)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="event-start">Starts</Label>
              <Input
                id="event-start"
                type="datetime-local"
                value={form.startAt}
                onChange={(event) => field('startAt', event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="event-end">Ends</Label>
              <Input
                id="event-end"
                type="datetime-local"
                value={form.endAt}
                onChange={(event) => field('endAt', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-type">Type</Label>
              <Select
                value={form.eventType}
                onValueChange={(value) => field('eventType', value as EventType)}
              >
                <SelectTrigger id="event-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {EVENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {titleCase(type)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-habit">Habit</Label>
              <Select
                value={form.habitId || NONE}
                onValueChange={(value) => field('habitId', value === NONE ? '' : value)}
              >
                <SelectTrigger id="event-habit">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>Not a habit reminder</SelectItem>
                  {habitOptions.map((habit) => (
                    <SelectItem key={habit.id} value={habit.id}>
                      {habit.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-task">Task id</Label>
              <Input
                id="event-task"
                value={form.taskId}
                placeholder="Optional"
                onChange={(event) => field('taskId', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-goal">Goal id</Label>
              <Input
                id="event-goal"
                value={form.goalId}
                placeholder="Optional"
                onChange={(event) => field('goalId', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-location">Location</Label>
              <Input
                id="event-location"
                maxLength={200}
                value={form.location}
                onChange={(event) => field('location', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="event-color">Colour</Label>
              <Input
                id="event-color"
                maxLength={16}
                placeholder="#38bdf8"
                value={form.color}
                onChange={(event) => field('color', event.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-md border p-3">
            <Switch
              id="event-allday"
              checked={form.allDay}
              onCheckedChange={(checked) => field('allDay', checked)}
            />
            <Label htmlFor="event-allday">All day</Label>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="event-recurrence">Recurrence rule</Label>
            <Input
              id="event-recurrence"
              maxLength={200}
              placeholder="FREQ=WEEKLY;BYDAY=MO,WE"
              value={form.removeRecurrence ? '' : form.recurrenceRule}
              disabled={form.removeRecurrence}
              onChange={(event) => field('recurrenceRule', event.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              The server owns the rule and expands it across any range you request. Leave this blank to keep
              the existing rule untouched.
            </p>
            {isRecurring ? (
              <label className="flex items-center gap-2 text-xs text-muted-foreground">
                <input
                  type="checkbox"
                  className="size-4 accent-[var(--color-primary)]"
                  checked={form.removeRecurrence}
                  onChange={(event) => field('removeRecurrence', event.target.checked)}
                />
                Remove the recurrence rule on save
              </label>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="event-description">Description</Label>
            <Textarea
              id="event-description"
              rows={3}
              maxLength={20000}
              value={form.description}
              onChange={(event) => field('description', event.target.value)}
            />
          </div>

          <DialogFooter className="sm:justify-between">
            <Button type="button" variant="destructive" onClick={onDelete}>
              <Trash2 /> Delete
            </Button>
            <div className="flex flex-col-reverse gap-2 sm:flex-row">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={busy}>
                Save event
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function CalendarPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const patchParams = useCallback(
    (patch: Record<string, string | null>) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(patch)) {
            if (value === null || value === '') next.delete(key)
            else next.set(key, value)
          }
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const view = searchParams.get('view') === 'month' ? 'month' : 'week'
  const anchor = searchParams.get('start') || todayIso()

  const weekStart = startOfWeekIso(anchor)
  const monthGridStart = startOfWeekIso(`${anchor.slice(0, 7)}-01`)
  const rangeStart = view === 'week' ? weekStart : monthGridStart
  const rangeEnd =
    view === 'week' ? addDaysIso(weekStart, 6) : addDaysIso(monthGridStart, 41)

  const days = useMemo(
    () => Array.from({ length: view === 'week' ? 7 : 42 }, (_, index) => addDaysIso(rangeStart, index)),
    [rangeStart, view],
  )

  const [editorOpen, setEditorOpen] = useState(false)
  const [form, setForm] = useState<EventFormState>(() =>
    defaultEventForm(toLocalDateTimeInput(new Date().toISOString()), toLocalDateTimeInput(new Date(Date.now() + 3_600_000).toISOString())),
  )
  const [editingSeriesId, setEditingSeriesId] = useState<string | null>(null)
  const [editingIsRecurring, setEditingIsRecurring] = useState(false)
  const [editorError, setEditorError] = useState<string>()
  const [moveOpen, setMoveOpen] = useState(false)
  const [moveStart, setMoveStart] = useState('')
  const [moveEnd, setMoveEnd] = useState('')
  const [moveSeriesId, setMoveSeriesId] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const feedQuery = useQuery({
    queryKey: ['calendar', 'feed', rangeStart, rangeEnd],
    queryFn: () => calendarApi.feed(rangeStart, rangeEnd),
  })

  const deadlinesQuery = useQuery({
    queryKey: ['calendar', 'deadlines', rangeStart, rangeEnd],
    queryFn: () => calendarApi.deadlines(rangeStart, rangeEnd),
  })

  const feed = feedQuery.data
  const occurrences = feed?.occurrences ?? []
  const habitSchedules = feed?.habitSchedules ?? []

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['calendar'] }),
      queryClient.invalidateQueries({ queryKey: ['tasks'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: (request: EventRequest) => calendarApi.create(request),
    onSuccess: async () => {
      setEditorOpen(false)
      setEditorError(undefined)
      await invalidate()
      toast.success('Event created')
    },
    onError: (error) => setEditorError(toNormalisedError(error).message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: EventRequest }) =>
      calendarApi.update(id, request),
    onSuccess: async () => {
      setEditorOpen(false)
      setEditingSeriesId(null)
      setEditorError(undefined)
      await invalidate()
      toast.success('Event updated')
    },
    onError: (error) => setEditorError(toNormalisedError(error).message),
  })

  const moveMutation = useMutation({
    mutationFn: ({ id, startAt, endAt }: { id: string; startAt: string; endAt: string }) =>
      calendarApi.move(id, { startAt, endAt }),
    onSuccess: async () => {
      setMoveOpen(false)
      setMoveSeriesId(null)
      await invalidate()
      toast.success('Event rescheduled')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => calendarApi.remove(id),
    onSuccess: async () => {
      setDeleteOpen(false)
      setEditorOpen(false)
      setEditingSeriesId(null)
      await invalidate()
      toast.success('Event deleted')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const hourBounds = useMemo(() => {
    let start = 8
    let end = 20
    for (const occurrence of occurrences) {
      const from = new Date(occurrence.startAt)
      const to = new Date(occurrence.endAt)
      if (from.getHours() < start) start = from.getHours()
      const spansDay = to.toDateString() !== from.toDateString()
      const toHour = spansDay ? 24 : to.getHours() + (to.getMinutes() > 0 ? 1 : 0)
      if (toHour > end) end = toHour
    }
    if (end - start < 5) end = Math.min(24, start + 5)
    return { start, end }
  }, [occurrences])

  const hours = useMemo(() => {
    const list: number[] = []
    for (let hour = hourBounds.start; hour < hourBounds.end; hour += 1) list.push(hour)
    return list
  }, [hourBounds])

  const eventsByDay = useMemo(() => {
    const map = new Map<string, Occurrence[]>()
    for (const day of days) map.set(day, [])
    for (const occurrence of occurrences) {
      const startDay = localDayIso(occurrence.startAt)
      const endDay = localDayIso(occurrence.endAt)
      let cursor = startDay
      let guard = 0
      while (cursor <= endDay && guard < 60) {
        map.get(cursor)?.push(occurrence)
        if (cursor === endDay) break
        cursor = addDaysIso(cursor, 1)
        guard += 1
      }
    }
    return map
  }, [days, occurrences])

  const allDayByDay = useMemo(() => {
    const map = new Map<string, Occurrence[]>()
    for (const [day, dayOccurrences] of eventsByDay) {
      map.set(
        day,
        dayOccurrences.filter((occurrence) => {
          const duration =
            (new Date(occurrence.endAt).getTime() - new Date(occurrence.startAt).getTime()) / 60_000
          return duration >= ALL_DAY_MINUTES
        }),
      )
    }
    return map
  }, [eventsByDay])

  const timedByDay = useMemo(() => {
    const map = new Map<string, PlacedEvent[]>()
    for (const day of days) {
      const dayStart = startOfLocalDay(day)
      const timed = (eventsByDay.get(day) ?? []).filter((occurrence) => {
        const duration =
          (new Date(occurrence.endAt).getTime() - new Date(occurrence.startAt).getTime()) / 60_000
        return duration < ALL_DAY_MINUTES
      })
      map.set(day, layoutDay(timed, dayStart, hourBounds.start, hourBounds.end))
    }
    return map
  }, [days, eventsByDay, hourBounds])

  const deadlinesByDay = useMemo(() => {
    const map = new Map<string, { id: string; title: string; deadline: string }[]>()
    for (const day of days) map.set(day, [])
    for (const task of feed?.deadlines ?? []) {
      if (!task.deadline) continue
      const day = localDayIso(task.deadline)
      map.get(day)?.push({ id: task.id, title: task.title, deadline: task.deadline })
    }
    return map
  }, [days, feed?.deadlines])

  const openCreate = (startAt: string, endAt: string) => {
    setEditingSeriesId(null)
    setEditingIsRecurring(false)
    setEditorError(undefined)
    setForm(defaultEventForm(startAt, endAt))
    setEditorOpen(true)
  }

  const openSlot = (day: string, hour: number) => {
    const [year, month, date] = day.split('-').map(Number)
    const start = new Date(year, month - 1, date, hour, 0, 0, 0)
    const end = new Date(year, month - 1, date, hour + 1, 0, 0, 0)
    openCreate(toLocalDateTimeInput(start.toISOString()), toLocalDateTimeInput(end.toISOString()))
  }

  const openOccurrence = (occurrence: Occurrence, allDay: boolean) => {
    setEditingSeriesId(occurrence.seriesId)
    setEditingIsRecurring(occurrence.seriesId !== occurrence.id)
    setEditorError(undefined)
    setForm(occurrenceToForm(occurrence, allDay))
    setEditorOpen(true)
  }

  const openMove = (occurrence: Occurrence) => {
    setMoveSeriesId(occurrence.seriesId)
    setMoveStart(toLocalDateTimeInput(occurrence.startAt))
    setMoveEnd(toLocalDateTimeInput(occurrence.endAt))
    setMoveOpen(true)
  }

  const step = (direction: -1 | 1) => {
    patchParams({ start: view === 'week' ? addDaysIso(weekStart, direction * 7) : addMonthsIso(anchor, direction) })
  }

  const habitOptions = useMemo(
    () => habitSchedules.map((habit) => ({ id: habit.id, name: habit.name })),
    [habitSchedules],
  )

  const lane = (type: EventType) => toneClasses(toneFor(LANE_TONE_KEY[type]))

  const deadlineAgenda = deadlinesQuery.data ?? []
  const busyEditor = createMutation.isPending || updateMutation.isPending

  const rangeLabel =
    view === 'week'
      ? `${formatDate(rangeStart)} – ${formatDate(rangeEnd)}`
      : `${formatDate(rangeStart)} – ${formatDate(rangeEnd)}`

  const weekGrid = (
    <div className="overflow-x-auto">
      <div className="min-w-[56rem]">
        <div className="grid grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] border-b">
          <div className="px-2 py-2 text-xs font-medium text-muted-foreground">All day</div>
          {days.map((day) => (
            <div key={day} className="border-l px-2 py-2">
              <p className="text-xs font-medium">{formatDate(day)}</p>
              {(allDayByDay.get(day) ?? []).map((occurrence) => (
                <button
                  key={occurrence.id}
                  type="button"
                  onClick={() => openOccurrence(occurrence, true)}
                  className={cn(
                    'mt-1 w-full truncate rounded px-1.5 py-0.5 text-left text-xs font-medium',
                    lane(occurrence.eventType),
                  )}
                >
                  {occurrence.title}
                </button>
              ))}
            </div>
          ))}
        </div>

        <div className="grid grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] border-b bg-secondary/20 px-2 py-2">
          <div className="text-xs font-medium text-muted-foreground">Deadlines</div>
          {days.map((day) => {
            const chips = deadlinesByDay.get(day) ?? []
            return (
              <div key={day} className="flex flex-wrap gap-1 border-l px-1">
                {chips.map((chip) => (
                  <Link
                    key={chip.id}
                    to={`/app/tasks/${chip.id}`}
                    title={`${chip.title} · ${formatTime(chip.deadline)}`}
                    className="max-w-full truncate rounded-full bg-destructive/15 px-2 py-0.5 text-xs font-medium text-destructive hover:underline"
                  >
                    {formatTime(chip.deadline)} {chip.title}
                  </Link>
                ))}
              </div>
            )
          })}
        </div>

        <div className="grid grid-cols-[3.5rem_repeat(7,minmax(0,1fr))]">
          <div className="relative">
            {hours.map((hour) => (
              <div
                key={hour}
                style={{ height: HOUR_HEIGHT }}
                className="relative border-b border-border/60 pr-2 text-right"
              >
                <span className="absolute right-2 -top-1.5 text-[10px] tabular-nums text-muted-foreground">
                  {String(hour).padStart(2, '0')}:00
                </span>
              </div>
            ))}
          </div>

          {days.map((day) => (
            <div key={day} className="relative border-l">
              {hours.map((hour) => (
                <button
                  key={hour}
                  type="button"
                  onClick={() => openSlot(day, hour)}
                  aria-label={`New event on ${day} at ${String(hour).padStart(2, '0')}:00`}
                  style={{ height: HOUR_HEIGHT }}
                  className="block w-full border-b border-border/60 text-left transition-colors hover:bg-secondary/40"
                />
              ))}

              {(timedByDay.get(day) ?? []).map((placed) => {
                const { occurrence } = placed
                const height = Math.max(20, (placed.heightMinutes / 60) * HOUR_HEIGHT)
                return (
                  <button
                    key={occurrence.id}
                    type="button"
                    onClick={() => openOccurrence(occurrence, false)}
                    onDoubleClick={() => openMove(occurrence)}
                    title={`${occurrence.title} · ${formatTime(occurrence.startAt)}–${formatTime(occurrence.endAt)}`}
                    style={{
                      top: (placed.topMinutes / 60) * HOUR_HEIGHT,
                      height,
                      left: `${(placed.column / placed.columns) * 100}%`,
                      width: `${(1 / placed.columns) * 100}%`,
                    }}
                    className={cn(
                      'absolute overflow-hidden rounded border-l-2 px-1.5 py-0.5 text-left text-xs hover:brightness-110',
                      lane(occurrence.eventType),
                    )}
                  >
                    <span className="block truncate font-medium">{occurrence.title}</span>
                    <span className="block truncate opacity-80">
                      {formatTime(occurrence.startAt)} – {formatTime(occurrence.endAt)}
                    </span>
                  </button>
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  )

  const monthGrid = (
    <div className="grid grid-cols-7 gap-px overflow-hidden rounded-lg border bg-border">
      {days.map((day) => {
        const dayOccurrences = eventsByDay.get(day) ?? []
        const chips = deadlinesByDay.get(day) ?? []
        const isToday = day === todayIso()
        return (
          <div
            key={day}
            className={cn(
              'min-h-28 space-y-1 bg-card p-1.5',
              isToday && 'bg-primary/5',
            )}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium tabular-nums">{Number(day.slice(8, 10))}</span>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`New event on ${day}`}
                onClick={() => openSlot(day, 9)}
              >
                <Plus className="size-3.5" />
              </Button>
            </div>

            {chips.map((chip) => (
              <Link
                key={chip.id}
                to={`/app/tasks/${chip.id}`}
                className="block truncate rounded bg-destructive/15 px-1 py-0.5 text-[11px] font-medium text-destructive hover:underline"
              >
                {formatTime(chip.deadline)} {chip.title}
              </Link>
            ))}

            {dayOccurrences.slice(0, 4).map((occurrence) => (
              <button
                key={occurrence.id}
                type="button"
                onClick={() => openOccurrence(occurrence, false)}
                className={cn(
                  'block w-full truncate rounded px-1 py-0.5 text-left text-[11px] font-medium',
                  lane(occurrence.eventType),
                )}
              >
                {formatTime(occurrence.startAt)} {occurrence.title}
              </button>
            ))}
            {dayOccurrences.length > 4 ? (
              <p className="px-1 text-[11px] text-muted-foreground">
                +{dayOccurrences.length - 4} more
              </p>
            ) : null}
          </div>
        )
      })}
    </div>
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Calendar"
        description="Events, focus blocks, habit reminders and task deadlines for the visible range."
        actions={
          <Button onClick={() => openSlot(rangeStart, new Date().getHours())}>
            <Plus /> New event
          </Button>
        }
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon-sm" aria-label="Previous range" onClick={() => step(-1)}>
            <ChevronLeft />
          </Button>
          <Button variant="outline" onClick={() => patchParams({ start: todayIso() })}>
            Today
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Next range" onClick={() => step(1)}>
            <ChevronRight />
          </Button>
          <p className="ml-1 inline-flex items-center gap-1.5 text-sm font-medium">
            <CalendarDays className="size-4" aria-hidden />
            {rangeLabel}
          </p>
        </div>

        <Tabs
          value={view}
          onValueChange={(value) => patchParams({ view: value, start: todayIso() })}
        >
          <TabsList>
            <TabsTrigger value="week">Week</TabsTrigger>
            <TabsTrigger value="month">Month</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {EVENT_TYPES.map((type) => (
          <div key={type} className="flex items-center gap-2 rounded-lg border bg-card px-3 py-2 text-sm">
            <span className={cn('size-2.5 rounded-full', lane(type))} aria-hidden />
            {titleCase(type)}
          </div>
        ))}
      </div>

      {feedQuery.isPending ? (
        <div className="space-y-2">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-96 w-full" />
        </div>
      ) : feedQuery.error ? (
        <div
          role="alert"
          className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
        >
          <p>{toNormalisedError(feedQuery.error).message}</p>
          <Button variant="outline" size="sm" onClick={() => void feedQuery.refetch()}>
            <RefreshCw /> Try again
          </Button>
        </div>
      ) : feed && feed.totalEvents === 0 && days.length > 0 && view === 'week' ? (
        <EmptyState
          icon={<CalendarDays />}
          title="Nothing scheduled in this week"
          description="Pick an hour in the grid, or add an event that recurs and let the server expand it."
          action={
            <Button onClick={() => openSlot(rangeStart, new Date().getHours())}>
              <Plus /> New event
            </Button>
          }
        />
      ) : (
        <SectionCard
          title={view === 'week' ? 'Week' : 'Month'}
          description={`${feed?.totalEvents ?? 0} occurrence${feed?.totalEvents === 1 ? '' : 's'} between ${rangeLabel}.`}
        >
          {view === 'week' ? weekGrid : monthGrid}
          <p className="mt-3 text-xs text-muted-foreground">
            Double-click an event block to reschedule it without reopening the full form.
          </p>
        </SectionCard>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard
          title="Deadline agenda"
          description="Task deadlines in this range, shown as instants rather than blocks."
        >
          {deadlinesQuery.isPending ? (
            <Spinner label="Loading deadlines" />
          ) : deadlinesQuery.error ? (
            <div role="alert" className="text-sm text-destructive">
              <p>{toNormalisedError(deadlinesQuery.error).message}</p>
              <Button
                variant="outline"
                size="sm"
                className="mt-2"
                onClick={() => void deadlinesQuery.refetch()}
              >
                <RefreshCw /> Try again
              </Button>
            </div>
          ) : deadlineAgenda.length === 0 ? (
            <EmptyState
              icon={<AlarmClock />}
              title="No deadlines in range"
              description="Deadlines appear here once an open task has a deadline inside the visible range."
            />
          ) : (
            <ul className="divide-y">
              {deadlineAgenda.map((item) => (
                <li key={item.id} className="flex items-center gap-3 py-2 text-sm">
                  <StatusBadge value="CRITICAL" label={formatTime(item.startAt)} />
                  <Link
                    to={`/app/tasks/${item.id}`}
                    className="min-w-0 flex-1 truncate hover:text-primary hover:underline"
                  >
                    {item.title}
                  </Link>
                  <Badge variant="outline">{formatDate(item.startAt)}</Badge>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>

        <SectionCard
          title="Habit reminder legend"
          description="Recurring habits that appear on this calendar."
        >
          {habitSchedules.length === 0 ? (
            <EmptyState
              icon={<Repeat />}
              title="No habits yet"
              description="Create a habit and its reminders will appear here and on the grid."
              action={
                <Link to="/app/habits">
                  <Button variant="outline">Go to habits</Button>
                </Link>
              }
            />
          ) : (
            <ul className="divide-y">
              {habitSchedules.map((habit) => (
                <li key={habit.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
                  <span
                    className={cn('size-2.5 rounded-full', lane('HABIT_REMINDER'))}
                    aria-hidden
                  />
                  <span className="min-w-0 flex-1 truncate font-medium">{habit.name}</span>
                  <Badge variant="outline">{titleCase(habit.frequencyType)}</Badge>
                  <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                    <Clock className="size-3" aria-hidden />
                    {habit.reminderTime ? habit.reminderTime.slice(0, 5) : 'any time'}
                  </span>
                  <span className="text-xs tabular-nums text-muted-foreground">
                    streak {habit.currentStreak}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      </div>

      <EventDialog
        open={editorOpen}
        onOpenChange={setEditorOpen}
        form={form}
        setForm={setForm}
        busy={busyEditor}
        errorMessage={editorError}
        habitOptions={habitOptions}
        isRecurring={editingIsRecurring}
        onSubmit={(request) => {
          if (editingSeriesId) updateMutation.mutate({ id: editingSeriesId, request })
          else createMutation.mutate(request)
        }}
        onDelete={() => {
          if (editingSeriesId) setDeleteOpen(true)
        }}
      />

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete event</DialogTitle>
            <DialogDescription>
              {editingIsRecurring
                ? 'This is part of a recurring series. Deleting removes the series and every future occurrence.'
                : 'The event and its reminder will be removed permanently.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              Keep event
            </Button>
            <Button
              variant="destructive"
              loading={deleteMutation.isPending}
              onClick={() => {
                if (editingSeriesId) deleteMutation.mutate(editingSeriesId)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={moveOpen} onOpenChange={setMoveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change time</DialogTitle>
            <DialogDescription>
              The duration is preserved by the server; only the start and end you set here are used.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="move-start">Starts</Label>
              <Input
                id="move-start"
                type="datetime-local"
                value={moveStart}
                onChange={(event) => setMoveStart(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="move-end">Ends</Label>
              <Input
                id="move-end"
                type="datetime-local"
                value={moveEnd}
                onChange={(event) => setMoveEnd(event.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoveOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={moveMutation.isPending}
              onClick={() => {
                const startAt = fromLocalDateTimeInput(moveStart)
                const endAt = fromLocalDateTimeInput(moveEnd)
                if (!moveSeriesId || !startAt || !endAt) {
                  toast.error('Choose a valid start and end time')
                  return
                }
                moveMutation.mutate({ id: moveSeriesId, startAt, endAt })
              }}
            >
              Reschedule
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}