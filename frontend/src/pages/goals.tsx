import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bot,
  ChevronDown,
  ChevronRight,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Target,
  Trash2,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { goalApi } from '@/api/endpoints'
import { PageHeader, SectionCard } from '@/components/page-parts'
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
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge, progressColor } from '@/lib/badges'
import { formatDate, fromLocalDateTimeInput, titleCase, toLocalDateTimeInput } from '@/lib/format'
import type {
  GoalQuery,
  GoalRequest,
  GoalResponse,
  GoalStatus,
  GoalType,
  MilestoneRequest,
  MilestoneResponse,
  Priority,
} from '@/types/api'

const GOAL_STATUSES: readonly GoalStatus[] = ['ACTIVE', 'ACHIEVED', 'PAUSED', 'ARCHIVED', 'CANCELLED']
const GOAL_TYPES: readonly GoalType[] = ['LONG_TERM', 'SHORT_TERM']
const PRIORITY_OPTIONS: readonly Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const ALL = '__all__'
const NONE = '__none__'
const PAGE_SIZES = ['10', '25', '50'] as const

interface MilestoneDraft {
  title: string
  description: string
  dueDate: string
  completed: boolean
  progress: number
}

interface GoalDraft {
  title: string
  description: string
  category: string
  goalType: GoalType
  targetDate: string
  progress: number
  priority: Priority
  status: GoalStatus
  color: string
  parentGoalId: string
  position: string
  milestones: MilestoneDraft[]
}

const EMPTY_DRAFT: GoalDraft = {
  title: '',
  description: '',
  category: '',
  goalType: 'LONG_TERM',
  targetDate: '',
  progress: 0,
  priority: 'MEDIUM',
  status: 'ACTIVE',
  color: '',
  parentGoalId: '',
  position: '',
  milestones: [],
}

function emptyMilestoneDraft(): MilestoneDraft {
  return {
    title: '',
    description: '',
    dueDate: '',
    completed: false,
    progress: 0,
  }
}

function toDraft(goal: GoalResponse | null): GoalDraft {
  if (!goal) return EMPTY_DRAFT
  return {
    title: goal.title,
    description: goal.description ?? '',
    category: goal.category ?? '',
    goalType: goal.goalType ?? 'LONG_TERM',
    targetDate: toLocalDateTimeInput(goal.targetDate),
    progress: goal.progress ?? 0,
    priority: goal.priority ?? 'MEDIUM',
    status: goal.status ?? 'ACTIVE',
    color: goal.color ?? '',
    parentGoalId: goal.parentGoalId ?? '',
    position: String(goal.position ?? 0),
    milestones: (goal.milestones ?? []).map((milestone) => ({
      title: milestone.title,
      description: milestone.description ?? '',
      dueDate: toLocalDateTimeInput(milestone.dueDate),
      completed: milestone.completed,
      progress: milestone.progress ?? 0,
    })),
  }
}

function milestoneRequest(draft: MilestoneDraft, index: number): MilestoneRequest {
  return {
    title: draft.title.trim(),
    description: draft.description.trim() || undefined,
    dueDate: fromLocalDateTimeInput(draft.dueDate),
    completed: draft.completed,
    progress: draft.progress,
    position: index,
  }
}

function toRequest(draft: GoalDraft): GoalRequest {
  const milestones = draft.milestones
    .filter((milestone) => milestone.title.trim().length > 0)
    .map(milestoneRequest)
  return {
    title: draft.title.trim(),
    description: draft.description.trim() || undefined,
    category: draft.category.trim() || undefined,
    goalType: draft.goalType,
    targetDate: fromLocalDateTimeInput(draft.targetDate),
    progress: draft.progress,
    priority: draft.priority,
    status: draft.status,
    color: draft.color.trim() || undefined,
    parentGoalId: draft.parentGoalId || undefined,
    position: draft.position.trim() ? Number(draft.position) : undefined,
    milestones: milestones.length ? milestones : undefined,
  }
}

function MilestoneEditor({
  milestone,
  busy,
  onSave,
  onRemove,
}: {
  milestone: MilestoneResponse
  busy: boolean
  onSave: (body: MilestoneRequest) => void
  onRemove: () => void
}) {
  const [progress, setProgress] = useState(milestone.progress ?? 0)

  useEffect(() => {
    setProgress(milestone.progress ?? 0)
  }, [milestone.progress])

  const dirty = progress !== (milestone.progress ?? 0)
  const late = Boolean(milestone.dueDate) && !milestone.completed && new Date(milestone.dueDate).getTime() < Date.now()

  return (
    <li className="space-y-3 rounded-md border p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{milestone.title}</p>
          <p className="text-xs text-muted-foreground">
            {milestone.dueDate ? `Due ${formatDate(milestone.dueDate)}` : 'No due date'}
            {late ? ' · overdue' : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="inline-flex items-center gap-2 text-xs">
            <input
              type="checkbox"
              className="size-4 accent-[var(--color-primary)]"
              checked={milestone.completed}
              disabled={busy}
              onChange={() =>
                onSave({
                  title: milestone.title,
                  description: milestone.description || undefined,
                  dueDate: milestone.dueDate || undefined,
                  completed: !milestone.completed,
                  progress: milestone.completed ? progress : 100,
                  position: milestone.position,
                })
              }
            />
            Done
          </label>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`Remove milestone ${milestone.title}`}
            disabled={busy}
            onClick={onRemove}
          >
            <X />
          </Button>
        </div>
      </div>

      <div className="space-y-1.5">
        <div className="flex items-center justify-between text-xs">
          <Label htmlFor={`milestone-progress-${milestone.id}`}>Progress</Label>
          <span className="tabular-nums text-muted-foreground">{progress}%</span>
        </div>
        <input
          id={`milestone-progress-${milestone.id}`}
          type="range"
          min={0}
          max={100}
          step={5}
          value={progress}
          onChange={(event) => setProgress(Number(event.target.value))}
          className="w-full accent-[var(--color-primary)]"
        />
        {dirty ? (
          <Button
            size="sm"
            variant="secondary"
            loading={busy}
            onClick={() =>
              onSave({
                title: milestone.title,
                description: milestone.description || undefined,
                dueDate: milestone.dueDate || undefined,
                completed: progress >= 100 ? true : milestone.completed,
                progress,
                position: milestone.position,
              })
            }
          >
            Save milestone progress
          </Button>
        ) : null}
      </div>
    </li>
  )
}

export default function GoalsPage() {
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

  const q = searchParams.get('q') ?? ''
  const statusParam = searchParams.get('status') ?? ''
  const status = GOAL_STATUSES.find((value) => value === statusParam)
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0)
  const size = Math.max(1, Number(searchParams.get('size') ?? '25') || 25)

  const [searchDraft, setSearchDraft] = useState(q)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingGoal, setEditingGoal] = useState<GoalResponse | null>(null)
  const [draft, setDraft] = useState<GoalDraft>(EMPTY_DRAFT)
  const [editorError, setEditorError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [deletingGoal, setDeletingGoal] = useState<GoalResponse | null>(null)
  const [removingMilestone, setRemovingMilestone] = useState<{ goalId: string; milestone: MilestoneResponse } | null>(null)
  const [newMilestone, setNewMilestone] = useState({ title: '', dueDate: '' })
  const [progressDraft, setProgressDraft] = useState<{ progress: number; status: GoalStatus } | null>(null)

  useEffect(() => {
    setSearchDraft(q)
  }, [q])

  useEffect(() => {
    if (searchDraft === q) return undefined
    const timer = window.setTimeout(() => {
      patchParams({ q: searchDraft.trim() || null, page: '0' })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [searchDraft, q, patchParams])

  const goalQuery = useMemo<GoalQuery>(
    () => ({ q: q || undefined, status, page, size }),
    [q, status, page, size],
  )

  const listQuery = useQuery({
    queryKey: ['goals', 'list', goalQuery],
    queryFn: () => goalApi.list(goalQuery),
  })

  const activeQuery = useQuery({
    queryKey: ['goals', 'active'],
    queryFn: () => goalApi.active(),
  })

  const detailQuery = useQuery({
    queryKey: ['goals', 'detail', expandedId],
    queryFn: () => goalApi.get(expandedId ?? ''),
    enabled: Boolean(expandedId),
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['goals'] }),
      queryClient.invalidateQueries({ queryKey: ['tasks'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: (request: GoalRequest) => goalApi.create(request),
    onSuccess: async (goal) => {
      closeEditor()
      await invalidate()
      toast.success('Goal created', { description: goal.title })
    },
    onError: (error) => applyFormError(error),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: GoalRequest }) => goalApi.update(id, request),
    onSuccess: async (goal) => {
      closeEditor()
      await invalidate()
      toast.success('Goal updated', { description: goal.title })
    },
    onError: (error) => applyFormError(error),
  })

  const progressMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { progress: number; status: GoalStatus } }) =>
      goalApi.updateProgress(id, body),
    onSuccess: async (goal) => {
      setProgressDraft({ progress: goal.progress, status: goal.status })
      await invalidate()
      toast.success('Progress updated', { description: goal.title })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => goalApi.remove(id),
    onSuccess: async () => {
      setDeletingGoal(null)
      setExpandedId(null)
      await invalidate()
      toast.success('Goal deleted')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const addMilestoneMutation = useMutation({
    mutationFn: ({ goalId, body }: { goalId: string; body: MilestoneRequest }) =>
      goalApi.addMilestone(goalId, body),
    onSuccess: async () => {
      setNewMilestone({ title: '', dueDate: '' })
      await invalidate()
      toast.success('Milestone added')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const updateMilestoneMutation = useMutation({
    mutationFn: ({
      goalId,
      milestoneId,
      body,
    }: {
      goalId: string
      milestoneId: string
      body: MilestoneRequest
    }) => goalApi.updateMilestone(goalId, milestoneId, body),
    onSuccess: async () => {
      await invalidate()
      toast.success('Milestone updated')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeMilestoneMutation = useMutation({
    mutationFn: ({ goalId, milestoneId }: { goalId: string; milestoneId: string }) =>
      goalApi.removeMilestone(goalId, milestoneId),
    onSuccess: async () => {
      setRemovingMilestone(null)
      await invalidate()
      toast.success('Milestone removed')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  function applyFormError(error: unknown) {
    const normalised = toNormalisedError(error)
    setEditorError(normalised.message)
    setFieldErrors(normalised.fieldErrors)
  }

  function closeEditor() {
    setEditorOpen(false)
    setEditingGoal(null)
    setEditorError(undefined)
    setFieldErrors({})
  }

  function openCreate() {
    setEditingGoal(null)
    setDraft(EMPTY_DRAFT)
    setEditorError(undefined)
    setFieldErrors({})
    setEditorOpen(true)
  }

  function openEdit(goal: GoalResponse) {
    setEditingGoal(goal)
    setDraft(toDraft(goal))
    setEditorError(undefined)
    setFieldErrors({})
    setEditorOpen(true)
  }

  const goals = listQuery.data?.content ?? []
  const activeGoals = activeQuery.data ?? []
  const expandedGoal = expandedId ? (detailQuery.data ?? null) : null

  const setDraftField = <K extends keyof GoalDraft>(key: K, value: GoalDraft[K]) =>
    setDraft((current) => ({ ...current, [key]: value }))

  const setMilestone = (index: number, patch: Partial<MilestoneDraft>) =>
    setDraft((current) => ({
      ...current,
      milestones: current.milestones.map((milestone, position) =>
        position === index ? { ...milestone, ...patch } : milestone,
      ),
    }))

  const submitGoal = (event: FormEvent) => {
    event.preventDefault()
    const request = toRequest(draft)
    if (editingGoal) updateMutation.mutate({ id: editingGoal.id, request })
    else createMutation.mutate(request)
  }

  useEffect(() => {
    setProgressDraft(
      expandedGoal ? { progress: expandedGoal.progress, status: expandedGoal.status } : null,
    )
  }, [expandedGoal])

  const parentOptions = activeGoals.filter(
    (goal) => !expandedGoal || goal.id !== expandedGoal.id,
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Goals"
        description="Outcomes, the milestones that prove them and how far along each one is."
        actions={
          <Button onClick={openCreate}>
            <Plus /> New goal
          </Button>
        }
      />

      <SectionCard
        title="Filters"
        description="Search and status filter are kept in the address bar."
      >
        <div className="flex flex-wrap items-end gap-3">
          <div className="min-w-56 flex-1 space-y-1.5">
            <Label htmlFor="goal-search">Search</Label>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden
              />
              <Input
                id="goal-search"
                className="pl-8"
                placeholder="Title or description"
                value={searchDraft}
                onChange={(event) => setSearchDraft(event.target.value)}
              />
            </div>
          </div>

          <div className="w-48 space-y-1.5">
            <Label htmlFor="goal-status">Status</Label>
            <Select
              value={statusParam || ALL}
              onValueChange={(value) =>
                patchParams({ status: value === ALL ? null : value, page: '0' })
              }
            >
              <SelectTrigger id="goal-status">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Any status</SelectItem>
                {GOAL_STATUSES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {titleCase(option)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="w-32 space-y-1.5">
            <Label htmlFor="goal-size">Per page</Label>
            <Select value={String(size)} onValueChange={(value) => patchParams({ size: value, page: '0' })}>
              <SelectTrigger id="goal-size">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {q || status ? (
            <Button variant="ghost" onClick={() => patchParams({ q: null, status: null, page: '0' })}>
              Clear filters
            </Button>
          ) : null}
        </div>
      </SectionCard>

      {listQuery.isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-32 w-full" />
          ))}
        </div>
      ) : listQuery.error ? (
        <div
          role="alert"
          className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
        >
          <p>{toNormalisedError(listQuery.error).message}</p>
          <Button variant="outline" size="sm" onClick={() => void listQuery.refetch()}>
            <RefreshCw /> Try again
          </Button>
        </div>
      ) : goals.length === 0 ? (
        <EmptyState
          icon={<Target />}
          title={q || status ? 'No goals match this view' : 'No goals yet'}
          description={
            q || status
              ? 'Clear the filters to see the rest of your goals.'
              : 'A goal is an outcome. Break it into milestones so progress is measurable.'
          }
          action={
            q || status ? (
              <Button variant="outline" onClick={() => patchParams({ q: null, status: null, page: '0' })}>
                Clear filters
              </Button>
            ) : (
              <Button onClick={openCreate}>
                <Plus /> New goal
              </Button>
            )
          }
        />
      ) : (
        <ul className="space-y-4">
          {goals.map((goal) => {
            const expanded = expandedId === goal.id
            const shown = expanded ? (expandedGoal ?? goal) : goal
            return (
              <li key={goal.id}>
                <Card>
                  <div className="space-y-4 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1.5">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-base font-semibold">{goal.title}</h2>
                          <StatusBadge value={goal.status} label={titleCase(goal.status)} />
                          <StatusBadge value={goal.priority} label={titleCase(goal.priority)} />
                          {goal.source === 'AI' ? (
                            <Badge variant="info">
                              <Bot className="size-3" aria-hidden /> AI proposed
                            </Badge>
                          ) : null}
                          {goal.aiConfirmed ? (
                            <Badge variant="success">
                              {titleCase(goal.source ?? 'USER')} confirmed
                            </Badge>
                          ) : null}
                          {goal.category ? <Badge variant="outline">{goal.category}</Badge> : null}
                        </div>
                        {goal.description ? (
                          <p className="line-clamp-2 text-sm text-muted-foreground">{goal.description}</p>
                        ) : null}
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setExpandedId(expanded ? null : goal.id)}
                        >
                          {expanded ? (
                            <ChevronDown className="size-4" aria-hidden />
                          ) : (
                            <ChevronRight className="size-4" aria-hidden />
                          )}
                          {expanded ? 'Collapse' : 'Manage'}
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => openEdit(goal)}>
                          Edit
                        </Button>
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => setDeletingGoal(goal)}
                        >
                          <Trash2 /> Delete
                        </Button>
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>Progress</span>
                        <span className="tabular-nums">{goal.progress}%</span>
                      </div>
                      <Progress
                        value={goal.progress}
                        aria-label={`${goal.title} progress`}
                        indicatorClassName={progressColor(goal.category)}
                      />
                    </div>

                    <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
                      <div>
                        <dt className="text-muted-foreground">Target date</dt>
                        <dd className="font-medium">{formatDate(goal.targetDate)}</dd>
                      </div>
                      <div>
                        <dt className="text-muted-foreground">Type</dt>
                        <dd className="font-medium">{titleCase(goal.goalType)}</dd>
                      </div>
                      <div>
                        <dt className="text-muted-foreground">Tasks</dt>
                        <dd className="font-medium tabular-nums">
                          {goal.completedTaskCount}/{goal.taskCount} done
                        </dd>
                      </div>
                      <div>
                        <dt className="text-muted-foreground">Milestones</dt>
                        <dd className="font-medium tabular-nums">
                          {goal.completedMilestoneCount}/{goal.milestoneCount} done
                        </dd>
                      </div>
                    </dl>

                    {expanded ? (
                      <div className="space-y-5 border-t pt-4">
                        <div className="grid gap-4 sm:grid-cols-2">
                          <div className="space-y-1.5">
                            <div className="flex items-center justify-between text-xs">
                              <Label htmlFor={`goal-progress-${goal.id}`}>Progress</Label>
                              <span className="tabular-nums text-muted-foreground">
                                {progressDraft?.progress ?? goal.progress}%
                              </span>
                            </div>
                            <input
                              id={`goal-progress-${goal.id}`}
                              type="range"
                              min={0}
                              max={100}
                              step={5}
                              value={progressDraft?.progress ?? goal.progress}
                              onChange={(event) =>
                                setProgressDraft((current) => ({
                                  progress: Number(event.target.value),
                                  status: current?.status ?? goal.status,
                                }))
                              }
                              className="w-full accent-[var(--color-primary)]"
                            />
                          </div>
                          <div className="space-y-1.5">
                            <Label htmlFor={`goal-status-${goal.id}`}>Status</Label>
                            <Select
                              value={progressDraft?.status ?? goal.status}
                              onValueChange={(value) =>
                                setProgressDraft((current) => ({
                                  progress: current?.progress ?? goal.progress,
                                  status: value as GoalStatus,
                                }))
                              }
                            >
                              <SelectTrigger id={`goal-status-${goal.id}`}>
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {GOAL_STATUSES.map((option) => (
                                  <SelectItem key={option} value={option}>
                                    {titleCase(option)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>
                        </div>

                        <div className="flex items-center gap-2">
                          <Button
                            size="sm"
                            loading={progressMutation.isPending}
                            disabled={
                              !progressDraft ||
                              (progressDraft.progress === shown.progress &&
                                progressDraft.status === shown.status)
                            }
                            onClick={() =>
                              progressDraft &&
                              progressMutation.mutate({ id: goal.id, body: progressDraft })
                            }
                          >
                            <RotateCcw /> Update progress
                          </Button>
                          <Link
                            to={`/app/tasks?q=&goalId=${encodeURIComponent(goal.id)}`}
                            className="text-xs text-primary hover:underline"
                          >
                            View linked tasks
                          </Link>
                        </div>

                        <div className="space-y-3">
                          <p className="text-sm font-medium">Milestones</p>
                          {detailQuery.isPending ? (
                            <Spinner label="Loading milestones" />
                          ) : (shown.milestones ?? []).length === 0 ? (
                            <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                              No milestones yet. Add the first checkpoint below.
                            </p>
                          ) : (
                            <ul className="space-y-2">
                              {(shown.milestones ?? []).map((milestone) => (
                                <MilestoneEditor
                                  key={milestone.id}
                                  milestone={milestone}
                                  busy={updateMilestoneMutation.isPending}
                                  onSave={(body) =>
                                    updateMilestoneMutation.mutate({
                                      goalId: goal.id,
                                      milestoneId: milestone.id,
                                      body,
                                    })
                                  }
                                  onRemove={() => setRemovingMilestone({ goalId: goal.id, milestone })}
                                />
                              ))}
                            </ul>
                          )}

                          <form
                            className="flex flex-wrap items-end gap-2 rounded-md border p-3"
                            onSubmit={(event) => {
                              event.preventDefault()
                              if (!newMilestone.title.trim()) return
                              addMilestoneMutation.mutate({
                                goalId: goal.id,
                                body: {
                                  title: newMilestone.title.trim(),
                                  dueDate: fromLocalDateTimeInput(newMilestone.dueDate),
                                },
                              })
                            }}
                          >
                            <div className="min-w-48 flex-1 space-y-1.5">
                              <Label htmlFor={`new-milestone-${goal.id}`}>New milestone</Label>
                              <Input
                                id={`new-milestone-${goal.id}`}
                                maxLength={200}
                                value={newMilestone.title}
                                onChange={(event) =>
                                  setNewMilestone((current) => ({ ...current, title: event.target.value }))
                                }
                              />
                            </div>
                            <div className="space-y-1.5">
                              <Label htmlFor={`new-milestone-due-${goal.id}`}>Due</Label>
                              <Input
                                id={`new-milestone-due-${goal.id}`}
                                type="datetime-local"
                                value={newMilestone.dueDate}
                                onChange={(event) =>
                                  setNewMilestone((current) => ({
                                    ...current,
                                    dueDate: event.target.value,
                                  }))
                                }
                              />
                            </div>
                            <Button
                              type="submit"
                              variant="secondary"
                              loading={addMilestoneMutation.isPending}
                              disabled={!newMilestone.title.trim()}
                            >
                              <Plus /> Add
                            </Button>
                          </form>
                        </div>

                        {(shown.subGoals ?? []).length > 0 ? (
                          <div className="space-y-2 border-t pt-4">
                            <p className="text-sm font-medium">Sub-goals</p>
                            <ul className="space-y-2">
                              {(shown.subGoals ?? []).map((sub) => (
                                <li key={sub.id} className="rounded-md border p-3">
                                  <div className="flex flex-wrap items-center justify-between gap-2">
                                    <span className="text-sm font-medium">{sub.title}</span>
                                    <StatusBadge value={sub.status} label={titleCase(sub.status)} />
                                  </div>
                                  <div className="mt-2 flex items-center gap-3">
                                    <Progress
                                      value={sub.progress}
                                      className="h-1.5"
                                      aria-label={`${sub.title} progress`}
                                      indicatorClassName={progressColor(sub.category)}
                                    />
                                    <span className="text-xs tabular-nums text-muted-foreground">
                                      {sub.progress}%
                                    </span>
                                  </div>
                                </li>
                              ))}
                            </ul>
                          </div>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                </Card>
              </li>
            )
          })}
        </ul>
      )}

      {listQuery.data && listQuery.data.totalPages > 1 ? (
        <nav
          aria-label="Goal pagination"
          className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-4"
        >
          <p className="text-sm text-muted-foreground">
            {listQuery.data.totalElements} goal
            {listQuery.data.totalElements === 1 ? '' : 's'} · page {listQuery.data.page + 1} of{' '}
            {listQuery.data.totalPages}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={listQuery.data.first}
              onClick={() => patchParams({ page: String(listQuery.data.page - 1) })}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={listQuery.data.last}
              onClick={() => patchParams({ page: String(listQuery.data.page + 1) })}
            >
              Next
            </Button>
          </div>
        </nav>
      ) : null}

      <Dialog
        open={editorOpen}
        onOpenChange={(open) => {
          if (!open) closeEditor()
        }}
      >
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editingGoal ? 'Edit goal' : 'New goal'}</DialogTitle>
            <DialogDescription>
              Milestones added here replace the current set, so keep the ones you still need.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={submitGoal} className="space-y-4" noValidate>
            {editorError ? (
              <p
                role="alert"
                className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {editorError}
              </p>
            ) : null}

            <div className="space-y-1.5">
              <Label htmlFor="goal-title">Title</Label>
              <Input
                id="goal-title"
                required
                maxLength={200}
                value={draft.title}
                onChange={(event) => setDraftField('title', event.target.value)}
                aria-invalid={Boolean(fieldErrors.title)}
              />
              {fieldErrors.title ? (
                <p className="text-xs text-destructive">{fieldErrors.title}</p>
              ) : null}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="goal-type">Type</Label>
                <Select
                  value={draft.goalType}
                  onValueChange={(value) => setDraftField('goalType', value as GoalType)}
                >
                  <SelectTrigger id="goal-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {GOAL_TYPES.map((option) => (
                      <SelectItem key={option} value={option}>
                        {titleCase(option)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-priority">Priority</Label>
                <Select
                  value={draft.priority}
                  onValueChange={(value) => setDraftField('priority', value as Priority)}
                >
                  <SelectTrigger id="goal-priority">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PRIORITY_OPTIONS.map((option) => (
                      <SelectItem key={option} value={option}>
                        {titleCase(option)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-target">Target date</Label>
                <Input
                  id="goal-target"
                  type="datetime-local"
                  value={draft.targetDate}
                  onChange={(event) => setDraftField('targetDate', event.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-category">Category</Label>
                <Input
                  id="goal-category"
                  maxLength={48}
                  value={draft.category}
                  onChange={(event) => setDraftField('category', event.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-editor-status">Status</Label>
                <Select
                  value={draft.status}
                  onValueChange={(value) => setDraftField('status', value as GoalStatus)}
                >
                  <SelectTrigger id="goal-editor-status">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {GOAL_STATUSES.map((option) => (
                      <SelectItem key={option} value={option}>
                        {titleCase(option)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-parent">Parent goal</Label>
                <Select
                  value={draft.parentGoalId || NONE}
                  onValueChange={(value) => setDraftField('parentGoalId', value === NONE ? '' : value)}
                >
                  <SelectTrigger id="goal-parent">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>No parent</SelectItem>
                    {parentOptions.map((option) => (
                      <SelectItem key={option.id} value={option.id}>
                        {option.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-color">Colour</Label>
                <Input
                  id="goal-color"
                  maxLength={16}
                  placeholder="#22d3ee"
                  value={draft.color}
                  onChange={(event) => setDraftField('color', event.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="goal-position">Position</Label>
                <Input
                  id="goal-position"
                  type="number"
                  min={0}
                  value={draft.position}
                  onChange={(event) => setDraftField('position', event.target.value)}
                />
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <div className="flex items-center justify-between text-sm">
                  <Label htmlFor="goal-progress-field">Progress</Label>
                  <span className="tabular-nums text-muted-foreground">{draft.progress}%</span>
                </div>
                <input
                  id="goal-progress-field"
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={draft.progress}
                  onChange={(event) => setDraftField('progress', Number(event.target.value))}
                  className="w-full accent-[var(--color-primary)]"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="goal-description">Description</Label>
              <Textarea
                id="goal-description"
                rows={3}
                maxLength={20000}
                value={draft.description}
                onChange={(event) => setDraftField('description', event.target.value)}
              />
            </div>

            <div className="space-y-3 border-t pt-4">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">Milestones</p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setDraftField('milestones', [...draft.milestones, emptyMilestoneDraft()])
                  }
                >
                  <Plus /> Add milestone
                </Button>
              </div>

              {draft.milestones.length === 0 ? (
                <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                  No milestones. A goal without checkpoints is hard to steer.
                </p>
              ) : (
                <ul className="space-y-3">
                  {draft.milestones.map((milestone, index) => (
                    <li key={index} className="space-y-2 rounded-md border p-3">
                      <div className="flex items-end gap-2">
                        <div className="min-w-40 flex-1 space-y-1.5">
                          <Label htmlFor={`draft-milestone-title-${index}`}>Title</Label>
                          <Input
                            id={`draft-milestone-title-${index}`}
                            maxLength={200}
                            value={milestone.title}
                            onChange={(event) => setMilestone(index, { title: event.target.value })}
                          />
                        </div>
                        <div className="space-y-1.5">
                          <Label htmlFor={`draft-milestone-due-${index}`}>Due</Label>
                          <Input
                            id={`draft-milestone-due-${index}`}
                            type="datetime-local"
                            value={milestone.dueDate}
                            onChange={(event) => setMilestone(index, { dueDate: event.target.value })}
                          />
                        </div>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          aria-label={`Remove milestone row ${index + 1}`}
                          onClick={() =>
                            setDraftField(
                              'milestones',
                              draft.milestones.filter((_, position) => position !== index),
                            )
                          }
                        >
                          <Trash2 />
                        </Button>
                      </div>
                      <div className="space-y-1.5">
                        <Label htmlFor={`draft-milestone-description-${index}`}>Description</Label>
                        <Textarea
                          id={`draft-milestone-description-${index}`}
                          rows={2}
                          maxLength={20000}
                          value={milestone.description}
                          onChange={(event) =>
                            setMilestone(index, { description: event.target.value })
                          }
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeEditor}>
                Cancel
              </Button>
              <Button
                type="submit"
                loading={createMutation.isPending || updateMutation.isPending}
              >
                {editingGoal ? 'Save goal' : 'Create goal'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={deletingGoal !== null} onOpenChange={(open) => !open && setDeletingGoal(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete goal</DialogTitle>
            <DialogDescription>
              {deletingGoal
                ? `"${deletingGoal.title}" and its milestones will be removed. Linked tasks keep their work but lose the link.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingGoal(null)}>
              Keep goal
            </Button>
            <Button
              variant="destructive"
              loading={deleteMutation.isPending}
              onClick={() => {
                if (deletingGoal) deleteMutation.mutate(deletingGoal.id)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={removingMilestone !== null}
        onOpenChange={(open) => !open && setRemovingMilestone(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Remove milestone</DialogTitle>
            <DialogDescription>
              {removingMilestone
                ? `"${removingMilestone.milestone.title}" will be removed from this goal.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRemovingMilestone(null)}>
              Keep milestone
            </Button>
            <Button
              variant="destructive"
              loading={removeMilestoneMutation.isPending}
              onClick={() => {
                if (removingMilestone) {
                  removeMilestoneMutation.mutate({
                    goalId: removingMilestone.goalId,
                    milestoneId: removingMilestone.milestone.id,
                  })
                }
              }}
            >
              Remove
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <p className="text-xs text-muted-foreground">
        Goals marked AI proposed were generated for you and stay editable until you confirm them.
      </p>
    </div>
  )
}