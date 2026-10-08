import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  BookOpen,
  CalendarClock,
  ExternalLink,
  GraduationCap,
  ListPlus,
  Pencil,
  Plus,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { Controller, useFieldArray, useForm } from 'react-hook-form'
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { toast } from 'sonner'

import { learningApi } from '@/api/endpoints'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge, progressColor } from '@/lib/badges'
import {
  formatDate,
  formatMinutes,
  formatNumber,
  formatPercent,
  fromLocalDateTimeInput,
  toLocalDateTimeInput,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import { toNormalisedError } from '@/hooks/use-api-error'
import type {
  ConfirmRoadmapRequest,
  LearningGoalRequest,
  LearningGoalResponse,
  LearningStatus,
  ResourceType,
  TopicRequest,
  TopicResponse,
} from '@/types/api'

const LEARNING_STATUSES: LearningStatus[] = ['ACTIVE', 'COMPLETED', 'PAUSED', 'ARCHIVED']

const RESOURCE_TYPES: { value: ResourceType; label: string }[] = [
  { value: 'ARTICLE', label: 'Article' },
  { value: 'VIDEO', label: 'Video' },
  { value: 'BOOK', label: 'Book' },
  { value: 'COURSE', label: 'Course' },
  { value: 'PROJECT', label: 'Project' },
  { value: 'DOCUMENTATION', label: 'Documentation' },
  { value: 'OTHER', label: 'Other' },
]

const STUDY_WINDOWS = [7, 30, 90] as const

type TabKey = 'goals' | 'study' | 'resources' | 'skills' | 'stats'

const TOOLTIP_STYLE = {
  backgroundColor: 'var(--color-card)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  color: 'var(--color-card-foreground)',
  fontSize: 12,
}

const ALL = 'ALL'
const NONE = 'NONE'

function parseList(value: string): string[] {
  return Array.from(
    new Set(
      value
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
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

interface ConfirmState {
  title: string
  description: string
  confirmLabel: string
  kind: 'goal' | 'topic' | 'resource' | 'skill'
  id: string
}

function ConfirmDialog({
  state,
  busy,
  onOpenChange,
  onConfirm,
}: {
  state: ConfirmState | null
  busy: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
}) {
  return (
    <Dialog open={state !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{state?.title ?? ''}</DialogTitle>
          <DialogDescription>{state?.description ?? ''}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            Cancel
          </Button>
          <Button variant="destructive" loading={busy} onClick={onConfirm}>
            {state?.confirmLabel ?? 'Delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/* -------------------------------------------------------------------------- */
/* Goals                                                                       */
/* -------------------------------------------------------------------------- */

interface GoalFormValues {
  title: string
  description: string
  category: string
  targetDate: string
  status: LearningStatus
  topics: { title: string; description: string; estimatedMinutes: string }[]
}

function GoalDialog({
  open,
  goal,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  goal: LearningGoalResponse | null
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: GoalFormValues) => void
}) {
  const { register, control, handleSubmit, reset, watch, formState } = useForm<GoalFormValues>({
    defaultValues: {
      title: goal?.title ?? '',
      description: goal?.description ?? '',
      category: goal?.category ?? '',
      targetDate: goal?.targetDate ? toLocalDateTimeInput(goal.targetDate).slice(0, 10) : '',
      status: goal?.status ?? 'ACTIVE',
      topics: (goal?.topics ?? []).map((topic) => ({
        title: topic.title,
        description: topic.description ?? '',
        estimatedMinutes: topic.estimatedMinutes ? String(topic.estimatedMinutes) : '',
      })),
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'topics' })
  const status = watch('status')

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        reset({
          title: goal?.title ?? '',
          description: goal?.description ?? '',
          category: goal?.category ?? '',
          targetDate: goal?.targetDate ? toLocalDateTimeInput(goal.targetDate).slice(0, 10) : '',
          status: goal?.status ?? 'ACTIVE',
          topics: (goal?.topics ?? []).map((topic) => ({
            title: topic.title,
            description: topic.description ?? '',
            estimatedMinutes: topic.estimatedMinutes ? String(topic.estimatedMinutes) : '',
          })),
        })
        onOpenChange(next)
      }}
    >
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>{goal ? `Edit ${goal.title}` : 'New learning goal'}</DialogTitle>
          <DialogDescription>
            Topics become your study checklist. Estimated minutes drive the time budget for the goal.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-1.5">
            <Label htmlFor="goal-title">Title</Label>
            <Input
              id="goal-title"
              required
              maxLength={200}
              aria-invalid={Boolean(formState.errors.title)}
              {...register('title', { required: 'A title is required' })}
            />
            {formState.errors.title ? (
              <p className="text-xs text-destructive">{formState.errors.title.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="goal-description">Description</Label>
            <Textarea id="goal-description" rows={2} maxLength={2000} {...register('description')} />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="goal-category">Category</Label>
              <Input id="goal-category" maxLength={60} {...register('category')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="goal-target">Target date</Label>
              <Input id="goal-target" type="date" {...register('targetDate')} />
            </div>
            <div className="space-y-1.5">
              <Label>Status</Label>
              <Controller
                control={control}
                name="status"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={(value) => field.onChange(value as LearningStatus)}>
                    <SelectTrigger id="goal-status" aria-label="Status">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {LEARNING_STATUSES.map((option) => (
                        <SelectItem key={option} value={option}>
                          {option.toLowerCase()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
              {status === 'COMPLETED' ? (
                <p className="text-xs text-muted-foreground">Completed goals stay visible but stop counting as active.</p>
              ) : null}
            </div>
          </div>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-muted-foreground">Topics</legend>
            {fields.length === 0 ? (
              <p className="text-sm text-muted-foreground">No topics yet. Add the first one below.</p>
            ) : null}
            {fields.map((field, index) => (
              <div key={field.id} className="grid gap-2 rounded-lg border p-3 sm:grid-cols-12">
                <div className="space-y-1.5 sm:col-span-5">
                  <Label htmlFor={`topic-title-${field.id}`}>Topic {index + 1}</Label>
                  <Input
                    id={`topic-title-${field.id}`}
                    maxLength={200}
                    {...register(`topics.${index}.title`)}
                  />
                </div>
                <div className="space-y-1.5 sm:col-span-4">
                  <Label htmlFor={`topic-description-${field.id}`}>Detail</Label>
                  <Input
                    id={`topic-description-${field.id}`}
                    maxLength={500}
                    {...register(`topics.${index}.description`)}
                  />
                </div>
                <div className="space-y-1.5 sm:col-span-2">
                  <Label htmlFor={`topic-minutes-${field.id}`}>Minutes</Label>
                  <Input
                    id={`topic-minutes-${field.id}`}
                    type="number"
                    min={0}
                    max={100000}
                    {...register(`topics.${index}.estimatedMinutes`)}
                  />
                </div>
                <div className="flex items-end sm:col-span-1">
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    onClick={() => remove(index)}
                    aria-label={`Remove topic ${index + 1}`}
                  >
                    <X />
                  </Button>
                </div>
              </div>
            ))}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => append({ title: '', description: '', estimatedMinutes: '' })}
            >
              <ListPlus /> Add topic row
            </Button>
          </fieldset>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {goal ? 'Save changes' : 'Create goal'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function TopicRow({
  topic,
  onToggle,
  onRemove,
  busy,
}: {
  topic: TopicResponse
  onToggle: (completed: boolean) => void
  onRemove: () => void
  busy: boolean
}) {
  const inputId = `topic-${topic.id}`
  return (
    <li className="flex items-start gap-3 rounded-lg border p-3">
      <input
        id={inputId}
        type="checkbox"
        className="mt-1 size-4 accent-[var(--color-primary)]"
        checked={topic.completed}
        disabled={busy}
        onChange={(event) => onToggle(event.target.checked)}
      />
      <div className="min-w-0 flex-1">
        <label htmlFor={inputId} className={cn('text-sm font-medium', topic.completed && 'line-through opacity-70')}>
          {topic.title}
        </label>
        {topic.description ? (
          <p className="mt-0.5 text-xs text-muted-foreground">{topic.description}</p>
        ) : null}
        <p className="mt-1 text-xs text-muted-foreground">
          {topic.estimatedMinutes > 0 ? formatMinutes(topic.estimatedMinutes) : 'no estimate'}
          {topic.completedAt ? ` · done ${formatDate(topic.completedAt)}` : ''}
        </p>
      </div>
      <Button
        size="icon-sm"
        variant="ghost"
        className="text-destructive hover:text-destructive"
        onClick={onRemove}
        aria-label={`Remove topic ${topic.title}`}
        disabled={busy}
      >
        <Trash2 />
      </Button>
    </li>
  )
}

function InlineTopicForm({
  goalId,
  onSubmit,
  busy,
  onCancel,
}: {
  goalId: string
  onSubmit: (values: { title: string; description: string; estimatedMinutes: string }) => void
  busy: boolean
  onCancel: () => void
}) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<{ title: string; description: string; estimatedMinutes: string }>({
    defaultValues: { title: '', description: '', estimatedMinutes: '' },
  })

  return (
    <form
      className="grid gap-2 rounded-lg border border-dashed p-3 sm:grid-cols-12"
      noValidate
      onSubmit={handleSubmit((values) => {
        onSubmit(values)
        reset({ title: '', description: '', estimatedMinutes: '' })
      })}
    >
      <div className="space-y-1.5 sm:col-span-5">
        <Label htmlFor={`inline-topic-title-${goalId}`}>New topic</Label>
        <Input
          id={`inline-topic-title-${goalId}`}
          maxLength={200}
          aria-invalid={Boolean(errors.title)}
          {...register('title', { required: 'A topic title is required' })}
        />
        {errors.title ? <p className="text-xs text-destructive">{errors.title.message}</p> : null}
      </div>
      <div className="space-y-1.5 sm:col-span-4">
        <Label htmlFor={`inline-topic-description-${goalId}`}>Detail</Label>
        <Input
          id={`inline-topic-description-${goalId}`}
          maxLength={500}
          {...register('description')}
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor={`inline-topic-minutes-${goalId}`}>Minutes</Label>
        <Input
          id={`inline-topic-minutes-${goalId}`}
          type="number"
          min={0}
          {...register('estimatedMinutes')}
        />
      </div>
      <div className="flex items-end gap-2 sm:col-span-1">
        <Button type="submit" size="sm" loading={busy}>
          Add
        </Button>
        <Button type="button" size="sm" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/* -------------------------------------------------------------------------- */
/* Roadmap proposal                                                            */
/* -------------------------------------------------------------------------- */

interface RoadmapFormValues {
  title: string
  description: string
  category: string
  targetDate: string
  stages: { title: string; description: string; estimatedHours: string; topicsText: string }[]
}

const EMPTY_STAGE = { title: '', description: '', estimatedHours: '', topicsText: '' }

function RoadmapDialog({
  open,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: RoadmapFormValues) => void
}) {
  const { register, control, handleSubmit, reset, formState } = useForm<RoadmapFormValues>({
    defaultValues: {
      title: '',
      description: '',
      category: '',
      targetDate: '',
      stages: [
        { ...EMPTY_STAGE, title: 'Foundations', topicsText: 'Core concepts, terminology' },
        { ...EMPTY_STAGE, title: 'Applied practice', topicsText: 'Hands-on exercises, mini project' },
      ],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'stages' })

  const close = (next: boolean) => {
    if (!next) {
      reset({
        title: '',
        description: '',
        category: '',
        targetDate: '',
        stages: [
          { ...EMPTY_STAGE, title: 'Foundations', topicsText: 'Core concepts, terminology' },
          { ...EMPTY_STAGE, title: 'Applied practice', topicsText: 'Hands-on exercises, mini project' },
        ],
      })
    }
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Confirm the proposed roadmap</DialogTitle>
          <DialogDescription>
            The server generated this roadmap as a proposal. Edit the stages below and confirm only
            the ones you want to keep — removing a stage here drops it from the plan entirely.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-1.5">
            <Label htmlFor="roadmap-title">Roadmap title</Label>
            <Input
              id="roadmap-title"
              required
              maxLength={200}
              aria-invalid={Boolean(formState.errors.title)}
              {...register('title', { required: 'A roadmap title is required' })}
            />
            {formState.errors.title ? (
              <p className="text-xs text-destructive">{formState.errors.title.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="roadmap-description">Description</Label>
            <Textarea
              id="roadmap-description"
              rows={2}
              maxLength={2000}
              {...register('description')}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="roadmap-category">Category</Label>
              <Input id="roadmap-category" maxLength={60} {...register('category')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="roadmap-target">Target date</Label>
              <Input id="roadmap-target" type="date" {...register('targetDate')} />
            </div>
          </div>

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-muted-foreground">Stages to keep</legend>
            {fields.map((field, index) => (
              <div key={field.id} className="space-y-3 rounded-lg border p-3">
                <div className="grid gap-3 sm:grid-cols-12">
                  <div className="space-y-1.5 sm:col-span-6">
                    <Label htmlFor={`stage-title-${field.id}`}>Stage {index + 1} title</Label>
                    <Input id={`stage-title-${field.id}`} maxLength={200} {...register(`stages.${index}.title`)} />
                  </div>
                  <div className="space-y-1.5 sm:col-span-4">
                    <Label htmlFor={`stage-hours-${field.id}`}>Estimated hours</Label>
                    <Input
                      id={`stage-hours-${field.id}`}
                      type="number"
                      min={0}
                      step={0.5}
                      {...register(`stages.${index}.estimatedHours`)}
                    />
                  </div>
                  <div className="flex items-end sm:col-span-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="text-destructive hover:text-destructive"
                      onClick={() => remove(index)}
                    >
                      <X /> Drop stage
                    </Button>
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor={`stage-description-${field.id}`}>Stage detail</Label>
                  <Input
                    id={`stage-description-${field.id}`}
                    maxLength={500}
                    {...register(`stages.${index}.description`)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor={`stage-topics-${field.id}`}>Topics (comma separated)</Label>
                  <Input
                    id={`stage-topics-${field.id}`}
                    placeholder="HTTP semantics, caching, versioning"
                    {...register(`stages.${index}.topicsText`)}
                  />
                </div>
              </div>
            ))}
            <Button type="button" size="sm" variant="outline" onClick={() => append({ ...EMPTY_STAGE })}>
              <ListPlus /> Add stage
            </Button>
          </fieldset>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              <Sparkles /> Confirm roadmap
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/* -------------------------------------------------------------------------- */
/* Study log                                                                   */
/* -------------------------------------------------------------------------- */

interface SessionFormValues {
  learningGoalId: string
  topicId: string
  startedAt: string
  endedAt: string
  minutes: string
  notes: string
  quizScore: string
}

function StudyTab({ goals }: { goals: LearningGoalResponse[] }) {
  const [days, setDays] = useState<number>(30)
  const now = new Date()

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<SessionFormValues>({
    defaultValues: {
      learningGoalId: '',
      topicId: '',
      startedAt: toLocalDateTimeInput(now.toISOString()),
      endedAt: toLocalDateTimeInput(now.toISOString()),
      minutes: '',
      notes: '',
      quizScore: '',
    },
  })

  const goalId = watch('learningGoalId')

  const sessions = useQuery({
    queryKey: ['learning', 'sessions', 20],
    queryFn: () => learningApi.sessions(20),
  })

  const study = useQuery({
    queryKey: ['learning', 'study', days],
    queryFn: () => learningApi.study(days),
  })

  const goalDetail = useQuery({
    queryKey: ['learning', 'goal', goalId],
    queryFn: () => learningApi.goal(goalId),
    enabled: goalId !== '',
  })

  const sessionMutation = useMutation({
    mutationFn: (values: SessionFormValues) =>
      learningApi.startSession({
        learningGoalId: values.learningGoalId || undefined,
        topicId: values.topicId || undefined,
        startedAt: fromLocalDateTimeInput(values.startedAt) ?? new Date().toISOString(),
        endedAt: fromLocalDateTimeInput(values.endedAt),
        minutes: values.minutes === '' ? undefined : Number(values.minutes),
        notes: values.notes.trim() || undefined,
        quizScore: values.quizScore === '' ? undefined : Number(values.quizScore),
      }),
    onSuccess: () => {
      toast.success('Study session recorded')
      reset({
        learningGoalId: '',
        topicId: '',
        startedAt: toLocalDateTimeInput(new Date().toISOString()),
        endedAt: toLocalDateTimeInput(new Date().toISOString()),
        minutes: '',
        notes: '',
        quizScore: '',
      })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const chartData = study.data ?? []

  return (
    <div className="space-y-4">
      <SectionCard title="Log a study session" description="Minutes are optional when the start and end timestamps differ.">
        <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit((v) => sessionMutation.mutate(v))}>
          <div className="space-y-1.5">
            <Label>Goal</Label>
            <Controller
              control={control}
              name="learningGoalId"
              render={({ field }) => (
                <Select
                  value={field.value === '' ? NONE : field.value}
                  onValueChange={(value) => {
                    field.onChange(value === NONE ? '' : value)
                    setValue('topicId', '')
                  }}
                >
                  <SelectTrigger aria-label="Learning goal">
                    <SelectValue placeholder="Pick a goal" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>No goal</SelectItem>
                    {goals.map((goal) => (
                      <SelectItem key={goal.id} value={goal.id}>
                        {goal.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="session-topic">Topic</Label>
            <Controller
              control={control}
              name="topicId"
              render={({ field }) => (
                <Select
                  value={field.value === '' ? NONE : field.value}
                  onValueChange={(value) => field.onChange(value === NONE ? '' : value)}
                  disabled={goalId === ''}
                >
                  <SelectTrigger id="session-topic" aria-label="Topic">
                    <SelectValue placeholder={goalId === '' ? 'Pick a goal first' : 'Optional'} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>No topic</SelectItem>
                    {(goalDetail.data?.topics ?? []).map((topic) => (
                      <SelectItem key={topic.id} value={topic.id}>
                        {topic.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="session-started">Started</Label>
            <Input
              id="session-started"
              type="datetime-local"
              required
              aria-invalid={Boolean(errors.startedAt)}
              {...register('startedAt', { required: 'A start time is required' })}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="session-ended">Ended</Label>
            <Input id="session-ended" type="datetime-local" {...register('endedAt')} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="session-minutes">Minutes</Label>
            <Input
              id="session-minutes"
              type="number"
              min={0}
              step={1}
              {...register('minutes')}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="session-quiz">Quiz score (0–100)</Label>
            <Input
              id="session-quiz"
              type="number"
              min={0}
              max={100}
              {...register('quizScore')}
            />
          </div>

          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="session-notes">Notes</Label>
            <Textarea id="session-notes" rows={2} maxLength={2000} {...register('notes')} />
          </div>

          <div className="sm:col-span-2">
            <Button type="submit" loading={sessionMutation.isPending}>
              <Plus /> Record session
            </Button>
          </div>
        </form>
      </SectionCard>

      <SectionCard
        title="Study minutes"
        description="Daily minutes against topics completed."
        action={
          <div className="flex gap-1" role="group" aria-label="Study window">
            {STUDY_WINDOWS.map((window) => (
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
        {study.isError ? (
          <ErrorPanel message={study.error.message} onRetry={() => void study.refetch()} />
        ) : study.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : chartData.length === 0 ? (
          <EmptyState
            icon={<CalendarClock />}
            title="No study activity in this window"
            description="Record a session above and the daily series will appear here."
          />
        ) : (
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  tickFormatter={(value: string) => String(value).slice(5)}
                  interval="preserveStartEnd"
                  minTickGap={24}
                />
                <YAxis
                  yAxisId="minutes"
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  tickFormatter={(value: number) => formatMinutes(value)}
                />
                <YAxis
                  yAxisId="topics"
                  orientation="right"
                  allowDecimals={false}
                  tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                />
                <Tooltip contentStyle={TOOLTIP_STYLE} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Line
                  yAxisId="minutes"
                  type="monotone"
                  dataKey="minutes"
                  name="Minutes"
                  stroke="var(--color-primary)"
                  strokeWidth={2}
                  dot={false}
                />
                <Line
                  yAxisId="topics"
                  type="monotone"
                  dataKey="topicsCompleted"
                  name="Topics completed"
                  stroke="var(--color-success)"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </SectionCard>

      <SectionCard title="Recent sessions" description="The 20 most recent sessions reported by the server.">
        {sessions.isError ? (
          <ErrorPanel message={sessions.error.message} onRetry={() => void sessions.refetch()} />
        ) : sessions.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : (sessions.data ?? []).length === 0 ? (
          <EmptyState
            icon={<GraduationCap />}
            title="No sessions yet"
            description="Log your first study block above to start the history."
          />
        ) : (
          <ul className="divide-y">
            {(sessions.data ?? []).map((session) => (
              <li key={session.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    {session.learningGoalTitle || 'Unassigned goal'}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {formatDate(session.startedAt)}
                    {session.topicTitle ? ` · ${session.topicTitle}` : ''}
                    {session.notes ? ` · ${session.notes}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2 text-xs">
                  <span className="tabular-nums text-muted-foreground">{formatMinutes(session.minutes)}</span>
                  {session.quizScore > 0 ? (
                    <StatusBadge value="INFO" label={`quiz ${session.quizScore}`} />
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </SectionCard>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Resources and skills                                                        */
/* -------------------------------------------------------------------------- */

interface ResourceFormValues {
  title: string
  url: string
  resourceType: ResourceType
  learningGoalId: string
  completed: boolean
}

function ResourceForm({
  goals,
  onSubmit,
  busy,
}: {
  goals: LearningGoalResponse[]
  onSubmit: (values: ResourceFormValues) => void
  busy: boolean
}) {
  const { register, control, handleSubmit, reset, formState } = useForm<ResourceFormValues>({
    defaultValues: { title: '', url: '', resourceType: 'ARTICLE', learningGoalId: '', completed: false },
  })

  return (
    <form
      className="grid gap-4 sm:grid-cols-2"
      noValidate
      onSubmit={handleSubmit((values) => {
        onSubmit(values)
        reset({ title: '', url: '', resourceType: 'ARTICLE', learningGoalId: '', completed: false })
      })}
    >
      <div className="space-y-1.5">
        <Label htmlFor="resource-title">Title</Label>
        <Input
          id="resource-title"
          required
          maxLength={200}
          aria-invalid={Boolean(formState.errors.title)}
          {...register('title', { required: 'A title is required' })}
        />
        {formState.errors.title ? (
          <p className="text-xs text-destructive">{formState.errors.title.message}</p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="resource-url">URL</Label>
        <Input id="resource-url" type="url" placeholder="https://" {...register('url')} />
      </div>

      <div className="space-y-1.5">
        <Label>Type</Label>
        <Controller
          control={control}
          name="resourceType"
          render={({ field }) => (
            <Select value={field.value} onValueChange={(value) => field.onChange(value as ResourceType)}>
              <SelectTrigger aria-label="Resource type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RESOURCE_TYPES.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>

      <div className="space-y-1.5">
        <Label>Attached goal</Label>
        <Controller
          control={control}
          name="learningGoalId"
          render={({ field }) => (
            <Select
              value={field.value === '' ? NONE : field.value}
              onValueChange={(value) => field.onChange(value === NONE ? '' : value)}
            >
              <SelectTrigger aria-label="Attached goal">
                <SelectValue placeholder="Not attached" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>Not attached</SelectItem>
                {goals.map((goal) => (
                  <SelectItem key={goal.id} value={goal.id}>
                    {goal.title}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>

      <label className="flex items-center gap-2 text-sm sm:col-span-2">
        <input
          type="checkbox"
          className="size-4 accent-[var(--color-primary)]"
          {...register('completed')}
        />
        Mark as already finished
      </label>

      <div className="sm:col-span-2">
        <Button type="submit" loading={busy}>
          <Plus /> Add resource
        </Button>
      </div>
    </form>
  )
}

interface SkillFormValues {
  name: string
  category: string
  proficiency: string
}

function SkillsTab({ onRemove }: { onRemove: (id: string, name: string) => void }) {
  const queryClient = useQueryClient()
  const skills = useQuery({ queryKey: ['learning', 'skills'], queryFn: () => learningApi.skills() })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<SkillFormValues>({ defaultValues: { name: '', category: '', proficiency: '3' } })

  const createMutation = useMutation({
    mutationFn: (values: SkillFormValues) =>
      learningApi.createSkill({
        name: values.name.trim(),
        category: values.category.trim() || undefined,
        proficiency: Number(values.proficiency) || 1,
      }),
    onSuccess: () => {
      toast.success('Skill added')
      void queryClient.invalidateQueries({ queryKey: ['learning'] })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  return (
    <div className="space-y-4">
      <SectionCard title="Add a skill" description="Proficiency runs from 1 (novice) to 5 (expert).">
        <form
          className="grid gap-4 sm:grid-cols-4"
          noValidate
          onSubmit={handleSubmit((values) => {
            createMutation.mutate(values)
            reset({ name: '', category: '', proficiency: '3' })
          })}
        >
          <div className="space-y-1.5">
            <Label htmlFor="skill-name">Name</Label>
            <Input
              id="skill-name"
              required
              maxLength={120}
              aria-invalid={Boolean(errors.name)}
              {...register('name', { required: 'A skill name is required' })}
            />
            {errors.name ? <p className="text-xs text-destructive">{errors.name.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="skill-category">Category</Label>
            <Input id="skill-category" maxLength={60} {...register('category')} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="skill-proficiency">Proficiency</Label>
            <Input
              id="skill-proficiency"
              type="number"
              min={1}
              max={5}
              {...register('proficiency')}
            />
          </div>
          <div className="flex items-end">
            <Button type="submit" loading={createMutation.isPending}>
              <Plus /> Add skill
            </Button>
          </div>
        </form>
      </SectionCard>

      <SectionCard title="Skill meters">
        {skills.isError ? (
          <ErrorPanel message={skills.error.message} onRetry={() => void skills.refetch()} />
        ) : skills.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : (skills.data ?? []).length === 0 ? (
          <EmptyState
            icon={<GraduationCap />}
            title="No skills tracked"
            description="Add the skills you are building so goals can link to them."
          />
        ) : (
          <ul className="space-y-3">
            {(skills.data ?? []).map((skill) => (
              <li key={skill.id} className="space-y-1.5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium">{skill.name}</p>
                    {skill.category ? (
                      <p className="text-xs text-muted-foreground">{skill.category}</p>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs tabular-nums text-muted-foreground">
                      {skill.proficiency}/5
                    </span>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      className="text-destructive hover:text-destructive"
                      onClick={() => onRemove(skill.id, skill.name)}
                      aria-label={`Delete skill ${skill.name}`}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </div>
                <Progress
                  value={(skill.proficiency / 5) * 100}
                  indicatorClassName="bg-violet-400"
                  aria-label={`${skill.name} proficiency`}
                />
              </li>
            ))}
          </ul>
        )}
      </SectionCard>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Page                                                                        */
/* -------------------------------------------------------------------------- */

export default function LearningPage() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['learning'] })

  const [tab, setTab] = useState<TabKey>('goals')
  const [status, setStatus] = useState<string>(ALL)
  const [expandedId, setExpandedId] = useState<string>()
  const [inlineTopicFor, setInlineTopicFor] = useState<string>()
  const [goalDialog, setGoalDialog] = useState<{ open: boolean; goal: LearningGoalResponse | null }>({
    open: false,
    goal: null,
  })
  const [roadmapOpen, setRoadmapOpen] = useState(false)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const goals = useQuery({
    queryKey: ['learning', 'goals', status],
    queryFn: () => learningApi.goals(status === ALL ? undefined : status),
  })

  const goalDetail = useQuery({
    queryKey: ['learning', 'goal', expandedId],
    queryFn: () => learningApi.goal(expandedId ?? ''),
    enabled: expandedId !== undefined,
  })

  const resources = useQuery({
    queryKey: ['learning', 'resources'],
    queryFn: () => learningApi.resources(),
  })

  const statistics = useQuery({
    queryKey: ['learning', 'statistics'],
    queryFn: () => learningApi.statistics(),
  })

  const saveGoal = useMutation({
    mutationFn: (values: GoalFormValues) => {
      const topics: TopicRequest[] = values.topics
        .filter((topic) => topic.title.trim())
        .map((topic, index) => ({
          title: topic.title.trim(),
          description: topic.description.trim() || undefined,
          estimatedMinutes: topic.estimatedMinutes === '' ? undefined : Number(topic.estimatedMinutes),
          position: index,
        }))
      const body: LearningGoalRequest = {
        title: values.title.trim(),
        description: values.description.trim() || undefined,
        category: values.category.trim() || undefined,
        status: values.status,
        targetDate: values.targetDate
          ? fromLocalDateTimeInput(`${values.targetDate}T12:00:00`)
          : undefined,
        topics: topics.length > 0 ? topics : undefined,
      }
      return goalDialog.goal
        ? learningApi.updateGoal(goalDialog.goal.id, body)
        : learningApi.createGoal(body)
    },
    onSuccess: () => {
      toast.success(goalDialog.goal ? 'Goal updated' : 'Goal created')
      setGoalDialog({ open: false, goal: null })
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const completeTopic = useMutation({
    mutationFn: (vars: { topicId: string; completed: boolean }) =>
      learningApi.completeTopic(vars.topicId, vars.completed),
    onSuccess: () => {
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const addTopic = useMutation({
    mutationFn: (vars: { goalId: string; title: string; description: string; estimatedMinutes: string }) =>
      learningApi.addTopic(vars.goalId, {
        title: vars.title.trim(),
        description: vars.description.trim() || undefined,
        estimatedMinutes: vars.estimatedMinutes === '' ? undefined : Number(vars.estimatedMinutes),
      }),
    onSuccess: () => {
      toast.success('Topic added')
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const confirmRoadmap = useMutation({
    mutationFn: (values: RoadmapFormValues) => {
      const body: ConfirmRoadmapRequest = {
        title: values.title.trim(),
        description: values.description.trim() || undefined,
        category: values.category.trim() || undefined,
        targetDate: values.targetDate
          ? fromLocalDateTimeInput(`${values.targetDate}T12:00:00`)
          : undefined,
        stages: values.stages
          .filter((stage) => stage.title.trim())
          .map((stage) => ({
            title: stage.title.trim(),
            description: stage.description.trim() || undefined,
            estimatedHours: stage.estimatedHours === '' ? undefined : Number(stage.estimatedHours),
            topics: parseList(stage.topicsText),
          })),
      }
      return learningApi.confirmRoadmap(body)
    },
    onSuccess: (goal) => {
      toast.success(`Roadmap confirmed: ${goal.title}`)
      setRoadmapOpen(false)
      setTab('goals')
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const createResource = useMutation({
    mutationFn: (values: ResourceFormValues) =>
      learningApi.createResource({
        title: values.title.trim(),
        url: values.url.trim() || undefined,
        resourceType: values.resourceType,
        learningGoalId: values.learningGoalId || undefined,
        completed: values.completed,
      }),
    onSuccess: () => {
      toast.success('Resource added')
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeEntity = useMutation({
    mutationFn: (vars: { kind: ConfirmState['kind']; id: string }) => {
      switch (vars.kind) {
        case 'goal':
          return learningApi.removeGoal(vars.id)
        case 'topic':
          return learningApi.removeTopic(vars.id)
        case 'resource':
          return learningApi.removeResource(vars.id)
        case 'skill':
          return learningApi.removeSkill(vars.id)
      }
    },
    onSuccess: () => {
      toast.success('Removed')
      setConfirm(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const goalList = goals.data ?? []
  const stats = statistics.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Learning"
        description="Plan study goals, break them into topics, and log the hours you actually put in."
        actions={
          <>
            <Button variant="outline" onClick={() => setRoadmapOpen(true)}>
              <Sparkles /> Review roadmap
            </Button>
            <Button
              onClick={() => {
                setGoalDialog({ open: true, goal: null })
                setTab('goals')
              }}
            >
              <Plus /> New goal
            </Button>
          </>
        }
      />

      {statistics.isError ? (
        <ErrorPanel message={statistics.error.message} onRetry={() => void statistics.refetch()} />
      ) : statistics.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-24 w-full" />
          ))}
        </div>
      ) : stats ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Active goals" value={stats.activeGoals} />
          <StatCard label="Hours this week" value={formatNumber(stats.hoursThisWeek)} hint={`${stats.minutesThisWeek} minutes`} />
          <StatCard label="Sessions this week" value={stats.sessionsThisWeek} />
          <StatCard
            label="Average quiz score"
            value={stats.averageQuizScore > 0 ? formatPercent(stats.averageQuizScore) : '—'}
            hint={stats.averageQuizScore > 0 ? 'Across recorded quizzes' : 'No quizzes recorded'}
          />
        </div>
      ) : null}

      <Tabs value={tab} onValueChange={(value) => setTab(value as TabKey)}>
        <TabsList className="flex-wrap">
          <TabsTrigger value="goals">Goals</TabsTrigger>
          <TabsTrigger value="study">Study log</TabsTrigger>
          <TabsTrigger value="resources">Resources</TabsTrigger>
          <TabsTrigger value="skills">Skills</TabsTrigger>
          <TabsTrigger value="stats">Statistics</TabsTrigger>
        </TabsList>

        <TabsContent value="goals">
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <Label htmlFor="goal-status-filter" className="text-xs">
              Status
            </Label>
            <div className="w-48">
              <Select value={status} onValueChange={setStatus}>
                <SelectTrigger id="goal-status-filter" aria-label="Filter goals by status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All statuses</SelectItem>
                  {LEARNING_STATUSES.map((option) => (
                    <SelectItem key={option} value={option}>
                      {option.toLowerCase()}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {goals.isError ? (
            <ErrorPanel message={goals.error.message} onRetry={() => void goals.refetch()} />
          ) : goals.isPending ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-40 w-full" />
              ))}
            </div>
          ) : goalList.length === 0 ? (
            <EmptyState
              icon={<BookOpen />}
              title={status === ALL ? 'No learning goals yet' : `No ${status.toLowerCase()} goals`}
              description="Add a goal with a topic checklist, or confirm a roadmap the server proposed."
              action={
                <div className="flex gap-2">
                  <Button
                    onClick={() => {
                      setGoalDialog({ open: true, goal: null })
                    }}
                  >
                    <Plus /> New goal
                  </Button>
                  <Button variant="outline" onClick={() => setRoadmapOpen(true)}>
                    <Sparkles /> Review roadmap
                  </Button>
                </div>
              }
            />
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              {goalList.map((goal) => {
                const expanded = expandedId === goal.id
                return (
                  <article key={goal.id} className="rounded-xl border bg-card p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <h2 className="truncate font-medium">{goal.title}</h2>
                        <div className="flex flex-wrap items-center gap-1.5">
                          <StatusBadge value={goal.status} label={goal.status.toLowerCase()} />
                          {goal.category ? (
                            <span className="text-xs text-muted-foreground">{goal.category}</span>
                          ) : null}
                          {goal.aiConfirmed ? (
                            <StatusBadge value="INFO" label="ai confirmed" />
                          ) : null}
                        </div>
                      </div>
                      <span className="shrink-0 text-sm font-medium tabular-nums">
                        {formatPercent(goal.progress)}
                      </span>
                    </div>

                    {goal.description ? (
                      <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">{goal.description}</p>
                    ) : null}

                    <div className="mt-3">
                      <Progress
                        value={goal.progress}
                        indicatorClassName={progressColor('learning')}
                        aria-label={`${goal.title} progress`}
                      />
                    </div>

                    <dl className="mt-3 grid grid-cols-3 gap-2 text-xs">
                      <div className="rounded-md border p-2">
                        <dt className="text-muted-foreground">Hours</dt>
                        <dd className="tabular-nums">{formatNumber(goal.hoursSpent)}</dd>
                      </div>
                      <div className="rounded-md border p-2">
                        <dt className="text-muted-foreground">Topics</dt>
                        <dd className="tabular-nums">
                          {goal.completedTopicCount}/{goal.topicCount}
                        </dd>
                      </div>
                      <div className="rounded-md border p-2">
                        <dt className="text-muted-foreground">Target</dt>
                        <dd>{formatDate(goal.targetDate)}</dd>
                      </div>
                    </dl>

                    <div className="mt-3 flex flex-wrap items-center gap-2 border-t pt-3">
                      <Button
                        size="sm"
                        variant={expanded ? 'default' : 'outline'}
                        onClick={() => setExpandedId(expanded ? undefined : goal.id)}
                      >
                        {expanded ? 'Hide topics' : 'Topics'}
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setGoalDialog({ open: true, goal })}
                      >
                        <Pencil /> Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-destructive hover:text-destructive"
                        onClick={() =>
                          setConfirm({
                            kind: 'goal',
                            id: goal.id,
                            title: `Delete ${goal.title}?`,
                            description:
                              'The goal, its topics and every logged study session for it are removed.',
                            confirmLabel: 'Delete goal',
                          })
                        }
                      >
                        <Trash2 /> Delete
                      </Button>
                    </div>

                    {expanded ? (
                      <div className="mt-3 space-y-2">
                        {goalDetail.isError ? (
                          <ErrorPanel
                            message={goalDetail.error.message}
                            onRetry={() => void goalDetail.refetch()}
                          />
                        ) : goalDetail.isPending ? (
                          <Skeleton className="h-32 w-full" />
                        ) : (goalDetail.data?.topics ?? []).length === 0 ? (
                          <EmptyState
                            title="No topics on this goal"
                            description="Add the first topic to turn the goal into a checklist."
                          />
                        ) : (
                          <ul className="space-y-2">
                            {(goalDetail.data?.topics ?? []).map((topic) => (
                              <TopicRow
                                key={topic.id}
                                topic={topic}
                                busy={completeTopic.isPending}
                                onToggle={(completed) =>
                                  completeTopic.mutate({ topicId: topic.id, completed })
                                }
                                onRemove={() =>
                                  setConfirm({
                                    kind: 'topic',
                                    id: topic.id,
                                    title: `Remove “${topic.title}”?`,
                                    description: 'The topic and its completion state are deleted.',
                                    confirmLabel: 'Remove topic',
                                  })
                                }
                              />
                            ))}
                          </ul>
                        )}

                        {inlineTopicFor === goal.id ? (
                          <InlineTopicForm
                            goalId={goal.id}
                            busy={addTopic.isPending}
                            onCancel={() => setInlineTopicFor(undefined)}
                            onSubmit={(values) => {
                              addTopic.mutate({ goalId: goal.id, ...values })
                              setInlineTopicFor(undefined)
                            }}
                          />
                        ) : (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setInlineTopicFor(goal.id)}
                          >
                            <Plus /> Add topic
                          </Button>
                        )}
                      </div>
                    ) : null}
                  </article>
                )
              })}
            </div>
          )}
        </div>
        </TabsContent>

        <TabsContent value="study">
          <StudyTab goals={goalList} />
        </TabsContent>

        <TabsContent value="resources">
        <div className="space-y-4">
          <SectionCard title="Add a resource" description="Attach reading or video to a goal so it shows up with the plan.">
            <ResourceForm goals={goalList} busy={createResource.isPending} onSubmit={(values) => createResource.mutate(values)} />
          </SectionCard>

          <SectionCard title="Saved resources">
            {resources.isError ? (
              <ErrorPanel message={resources.error.message} onRetry={() => void resources.refetch()} />
            ) : resources.isPending ? (
              <Skeleton className="h-40 w-full" />
            ) : (resources.data ?? []).length === 0 ? (
              <EmptyState
                icon={<BookOpen />}
                title="No resources yet"
                description="Save the articles, videos and books you are working through."
              />
            ) : (
              <ul className="divide-y">
                {(resources.data ?? []).map((resource) => {
                  const goal = goalList.find((item) => item.id === resource.learningGoalId)
                  return (
                    <li key={resource.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                      <div className="min-w-0">
                        {resource.url ? (
                          <a
                            href={resource.url}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-sm font-medium text-primary hover:underline"
                          >
                            {resource.title}
                            <ExternalLink className="size-3.5" aria-hidden />
                          </a>
                        ) : (
                          <p className="text-sm font-medium">{resource.title}</p>
                        )}
                        <p className="text-xs text-muted-foreground">
                          {resource.resourceType.toLowerCase().replace(/_/g, ' ')}
                          {goal ? ` · ${goal.title}` : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        {resource.completed ? <StatusBadge value="COMPLETED" label="done" /> : null}
                        <Button
                          size="icon-sm"
                          variant="ghost"
                          className="text-destructive hover:text-destructive"
                          onClick={() =>
                            setConfirm({
                              kind: 'resource',
                              id: resource.id,
                              title: `Remove “${resource.title}”?`,
                              description: 'The resource is removed from your library.',
                              confirmLabel: 'Remove resource',
                            })
                          }
                          aria-label={`Remove resource ${resource.title}`}
                        >
                          <Trash2 />
                        </Button>
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </SectionCard>
        </div>
        </TabsContent>

        <TabsContent value="skills">
        <SkillsTab
          onRemove={(id, name) =>
            setConfirm({
              kind: 'skill',
              id,
              title: `Delete ${name}?`,
              description: 'Goals linked to this skill will lose the link.',
              confirmLabel: 'Delete skill',
            })
          }
        />
        </TabsContent>

        <TabsContent value="stats">
        <div className="space-y-4">
          {statistics.isError ? (
            <ErrorPanel message={statistics.error.message} onRetry={() => void statistics.refetch()} />
          ) : statistics.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : stats ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <StatCard label="Active goals" value={stats.activeGoals} />
                <StatCard label="Hours this week" value={formatNumber(stats.hoursThisWeek)} />
                <StatCard label="Hours total" value={formatNumber(stats.hoursTotal)} />
                <StatCard label="Minutes this week" value={formatNumber(stats.minutesThisWeek)} />
                <StatCard label="Sessions this week" value={stats.sessionsThisWeek} />
                <StatCard
                  label="Average quiz score"
                  value={stats.averageQuizScore > 0 ? formatPercent(stats.averageQuizScore) : '—'}
                />
              </div>
              <SectionCard title="Top goals" description="The goals the server ranks highest for effort.">
                {stats.topGoals.length === 0 ? (
                  <EmptyState
                    icon={<GraduationCap />}
                    title="No ranked goals yet"
                    description="Rankings appear once goals carry topics and logged study time."
                  />
                ) : (
                  <ul className="space-y-3">
                    {stats.topGoals.map((goal) => (
                      <li key={goal.id} className="space-y-1.5">
                        <div className="flex items-center justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium">{goal.title}</p>
                            <p className="text-xs text-muted-foreground">
                              {formatNumber(goal.hoursSpent)}h · {goal.completedTopicCount}/{goal.topicCount}{' '}
                              topics · {formatDate(goal.targetDate)}
                            </p>
                          </div>
                          <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                            {formatPercent(goal.progress)}
                          </span>
                        </div>
                        <Progress value={goal.progress} indicatorClassName={progressColor('learning')} />
                      </li>
                    ))}
                  </ul>
                )}
              </SectionCard>
            </>
          ) : null}
        </div>
        </TabsContent>
      </Tabs>

      <GoalDialog
        open={goalDialog.open}
        goal={goalDialog.goal}
        busy={saveGoal.isPending}
        onOpenChange={(open) => setGoalDialog({ open, goal: open ? goalDialog.goal : null })}
        onSubmit={(values) => saveGoal.mutate(values)}
      />

      <RoadmapDialog
        open={roadmapOpen}
        busy={confirmRoadmap.isPending}
        onOpenChange={setRoadmapOpen}
        onSubmit={(values) => confirmRoadmap.mutate(values)}
      />

      <ConfirmDialog
        state={confirm}
        busy={removeEntity.isPending}
        onOpenChange={(open) => {
          if (!open) setConfirm(null)
        }}
        onConfirm={() => {
          if (confirm) removeEntity.mutate({ kind: confirm.kind, id: confirm.id })
        }}
      />
    </div>
  )
}