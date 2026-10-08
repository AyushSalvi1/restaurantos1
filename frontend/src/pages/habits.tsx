import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { Archive, CalendarDays, Flame, Pencil, Plus, Repeat, Sparkles, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'

import { habitApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
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
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { StatusBadge } from '@/lib/badges'
import { addDaysIso, formatDate, formatPercent, todayIso } from '@/lib/format'
import { cn } from '@/lib/utils'
import { toNormalisedError } from '@/hooks/use-api-error'
import type {
  HabitFrequency,
  HabitLogResponse,
  HabitRequest,
  HabitResponse,
  HabitStats,
  HabitTrendPoint,
} from '@/types/api'

const WEEKDAYS: { iso: number; short: string }[] = [
  { iso: 1, short: 'M' },
  { iso: 2, short: 'T' },
  { iso: 3, short: 'W' },
  { iso: 4, short: 'T' },
  { iso: 5, short: 'F' },
  { iso: 6, short: 'S' },
  { iso: 7, short: 'S' },
]

const FREQUENCIES: { value: HabitFrequency; label: string; hint: string }[] = [
  { value: 'DAILY', label: 'Daily', hint: 'Every day counts' },
  { value: 'WEEKLY', label: 'Weekly', hint: 'A set number of times per week' },
]

const TREND_WINDOWS = [7, 30, 90] as const

const TOOLTIP_STYLE = {
  backgroundColor: 'var(--color-card)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  color: 'var(--color-card-foreground)',
  fontSize: 12,
}

const SHORT_WEEKDAY = new Intl.DateTimeFormat(undefined, { weekday: 'short' })

function weekdayShort(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  return Number.isNaN(date.getTime()) ? iso : SHORT_WEEKDAY.format(date)
}

function dayTick(iso: string, days: number): string {
  return days <= 7 ? weekdayShort(iso) : iso.slice(5)
}

interface HabitFormValues {
  name: string
  description: string
  category: string
  frequencyType: HabitFrequency
  timesPerPeriod: number
  targetDays: number[]
  reminderTime: string
  color: string
  archived: boolean
}

const EMPTY_HABIT: HabitFormValues = {
  name: '',
  description: '',
  category: '',
  frequencyType: 'DAILY',
  timesPerPeriod: 3,
  targetDays: [],
  reminderTime: '',
  color: '#38bdf8',
  archived: false,
}

function toFormValues(habit: HabitResponse): HabitFormValues {
  return {
    name: habit.name,
    description: habit.description ?? '',
    category: habit.category ?? '',
    frequencyType: habit.frequencyType ?? 'DAILY',
    timesPerPeriod: habit.timesPerPeriod ?? 3,
    targetDays: habit.targetDays ?? [],
    reminderTime: (habit.reminderTime ?? '').slice(0, 5),
    color: habit.color || '#38bdf8',
    archived: habit.archived ?? false,
  }
}

function toRequest(values: HabitFormValues): HabitRequest {
  return {
    name: values.name.trim(),
    description: values.description.trim() || undefined,
    category: values.category.trim() || undefined,
    frequencyType: values.frequencyType,
    timesPerPeriod: values.frequencyType === 'WEEKLY' ? values.timesPerPeriod : undefined,
    targetDays: values.targetDays.length > 0 ? [...values.targetDays].sort((a, b) => a - b) : undefined,
    reminderTime: values.reminderTime || undefined,
    color: values.color.trim() || undefined,
    archived: values.archived,
  }
}

function WeekdayChips({ targetDays, muted }: { targetDays: number[]; muted?: boolean }) {
  if (targetDays.length === 0) {
    return <span className="text-xs text-muted-foreground">No target days set</span>
  }
  return (
    <div className="flex gap-1" aria-label="Target days">
      {WEEKDAYS.map((day) => {
        const active = targetDays.includes(day.iso)
        return (
          <span
            key={day.iso}
            title={`ISO weekday ${day.iso}`}
            className={cn(
              'flex size-6 items-center justify-center rounded-md border text-[11px] font-medium',
              active
                ? 'border-primary bg-primary/15 text-primary'
                : muted
                  ? 'border-border text-muted-foreground/40'
                  : 'border-border text-muted-foreground/60',
            )}
          >
            {day.short}
          </span>
        )
      })}
    </div>
  )
}

function HabitFormDialog({
  open,
  habit,
  onOpenChange,
  onSubmit,
  busy,
}: {
  open: boolean
  habit: HabitResponse | null
  onOpenChange: (open: boolean) => void
  onSubmit: (values: HabitFormValues) => void
  busy: boolean
}) {
  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<HabitFormValues>({
    defaultValues: habit ? toFormValues(habit) : { ...EMPTY_HABIT, targetDays: [] },
  })
  const frequency = watch('frequencyType')
  const targetDays = watch('targetDays')

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        reset(habit ? toFormValues(habit) : { ...EMPTY_HABIT, targetDays: [] })
        onOpenChange(next)
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{habit ? `Edit ${habit.name}` : 'New habit'}</DialogTitle>
          <DialogDescription>
            {habit
              ? 'Changes apply from now on. Archived habits stop counting towards your streak.'
              : 'Pick the cadence that matches how you actually want to keep this.'}
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          noValidate
          onSubmit={handleSubmit((values) => onSubmit(values))}
        >
          <div className="space-y-1.5">
            <Label htmlFor="habit-name">Name</Label>
            <Input
              id="habit-name"
              maxLength={120}
              required
              aria-invalid={Boolean(errors.name)}
              {...register('name', { required: 'A name is required' })}
            />
            {errors.name ? <p className="text-xs text-destructive">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="habit-description">Description</Label>
            <Textarea id="habit-description" rows={2} maxLength={500} {...register('description')} />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="habit-category">Category</Label>
              <Input id="habit-category" maxLength={60} placeholder="Health" {...register('category')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="habit-reminder">Reminder time</Label>
              <Input id="habit-reminder" type="time" {...register('reminderTime')} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Frequency</Label>
            <Controller
              control={control}
              name="frequencyType"
              render={({ field }) => (
                <Select value={field.value} onValueChange={(value) => field.onChange(value as HabitFrequency)}>
                  <SelectTrigger id="habit-frequency" aria-label="Frequency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FREQUENCIES.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            <p className="text-xs text-muted-foreground">
              {FREQUENCIES.find((option) => option.value === frequency)?.hint}
            </p>
          </div>

          {frequency === 'WEEKLY' ? (
            <div className="space-y-1.5">
              <Label htmlFor="habit-times">Times per week</Label>
              <Input
                id="habit-times"
                type="number"
                min={1}
                max={7}
                {...register('timesPerPeriod', { valueAsNumber: true })}
              />
              <p className="text-xs text-muted-foreground">
                Leave the target days empty to schedule the whole week.
              </p>
            </div>
          ) : null}

          <fieldset className="space-y-1.5">
            <legend className="text-sm font-medium text-muted-foreground">
              Target days {frequency === 'WEEKLY' ? '(week view)' : '(informational)'}
            </legend>
            <div className="flex flex-wrap gap-2">
              {WEEKDAYS.map((day) => {
                const checked = targetDays.includes(day.iso)
                const inputId = `habit-day-${day.iso}`
                return (
                  <label
                    key={day.iso}
                    htmlFor={inputId}
                    className={cn(
                      'flex size-9 cursor-pointer items-center justify-center rounded-md border text-xs font-medium transition-colors',
                      checked
                        ? 'border-primary bg-primary/15 text-primary'
                        : 'text-muted-foreground hover:bg-secondary/60',
                    )}
                  >
                    <input
                      id={inputId}
                      type="checkbox"
                      className="sr-only"
                      checked={checked}
                      onChange={() => {
                        const next = checked
                          ? targetDays.filter((value) => value !== day.iso)
                          : [...targetDays, day.iso]
                        setValue('targetDays', next, { shouldDirty: true })
                      }}
                    />
                    {day.short}
                  </label>
                )
              })}
            </div>
          </fieldset>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="habit-color">Colour</Label>
              <Input id="habit-color" type="color" className="h-9 p-1" {...register('color')} />
            </div>
            <div className="flex items-end gap-3 rounded-md border p-3">
              <Controller
                control={control}
                name="archived"
                render={({ field }) => (
                  <Switch
                    id="habit-archived"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    aria-label="Archived"
                  />
                )}
              />
              <div className="space-y-0.5">
                <Label htmlFor="habit-archived">Archived</Label>
                <p className="text-xs text-muted-foreground">Hidden unless archived habits are shown.</p>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {habit ? 'Save changes' : 'Create habit'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  busy,
  onConfirm,
  onOpenChange,
}: {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  busy: boolean
  onConfirm: () => void
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            Keep it
          </Button>
          <Button variant="destructive" loading={busy} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function HabitDetailPanel({ habitId }: { habitId: string }) {
  const [days, setDays] = useState<number>(30)

  const statistics = useQuery({
    queryKey: ['habits', 'statistics'],
    queryFn: () => habitApi.statistics(),
  })

  const trend = useQuery({
    queryKey: ['habits', 'trend', habitId, days],
    queryFn: () => habitApi.trend(habitId, days),
  })

  const logs = useQuery({
    queryKey: ['habits', 'logs', habitId],
    queryFn: () => {
      const to = todayIso()
      return habitApi.logs(habitId, addDaysIso(to, -29), to)
    },
  })

  const stats: HabitStats | undefined = statistics.data?.find((item) => item.habitId === habitId)

  const chartData = (trend.data?.points ?? []).map((point: HabitTrendPoint) => ({
    date: point.date,
    completed: point.completed ? 1 : 0,
    missed: point.scheduled && !point.completed ? 1 : 0,
  }))

  const logByDate = new Map<string, HabitLogResponse>(
    (logs.data ?? []).map((log) => [log.logDate, log]),
  )
  const gridDays = Array.from({ length: 30 }, (_, index) => addDaysIso(todayIso(), index - 29))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-3">
        <SectionCard
          className="lg:col-span-1"
          title="Statistics"
          description={stats ? stats.name : 'Loading'}
        >
          {statistics.isError ? (
            <ErrorPanel
              message={statistics.error.message}
              onRetry={() => void statistics.refetch()}
            />
          ) : !stats ? (
            <Skeleton className="h-32 w-full" />
          ) : (
            <dl className="space-y-3 text-sm">
              <Row label="Current streak" value={`${stats.currentStreak} day(s)`} />
              <Row label="Longest streak" value={`${stats.longestStreak} day(s)`} />
              <Row label="Best day of week" value={stats.bestDayOfWeek || '—'} />
              <Row
                label="Missed (30 days)"
                value={`${stats.missedLast30Days} of ${stats.scheduledLast30Days} scheduled`}
              />
              <div className="space-y-1 border-t pt-3">
                <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Server suggestion
                </dt>
                <dd className="flex gap-2 text-sm">
                  <Sparkles className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                  <span>{stats.suggestion}</span>
                </dd>
              </div>
            </dl>
          )}
        </SectionCard>

        <SectionCard
          className="lg:col-span-2"
          title="Completion trend"
          description="Green days were completed; red days were scheduled but missed."
          action={
            <div className="flex gap-1" role="group" aria-label="Trend window">
              {TREND_WINDOWS.map((window) => (
                <Button
                  key={window}
                  size="sm"
                  variant={days === window ? 'default' : 'outline'}
                  onClick={() => setDays(window)}
                  aria-pressed={days === window}
                >
                  {window}d
                </Button>
              ))}
            </div>
          }
        >
          {trend.isError ? (
            <ErrorPanel message={trend.error.message} onRetry={() => void trend.refetch()} />
          ) : trend.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : chartData.length === 0 ? (
            <EmptyState
              title="No trend data yet"
              description="The server has no completion history for this window. Log the habit to start the series."
            />
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="date"
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                    tickFormatter={(value: string) => dayTick(String(value), days)}
                    interval="preserveStartEnd"
                    minTickGap={16}
                  />
                  <YAxis
                    allowDecimals={false}
                    domain={[0, 1]}
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  />
                  <Tooltip contentStyle={TOOLTIP_STYLE} cursor={{ fill: 'var(--color-secondary)' }} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="completed" name="Completed" fill="var(--color-success)" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="missed" name="Missed" fill="var(--color-destructive)" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>
      </div>

      <SectionCard title="Last 30 days" description="One cell per day, oldest first.">
        {logs.isError ? (
          <ErrorPanel message={logs.error.message} onRetry={() => void logs.refetch()} />
        ) : logs.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : (
          <div
            className="grid gap-1.5"
            style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(2.25rem, 1fr))' }}
          >
            {gridDays.map((iso) => {
              const log = logByDate.get(iso)
              const state = log?.completed ? 'done' : log ? 'missed' : 'empty'
              return (
                <div
                  key={iso}
                  title={`${formatDate(iso)}${log?.note ? ` — ${log.note}` : ''}`}
                  className={cn(
                    'flex h-9 flex-col items-center justify-center rounded-md border text-[10px]',
                    state === 'done' && 'border-success/40 bg-success/15 text-success',
                    state === 'missed' && 'border-destructive/40 bg-destructive/10 text-destructive',
                    state === 'empty' && 'border-border text-muted-foreground/50',
                  )}
                >
                  <span>{iso.slice(8)}</span>
                  <span className="uppercase">{weekdayShort(iso).slice(0, 1)}</span>
                </div>
              )
            })}
          </div>
        )}
      </SectionCard>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium tabular-nums">{value}</dd>
    </div>
  )
}

function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="space-y-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4">
      <p className="text-sm text-destructive">{message}</p>
      <Button size="sm" variant="outline" onClick={onRetry}>
        Try again
      </Button>
    </div>
  )
}

export default function HabitsPage() {
  const queryClient = useQueryClient()
  const [includeArchived, setIncludeArchived] = useState(false)
  const [selectedId, setSelectedId] = useState<string>()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<HabitResponse | null>(null)
  const [pendingDelete, setPendingDelete] = useState<HabitResponse | null>(null)

  const habits = useQuery({
    queryKey: ['habits', 'list', includeArchived],
    queryFn: () => habitApi.list(includeArchived),
  })

  const invalidateHabits = () => queryClient.invalidateQueries({ queryKey: ['habits'] })

  const saveMutation = useMutation({
    mutationFn: async (values: HabitFormValues) => {
      const body = toRequest(values)
      return editing
        ? habitApi.update(editing.id, body)
        : habitApi.create({ ...body, archived: undefined })
    },
    onSuccess: (habit) => {
      toast.success(editing ? 'Habit updated' : 'Habit created')
      setFormOpen(false)
      setEditing(null)
      if (habit?.archived) setIncludeArchived(true)
      void invalidateHabits()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const toggleMutation = useMutation<
    HabitLogResponse,
    unknown,
    { id: string; logDate: string },
    { previous: [QueryKey, HabitResponse[] | undefined][] }
  >({
    mutationFn: ({ id, logDate }) => habitApi.toggle(id, { logDate }),
    onMutate: async ({ id, logDate }) => {
      await queryClient.cancelQueries({ queryKey: ['habits', 'list'] })
      const previous = queryClient.getQueriesData<HabitResponse[]>({ queryKey: ['habits', 'list'] })
      queryClient.setQueriesData<HabitResponse[]>({ queryKey: ['habits', 'list'] }, (current) =>
        (current ?? []).map((habit) =>
          habit.id !== id
            ? habit
            : {
                ...habit,
                completedToday:
                  logDate === todayIso() ? !habit.completedToday : habit.completedToday,
                lastCompletedDate: logDate,
              },
        ),
      )
      return { previous }
    },
    onError: (error, _variables, context) => {
      context?.previous.forEach(([key, data]) => queryClient.setQueryData(key, data))
      toast.error(toNormalisedError(error).message)
    },
    onSettled: () => {
      void invalidateHabits()
    },
  })

  const logMutation = useMutation({
    mutationFn: ({ id, logDate, completed }: { id: string; logDate: string; completed: boolean }) =>
      habitApi.log(id, { logDate, completed }),
    onSuccess: () => {
      toast.success('Log saved')
      void invalidateHabits()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeMutation = useMutation({
    mutationFn: (id: string) => habitApi.remove(id),
    onSuccess: () => {
      toast.success('Habit deleted')
      setPendingDelete(null)
      setSelectedId((current) => (current === pendingDelete?.id ? undefined : current))
      void invalidateHabits()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const list = habits.data ?? []
  const activeCount = list.filter((habit) => !habit.archived).length
  const completedToday = list.filter((habit) => habit.completedToday).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Habits"
        description="Track daily and weekly routines, keep the streak honest, and log a past day when you forget."
        actions={
          <>
            <div className="flex items-center gap-2 rounded-md border px-3 py-1.5">
              <Label htmlFor="include-archived" className="text-xs">
                Include archived
              </Label>
              <Switch
                id="include-archived"
                checked={includeArchived}
                onCheckedChange={(checked) => setIncludeArchived(checked)}
              />
            </div>
            <Button
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              <Plus /> New habit
            </Button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Habits" value={activeCount} hint={includeArchived ? `${list.length} shown` : 'Active only'} />
        <StatCard label="Done today" value={completedToday} hint={`of ${activeCount} active`} />
        <StatCard
          label="Average consistency"
          value={formatPercent(
            list.length === 0
              ? 0
              : list.reduce((total, habit) => total + (habit.consistencyRate30d ?? 0), 0) / list.length,
          )}
          hint="Last 30 days"
        />
        <StatCard
          label="Longest active streak"
          value={list.reduce((max, habit) => Math.max(max, habit.currentStreak ?? 0), 0)}
          hint="Days"
        />
      </div>

      {habits.isError ? (
        <ErrorPanel message={habits.error.message} onRetry={() => void habits.refetch()} />
      ) : habits.isPending ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-52 w-full" />
          ))}
        </div>
      ) : list.length === 0 ? (
        <EmptyState
          icon={<Repeat />}
          title={includeArchived ? 'No habits at all' : 'No habits yet'}
          description={
            includeArchived
              ? 'There are no habits in your account, archived or otherwise.'
              : 'Create your first habit and tick it off each day to start a streak.'
          }
          action={
            <Button
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              <Plus /> Create a habit
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.map((habit) => {
            const toggleId = `habit-toggle-${habit.id}`
            const isSelected = selectedId === habit.id
            return (
              <article
                key={habit.id}
                className={cn(
                  'flex flex-col gap-3 rounded-xl border bg-card p-4',
                  isSelected && 'border-primary/60',
                  habit.archived && 'opacity-70',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 space-y-1">
                    <h2 className="truncate font-medium">{habit.name}</h2>
                    <div className="flex flex-wrap items-center gap-1.5">
                      <StatusBadge value={habit.frequencyType} label={habit.frequencyType.toLowerCase()} />
                      {habit.category ? (
                        <span className="text-xs text-muted-foreground">{habit.category}</span>
                      ) : null}
                      {habit.archived ? <StatusBadge value="ARCHIVED" label="archived" /> : null}
                    </div>
                  </div>
                  <label
                    htmlFor={toggleId}
                    className="flex shrink-0 cursor-pointer items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs"
                  >
                    <input
                      id={toggleId}
                      type="checkbox"
                      className="size-4 accent-[var(--color-primary)]"
                      checked={habit.completedToday}
                      disabled={toggleMutation.isPending}
                      onChange={() =>
                        toggleMutation.mutate({ id: habit.id, logDate: todayIso() })
                      }
                    />
                    Today
                  </label>
                </div>

                {habit.description ? (
                  <p className="line-clamp-2 text-sm text-muted-foreground">{habit.description}</p>
                ) : null}

                <div className="grid grid-cols-3 gap-2 text-center text-xs">
                  <div className="rounded-md border p-2">
                    <p className="flex items-center justify-center gap-1 font-medium tabular-nums">
                      <Flame className="size-3.5 text-warning" aria-hidden />
                      {habit.currentStreak}
                    </p>
                    <p className="text-muted-foreground">Streak</p>
                  </div>
                  <div className="rounded-md border p-2">
                    <p className="font-medium tabular-nums">{habit.longestStreak}</p>
                    <p className="text-muted-foreground">Best</p>
                  </div>
                  <div className="rounded-md border p-2">
                    <p className="font-medium tabular-nums">{formatPercent(habit.consistencyRate30d)}</p>
                    <p className="text-muted-foreground">30d rate</p>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-1">
                      <CalendarDays className="size-3.5" aria-hidden />
                      {habit.frequencyType === 'WEEKLY'
                        ? `${habit.timesPerPeriod}× per week`
                        : 'Every day'}
                    </span>
                    {habit.reminderTime ? <span>{habit.reminderTime.slice(0, 5)}</span> : null}
                  </div>
                  <WeekdayChips
                    targetDays={habit.targetDays ?? []}
                    muted={habit.frequencyType === 'DAILY'}
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>
                      {habit.completedLast30Days} of {habit.scheduledLast30Days} scheduled days
                    </span>
                  </div>
                  <Progress
                    value={
                      habit.scheduledLast30Days === 0
                        ? 0
                        : (habit.completedLast30Days / habit.scheduledLast30Days) * 100
                    }
                    indicatorClassName={
                      habit.consistencyRate30d >= 70
                        ? 'bg-success'
                        : habit.consistencyRate30d >= 40
                          ? 'bg-warning'
                          : 'bg-destructive'
                    }
                  />
                </div>

                <div className="mt-auto flex flex-wrap items-center gap-2 border-t pt-3">
                  <Button
                    size="sm"
                    variant={isSelected ? 'default' : 'outline'}
                    onClick={() => setSelectedId(isSelected ? undefined : habit.id)}
                  >
                    {isSelected ? 'Hide details' : 'Details'}
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      setEditing(habit)
                      setFormOpen(true)
                    }}
                  >
                    <Pencil /> Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() =>
                      logMutation.mutate({ id: habit.id, logDate: addDaysIso(todayIso(), -1), completed: true })
                    }
                    disabled={logMutation.isPending}
                  >
                    <Archive /> Log yesterday
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    onClick={() => setPendingDelete(habit)}
                  >
                    <Trash2 /> Delete
                  </Button>
                </div>
              </article>
            )
          })}
        </div>
      )}

      {selectedId ? <HabitDetailPanel habitId={selectedId} key={selectedId} /> : null}

      <HabitFormDialog
        open={formOpen}
        habit={editing}
        busy={saveMutation.isPending}
        onOpenChange={(open) => {
          setFormOpen(open)
          if (!open) setEditing(null)
        }}
        onSubmit={(values) => saveMutation.mutate(values)}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        title={`Delete ${pendingDelete?.name ?? 'habit'}?`}
        description="This removes the habit and its entire log history. Archive it instead if you only want to hide it."
        confirmLabel="Delete permanently"
        busy={removeMutation.isPending}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null)
        }}
        onConfirm={() => {
          if (pendingDelete) removeMutation.mutate(pendingDelete.id)
        }}
      />
    </div>
  )
}