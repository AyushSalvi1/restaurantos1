import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  CircleSlash,
  Clock,
  RefreshCw,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { dashboardApi, habitApi, taskApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { LoadingPanel } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import {
  formatDate,
  formatDateTime,
  formatMinutes,
  formatNumber,
  formatPercent,
  formatTime,
  titleCase,
  todayIso,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BalanceScore, HabitMini, TaskResponse, TaskStatus, TodayResponse } from '@/types/api'

const TODAY_KEY = ['dashboard', 'today'] as const

const TASK_STATUSES: { value: TaskStatus; label: string }[] = [
  { value: 'TODO', label: 'To do' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

/** Server-supplied links only navigate when they already point inside the app shell. */
function appRoute(path: string | null | undefined): string | null {
  return path && path.startsWith('/app') ? path : null
}

function SummaryStrip({ summary }: { summary: TodayResponse['summary'] }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      <StatCard label="Due today" value={formatNumber(summary.tasksDue)} hint={formatMinutes(summary.estimatedMinutes)} />
      <StatCard label="Completed today" value={formatNumber(summary.tasksCompleted)} tone="text-success" />
      <StatCard
        label="Overdue"
        value={formatNumber(summary.tasksOverdue)}
        tone={summary.tasksOverdue > 0 ? 'text-destructive' : undefined}
      />
      <StatCard label="Habits" value={`${summary.habitsCompleted}/${summary.habitsScheduled}`} />
      <StatCard
        label="Events"
        value={formatNumber(summary.calendarEvents)}
        hint={`${formatNumber(summary.focusMinutesToday)} focus min`}
      />
    </div>
  )
}

function TaskRow({
  task,
  tone,
  busy,
  onComplete,
  onStatus,
}: {
  task: TaskResponse
  tone: string
  busy: boolean
  onComplete: (id: string) => void
  onStatus: (id: string, status: TaskStatus) => void
}) {
  return (
    <li className={cn('flex flex-col gap-2 rounded-lg border bg-background/40 p-3 sm:flex-row sm:items-center sm:justify-between', tone)}>
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <Link
            to={`/app/tasks/${task.id}`}
            className="truncate text-sm font-medium underline-offset-4 hover:underline"
          >
            {task.title}
          </Link>
          <StatusBadge value={task.priority} />
          <StatusBadge value={task.status} />
          {task.category ? <Badge variant="outline">{task.category}</Badge> : null}
        </div>
        <p className="text-xs text-muted-foreground">
          {task.deadline ? `Deadline ${formatDateTime(task.deadline)}` : 'No deadline'}
          {task.estimatedMinutes > 0 ? ` · about ${formatMinutes(task.estimatedMinutes)}` : ''}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Select value={task.status} onValueChange={(value) => onStatus(task.id, value as TaskStatus)}>
          <SelectTrigger className="w-36" disabled={busy} aria-label={`Status for ${task.title}`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {TASK_STATUSES.map((status) => (
              <SelectItem key={status.value} value={status.value}>
                {status.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          size="sm"
          variant="outline"
          loading={busy}
          disabled={task.status === 'COMPLETED'}
          onClick={() => onComplete(task.id)}
        >
          <CheckCircle2 />
          Complete
        </Button>
      </div>
    </li>
  )
}

function TaskList({
  tasks,
  tone,
  emptyTitle,
  emptyDescription,
  busy,
  onComplete,
  onStatus,
}: {
  tasks: TaskResponse[]
  tone: string
  emptyTitle: string
  emptyDescription: string
  busy: boolean
  onComplete: (id: string) => void
  onStatus: (id: string, status: TaskStatus) => void
}) {
  if (tasks.length === 0) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        action={
          <Button asChild size="sm">
            <Link to="/app/tasks">Open tasks</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ul className="space-y-2">
      {tasks.map((task) => (
        <TaskRow
          key={task.id}
          task={task}
          tone={tone}
          busy={busy}
          onComplete={onComplete}
          onStatus={onStatus}
        />
      ))}
    </ul>
  )
}

function FocusList({
  items,
  busy,
  onComplete,
}: {
  items: TodayResponse['todaysFocus']
  busy: boolean
  onComplete: (id: string) => void
}) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="No focus list"
        description="Nothing is due or overdue, so there is nothing to prioritise. Give a task a deadline and it will appear here."
        action={
          <Button asChild size="sm">
            <Link to="/app/tasks">Plan today</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ol className="space-y-2">
      {items.map((item) => (
        <li
          key={item.taskId}
          className="flex flex-col gap-2 rounded-lg border bg-background/40 p-3 sm:flex-row sm:items-start sm:justify-between"
        >
          <div className="min-w-0 space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="secondary" className="tabular-nums">
                #{item.rank}
              </Badge>
              <Link
                to={`/app/tasks/${item.taskId}`}
                className="truncate font-medium underline-offset-4 hover:underline"
              >
                {item.title}
              </Link>
              <StatusBadge value={item.priority} />
            </div>
            <p className="text-xs text-muted-foreground">{item.reason}</p>
            {item.blocked ? (
              <div className="rounded-md border border-warning/40 bg-warning/5 p-2 text-xs text-warning">
                <p className="font-medium">
                  Blocked
                  {item.blockedBy.length > 0 ? ` by ${item.blockedBy.length} task(s)` : ''}
                </p>
                {item.blockedBy.length > 0 ? (
                  <ul className="mt-1 space-y-0.5">
                    {item.blockedBy.map((blockerId) => (
                      <li key={blockerId}>
                        <Link
                          to={`/app/tasks/${blockerId}`}
                          className="underline underline-offset-4 hover:text-foreground"
                        >
                          Open blocking task {blockerId.slice(0, 8)}
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
          </div>
          <Button
            size="sm"
            variant="outline"
            className="shrink-0"
            loading={busy}
            onClick={() => onComplete(item.taskId)}
          >
            <CheckCircle2 />
            Complete
          </Button>
        </li>
      ))}
    </ol>
  )
}

function HabitsPanel({
  habits,
  busy,
  onToggle,
}: {
  habits: HabitMini[]
  busy: boolean
  onToggle: (habit: HabitMini) => void
}) {
  if (habits.length === 0) {
    return (
      <EmptyState
        title="No habits yet"
        description="Create a habit to get a check-in prompt on the day view."
        action={
          <Button asChild size="sm">
            <Link to="/app/habits">Create a habit</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ul className="divide-y">
      {habits.map((habit) => {
        const inputId = `today-habit-${habit.id}`
        return (
          <li key={habit.id} className="flex items-center justify-between gap-3 py-2.5">
            <div className="min-w-0 space-y-0.5">
              <Label htmlFor={inputId} className="cursor-pointer truncate">
                {habit.name}
              </Label>
              <p className="text-xs text-muted-foreground">
                {habit.currentStreak > 0 ? `${habit.currentStreak} day streak` : 'No current streak'}
                {habit.reminderTime ? ` · reminder ${habit.reminderTime.slice(0, 5)}` : ''}
              </p>
            </div>
            <Switch
              id={inputId}
              checked={habit.completedToday}
              disabled={busy}
              onCheckedChange={() => onToggle(habit)}
              aria-label={`Mark ${habit.name} as done today`}
            />
          </li>
        )
      })}
    </ul>
  )
}

function SchedulePanel({ events }: { events: TodayResponse['schedule'] }) {
  const ordered = useMemo(
    () => [...(events ?? [])].sort((a, b) => a.startAt.localeCompare(b.startAt)),
    [events],
  )
  if (ordered.length === 0) {
    return (
      <EmptyState
        title="Nothing scheduled today"
        description="Your calendar has no events left today."
        action={
          <Button asChild size="sm">
            <Link to="/app/calendar">Open calendar</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ol className="space-y-2">
      {ordered.map((event) => (
        <li key={event.id} className="flex items-start gap-3 rounded-lg border bg-background/40 p-3">
          <div className="w-20 shrink-0 text-xs tabular-nums text-muted-foreground">
            {event.allDay ? (
              <span className="uppercase tracking-wide">All day</span>
            ) : (
              <>
                <span className="block text-sm text-foreground">{formatTime(event.startAt)}</span>
                <span className="block">to {formatTime(event.endAt)}</span>
              </>
            )}
          </div>
          <div className="min-w-0 space-y-1">
            <p className="truncate text-sm font-medium">{event.title}</p>
            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
              <StatusBadge value={event.type} />
              {event.location ? <span>{event.location}</span> : null}
            </div>
          </div>
        </li>
      ))}
    </ol>
  )
}

function LifeBalancePanel({ balance }: { balance: BalanceScore }) {
  const dimensions = balance.dimensions ?? []
  return (
    <div className="space-y-3">
      <div className="flex items-end gap-3">
        <p className="text-3xl font-semibold tabular-nums">
          {balance.sufficientData ? balance.overallScore : '—'}
        </p>
        <p className="pb-1 text-sm text-muted-foreground">
          {balance.sufficientData ? balance.grade : 'Not enough data'}
        </p>
      </div>
      <ul className="space-y-2">
        {dimensions.map((dimension) => (
          <li key={dimension.key}>
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className={dimension.sufficientData ? '' : 'text-muted-foreground'}>
                {dimension.label}
              </span>
              {dimension.sufficientData ? (
                <span className="tabular-nums text-muted-foreground">
                  {formatNumber(dimension.score)}
                </span>
              ) : (
                <span className="text-xs uppercase tracking-wide text-muted-foreground">excluded</span>
              )}
            </div>
            {dimension.sufficientData ? (
              <Progress value={dimension.score} indicatorClassName="bg-primary" />
            ) : (
              <div className="h-2 w-full rounded-full bg-secondary/50" aria-hidden />
            )}
          </li>
        ))}
      </ul>
      {balance.disclaimer ? <p className="text-xs text-muted-foreground">{balance.disclaimer}</p> : null}
    </div>
  )
}

function RecommendationsPanel({ items }: { items: TodayResponse['recommendations'] }) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="No recommendations"
        description="Recommendations are produced from your recorded tasks, habits and transactions."
      />
    )
  }
  return (
    <ul className="space-y-2">
      {items.map((item) => {
        const target = appRoute(item.actionPath)
        return (
          <li key={item.id} className="space-y-1.5 rounded-lg border bg-background/40 p-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{titleCase(item.type)}</Badge>
              <span className="text-xs tabular-nums text-muted-foreground">
                Score {formatPercent(item.score)}
              </span>
            </div>
            <p className="text-sm font-medium">{item.title}</p>
            <p className="text-sm text-muted-foreground">{item.rationale}</p>
            {item.supportingData?.length ? (
              <ul className="list-disc space-y-0.5 pl-5 text-xs text-muted-foreground">
                {item.supportingData.map((datum) => (
                  <li key={datum}>{datum}</li>
                ))}
              </ul>
            ) : null}
            {item.actionLabel ? (
              target ? (
                <Button asChild size="sm" variant="outline" className="mt-1">
                  <Link to={target}>{item.actionLabel}</Link>
                </Button>
              ) : (
                <p className="pt-1 text-xs text-muted-foreground">
                  Suggested next step: {item.actionLabel}
                </p>
              )
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}

export default function TodayPage() {
  const queryClient = useQueryClient()
  const [refreshing, setRefreshing] = useState(false)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: TODAY_KEY,
    queryFn: () => dashboardApi.today(),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    void queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }

  const completeTask = useMutation({
    mutationFn: (taskId: string) => taskApi.complete(taskId, {}),
    onSuccess: () => {
      invalidate()
      toast.success('Task completed')
    },
    onError: (mutationError) => toast.error(toNormalisedError(mutationError).message),
  })

  const changeStatus = useMutation({
    mutationFn: (input: { taskId: string; status: TaskStatus }) =>
      taskApi.updateStatus(input.taskId, { status: input.status }),
    onSuccess: (_result, input) => {
      invalidate()
      toast.success(`Task marked ${titleCase(input.status).toLowerCase()}`)
    },
    onError: (mutationError) => toast.error(toNormalisedError(mutationError).message),
  })

  const toggleHabit = useMutation({
    mutationFn: (habitId: string) => habitApi.toggle(habitId, { logDate: todayIso() }),
    onMutate: async (habitId) => {
      await queryClient.cancelQueries({ queryKey: TODAY_KEY })
      const previous = queryClient.getQueryData<TodayResponse>(TODAY_KEY)
      queryClient.setQueryData<TodayResponse>(TODAY_KEY, (current) => {
        const target = current?.habits.find((habit) => habit.id === habitId)
        if (!current || !target) return current
        const next = !target.completedToday
        return {
          ...current,
          habits: current.habits.map((habit) =>
            habit.id === habitId
              ? {
                  ...habit,
                  completedToday: next,
                  currentStreak: Math.max(0, habit.currentStreak + (next ? 1 : -1)),
                }
              : habit,
          ),
          summary: {
            ...current.summary,
            habitsCompleted: Math.max(0, current.summary.habitsCompleted + (next ? 1 : -1)),
          },
        }
      })
      return { previous }
    },
    onError: (mutationError, _habitId, context) => {
      if (context?.previous) queryClient.setQueryData(TODAY_KEY, context.previous)
      toast.error(toNormalisedError(mutationError).message)
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['habits'] })
    },
  })

  const refresh = async () => {
    setRefreshing(true)
    try {
      const result = await refetch()
      if (result.isError) toast.error(toNormalisedError(result.error).message)
      else toast.success('Today refreshed')
    } finally {
      setRefreshing(false)
    }
  }

  if (isPending) return <LoadingPanel label="Loading today" />

  if (isError || !data) {
    return (
      <div className="space-y-4">
        <PageHeader title="Today" />
        <EmptyState
          icon={<AlertTriangle />}
          title="Today could not be loaded"
          description={toNormalisedError(error).message}
          action={
            <Button onClick={() => void refetch()}>
              <RefreshCw />
              Try again
            </Button>
          }
        />
      </div>
    )
  }

  const nothingScheduled =
    data.overdue.length === 0 &&
    data.dueToday.length === 0 &&
    (data.schedule?.length ?? 0) === 0 &&
    data.todaysFocus.length === 0

  return (
    <div className="space-y-6">
      <PageHeader
        title={`${data.greeting.salutation}, ${data.greeting.displayName}`}
        description={data.greeting.message}
        actions={
          <Button variant="outline" onClick={() => void refresh()} loading={refreshing}>
            <RefreshCw />
            Refresh
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <CalendarDays className="size-3.5" aria-hidden />
          {formatDate(data.date)}
        </span>
        <span>{data.greeting.timezone}</span>
        <span className="flex items-center gap-1.5">
          <Clock className="size-3.5" aria-hidden />
          {formatNumber(data.summary.focusMinutesToday)} focus min ·{' '}
          {formatNumber(data.summary.studyMinutesToday)} study min
        </span>
      </div>

      <SummaryStrip summary={data.summary} />

      {nothingScheduled ? (
        <EmptyState
          icon={<CircleSlash />}
          title="Nothing is scheduled for today"
          description="No tasks are due or overdue and your calendar is clear. Plan the day or check your calendar when you are ready."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button asChild size="sm">
                <Link to="/app/tasks">Go to tasks</Link>
              </Button>
              <Button asChild size="sm" variant="outline">
                <Link to="/app/calendar">Go to calendar</Link>
              </Button>
            </div>
          }
        />
      ) : null}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <SectionCard
            title="Overdue"
            description="Past their deadline and still open."
            contentClassName="p-3 sm:p-4"
          >
            <TaskList
              tasks={data.overdue}
              tone="border-l-2 border-l-destructive"
              emptyTitle="Nothing overdue"
              emptyDescription="No open task has passed its deadline."
              busy={completeTask.isPending || changeStatus.isPending}
              onComplete={(id) => completeTask.mutate(id)}
              onStatus={(id, status) => changeStatus.mutate({ taskId: id, status })}
            />
          </SectionCard>

          <SectionCard
            title="Due today"
            description="Deadlines landing on this date."
            contentClassName="p-3 sm:p-4"
          >
            <TaskList
              tasks={data.dueToday}
              tone="border-l-2 border-l-primary"
              emptyTitle="Nothing due today"
              emptyDescription="No task has a deadline on today's date."
              busy={completeTask.isPending || changeStatus.isPending}
              onComplete={(id) => completeTask.mutate(id)}
              onStatus={(id, status) => changeStatus.mutate({ taskId: id, status })}
            />
          </SectionCard>

          <SectionCard
            title="Focus list"
            description="Ranked by the server from overdue work, nearest deadline and priority."
            contentClassName="p-3 sm:p-4"
          >
            <FocusList
              items={data.todaysFocus}
              busy={completeTask.isPending}
              onComplete={(id) => completeTask.mutate(id)}
            />
          </SectionCard>
        </div>

        <div className="space-y-6">
          <SectionCard title="Schedule" description="Today in time order.">
            <SchedulePanel events={data.schedule ?? []} />
          </SectionCard>

          <SectionCard title="Habits" description="Check in as you go.">
            <HabitsPanel
              habits={data.habits ?? []}
              busy={toggleHabit.isPending}
              onToggle={(habit) => toggleHabit.mutate(habit.id)}
            />
          </SectionCard>

          <SectionCard title="Life balance" description="Dimensions without enough data are excluded.">
            <LifeBalancePanel balance={data.lifeBalance} />
          </SectionCard>

          <SectionCard title="Recommendations" description="Ranked actions from your current records.">
            <RecommendationsPanel items={data.recommendations ?? []} />
          </SectionCard>
        </div>
      </div>

      <p className="border-t pt-4 text-xs text-muted-foreground">
        The focus list, balance score and recommendations are computed on the server from your own records. No
        remote model was consulted.
      </p>
    </div>
  )
}
