import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  CircleSlash,
  RefreshCw,
  Sliders,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { toast } from 'sonner'

import { analyticsApi, settingsApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge, progressColor } from '@/lib/badges'
import {
  addDaysIso,
  formatCurrency,
  formatDate,
  formatDateTime,
  formatMinutes,
  formatNumber,
  formatPercent,
  titleCase,
  todayIso,
} from '@/lib/format'
import { toNormalisedError } from '@/hooks/use-api-error'
import type {
  AnalyticsSummary,
  InsightType,
  PredictionResponse,
  PredictionType,
} from '@/types/api'

const INSIGHT_TYPES: readonly InsightType[] = [
  'PRODUCTIVITY',
  'HABIT',
  'GOAL',
  'FINANCE',
  'LEARNING',
  'FOCUS',
  'PLANNING',
  'JOURNAL',
]

const PREDICTION_TYPES: readonly PredictionType[] = [
  'DEADLINE_RISK',
  'GOAL_COMPLETION',
  'BUDGET_OVERSPEND',
  'HABIT_CONSISTENCY',
  'PRODUCTIVITY_CHANGE',
]

const RANGE_PRESETS = [
  { key: '7d', label: 'Last 7 days' },
  { key: '30d', label: 'Last 30 days' },
  { key: '90d', label: 'Last 90 days' },
  { key: 'ytd', label: 'This year' },
] as const

const HISTORY_WINDOWS = ['7', '30', '90'] as const
const MONTH_LABELS = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
] as const

const ALL = '__all__'
const INSIGHT_PAGE_SIZE = 10
const NO_WEIGHTS: Record<string, number> = {}

const SERIES = {
  primary: 'var(--color-primary)',
  sky: 'var(--color-sky-400)',
  violet: 'var(--color-violet-400)',
  emerald: 'var(--color-emerald-400)',
  amber: 'var(--color-amber-400)',
  rose: 'var(--color-rose-400)',
  teal: 'var(--color-teal-400)',
} as const

const AXIS_TICK = { fontSize: 11, fill: 'var(--color-muted-foreground)' } as const
const TOOLTIP_STYLE = {
  backgroundColor: 'var(--color-popover)',
  border: '1px solid var(--color-border)',
  borderRadius: '0.5rem',
  fontSize: '12px',
  color: 'var(--color-popover-foreground)',
} as const

function presetRange(preset: string): { from: string; to: string } {
  const to = todayIso()
  if (preset === 'ytd') return { from: `${to.slice(0, 4)}-01-01`, to }
  const days = preset === '7d' ? 7 : preset === '90d' ? 90 : 30
  return { from: addDaysIso(to, -(days - 1)), to }
}

function activePreset(from: string, to: string): string | null {
  const match = RANGE_PRESETS.find((preset) => {
    const range = presetRange(preset.key)
    return range.from === from && range.to === to
  })
  return match ? match.key : null
}

function round1(value: number): number {
  return Math.round(value * 10) / 10
}

function monthLabel(month: { year: number; month: number }): string {
  const name = MONTH_LABELS[month.month - 1] ?? `M${month.month}`
  return `${name} ${String(month.year).slice(2)}`
}

function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
    >
      <p>{message}</p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        <RefreshCw /> Try again
      </Button>
    </div>
  )
}

function SummaryCards({ summary, rangeLabel }: { summary: AnalyticsSummary; rangeLabel: string }) {
  const cards = [
    { label: 'Tasks created', value: formatNumber(summary.tasksCreated), hint: rangeLabel },
    { label: 'Tasks completed', value: formatNumber(summary.tasksCompleted), hint: rangeLabel },
    { label: 'Tasks missed', value: formatNumber(summary.tasksMissed), hint: rangeLabel },
    {
      label: 'Completion rate',
      value: formatPercent(summary.taskCompletionRate, 1),
      hint: `${formatNumber(summary.tasksCompleted)} of ${formatNumber(summary.tasksCreated)} created`,
    },
    {
      label: 'Productivity score',
      value: formatPercent(summary.averageProductivityScore, 1),
      hint: `Overall ${formatPercent(summary.score, 1)}`,
    },
    {
      label: 'Focus',
      value: formatMinutes(summary.focusMinutes),
      hint: `${formatNumber(summary.focusSessions)} sessions`,
    },
    {
      label: 'Study',
      value: formatMinutes(summary.studyMinutes),
      hint: 'Recorded learning time',
    },
    {
      label: 'Habit check-ins',
      value: formatNumber(summary.habitCompletions),
      hint: `${formatPercent(summary.habitConsistencyRate, 1)} consistency`,
    },
    {
      label: 'Calendar events',
      value: formatNumber(summary.calendarEvents),
      hint: `${formatNumber(summary.habitScheduled)} habits scheduled`,
    },
    { label: 'Income', value: formatCurrency(summary.income), hint: 'Recorded in this range' },
    { label: 'Expenses', value: formatCurrency(summary.expenses), hint: 'Recorded in this range' },
    { label: 'Savings', value: formatCurrency(summary.savings), hint: 'Income minus expenses' },
  ]

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <StatCard key={card.label} label={card.label} value={card.value} hint={card.hint} />
      ))}
    </div>
  )
}

export default function AnalyticsPage() {
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

  const defaultRange = useMemo(() => presetRange('30d'), [])
  const from = searchParams.get('from') ?? defaultRange.from
  const to = searchParams.get('to') ?? defaultRange.to
  const insightTypeParam = searchParams.get('insightType') ?? ''
  const includeDismissed = searchParams.get('dismissed') === 'true'
  const insightPage = Math.max(0, Number(searchParams.get('ip') ?? '0') || 0)
  const predictionTypeParam = searchParams.get('predictionType') ?? ''
  const historyDays = searchParams.get('days') ?? '30'

  const [fromDraft, setFromDraft] = useState(from)
  const [toDraft, setToDraft] = useState(to)
  const [weightsDraft, setWeightsDraft] = useState<Record<string, number>>({})
  const [deletingPrediction, setDeletingPrediction] = useState<PredictionResponse | null>(null)

  useEffect(() => {
    setFromDraft(from)
  }, [from])

  useEffect(() => {
    setToDraft(to)
  }, [to])

  const rangeValid = from.length === 10 && to.length === 10 && from <= to
  const insightType = INSIGHT_TYPES.find((option) => option === insightTypeParam)
  const predictionType = PREDICTION_TYPES.find((option) => option === predictionTypeParam)
  const preset = activePreset(from, to)

  const summaryQuery = useQuery({
    queryKey: ['analytics', 'summary', from, to],
    queryFn: () => analyticsApi.summary(from, to),
    enabled: rangeValid,
  })

  const fullQuery = useQuery({
    queryKey: ['analytics', 'full', from, to],
    queryFn: () => analyticsApi.full(from, to),
    enabled: rangeValid,
  })

  const insightsQuery = useQuery({
    queryKey: ['analytics', 'insights', insightType ?? null, includeDismissed, insightPage],
    queryFn: () =>
      analyticsApi.insights({
        type: insightType,
        includeDismissed,
        page: insightPage,
        size: INSIGHT_PAGE_SIZE,
      }),
  })

  const predictionsQuery = useQuery({
    queryKey: ['analytics', 'predictions'],
    queryFn: () => analyticsApi.predictions(),
  })

  const historyQuery = useQuery({
    queryKey: ['analytics', 'predictionHistory', predictionType ?? null, historyDays],
    queryFn: () => analyticsApi.predictionHistory({ type: predictionType, days: Number(historyDays) }),
  })

  const balanceQuery = useQuery({
    queryKey: ['analytics', 'balance'],
    queryFn: () => analyticsApi.balance(),
  })

  /**
   * The balance response echoes a nominal weight per dimension, so the stored weights are read from
   * preferences to show what the user actually set rather than a placeholder.
   */
  const preferencesQuery = useQuery({
    queryKey: ['analytics', 'balance-weights-preference'],
    queryFn: () => settingsApi.preferences(),
  })

  const generateMutation = useMutation({
    mutationFn: () => analyticsApi.generateInsights(),
    onSuccess: async (result) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['analytics', 'insights'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'full'] }),
      ])
      toast.success('Insight rules evaluated', {
        description: `${result.evaluated} rule(s) evaluated, ${result.created.length} insight(s) created.`,
      })
      if (result.skippedReasons.length > 0) {
        toast.info('Some rules were skipped', {
          description: result.skippedReasons.join(' · '),
        })
      }
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const dismissMutation = useMutation({
    mutationFn: ({ id, dismissed }: { id: string; dismissed: boolean }) =>
      analyticsApi.updateInsight(id, { dismissed }),
    onSuccess: async (insight) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['analytics', 'insights'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'full'] }),
      ])
      toast.success(insight.dismissed ? 'Insight dismissed' : 'Insight restored')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const regenerateMutation = useMutation({
    mutationFn: () => analyticsApi.regeneratePredictions(),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['analytics', 'predictions'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'predictionHistory'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'full'] }),
      ])
      toast.success('Predictions regenerated')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removePredictionMutation = useMutation({
    mutationFn: (id: string) => analyticsApi.removePrediction(id),
    onSuccess: async () => {
      const label = deletingPrediction?.label ?? 'Prediction'
      setDeletingPrediction(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['analytics', 'predictions'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'predictionHistory'] }),
        queryClient.invalidateQueries({ queryKey: ['analytics', 'full'] }),
      ])
      toast.success('Prediction dismissed', { description: label })
    },
    onError: (error) => {
      setDeletingPrediction(null)
      toast.error(toNormalisedError(error).message)
    },
  })

  const weightsMutation = useMutation({
    mutationFn: (weights: Record<string, number>) => analyticsApi.updateWeights({ weights }),
    onSuccess: async (balance) => {
      queryClient.setQueryData(['analytics', 'balance'], balance)
      await queryClient.invalidateQueries({ queryKey: ['analytics', 'full'] })
      toast.success('Balance weights saved', {
        description: `Overall score is now ${formatNumber(balance.overallScore)} of 100.`,
      })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const balance = balanceQuery.data
  const storedWeights = preferencesQuery.data?.lifeBalanceWeights ?? NO_WEIGHTS

  useEffect(() => {
    if (!balance) return
    setWeightsDraft((current) => {
      const next: Record<string, number> = {}
      for (const dimension of balance.dimensions) {
        const existing = current[dimension.key]
        next[dimension.key] =
          existing ?? storedWeights[dimension.key] ?? Math.round(dimension.weight ?? 100)
      }
      return next
    })
  }, [balance, storedWeights])

  const rangeLabel = `${formatDate(from)} – ${formatDate(to)}`

  const dailyData = useMemo(
    () =>
      (fullQuery.data?.daily ?? []).map((point) => ({
        day: formatDate(point.date),
        tasksCompleted: point.tasksCompleted,
        focusMinutes: point.focusMinutes,
        studyMinutes: point.studyMinutes,
        habitsCompleted: point.habitsCompleted,
        productivityScore: round1(point.productivityScore),
      })),
    [fullQuery.data],
  )

  const weeklyData = useMemo(
    () =>
      (fullQuery.data?.weekly ?? []).map((point) => ({
        week: formatDate(point.weekStart),
        averageScore: round1(point.averageScore),
        tasksCompleted: point.tasksCompleted,
      })),
    [fullQuery.data],
  )

  const monthlyData = useMemo(
    () =>
      (fullQuery.data?.monthly ?? []).map((point) => ({
        month: monthLabel(point.month),
        tasksCompleted: point.tasksCompleted,
        averageScore: round1(point.averageScore),
      })),
    [fullQuery.data],
  )

  const monthlyAverage = useMemo(() => {
    if (monthlyData.length === 0) return 0
    const total = monthlyData.reduce((sum, point) => sum + point.tasksCompleted, 0)
    return round1(total / monthlyData.length)
  }, [monthlyData])

  const categoryData = useMemo(() => {
    const spending = fullQuery.data?.spendingByCategory ?? []
    const income = fullQuery.data?.incomeByCategory ?? []
    const categories = Array.from(new Set([...spending, ...income].map((entry) => entry.category)))
    return categories.map((category) => ({
      category,
      spending: spending.find((entry) => entry.category === category)?.total ?? 0,
      income: income.find((entry) => entry.category === category)?.total ?? 0,
    }))
  }, [fullQuery.data])

  const radarData = useMemo(
    () =>
      (balance?.dimensions ?? [])
        .filter((dimension) => dimension.sufficientData)
        .map((dimension) => ({ dimension: dimension.label, score: dimension.score })),
    [balance],
  )

  const insights = insightsQuery.data?.content ?? []
  const activePredictions = predictionsQuery.data ?? []
  const history = historyQuery.data ?? []
  const dataGaps = fullQuery.data?.dataGaps ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        description="Everything LIFEOS has measured, plus the insights, predictions and balance score derived from it."
      />

      <SectionCard
        title="Range"
        description="Kept in the address bar so a view can be shared or bookmarked."
      >
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {RANGE_PRESETS.map((option) => (
              <Button
                key={option.key}
                size="sm"
                variant={preset === option.key ? 'default' : 'outline'}
                onClick={() => {
                  const range = presetRange(option.key)
                  setFromDraft(range.from)
                  setToDraft(range.to)
                  patchParams({ from: range.from, to: range.to })
                }}
              >
                {option.label}
              </Button>
            ))}
            {preset ? null : (
              <Badge variant="outline">Custom range</Badge>
            )}
          </div>

          <div className="flex flex-wrap items-end gap-3">
            <div className="w-44 space-y-1.5">
              <Label htmlFor="analytics-from">From</Label>
              <Input
                id="analytics-from"
                type="date"
                value={fromDraft}
                onChange={(event) => setFromDraft(event.target.value)}
              />
            </div>
            <div className="w-44 space-y-1.5">
              <Label htmlFor="analytics-to">To</Label>
              <Input
                id="analytics-to"
                type="date"
                value={toDraft}
                onChange={(event) => setToDraft(event.target.value)}
              />
            </div>
            <Button
              onClick={() => patchParams({ from: fromDraft, to: toDraft })}
              disabled={!rangeValid || (fromDraft === from && toDraft === to)}
            >
              Apply range
            </Button>
            {fromDraft !== from || toDraft !== to ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setFromDraft(from)
                  setToDraft(to)
                }}
              >
                Reset
              </Button>
            ) : null}
          </div>

          {rangeValid ? null : (
            <p className="flex items-center gap-2 text-sm text-destructive">
              <AlertTriangle className="size-4" aria-hidden />
              The start date must not be after the end date.
            </p>
          )}
        </div>
      </SectionCard>

      {/* ------------------------------------------------------------- summary */}
      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">Summary · {rangeLabel}</h2>
          {summaryQuery.data ? (
            <span className="text-xs text-muted-foreground">
              Times shown in {summaryQuery.data.timezone}
            </span>
          ) : null}
        </div>

        {summaryQuery.isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, index) => (
              <Skeleton key={index} className="h-24 w-full" />
            ))}
          </div>
        ) : summaryQuery.error ? (
          <ErrorPanel
            message={toNormalisedError(summaryQuery.error).message}
            onRetry={() => void summaryQuery.refetch()}
          />
        ) : summaryQuery.data ? (
          <SummaryCards summary={summaryQuery.data} rangeLabel={rangeLabel} />
        ) : null}
      </section>

      {/* ---------------------------------------------------------- data gaps */}
      {fullQuery.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : fullQuery.error ? (
        <ErrorPanel
          message={toNormalisedError(fullQuery.error).message}
          onRetry={() => void fullQuery.refetch()}
        />
      ) : dataGaps.length > 0 ? (
        <div className="rounded-xl border border-warning/40 bg-warning/10 p-4">
          <p className="flex items-center gap-2 text-sm font-semibold text-warning">
            <AlertTriangle className="size-4" aria-hidden />
            Data gaps · {dataGaps.length} input(s) missing
          </p>
          <ul className="mt-2 space-y-1 text-sm text-warning">
            {dataGaps.map((gap) => (
              <li key={gap} className="flex gap-2">
                <span aria-hidden>•</span>
                <span>{gap}</span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-warning">
            Every score on this page is only as complete as the data behind it. Closing these gaps is
            the fastest way to make the analytics meaningful.
          </p>
        </div>
      ) : (
        <p className="rounded-xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
          The backend reported no data gaps for {rangeLabel}.
        </p>
      )}

      {/* ------------------------------------------------------------- charts */}
      <section className="grid gap-4 lg:grid-cols-2">
        <SectionCard
          title="Daily activity"
          description="Tasks completed, focus minutes, study minutes and habit check-ins per day."
        >
          {dailyData.length === 0 ? (
            <EmptyState
              icon={<BarChart3 />}
              title="No daily activity in this range"
              description="Complete tasks, log focus sessions or study time and this chart fills in."
            />
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={dailyData} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="day"
                    tick={AXIS_TICK}
                    minTickGap={32}
                    interval="preserveStartEnd"
                    stroke="var(--color-border)"
                  />
                  <YAxis tick={AXIS_TICK} stroke="var(--color-border)" allowDecimals={false} />
                  <Tooltip contentStyle={TOOLTIP_STYLE} labelStyle={TOOLTIP_STYLE} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line
                    type="monotone"
                    dataKey="tasksCompleted"
                    name="Tasks"
                    stroke={SERIES.sky}
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="focusMinutes"
                    name="Focus min"
                    stroke={SERIES.primary}
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="studyMinutes"
                    name="Study min"
                    stroke={SERIES.violet}
                    strokeWidth={2}
                    dot={false}
                  />
                  <Line
                    type="monotone"
                    dataKey="habitsCompleted"
                    name="Habits"
                    stroke={SERIES.emerald}
                    strokeWidth={2}
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>

        <SectionCard title="Weekly average score" description="Mean productivity score per week.">
          {weeklyData.length === 0 ? (
            <EmptyState
              icon={<BarChart3 />}
              title="No weekly history yet"
              description="Weeks appear once the daily productivity metrics have been recorded."
            />
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={weeklyData} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="week"
                    tick={AXIS_TICK}
                    minTickGap={16}
                    interval="preserveStartEnd"
                    stroke="var(--color-border)"
                  />
                  <YAxis tick={AXIS_TICK} stroke="var(--color-border)" domain={[0, 100]} />
                  <Tooltip contentStyle={TOOLTIP_STYLE} labelStyle={TOOLTIP_STYLE} />
                  <Bar
                    dataKey="averageScore"
                    name="Average score"
                    fill={SERIES.primary}
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>

        <SectionCard
          title="Monthly completions"
          description="Tasks completed per month, with the monthly average marked."
        >
          {monthlyData.length === 0 ? (
            <EmptyState
              icon={<BarChart3 />}
              title="No monthly history yet"
              description="A full month of activity is needed before this chart has anything to show."
            />
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={monthlyData} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="month" tick={AXIS_TICK} stroke="var(--color-border)" />
                  <YAxis tick={AXIS_TICK} stroke="var(--color-border)" allowDecimals={false} />
                  <Tooltip contentStyle={TOOLTIP_STYLE} labelStyle={TOOLTIP_STYLE} />
                  <Bar
                    dataKey="tasksCompleted"
                    name="Tasks completed"
                    fill={SERIES.emerald}
                    radius={[4, 4, 0, 0]}
                  />
                  <ReferenceLine
                    y={monthlyAverage}
                    stroke={SERIES.amber}
                    strokeDasharray="4 4"
                    label={{
                      value: `avg ${monthlyAverage}`,
                      position: 'insideTopRight',
                      fontSize: 11,
                      fill: 'var(--color-muted-foreground)',
                    }}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>

        <SectionCard
          title="Money by category"
          description="Spending and income recorded in this range, side by side."
        >
          {categoryData.length === 0 ? (
            <EmptyState
              icon={<BarChart3 />}
              title="No transactions in this range"
              description="Record income and expenses in Finance and the split appears here."
            />
          ) : (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={categoryData} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="category"
                    tick={AXIS_TICK}
                    interval={0}
                    angle={-20}
                    textAnchor="end"
                    height={56}
                    stroke="var(--color-border)"
                  />
                  <YAxis tick={AXIS_TICK} stroke="var(--color-border)" />
                  <Tooltip contentStyle={TOOLTIP_STYLE} labelStyle={TOOLTIP_STYLE} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="spending" name="Spending" fill={SERIES.rose} radius={[4, 4, 0, 0]} />
                  <Bar dataKey="income" name="Income" fill={SERIES.teal} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>
      </section>

      {/* -------------------------------------------------------- goal progress */}
      <SectionCard
        title="Goal progress"
        description="Recorded progress on every goal that moved in this range."
      >
        {(fullQuery.data?.goalProgress.length ?? 0) === 0 ? (
          <EmptyState
            icon={<BarChart3 />}
            title="No goal progress recorded"
            description="Update progress on a goal and it will show up here."
          />
        ) : (
          <ul className="space-y-3">
            {(fullQuery.data?.goalProgress ?? []).map((goal) => (
              <li key={goal.goalId} className="space-y-1.5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-sm font-medium">{goal.title}</span>
                  <span className="flex items-center gap-2 text-xs text-muted-foreground">
                    <StatusBadge value={goal.status} label={titleCase(goal.status)} />
                    <span className="tabular-nums">
                      {formatNumber(goal.tasksCompleted)}/{formatNumber(goal.tasksTotal)} tasks
                    </span>
                    <span className="tabular-nums">{formatPercent(goal.progress)}</span>
                  </span>
                </div>
                <Progress
                  value={goal.progress}
                  className="h-2"
                  aria-label={`${goal.title} progress`}
                  indicatorClassName={progressColor('goals')}
                />
              </li>
            ))}
          </ul>
        )}
      </SectionCard>

      {/* --------------------------------------------------------- life balance */}
      <SectionCard
        title="Life balance"
        description="Each dimension scored 0-100 from measured activity. Dimensions without enough data are excluded, never scored as zero."
        action={
          <Button
            size="sm"
            variant="secondary"
            loading={weightsMutation.isPending}
            disabled={Object.keys(weightsDraft).length === 0}
            onClick={() => weightsMutation.mutate(weightsDraft)}
          >
            <Sliders /> Save weights
          </Button>
        }
      >
        {balanceQuery.isPending ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
        ) : balanceQuery.error ? (
          <ErrorPanel
            message={toNormalisedError(balanceQuery.error).message}
            onRetry={() => void balanceQuery.refetch()}
          />
        ) : balance ? (
          <div className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-center">
              <div className="rounded-xl border p-4 text-center">
                <p className="text-xs text-muted-foreground">Overall</p>
                <p className="text-3xl font-semibold tabular-nums">{formatNumber(balance.overallScore)}</p>
                <p className="text-sm text-muted-foreground">grade {balance.grade}</p>
              </div>
              {radarData.length > 0 ? (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <RadarChart data={radarData} outerRadius="80%">
                      <PolarGrid stroke="var(--color-border)" />
                      <PolarAngleAxis dataKey="dimension" tick={AXIS_TICK} />
                      <PolarRadiusAxis domain={[0, 100]} tick={AXIS_TICK} />
                      <Tooltip contentStyle={TOOLTIP_STYLE} labelStyle={TOOLTIP_STYLE} />
                      <Radar
                        dataKey="score"
                        name="Score"
                        stroke={SERIES.primary}
                        fill={SERIES.primary}
                        fillOpacity={0.25}
                      />
                    </RadarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  No dimension has enough data yet, so nothing is scored.
                </p>
              )}
            </div>

            {balance.sufficientData ? null : (
              <p className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning">
                <CircleSlash className="mt-0.5 size-4 shrink-0" aria-hidden />
                No dimension has enough recorded data, so the overall score carries no meaning yet.
              </p>
            )}

            <ul className="grid gap-3 lg:grid-cols-2">
              {balance.dimensions.map((dimension) => (
                <li key={dimension.key}>
                  <Card
                    className={
                      dimension.sufficientData ? undefined : 'border-dashed bg-secondary/20 opacity-80'
                    }
                  >
                    <div className="space-y-2 p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="text-sm font-semibold">{dimension.label}</span>
                        {dimension.sufficientData ? (
                          <span className="text-sm tabular-nums">
                            {formatNumber(dimension.score)}
                            <span className="text-muted-foreground">/100</span>
                          </span>
                        ) : (
                          <Badge variant="outline">
                            <CircleSlash className="size-3" aria-hidden /> Excluded
                          </Badge>
                        )}
                      </div>

                      {dimension.sufficientData ? (
                        <Progress
                          value={dimension.score}
                          className="h-2"
                          aria-label={`${dimension.label} score`}
                          indicatorClassName={progressColor(dimension.key)}
                        />
                      ) : (
                        <p className="text-xs text-muted-foreground">
                          Excluded from the overall score because there is not enough data. It is not
                          scored as zero.
                        </p>
                      )}

                      <p className="text-xs text-muted-foreground">{dimension.explanation}</p>

                      {dimension.inputs.length > 0 ? (
                        <ul className="space-y-0.5 text-xs text-muted-foreground">
                          {dimension.inputs.map((input) => (
                            <li key={input} className="flex gap-2">
                              <span aria-hidden>•</span>
                              <span>{input}</span>
                            </li>
                          ))}
                        </ul>
                      ) : null}

                      <Separator className="my-2" />

                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between text-xs">
                          <Label htmlFor={`weight-${dimension.key}`}>Weight</Label>
                          <span className="tabular-nums text-muted-foreground">
                            {weightsDraft[dimension.key] ?? 0}
                          </span>
                        </div>
                        <input
                          id={`weight-${dimension.key}`}
                          type="range"
                          min={0}
                          max={100}
                          step={1}
                          value={weightsDraft[dimension.key] ?? 0}
                          onChange={(event) =>
                            setWeightsDraft((current) => ({
                              ...current,
                              [dimension.key]: Number(event.target.value),
                            }))
                          }
                          className="w-full accent-[var(--color-primary)]"
                        />
                      </div>
                    </div>
                  </Card>
                </li>
              ))}
            </ul>

            <div className="space-y-1 rounded-md border p-3 text-xs text-muted-foreground">
              <p>{balance.formula}</p>
              <p>{balance.disclaimer}</p>
            </div>
          </div>
        ) : null}
      </SectionCard>

      {/* ------------------------------------------------------------ insights */}
      <SectionCard
        title="Insights"
        description="Rule-based observations about this period. Dismissed insights stay in the database."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Select
              value={insightTypeParam || ALL}
              onValueChange={(value) =>
                patchParams({ insightType: value === ALL ? null : value, ip: '0' })
              }
            >
              <SelectTrigger id="insight-type" className="w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                {INSIGHT_TYPES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {titleCase(option)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button size="sm" loading={generateMutation.isPending} onClick={() => generateMutation.mutate()}>
              <Sparkles /> Generate insights
            </Button>
          </div>
        }
      >
        <div className="space-y-4">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-[var(--color-primary)]"
              checked={includeDismissed}
              onChange={(event) =>
                patchParams({
                  dismissed: event.target.checked ? 'true' : null,
                  ip: '0',
                })
              }
            />
            Include dismissed insights
          </label>

          {insightsQuery.isPending ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-24 w-full" />
              ))}
            </div>
          ) : insightsQuery.error ? (
            <ErrorPanel
              message={toNormalisedError(insightsQuery.error).message}
              onRetry={() => void insightsQuery.refetch()}
            />
          ) : insights.length === 0 ? (
            <EmptyState
              icon={<Sparkles />}
              title={insightTypeParam ? 'No insights of this type' : 'No insights yet'}
              description="Insights come from rules over your recorded data. Run generation to evaluate the rules; those without enough history report why they were skipped."
              action={
                insightTypeParam ? (
                  <Button
                    variant="outline"
                    onClick={() => patchParams({ insightType: null, ip: '0' })}
                  >
                    Show all types
                  </Button>
                ) : (
                  <Button loading={generateMutation.isPending} onClick={() => generateMutation.mutate()}>
                    <Sparkles /> Generate insights
                  </Button>
                )
              }
            />
          ) : (
            <>
              <ul className="space-y-3">
                {insights.map((insight) => (
                  <li key={insight.id}>
                    <Card className={insight.dismissed ? 'border-dashed opacity-70' : undefined}>
                      <div className="space-y-2 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <StatusBadge value={insight.severity} label={titleCase(insight.severity)} />
                              <Badge variant="outline">{titleCase(insight.insightType)}</Badge>
                              {insight.dismissed ? <Badge variant="secondary">Dismissed</Badge> : null}
                            </div>
                            <h3 className="text-sm font-semibold">{insight.title}</h3>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-xs tabular-nums text-muted-foreground">
                              {formatPercent(insight.confidence)} confidence
                            </span>
                            <Button
                              size="sm"
                              variant="outline"
                              loading={dismissMutation.isPending}
                              onClick={() =>
                                dismissMutation.mutate({
                                  id: insight.id,
                                  dismissed: !insight.dismissed,
                                })
                              }
                            >
                              {insight.dismissed ? 'Restore' : 'Dismiss'}
                            </Button>
                          </div>
                        </div>

                        <p className="text-sm text-muted-foreground">{insight.body}</p>

                        {insight.factors.length > 0 ? (
                          <div className="rounded-md border bg-secondary/40 px-3 py-2">
                            <p className="text-xs font-medium">What this is based on</p>
                            <ul className="mt-1 list-disc space-y-0.5 pl-4 text-xs text-muted-foreground">
                              {insight.factors.map((factor) => (
                                <li key={factor}>{factor}</li>
                              ))}
                            </ul>
                          </div>
                        ) : null}

                        <p className="text-xs text-muted-foreground">
                          {formatDate(insight.periodStart)} to {formatDate(insight.periodEnd)} · detected by{' '}
                          {insight.source} on {formatDateTime(insight.createdAt)}
                        </p>
                      </div>
                    </Card>
                  </li>
                ))}
              </ul>

              {insightsQuery.data && insightsQuery.data.totalPages > 1 ? (
                <nav
                  aria-label="Insight pagination"
                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
                >
                  <p className="text-sm text-muted-foreground">
                    Page {insightsQuery.data.page + 1} of {insightsQuery.data.totalPages} ·{' '}
                    {formatNumber(insightsQuery.data.totalElements)} insights
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={insightsQuery.data.first}
                      onClick={() => patchParams({ ip: String(insightsQuery.data.page - 1) })}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={insightsQuery.data.last}
                      onClick={() => patchParams({ ip: String(insightsQuery.data.page + 1) })}
                    >
                      Next
                    </Button>
                  </div>
                </nav>
              ) : null}
            </>
          )}
        </div>
      </SectionCard>

      {/* --------------------------------------------------------- predictions */}
      <SectionCard
        title="Predictions"
        description="Probabilistic forecasts from your recorded patterns. They are estimates, not certainties."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <Select
              value={predictionTypeParam || ALL}
              onValueChange={(value) =>
                patchParams({ predictionType: value === ALL ? null : value })
              }
            >
              <SelectTrigger id="prediction-type" className="w-52">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                {PREDICTION_TYPES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {titleCase(option)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              size="sm"
              loading={regenerateMutation.isPending}
              onClick={() => regenerateMutation.mutate()}
            >
              <RefreshCw /> Regenerate
            </Button>
          </div>
        }
      >
        <div className="space-y-6">
          <div className="space-y-3">
            <p className="text-sm font-semibold">Active</p>
            {predictionsQuery.isPending ? (
              <div className="space-y-3">
                {Array.from({ length: 2 }).map((_, index) => (
                  <Skeleton key={index} className="h-24 w-full" />
                ))}
              </div>
            ) : predictionsQuery.error ? (
              <ErrorPanel
                message={toNormalisedError(predictionsQuery.error).message}
                onRetry={() => void predictionsQuery.refetch()}
              />
            ) : activePredictions.length === 0 ? (
              <EmptyState
                icon={<Sparkles />}
                title="No active predictions"
                description="Predictions need enough history to be meaningful. Keep recording activity, then regenerate."
                action={
                  <Button loading={regenerateMutation.isPending} onClick={() => regenerateMutation.mutate()}>
                    <RefreshCw /> Regenerate predictions
                  </Button>
                }
              />
            ) : (
              <ul className="grid gap-3 lg:grid-cols-2">
                {activePredictions.map((prediction) => (
                  <li key={prediction.id}>
                    <Card>
                      <div className="space-y-2 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="min-w-0 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant="outline">{titleCase(prediction.predictionType)}</Badge>
                              <Badge variant="secondary">{titleCase(prediction.subjectType)}</Badge>
                            </div>
                            <h3 className="text-sm font-semibold">{prediction.label}</h3>
                          </div>
                          <Button
                            size="icon-sm"
                            variant="ghost"
                            aria-label={`Dismiss prediction ${prediction.label}`}
                            onClick={() => setDeletingPrediction(prediction)}
                          >
                            <Trash2 />
                          </Button>
                        </div>

                        <div className="grid grid-cols-3 gap-3 text-xs">
                          <div>
                            <p className="text-muted-foreground">Probability</p>
                            <p className="font-medium tabular-nums">
                              {formatPercent(prediction.probability)}
                            </p>
                          </div>
                          <div>
                            <p className="text-muted-foreground">Confidence</p>
                            <p className="font-medium tabular-nums">
                              {formatPercent(prediction.confidence)}
                            </p>
                          </div>
                          <div>
                            <p className="text-muted-foreground">Horizon</p>
                            <p className="font-medium tabular-nums">
                              {formatNumber(prediction.horizonDays)}d
                            </p>
                          </div>
                        </div>

                        <Progress
                          value={prediction.probability}
                          className="h-2"
                          aria-label={`${prediction.label} probability`}
                          indicatorClassName={progressColor(prediction.predictionType)}
                        />

                        {prediction.factors.length > 0 ? (
                          <ul className="space-y-0.5 text-xs text-muted-foreground">
                            {prediction.factors.map((factor) => (
                              <li key={factor} className="flex gap-2">
                                <span aria-hidden>•</span>
                                <span>{factor}</span>
                              </li>
                            ))}
                          </ul>
                        ) : null}

                        <p className="text-xs text-muted-foreground">{prediction.disclaimer}</p>
                        <p className="text-xs text-muted-foreground">
                          Created {formatDateTime(prediction.createdAt)}
                          {prediction.expiresAt
                            ? ` · expires ${formatDateTime(prediction.expiresAt)}`
                            : ''}
                        </p>
                      </div>
                    </Card>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Separator />

          <div className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-semibold">History</p>
              <div className="w-32 space-y-1.5">
                <Label htmlFor="prediction-days">Window</Label>
                <Select value={historyDays} onValueChange={(value) => patchParams({ days: value })}>
                  <SelectTrigger id="prediction-days">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {HISTORY_WINDOWS.map((option) => (
                      <SelectItem key={option} value={option}>
                        {option} days
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {historyQuery.isPending ? (
              <div className="space-y-2">
                {Array.from({ length: 2 }).map((_, index) => (
                  <Skeleton key={index} className="h-14 w-full" />
                ))}
              </div>
            ) : historyQuery.error ? (
              <ErrorPanel
                message={toNormalisedError(historyQuery.error).message}
                onRetry={() => void historyQuery.refetch()}
              />
            ) : history.length === 0 ? (
              <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                No predictions were generated in the last {historyDays} days.
              </p>
            ) : (
              <ul className="space-y-2">
                {history.map((prediction) => (
                  <li
                    key={prediction.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3"
                  >
                    <div className="min-w-0 space-y-0.5">
                      <p className="truncate text-sm font-medium">{prediction.label}</p>
                      <p className="text-xs text-muted-foreground">
                        {titleCase(prediction.predictionType)} ·{' '}
                        {formatPercent(prediction.probability)} · created{' '}
                        {formatDateTime(prediction.createdAt)}
                      </p>
                    </div>
                    <Badge variant="outline">
                      {formatPercent(prediction.confidence)} confidence
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </SectionCard>

      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <CheckCircle2 className="size-3.5" aria-hidden />
        Scores and predictions are computed from your own records. They describe patterns in what you
        recorded, nothing more.
      </p>

      <Dialog
        open={deletingPrediction !== null}
        onOpenChange={(open) => !open && setDeletingPrediction(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Dismiss prediction</DialogTitle>
            <DialogDescription>
              {deletingPrediction
                ? `"${deletingPrediction.label}" will be dismissed. It stays in the history and can reappear after the next regeneration.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingPrediction(null)}>
              Keep prediction
            </Button>
            <Button
              variant="destructive"
              loading={removePredictionMutation.isPending}
              onClick={() => {
                if (deletingPrediction) removePredictionMutation.mutate(deletingPrediction.id)
              }}
            >
              Dismiss
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}