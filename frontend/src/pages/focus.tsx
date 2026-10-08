import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  BarChart3,
  Clock,
  Flame,
  Pause,
  Play,
  RefreshCw,
  Square,
  Timer,
  Trash2,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import { focusApi, goalApi, taskApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
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
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import {
  addDaysIso,
  formatDateTime,
  formatMinutes,
  formatNumber,
  formatPercent,
  formatTime,
  titleCase,
  todayIso,
} from '@/lib/format'
import type { FocusMode, FocusSessionRequest } from '@/types/api'

const FOCUS_MODES: readonly FocusMode[] = [
  'POMODORO_25_5',
  'POMODORO_50_10',
  'DEEP_WORK',
  'CUSTOM',
]

const MODE_PRESETS: Record<FocusMode, number> = {
  POMODORO_25_5: 25,
  POMODORO_50_10: 50,
  DEEP_WORK: 90,
  CUSTOM: 30,
}

const HISTORY_WINDOWS = [
  { label: 'Last 7 days', days: 7 },
  { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 },
]

const NONE = '__none__'
const TICK_MS = 1000

interface ActiveSession {
  id: string
  startedAtMs: number
  plannedMinutes: number
  mode: FocusMode
  taskId: string
  taskTitle: string
  goalId: string
  interruptedCount: number
}

function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  const minutesPart = Math.floor(seconds / 60)
  const secondsPart = seconds % 60
  return `${String(minutesPart).padStart(2, '0')}:${String(secondsPart).padStart(2, '0')}`
}

export default function FocusPage() {
  const queryClient = useQueryClient()

  const [mode, setMode] = useState<FocusMode>('POMODORO_25_5')
  const [plannedMinutes, setPlannedMinutes] = useState(MODE_PRESETS.POMODORO_25_5)
  const [taskId, setTaskId] = useState(NONE)
  const [goalId, setGoalId] = useState(NONE)

  const [active, setActive] = useState<ActiveSession | null>(null)
  const [paused, setPaused] = useState(false)
  const [frozenElapsedMs, setFrozenElapsedMs] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const [stopOpen, setStopOpen] = useState(false)
  const [abandonOpen, setAbandonOpen] = useState(false)
  const [actualMinutes, setActualMinutes] = useState('')
  const [completed, setCompleted] = useState(true)
  const [interruptedCount, setInterruptedCount] = useState('0')
  const [outcome, setOutcome] = useState('')
  const [notes, setNotes] = useState('')
  const [rating, setRating] = useState('4')

  const [historyDays, setHistoryDays] = useState(30)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)

  useEffect(() => {
    if (!active || paused) return undefined
    const timer = window.setInterval(() => setNow(Date.now()), TICK_MS)
    return () => window.clearInterval(timer)
  }, [active, paused])

  const statsQuery = useQuery({
    queryKey: ['focus', 'statistics', 30],
    queryFn: () => focusApi.statistics(30),
  })

  const sessionsQuery = useQuery({
    queryKey: ['focus', 'sessions', historyDays],
    queryFn: () =>
      focusApi.sessions({
        from: addDaysIso(todayIso(), -historyDays),
        to: todayIso(),
        limit: 100,
      }),
  })

  const taskQuery = useQuery({
    queryKey: ['tasks', 'focus-candidates'],
    queryFn: () => taskApi.list({ status: ['TODO', 'IN_PROGRESS'], size: 50, sort: 'deadline' }),
  })

  const goalQuery = useQuery({
    queryKey: ['goals', 'active'],
    queryFn: () => goalApi.active(),
  })

  const detailQuery = useQuery({
    queryKey: ['focus', 'session', detailId],
    queryFn: () => focusApi.get(detailId ?? ''),
    enabled: Boolean(detailId),
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['focus'] }),
      queryClient.invalidateQueries({ queryKey: ['tasks'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  }

  const startMutation = useMutation({
    mutationFn: (body: FocusSessionRequest) => focusApi.start(body),
    onSuccess: async (session) => {
      const parsed = Date.parse(session.startedAt ?? '')
      const startedAtMs = Number.isNaN(parsed) ? Date.now() : parsed
      setActive({
        id: session.id,
        startedAtMs,
        plannedMinutes: session.plannedMinutes > 0 ? session.plannedMinutes : plannedMinutes,
        mode: session.mode ?? mode,
        taskId: session.taskId ?? '',
        taskTitle: session.taskTitle ?? '',
        goalId: session.goalId ?? '',
        interruptedCount: 0,
      })
      setFrozenElapsedMs(null)
      setPaused(false)
      setNow(Date.now())
      await invalidate()
      toast.success('Focus session started')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const completeMutation = useMutation({
    mutationFn: ({
      sessionId,
      body,
    }: {
      sessionId: string
      body: {
        actualMinutes?: number
        completed: boolean
        interruptedCount: number
        outcome?: string
        notes?: string
        rating?: number
      }
    }) => focusApi.complete(sessionId, body),
    onSuccess: async () => {
      setActive(null)
      setPaused(false)
      setFrozenElapsedMs(null)
      setStopOpen(false)
      await invalidate()
      toast.success('Session recorded')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeMutation = useMutation({
    mutationFn: (id: string) => focusApi.remove(id),
    onSuccess: async (_result, id) => {
      if (active?.id === id) {
        setActive(null)
        setPaused(false)
        setFrozenElapsedMs(null)
      }
      setDeletingId(null)
      await invalidate()
      toast.success('Session discarded')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const elapsedMs = useMemo(() => {
    if (!active) return 0
    if (paused && frozenElapsedMs !== null) return frozenElapsedMs
    return Math.max(0, now - active.startedAtMs)
  }, [active, paused, frozenElapsedMs, now])

  const targetMs = active ? active.plannedMinutes * 60_000 : 0
  const remainingMs = Math.max(0, targetMs - elapsedMs)
  const finished = Boolean(active) && targetMs > 0 && remainingMs === 0
  const progressPercent = targetMs > 0 ? Math.min(100, (elapsedMs / targetMs) * 100) : 0

  const openStop = () => {
    setActualMinutes(String(Math.max(1, Math.round(elapsedMs / 60_000))))
    setCompleted(remainingMs === 0)
    setInterruptedCount(String(active?.interruptedCount ?? 0))
    setOutcome('')
    setNotes('')
    setRating('4')
    setStopOpen(true)
  }

  const selectMode = (next: FocusMode) => {
    setMode(next)
    setPlannedMinutes(MODE_PRESETS[next])
  }

  const stats = statsQuery.data
  const chartData = useMemo(
    () =>
      (stats?.daily ?? []).map((point) => ({
        label: point.date.slice(5),
        minutes: point.minutes,
      })),
    [stats?.daily],
  )

  const sessions = sessionsQuery.data ?? []
  const taskOptions = taskQuery.data?.content ?? []
  const goalOptions = goalQuery.data ?? []
  const detail = detailQuery.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Focus"
        description="Run a real timer, record what happened, and watch the trend build up."
        actions={
          <Button variant="outline" onClick={() => void statsQuery.refetch()}>
            <RefreshCw /> Refresh stats
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-5">
        <SectionCard
          title="Timer"
          description="The countdown is wall-clock time measured from the start instant the server recorded."
          className="lg:col-span-3"
        >
          {active ? (
            <div className="space-y-5">
              <div className="text-center">
                <p className="text-5xl font-semibold tabular-nums">
                  {formatCountdown(remainingMs / 1000)}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {paused
                    ? 'Paused'
                    : finished
                      ? 'Planned time reached — stop to record the session'
                      : `${formatMinutes(Math.round(elapsedMs / 60_000))} of ${formatMinutes(active.plannedMinutes)} elapsed`}
                </p>
              </div>

              <Progress
                value={progressPercent}
                aria-label="Session progress"
                indicatorClassName={finished ? 'bg-success' : undefined}
              />

              <div className="flex flex-wrap items-center justify-center gap-2">
                {paused ? (
                  <Button
                    onClick={() => {
                      if (frozenElapsedMs === null) return
                      const frozen = frozenElapsedMs
                      setActive((current) =>
                        current ? { ...current, startedAtMs: Date.now() - frozen } : current,
                      )
                      setFrozenElapsedMs(null)
                      setPaused(false)
                      setNow(Date.now())
                    }}
                  >
                    <Play /> Resume
                  </Button>
                ) : (
                  <Button
                    variant="outline"
                    onClick={() => {
                      setFrozenElapsedMs(Date.now() - active.startedAtMs)
                      setPaused(true)
                    }}
                  >
                    <Pause /> Pause
                  </Button>
                )}
                <Button onClick={openStop}>
                  <Square /> Stop and record
                </Button>
                <Button variant="ghost" onClick={() => setAbandonOpen(true)}>
                  <X /> Abandon
                </Button>
              </div>

              <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
                <div>
                  <dt className="text-muted-foreground">Mode</dt>
                  <dd className="font-medium">{titleCase(active.mode)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Started</dt>
                  <dd className="font-medium">
                    {formatTime(new Date(active.startedAtMs).toISOString())}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Ends</dt>
                  <dd className="font-medium">
                    {formatTime(new Date(active.startedAtMs + targetMs).toISOString())}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Interruptions</dt>
                  <dd className="font-medium tabular-nums">{active.interruptedCount}</dd>
                </div>
                <div className="col-span-2 sm:col-span-4">
                  <dt className="text-muted-foreground">Focusing on</dt>
                  <dd className="font-medium">
                    {active.taskId ? (
                      <Link to={`/app/tasks/${active.taskId}`} className="text-primary hover:underline">
                        {active.taskTitle || active.taskId}
                      </Link>
                    ) : active.goalId ? (
                      <Link
                        to={`/app/goals?goalId=${encodeURIComponent(active.goalId)}`}
                        className="text-primary hover:underline"
                      >
                        {goalOptions.find((goal) => goal.id === active.goalId)?.title ?? active.goalId}
                      </Link>
                    ) : (
                      'Unassigned focus block'
                    )}
                  </dd>
                </div>
              </dl>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="focus-mode">Mode</Label>
                  <Select value={mode} onValueChange={(value) => selectMode(value as FocusMode)}>
                    <SelectTrigger id="focus-mode">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FOCUS_MODES.map((option) => (
                        <SelectItem key={option} value={option}>
                          {titleCase(option)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="focus-planned">Planned minutes</Label>
                  <Input
                    id="focus-planned"
                    type="number"
                    min={1}
                    max={600}
                    value={plannedMinutes}
                    onChange={(event) =>
                      setPlannedMinutes(Math.min(600, Math.max(1, Number(event.target.value) || 1)))
                    }
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="focus-task">Task</Label>
                  <Select value={taskId} onValueChange={setTaskId}>
                    <SelectTrigger id="focus-task">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>No task</SelectItem>
                      {taskOptions.map((task) => (
                        <SelectItem key={task.id} value={task.id}>
                          {task.title}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="focus-goal">Goal</Label>
                  <Select
                    value={goalId}
                    onValueChange={(value) => {
                      setGoalId(value)
                      if (value !== NONE) setTaskId(NONE)
                    }}
                  >
                    <SelectTrigger id="focus-goal">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE}>No goal</SelectItem>
                      {goalOptions.map((goal) => (
                        <SelectItem key={goal.id} value={goal.id}>
                          {goal.title}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <Button
                className="w-full"
                loading={startMutation.isPending}
                onClick={() =>
                  startMutation.mutate({
                    mode,
                    plannedMinutes,
                    taskId: taskId === NONE ? undefined : taskId,
                    goalId: goalId === NONE ? undefined : goalId,
                    startedAt: new Date().toISOString(),
                  })
                }
              >
                <Timer /> Start focus session
              </Button>
              <p className="text-xs text-muted-foreground">
                The session is created on the server first. Pausing and resuming only affect this countdown,
                never the stored record.
              </p>
            </div>
          )}
        </SectionCard>

        <div className="grid content-start gap-3 lg:col-span-2">
          <div className="grid gap-3 sm:grid-cols-2">
            <StatCard
              label="Total sessions"
              value={statsQuery.isPending ? '—' : formatNumber(stats?.totalSessions ?? 0)}
              hint="Last 30 days"
              icon={<BarChart3 />}
            />
            <StatCard
              label="Completed"
              value={statsQuery.isPending ? '—' : formatNumber(stats?.completedSessions ?? 0)}
              hint={
                statsQuery.isPending ? undefined : `${formatPercent(stats?.completionRate ?? 0, 1)} rate`
              }
              icon={<Flame />}
            />
            <StatCard
              label="Focus time"
              value={statsQuery.isPending ? '—' : formatMinutes(stats?.totalMinutes ?? 0)}
              hint={
                statsQuery.isPending
                  ? undefined
                  : `${formatMinutes(stats?.averageMinutes ?? 0)} average per session`
              }
              icon={<Clock />}
            />
            <StatCard
              label="Best streak"
              value={statsQuery.isPending ? '—' : formatNumber(stats?.bestStreak ?? 0)}
              hint={`${formatMinutes(stats?.todayMinutes ?? 0)} today`}
              icon={<Flame />}
            />
          </div>

          <StatCard
            label="This week"
            value={
              statsQuery.isPending
                ? '—'
                : `${formatMinutes(stats?.thisWeekMinutes ?? 0)} · ${formatNumber(
                    stats?.thisWeekSessions ?? 0,
                  )} sessions`
            }
            hint="Monday to today, completed focus time only"
            icon={<Timer />}
          />
        </div>
      </div>

      <SectionCard
        title="Daily focus minutes"
        description="Completed minutes per day over the last 30 days."
        action={statsQuery.isFetching ? <Spinner label="Refreshing chart" /> : undefined}
      >
        {statsQuery.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : statsQuery.error ? (
          <div role="alert" className="space-y-2 text-sm text-destructive">
            <p>{toNormalisedError(statsQuery.error).message}</p>
            <Button variant="outline" size="sm" onClick={() => void statsQuery.refetch()}>
              <RefreshCw /> Try again
            </Button>
          </div>
        ) : chartData.every((point) => point.minutes === 0) ? (
          <EmptyState
            icon={<BarChart3 />}
            title="No focus time recorded yet"
            description="Run your first session and this chart fills in day by day."
          />
        ) : (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-border" />
                <XAxis
                  dataKey="label"
                  tick={{ fontSize: 10 }}
                  stroke="currentColor"
                  className="text-muted-foreground"
                />
                <YAxis
                  tick={{ fontSize: 10 }}
                  stroke="currentColor"
                  className="text-muted-foreground"
                />
                <Tooltip
                  cursor={{ fill: 'rgba(148, 163, 184, 0.12)' }}
                  contentStyle={{
                    background: 'hsl(var(--color-popover))',
                    border: '1px solid hsl(var(--color-border))',
                    borderRadius: 8,
                    color: 'hsl(var(--color-popover-foreground))',
                    fontSize: 12,
                  }}
                />
                <Bar
                  dataKey="minutes"
                  name="Minutes"
                  fill="hsl(var(--color-primary))"
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </SectionCard>

      <SectionCard
        title="Session history"
        description="Every recorded focus session in the selected window."
        action={
          <div className="w-44">
            <Label htmlFor="history-window" className="sr-only">
              History window
            </Label>
            <Select
              value={String(historyDays)}
              onValueChange={(value) => setHistoryDays(Number(value))}
            >
              <SelectTrigger id="history-window">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {HISTORY_WINDOWS.map((option) => (
                  <SelectItem key={option.days} value={String(option.days)}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        }
      >
        {sessionsQuery.isPending ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, index) => (
              <Skeleton key={index} className="h-10 w-full" />
            ))}
          </div>
        ) : sessionsQuery.error ? (
          <div role="alert" className="space-y-2 text-sm text-destructive">
            <p>{toNormalisedError(sessionsQuery.error).message}</p>
            <Button variant="outline" size="sm" onClick={() => void sessionsQuery.refetch()}>
              <RefreshCw /> Try again
            </Button>
          </div>
        ) : sessions.length === 0 ? (
          <EmptyState
            icon={<Timer />}
            title="No sessions in this window"
            description="Widen the window, or run a focus block and it will appear here."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[46rem] text-sm">
              <caption className="sr-only">Focus session history</caption>
              <thead>
                <tr className="border-b text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Started
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Mode
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Task
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Planned
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Actual
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Result
                  </th>
                  <th scope="col" className="py-2 pr-3 font-medium">
                    Rating
                  </th>
                  <th scope="col" className="py-2 font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {sessions.map((session) => (
                  <tr key={session.id}>
                    <td className="whitespace-nowrap py-2 pr-3">
                      {formatDateTime(session.startedAt)}
                    </td>
                    <td className="py-2 pr-3">
                      <StatusBadge value={session.mode} label={titleCase(session.mode)} />
                    </td>
                    <td className="py-2 pr-3">
                      {session.taskId ? (
                        <Link
                          to={`/app/tasks/${session.taskId}`}
                          className="block max-w-56 truncate hover:text-primary hover:underline"
                        >
                          {session.taskTitle || session.taskId}
                        </Link>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </td>
                    <td className="py-2 pr-3 tabular-nums">{formatMinutes(session.plannedMinutes)}</td>
                    <td className="py-2 pr-3 tabular-nums">{formatMinutes(session.actualMinutes)}</td>
                    <td className="py-2 pr-3">
                      <span className="flex items-center gap-1.5">
                        <StatusBadge
                          value={session.completed ? 'COMPLETED' : 'CANCELLED'}
                          label={session.completed ? 'Completed' : 'Stopped early'}
                        />
                        {session.interruptedCount > 0 ? (
                          <Badge variant="outline">{session.interruptedCount} breaks</Badge>
                        ) : null}
                      </span>
                    </td>
                    <td className="py-2 pr-3 tabular-nums">
                      {session.rating ? `${session.rating}/5` : <span className="text-muted-foreground">—</span>}
                    </td>
                    <td className="py-2">
                      <span className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDetailId(session.id)}
                        >
                          Details
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={`Delete focus session from ${formatDateTime(session.startedAt)}`}
                          onClick={() => setDeletingId(session.id)}
                        >
                          <Trash2 />
                        </Button>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      <Dialog open={stopOpen} onOpenChange={setStopOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Record this session</DialogTitle>
            <DialogDescription>
              The elapsed time has been measured on this device. Tell the server what actually happened.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="stop-minutes">Actual minutes</Label>
                <Input
                  id="stop-minutes"
                  type="number"
                  min={0}
                  max={600}
                  value={actualMinutes}
                  onChange={(event) => setActualMinutes(event.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="stop-interrupted">Interruptions</Label>
                <Input
                  id="stop-interrupted"
                  type="number"
                  min={0}
                  max={100}
                  value={interruptedCount}
                  onChange={(event) => setInterruptedCount(event.target.value)}
                />
              </div>
            </div>

            <div className="flex items-center gap-3 rounded-md border p-3">
              <Switch id="stop-completed" checked={completed} onCheckedChange={setCompleted} />
              <Label htmlFor="stop-completed">Reached the planned outcome</Label>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="stop-outcome">Outcome</Label>
              <Input
                id="stop-outcome"
                maxLength={500}
                placeholder="e.g. Drafted the migration plan"
                value={outcome}
                onChange={(event) => setOutcome(event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="stop-notes">Notes</Label>
              <Textarea
                id="stop-notes"
                rows={3}
                maxLength={20000}
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="stop-rating">Rating</Label>
              <Select value={rating} onValueChange={setRating}>
                <SelectTrigger id="stop-rating">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {[1, 2, 3, 4, 5].map((value) => (
                    <SelectItem key={value} value={String(value)}>
                      {value} / 5
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setStopOpen(false)}>
              Keep focusing
            </Button>
            <Button
              loading={completeMutation.isPending}
              onClick={() => {
                if (!active) return
                const parsedMinutes = Number(actualMinutes)
                completeMutation.mutate({
                  sessionId: active.id,
                  body: {
                    actualMinutes:
                      Number.isFinite(parsedMinutes) && parsedMinutes >= 0
                        ? Math.min(600, Math.round(parsedMinutes))
                        : Math.max(1, Math.round(elapsedMs / 60_000)),
                    completed,
                    interruptedCount: Math.max(
                      0,
                      Number.isFinite(Number(interruptedCount))
                        ? Math.min(100, Math.round(Number(interruptedCount)))
                        : 0,
                    ),
                    outcome: outcome.trim() || undefined,
                    notes: notes.trim() || undefined,
                    rating: Math.min(5, Math.max(1, Number(rating) || 4)),
                  },
                })
              }}
            >
              Save session
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={abandonOpen} onOpenChange={setAbandonOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Abandon this session</DialogTitle>
            <DialogDescription>
              The open session is deleted and its minutes are not counted. Nothing is recorded for this
              block.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAbandonOpen(false)}>
              Keep the session
            </Button>
            <Button
              variant="destructive"
              loading={removeMutation.isPending}
              onClick={() => {
                if (active) {
                  setAbandonOpen(false)
                  removeMutation.mutate(active.id)
                }
              }}
            >
              Discard session
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deletingId !== null} onOpenChange={(open) => !open && setDeletingId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete focus session</DialogTitle>
            <DialogDescription>
              The session and its notes are removed, and the statistics are recalculated.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingId(null)}>
              Keep session
            </Button>
            <Button
              variant="destructive"
              loading={removeMutation.isPending}
              onClick={() => {
                if (deletingId) removeMutation.mutate(deletingId)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={detailId !== null} onOpenChange={(open) => !open && setDetailId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Session details</DialogTitle>
            <DialogDescription>
              The full record as stored by the server.
            </DialogDescription>
          </DialogHeader>

          {detailQuery.isPending ? (
            <Spinner label="Loading session" />
          ) : detailQuery.error || !detail ? (
            <div role="alert" className="space-y-2 text-sm text-destructive">
              <p>{toNormalisedError(detailQuery.error).message}</p>
              <Button variant="outline" size="sm" onClick={() => void detailQuery.refetch()}>
                <RefreshCw /> Try again
              </Button>
            </div>
          ) : (
            <div className="space-y-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge value={detail.mode} label={titleCase(detail.mode)} />
                <StatusBadge
                  value={detail.completed ? 'COMPLETED' : 'CANCELLED'}
                  label={detail.completed ? 'Completed' : 'Stopped early'}
                />
                {detail.rating ? <Badge variant="outline">{detail.rating}/5</Badge> : null}
              </div>
              <dl className="divide-y">
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Started</dt>
                  <dd className="font-medium">{formatDateTime(detail.startedAt)}</dd>
                </div>
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Ended</dt>
                  <dd className="font-medium">{formatDateTime(detail.endedAt)}</dd>
                </div>
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Planned vs actual</dt>
                  <dd className="font-medium tabular-nums">
                    {formatMinutes(detail.plannedMinutes)} / {formatMinutes(detail.actualMinutes)}
                  </dd>
                </div>
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Interruptions</dt>
                  <dd className="font-medium tabular-nums">{detail.interruptedCount}</dd>
                </div>
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Task</dt>
                  <dd className="min-w-0 truncate font-medium">
                    {detail.taskId ? (
                      <Link
                        to={`/app/tasks/${detail.taskId}`}
                        className="text-primary hover:underline"
                      >
                        {detail.taskTitle || detail.taskId}
                      </Link>
                    ) : (
                      '—'
                    )}
                  </dd>
                </div>
                <div className="flex justify-between gap-3 py-1.5">
                  <dt className="text-muted-foreground">Goal</dt>
                  <dd className="min-w-0 truncate font-medium">
                    {detail.goalId ? (
                      <Link
                        to={`/app/goals?goalId=${encodeURIComponent(detail.goalId)}`}
                        className="text-primary hover:underline"
                      >
                        {detail.goalId}
                      </Link>
                    ) : (
                      '—'
                    )}
                  </dd>
                </div>
              </dl>
              <div className="space-y-1">
                <p className="text-muted-foreground">Outcome</p>
                <p className="font-medium">{detail.outcome || 'Not recorded'}</p>
              </div>
              <div className="space-y-1">
                <p className="text-muted-foreground">Notes</p>
                <p className="whitespace-pre-wrap">{detail.notes || 'Not recorded'}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}