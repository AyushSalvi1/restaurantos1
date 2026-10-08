import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  Compass,
  ListTodo,
  RefreshCw,
  Target,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'

import { dashboardApi, habitApi, taskApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { LoadingPanel } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatMinutes,
  formatNumber,
  formatPercent,
  formatTime,
  titleCase,
  todayIso,
} from '@/lib/format'
import type { BalanceScore, DashboardResponse, HabitMini } from '@/types/api'

const DASHBOARD_KEY = ['dashboard', 'overview'] as const

/**
 * Server-supplied links are only turned into navigation when they are already absolute application
 * routes. Anything else is rendered as plain text rather than a link that would dead-end.
 */
function appRoute(path: string | null | undefined): string | null {
  return path && path.startsWith('/app') ? path : null
}

function GreetingHeader({ data, actions }: { data: DashboardResponse; actions: ReactNode }) {
  const { greeting, timezone } = data
  return (
    <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="space-y-1">
        <p className="text-xs font-medium uppercase tracking-wide text-primary">{greeting.salutation}</p>
        <h1 className="text-2xl font-semibold tracking-tight">
          {greeting.salutation}, {greeting.displayName}
        </h1>
        <p className="text-sm text-muted-foreground">{greeting.message}</p>
        <dl className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <CalendarDays className="size-3.5" aria-hidden />
            <dt className="sr-only">Date</dt>
            <dd>{formatDate(greeting.date)}</dd>
          </div>
          <div className="flex items-center gap-1.5">
            <Compass className="size-3.5" aria-hidden />
            <dt className="sr-only">Your timezone</dt>
            <dd>{timezone || greeting.timezone || 'Not set'}</dd>
          </div>
          <div className="flex items-center gap-1.5">
            <dt>Server time</dt>
            <dd>{formatDateTime(greeting.serverTime)}</dd>
          </div>
        </dl>
        {greeting.timezone && timezone && greeting.timezone !== timezone ? (
          <p className="text-xs text-warning">
            Your profile timezone is {timezone}, but this greeting was computed in {greeting.timezone}.
          </p>
        ) : null}
      </div>
      <div className="flex flex-wrap items-center gap-2">{actions}</div>
    </header>
  )
}

function DataGaps({ gaps }: { gaps: string[] }) {
  if (gaps.length === 0) return null
  return (
    <section className="rounded-xl border border-warning/40 bg-warning/5 p-4">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden />
        <div className="min-w-0 space-y-2">
          <h2 className="text-sm font-semibold text-warning">Missing inputs</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            {gaps.map((gap) => (
              <li key={gap}>{gap}</li>
            ))}
          </ul>
          <p className="text-xs text-muted-foreground">
            Scores, insights and forecasts below stay empty or low-confidence until these inputs exist.
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <Button asChild size="sm" variant="outline">
              <Link to="/app/tasks">Add a task</Link>
            </Button>
            <Button asChild size="sm" variant="outline">
              <Link to="/app/habits">Add a habit</Link>
            </Button>
            <Button asChild size="sm" variant="outline">
              <Link to="/app/goals">Set a goal</Link>
            </Button>
            <Button asChild size="sm" variant="outline">
              <Link to="/app/focus">Log focus time</Link>
            </Button>
          </div>
        </div>
      </div>
    </section>
  )
}

function TodayStats({ data }: { data: DashboardResponse['today'] }) {
  const cards: { label: string; value: string; hint: string; icon: LucideIcon; tone?: string }[] = [
    {
      label: 'Due today',
      value: formatNumber(data.tasksDue),
      hint: `${formatMinutes(data.estimatedMinutes)} estimated`,
      icon: ListTodo,
    },
    {
      label: 'Completed today',
      value: formatNumber(data.tasksCompleted),
      hint: 'Recorded in today’s metric',
      icon: CheckCircle2,
      tone: 'text-success',
    },
    {
      label: 'Overdue',
      value: formatNumber(data.tasksOverdue),
      hint: 'Past their deadline',
      icon: AlertTriangle,
      tone: data.tasksOverdue > 0 ? 'text-destructive' : undefined,
    },
    { label: 'Open tasks', value: formatNumber(data.openTasks), hint: 'Todo and in progress', icon: Target },
    {
      label: 'Focus minutes',
      value: formatNumber(data.focusMinutesToday),
      hint: 'Logged today',
      icon: TrendingUp,
    },
    {
      label: 'Study minutes',
      value: formatNumber(data.studyMinutesToday),
      hint: 'Logged today',
      icon: TrendingUp,
    },
    {
      label: 'Habits',
      value: `${data.habitsCompleted}/${data.habitsScheduled}`,
      hint: 'Checked in today',
      icon: CheckCircle2,
    },
    {
      label: 'Calendar events',
      value: formatNumber(data.calendarEvents),
      hint: 'Scheduled today',
      icon: CalendarDays,
    },
  ]

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <StatCard
          key={card.label}
          label={card.label}
          value={card.value}
          hint={card.hint}
          icon={<card.icon />}
          tone={card.tone}
        />
      ))}
    </div>
  )
}

function SummaryCards({ cards }: { cards: DashboardResponse['summaryCards'] }) {
  if (cards.length === 0) {
    return (
      <EmptyState
        title="No activity to summarise yet"
        description="Complete a task, log a focus session or record a transaction and the last 30 days will appear here."
        action={
          <Button asChild size="sm">
            <Link to="/app/tasks">Open tasks</Link>
          </Button>
        }
      />
    )
  }
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {cards.map((card) => (
        <StatCard
          key={card.label}
          label={card.label}
          value={formatNumber(card.value)}
          hint={card.trend}
          to={appRoute(card.link) ?? undefined}
        />
      ))}
    </div>
  )
}

function FocusList({
  items,
  onComplete,
  pending,
}: {
  items: DashboardResponse['todaysFocus']
  onComplete: (taskId: string) => void
  pending: boolean
}) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="Nothing due or overdue"
        description="No task has a deadline on or before today, so there is no focus list. Give a task a deadline to plan the next one."
        action={
          <Button asChild size="sm">
            <Link to="/app/tasks">Open tasks</Link>
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
              {item.category ? <Badge variant="outline">{item.category}</Badge> : null}
            </div>
            <p className="text-xs text-muted-foreground">{item.reason}</p>
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
              {item.deadline ? <span>Deadline {formatDateTime(item.deadline)}</span> : null}
              {item.estimatedMinutes > 0 ? <span>About {formatMinutes(item.estimatedMinutes)}</span> : null}
            </p>
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
            loading={pending}
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

function LifeBalancePanel({ balance }: { balance: BalanceScore }) {
  const dimensions = balance.dimensions ?? []
  return (
    <div className="space-y-3">
      <div className="flex items-end gap-3">
        <p className="text-3xl font-semibold tabular-nums">
          {balance.sufficientData ? balance.overallScore : '—'}
        </p>
        <p className="pb-1 text-sm text-muted-foreground">
          {balance.sufficientData ? balance.grade : 'Not enough data to grade'}
        </p>
      </div>
      {balance.sufficientData ? null : (
        <p className="rounded-md border border-warning/40 bg-warning/5 px-3 py-2 text-xs text-warning">
          No dimension has enough recorded activity, so nothing is included in this score.
        </p>
      )}
      <ul className="space-y-3">
        {dimensions.map((dimension) => (
          <li key={dimension.key} className="space-y-1">
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
            {dimension.sufficientData && dimension.explanation ? (
              <p className="text-xs text-muted-foreground">{dimension.explanation}</p>
            ) : null}
            {!dimension.sufficientData && dimension.inputs?.length ? (
              <p className="text-xs text-muted-foreground">
                Needs {dimension.inputs.join(', ')} before it can be scored.
              </p>
            ) : null}
          </li>
        ))}
      </ul>
      {balance.disclaimer ? <p className="text-xs text-muted-foreground">{balance.disclaimer}</p> : null}
    </div>
  )
}

function GoalsProgress({ bars }: { bars: DashboardResponse['goalProgress'] }) {
  if (bars.length === 0) {
    return (
      <EmptyState
        title="No active goals"
        description="Goal bars appear once you have at least one active goal."
        action={
          <Button asChild size="sm">
            <Link to="/app/goals">Create a goal</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ul className="space-y-3">
      {bars.map((bar) => {
        const target = appRoute(bar.link)
        const barBody = (
          <div className="space-y-1">
            <div className="flex items-baseline justify-between gap-3">
              <span className="truncate text-sm font-medium">{bar.label}</span>
              <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                {formatPercent(bar.progress)}
              </span>
            </div>
            <Progress value={bar.progress} indicatorClassName="bg-emerald-400" />
            {bar.meta ? <p className="text-xs text-muted-foreground">{bar.meta}</p> : null}
          </div>
        )
        return (
          <li key={bar.id}>
            {target ? (
              <Link to={target} className="block rounded-md underline-offset-4 hover:underline">
                {barBody}
              </Link>
            ) : (
              barBody
            )}
          </li>
        )
      })}
    </ul>
  )
}

function HabitsToday({
  habits,
  onToggle,
  pending,
}: {
  habits: HabitMini[]
  onToggle: (habit: HabitMini) => void
  pending: boolean
}) {
  if (habits.length === 0) {
    return (
      <EmptyState
        title="No habits yet"
        description="Create a habit to get a daily check-in prompt on the dashboard."
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
        const inputId = `habit-toggle-${habit.id}`
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
              disabled={pending}
              onCheckedChange={() => onToggle(habit)}
              aria-label={`Mark ${habit.name} as done today`}
            />
          </li>
        )
      })}
    </ul>
  )
}

function UpcomingEvents({ events }: { events: DashboardResponse['upcomingEvents'] }) {
  if (events.length === 0) {
    return (
      <EmptyState
        title="Nothing scheduled today"
        description="Your calendar has no remaining events for today."
        action={
          <Button asChild size="sm">
            <Link to="/app/calendar">Open calendar</Link>
          </Button>
        }
      />
    )
  }
  return (
    <ul className="space-y-2">
      {events.map((event) => (
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
    </ul>
  )
}

function FinancePanel({ data }: { data: DashboardResponse['finance'] }) {
  const currency = data.currency || 'USD'
  const categories = data.expenseByCategory ?? []
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Income this month"
          value={formatCurrency(data.incomeThisMonth, currency)}
          tone="text-success"
        />
        <StatCard
          label="Expenses this month"
          value={formatCurrency(data.expenseThisMonth, currency)}
          tone="text-destructive"
        />
        <StatCard label="Saved this month" value={formatCurrency(data.savingsThisMonth, currency)} />
        <StatCard
          label="Remaining budget"
          value={formatCurrency(data.remainingBudget, currency)}
          tone={data.remainingBudget < 0 ? 'text-destructive' : undefined}
        />
      </div>

      {data.monthlyTrend?.length ? (
        <div className="h-48 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data.monthlyTrend} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis dataKey="month" tickLine={false} axisLine={false} tick={{ fontSize: 11 }} />
              <YAxis tickLine={false} axisLine={false} width={56} tick={{ fontSize: 11 }} />
              <Tooltip
                cursor={{ fill: 'var(--color-secondary)', opacity: 0.4 }}
                contentStyle={{
                  background: 'var(--color-popover)',
                  border: '1px solid var(--color-border)',
                  borderRadius: 8,
                  fontSize: 12,
                  color: 'var(--color-popover-foreground)',
                }}
                formatter={(value, name) => `${String(name)}: ${formatCurrency(Number(value ?? 0), currency)}`}
              />
              <Bar dataKey="income" name="Income" fill="var(--color-success)" radius={[3, 3, 0, 0]} />
              <Bar
                dataKey="expenses"
                name="Expenses"
                fill="var(--color-destructive)"
                radius={[3, 3, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <EmptyState
          title="No monthly trend yet"
          description="Record income or expense transactions to build the last months at a glance."
          action={
            <Button asChild size="sm">
              <Link to="/app/finance">Open finance</Link>
            </Button>
          }
        />
      )}

      <div>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Expenses by category
        </h3>
        {categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">No expense transactions recorded this month.</p>
        ) : (
          <ul className="space-y-2">
            {categories.map((category) => (
              <li key={category.category} className="space-y-1">
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="truncate">{titleCase(category.category) || category.category}</span>
                  <span className="shrink-0 tabular-nums text-muted-foreground">
                    {formatCurrency(category.total, currency)} · {formatPercent(category.sharePercent)}
                  </span>
                </div>
                <Progress value={category.sharePercent} indicatorClassName="bg-teal-400" />
                <p className="text-xs text-muted-foreground">
                  {formatNumber(category.transactionCount)} transaction
                  {category.transactionCount === 1 ? '' : 's'}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function LearningPanel({ data }: { data: DashboardResponse['learning'] }) {
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard label="Active goals" value={formatNumber(data.activeGoals)} />
        <StatCard
          label="Hours this week"
          value={data.hoursThisWeek.toFixed(1)}
          hint={`${data.sessionsThisWeek} session(s)`}
        />
        <StatCard
          label="Average quiz score"
          value={data.averageQuizScore > 0 ? formatPercent(data.averageQuizScore) : '—'}
          hint={data.averageQuizScore > 0 ? undefined : 'No quiz results yet'}
        />
      </div>
      <div>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Top learning goals
        </h3>
        {data.topGoals?.length ? (
          <ul className="space-y-3">
            {data.topGoals.map((goal) => (
              <li key={goal.id} className="space-y-1">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="truncate text-sm font-medium">{goal.title}</span>
                  <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                    {formatPercent(goal.progress)}
                  </span>
                </div>
                <Progress value={goal.progress} indicatorClassName="bg-violet-400" />
                <p className="text-xs text-muted-foreground">
                  {goal.completedTopicCount} of {goal.topicCount} topics · {goal.hoursSpent}h logged
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">No learning goals yet.</p>
        )}
      </div>
    </div>
  )
}

function InsightsPanel({ insights }: { insights: DashboardResponse['insights'] }) {
  if (insights.length === 0) {
    return (
      <EmptyState
        title="No insights yet"
        description="Insights are derived from your own recorded activity and appear once there is enough history."
      />
    )
  }
  return (
    <ul className="space-y-3">
      {insights.map((insight) => (
        <li key={insight.id} className="space-y-1.5 rounded-lg border bg-background/40 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge value={insight.severity} />
            <Badge variant="outline">{titleCase(insight.insightType)}</Badge>
            <span className="text-xs tabular-nums text-muted-foreground">
              Confidence {formatPercent(insight.confidence)}
            </span>
          </div>
          <p className="text-sm font-medium">{insight.title}</p>
          <p className="text-sm text-muted-foreground">{insight.body}</p>
          {insight.factors.length > 0 ? (
            <p className="rounded-md bg-secondary/50 px-2 py-1.5 text-xs text-muted-foreground">
              {insight.factors.join(' · ')}
            </p>
          ) : null}
        </li>
      ))}
    </ul>
  )
}

function RecommendationsPanel({ items }: { items: DashboardResponse['recommendations'] }) {
  if (items.length === 0) {
    return (
      <EmptyState
        title="No recommendations"
        description="Recommendations come from your recorded tasks, habits and transactions, so they appear once that history exists."
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

function PredictionsPanel({ predictions }: { predictions: DashboardResponse['predictions'] }) {
  if (predictions.length === 0) {
    return (
      <EmptyState
        title="No active forecasts"
        description="Forecasts are recalculated from deadlines, budgets and habit history as those records accumulate."
      />
    )
  }
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {predictions.map((prediction) => (
        <div key={prediction.id} className="space-y-1.5 rounded-lg border bg-background/40 p-3">
          <div className="flex items-center justify-between gap-2">
            <Badge variant="outline">{titleCase(prediction.type)}</Badge>
            <span className="text-sm font-semibold tabular-nums">
              {formatPercent(prediction.probability)}
            </span>
          </div>
          <p className="text-sm">{prediction.label}</p>
          {prediction.factors?.length ? (
            <ul className="list-disc space-y-0.5 pl-5 text-xs text-muted-foreground">
              {prediction.factors.map((factor) => (
                <li key={factor}>{factor}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ))}
    </div>
  )
}

export default function DashboardPage() {
  const queryClient = useQueryClient()
  const [refreshing, setRefreshing] = useState(false)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: DASHBOARD_KEY,
    queryFn: () => dashboardApi.dashboard(),
  })

  const completeFocus = useMutation({
    mutationFn: (taskId: string) => taskApi.complete(taskId, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['tasks'] })
      toast.success('Task completed')
    },
    onError: (mutationError) => toast.error(toNormalisedError(mutationError).message),
  })

  const toggleHabit = useMutation({
    mutationFn: (habitId: string) => habitApi.toggle(habitId, { logDate: todayIso() }),
    onMutate: async (habitId) => {
      await queryClient.cancelQueries({ queryKey: DASHBOARD_KEY })
      const previous = queryClient.getQueryData<DashboardResponse>(DASHBOARD_KEY)
      queryClient.setQueryData<DashboardResponse>(DASHBOARD_KEY, (current) => {
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
          today: {
            ...current.today,
            habitsCompleted: Math.max(0, current.today.habitsCompleted + (next ? 1 : -1)),
          },
        }
      })
      return { previous }
    },
    onError: (mutationError, _habitId, context) => {
      if (context?.previous) queryClient.setQueryData(DASHBOARD_KEY, context.previous)
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
      else toast.success('Dashboard refreshed')
    } finally {
      setRefreshing(false)
    }
  }

  if (isPending) return <LoadingPanel label="Loading your dashboard" />

  if (isError || !data) {
    return (
      <div className="space-y-4">
        <PageHeader title="Dashboard" />
        <EmptyState
          icon={<AlertTriangle />}
          title="The dashboard could not be loaded"
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

  return (
    <div className="space-y-6">
      <GreetingHeader
        data={data}
        actions={
          <Button variant="outline" onClick={() => void refresh()} loading={refreshing}>
            <RefreshCw />
            Refresh
          </Button>
        }
      />

      <DataGaps gaps={data.dataGaps ?? []} />

      <TodayStats data={data.today} />

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <SectionCard
            title="Today's focus"
            description="Ranked by the server from overdue work, nearest deadline and priority."
            contentClassName="p-3 sm:p-4"
          >
            <FocusList
              items={data.todaysFocus ?? []}
              pending={completeFocus.isPending}
              onComplete={(taskId) => completeFocus.mutate(taskId)}
            />
          </SectionCard>

          <SectionCard title="Last 30 days" description="Counts computed from your stored records.">
            <SummaryCards cards={data.summaryCards ?? []} />
          </SectionCard>

          <SectionCard
            title="Life balance"
            description="Weighted mean of the dimensions that have recorded activity."
          >
            <LifeBalancePanel balance={data.lifeBalance} />
          </SectionCard>

          <SectionCard title="Goals progress" description="Active goals, closest to done first.">
            <GoalsProgress bars={data.goalProgress ?? []} />
          </SectionCard>

          <SectionCard title="Finance" description="This month against the budgets you set.">
            <FinancePanel data={data.finance} />
          </SectionCard>

          <SectionCard title="Learning" description="Active goals and this week's study activity.">
            <LearningPanel data={data.learning} />
          </SectionCard>
        </div>

        <div className="space-y-6">
          <SectionCard title="Productivity score" description="0 to 100, for today.">
            <div className="space-y-2">
              <p className="text-4xl font-semibold tabular-nums">
                {data.productivityScore > 0 ? data.productivityScore : '—'}
              </p>
              <Progress value={data.productivityScore} indicatorClassName="bg-sky-400" />
              <p className="text-sm text-muted-foreground">{data.productivityExplanation}</p>
            </div>
          </SectionCard>

          <SectionCard title="Habits today" description="Check in as you go; the streak updates immediately.">
            <HabitsToday
              habits={data.habits ?? []}
              pending={toggleHabit.isPending}
              onToggle={(habit) => toggleHabit.mutate(habit.id)}
            />
          </SectionCard>

          <SectionCard title="Upcoming events" description="The rest of today.">
            <UpcomingEvents events={data.upcomingEvents ?? []} />
          </SectionCard>

          <SectionCard
            title="Insights"
            description="Derived from your own records, with the evidence that produced each one."
          >
            <InsightsPanel insights={data.insights ?? []} />
          </SectionCard>

          <SectionCard title="Recommendations" description="Ranked actions from your current records.">
            <RecommendationsPanel items={data.recommendations ?? []} />
          </SectionCard>

          <SectionCard title="Forecasts" description="Probabilities from measured history.">
            <PredictionsPanel predictions={data.predictions ?? []} />
          </SectionCard>
        </div>
      </div>

      <p className="border-t pt-4 text-xs text-muted-foreground">
        Scores, insights, recommendations and forecasts on this page are computed on the server from your own
        stored records. No remote model was consulted.
      </p>
    </div>
  )
}
