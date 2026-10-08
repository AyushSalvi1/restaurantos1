import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowDown,
  ArrowUp,
  CalendarClock,
  CheckSquare,
  ClipboardList,
  LayoutGrid,
  ListTodo,
  Plus,
  RotateCcw,
  Search,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { goalApi, taskApi } from '@/api/endpoints'
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { EmptyState } from '@/components/ui/empty-state'
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import {
  formatDateTime,
  formatMinutes,
  formatRelative,
  fromLocalDateTimeInput,
  toLocalDateTimeInput,
  titleCase,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import type {
  BulkAction,
  BulkTaskRequest,
  Difficulty,
  EnergyRequirement,
  Priority,
  QuickCreateRequest,
  TaskQuery,
  TaskRequest,
  TaskResponse,
  TaskStatus,
} from '@/types/api'

const STATUS_OPTIONS: readonly TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
const PRIORITY_OPTIONS: readonly Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const SORT_OPTIONS = [
  { value: 'deadline', label: 'Deadline' },
  { value: 'priority', label: 'Priority' },
  { value: 'title', label: 'Title' },
  { value: 'created', label: 'Newest first' },
  { value: 'updated', label: 'Recently updated' },
  { value: 'position', label: 'Manual order' },
] as const

const PAGE_SIZES = ['10', '25', '50', '100'] as const

type BulkValueAction = Extract<
  BulkAction,
  'SET_PRIORITY' | 'MOVE_TO_GOAL' | 'MOVE_TO_PROJECT' | 'SET_CATEGORY'
>

const NO_VALUE = '__none__'

/** Multi-select values travel through the URL as one comma separated parameter. */
function readMulti<T extends string>(raw: string | null, allowed: readonly T[]): T[] {
  if (!raw) return []
  return raw
    .split(',')
    .map((value) => value.trim())
    .filter((value): value is T => (allowed as readonly string[]).includes(value))
}

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

function toNumber(value: string): number | undefined {
  if (!value.trim()) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function isOverdue(task: TaskResponse): boolean {
  if (!task.deadline || task.status === 'COMPLETED') return false
  return new Date(task.deadline).getTime() < Date.now()
}

function orderTasks(tasks: TaskResponse[], order: string[]): TaskResponse[] {
  const rank = new Map(order.map((id, index) => [id, index]))
  return [...tasks].sort(
    (left, right) =>
      (rank.get(left.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(right.id) ?? Number.MAX_SAFE_INTEGER),
  )
}

function moveId(order: string[], index: number, delta: number): string[] {
  const target = index + delta
  if (target < 0 || target >= order.length) return order
  const next = [...order]
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  return next
}

interface TaskFormState {
  title: string
  description: string
  notes: string
  status: TaskStatus
  priority: Priority
  category: string
  deadline: string
  estimatedMinutes: string
  actualMinutes: string
  difficulty: Difficulty
  energyRequirement: EnergyRequirement
  tags: string
  goalId: string
  milestoneId: string
  projectId: string
  recurrenceRule: string
  recurrenceEndDate: string
  position: string
}

const EMPTY_TASK_FORM: TaskFormState = {
  title: '',
  description: '',
  notes: '',
  status: 'TODO',
  priority: 'MEDIUM',
  category: '',
  deadline: '',
  estimatedMinutes: '',
  actualMinutes: '',
  difficulty: 'MEDIUM',
  energyRequirement: 'MEDIUM',
  tags: '',
  goalId: '',
  milestoneId: '',
  projectId: '',
  recurrenceRule: '',
  recurrenceEndDate: '',
  position: '',
}

function toTaskForm(task: TaskResponse | null): TaskFormState {
  if (!task) return EMPTY_TASK_FORM
  return {
    title: task.title,
    description: task.description ?? '',
    notes: task.notes ?? '',
    status: task.status,
    priority: task.priority,
    category: task.category ?? '',
    deadline: toLocalDateTimeInput(task.deadline),
    estimatedMinutes: task.estimatedMinutes ? String(task.estimatedMinutes) : '',
    actualMinutes: task.actualMinutes ? String(task.actualMinutes) : '',
    difficulty: task.difficulty ?? 'MEDIUM',
    energyRequirement: task.energyRequirement ?? 'MEDIUM',
    tags: (task.tags ?? []).join(', '),
    goalId: task.goalId ?? '',
    milestoneId: task.milestoneId ?? '',
    projectId: task.projectId ?? '',
    recurrenceRule: task.recurrenceRule ?? '',
    recurrenceEndDate: toLocalDateTimeInput(task.recurrenceEndDate),
    position: String(task.position ?? 0),
  }
}

function toTaskRequest(form: TaskFormState): TaskRequest {
  return {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    notes: form.notes.trim() || undefined,
    status: form.status,
    priority: form.priority,
    category: form.category.trim() || undefined,
    deadline: fromLocalDateTimeInput(form.deadline),
    estimatedMinutes: toNumber(form.estimatedMinutes),
    actualMinutes: toNumber(form.actualMinutes),
    difficulty: form.difficulty,
    energyRequirement: form.energyRequirement,
    tags: parseList(form.tags),
    goalId: form.goalId || undefined,
    milestoneId: form.milestoneId || undefined,
    projectId: form.projectId || undefined,
    recurrenceRule: form.recurrenceRule.trim() || undefined,
    recurrenceEndDate: fromLocalDateTimeInput(form.recurrenceEndDate),
    position: toNumber(form.position),
  }
}

function TaskFormDialog({
  open,
  onOpenChange,
  task,
  goalOptions,
  busy,
  errorMessage,
  fieldErrors,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  task: TaskResponse | null
  goalOptions: { id: string; title: string }[]
  busy: boolean
  errorMessage?: string
  fieldErrors: Record<string, string>
  onSubmit: (request: TaskRequest) => void
}) {
  const [form, setForm] = useState<TaskFormState>(() => toTaskForm(task))

  useEffect(() => {
    if (open) setForm(toTaskForm(task))
  }, [open, task])

  const set = <K extends keyof TaskFormState>(key: K, value: TaskFormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit(toTaskRequest(form))
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{task ? 'Edit task' : 'New task'}</DialogTitle>
          <DialogDescription>
            {task
              ? 'Changes are saved immediately and appear everywhere the task is shown.'
              : 'Capture the outcome first, then refine the estimate and deadline.'}
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
            <Label htmlFor="task-title">Title</Label>
            <Input
              id="task-title"
              required
              maxLength={200}
              value={form.title}
              onChange={(event) => set('title', event.target.value)}
              aria-invalid={Boolean(fieldErrors.title)}
            />
            {fieldErrors.title ? (
              <p className="text-xs text-destructive">{fieldErrors.title}</p>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="task-priority">Priority</Label>
              <Select
                value={form.priority}
                onValueChange={(value) => set('priority', value as Priority)}
              >
                <SelectTrigger id="task-priority">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PRIORITY_OPTIONS.map((priority) => (
                    <SelectItem key={priority} value={priority}>
                      {titleCase(priority)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-status">Status</Label>
              <Select
                value={form.status}
                onValueChange={(value) => set('status', value as TaskStatus)}
              >
                <SelectTrigger id="task-status">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {STATUS_OPTIONS.map((status) => (
                    <SelectItem key={status} value={status}>
                      {titleCase(status)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-category">Category</Label>
              <Input
                id="task-category"
                maxLength={48}
                placeholder="e.g. Deep work"
                value={form.category}
                onChange={(event) => set('category', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-goal">Goal</Label>
              <Select
                value={form.goalId || NO_VALUE}
                onValueChange={(value) => set('goalId', value === NO_VALUE ? '' : value)}
              >
                <SelectTrigger id="task-goal">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_VALUE}>No goal</SelectItem>
                  {goalOptions.map((goal) => (
                    <SelectItem key={goal.id} value={goal.id}>
                      {goal.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-deadline">Deadline</Label>
              <Input
                id="task-deadline"
                type="datetime-local"
                value={form.deadline}
                onChange={(event) => set('deadline', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-tags">Tags</Label>
              <Input
                id="task-tags"
                placeholder="comma, separated"
                value={form.tags}
                onChange={(event) => set('tags', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-estimated">Estimated minutes</Label>
              <Input
                id="task-estimated"
                type="number"
                min={1}
                value={form.estimatedMinutes}
                onChange={(event) => set('estimatedMinutes', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-actual">Actual minutes</Label>
              <Input
                id="task-actual"
                type="number"
                min={0}
                value={form.actualMinutes}
                onChange={(event) => set('actualMinutes', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-difficulty">Difficulty</Label>
              <Select
                value={form.difficulty}
                onValueChange={(value) => set('difficulty', value as Difficulty)}
              >
                <SelectTrigger id="task-difficulty">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(['EASY', 'MEDIUM', 'HARD'] as const).map((difficulty) => (
                    <SelectItem key={difficulty} value={difficulty}>
                      {titleCase(difficulty)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-energy">Energy required</Label>
              <Select
                value={form.energyRequirement}
                onValueChange={(value) => set('energyRequirement', value as EnergyRequirement)}
              >
                <SelectTrigger id="task-energy">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(['LOW', 'MEDIUM', 'HIGH'] as const).map((energy) => (
                    <SelectItem key={energy} value={energy}>
                      {titleCase(energy)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-milestone">Milestone id</Label>
              <Input
                id="task-milestone"
                value={form.milestoneId}
                onChange={(event) => set('milestoneId', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-project">Project id</Label>
              <Input
                id="task-project"
                value={form.projectId}
                onChange={(event) => set('projectId', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-position">Position</Label>
              <Input
                id="task-position"
                type="number"
                min={0}
                value={form.position}
                onChange={(event) => set('position', event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-recurrence">Recurrence rule</Label>
              <Input
                id="task-recurrence"
                maxLength={120}
                placeholder="FREQ=WEEKLY;BYDAY=MO"
                value={form.recurrenceRule}
                onChange={(event) => set('recurrenceRule', event.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                The server expands this rule. You only request the next occurrence when you need it.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="task-recurrence-end">Recurrence ends</Label>
              <Input
                id="task-recurrence-end"
                type="datetime-local"
                value={form.recurrenceEndDate}
                onChange={(event) => set('recurrenceEndDate', event.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="task-description">Description</Label>
            <Textarea
              id="task-description"
              rows={3}
              maxLength={20000}
              value={form.description}
              onChange={(event) => set('description', event.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="task-notes">Notes</Label>
            <Textarea
              id="task-notes"
              rows={3}
              maxLength={20000}
              value={form.notes}
              onChange={(event) => set('notes', event.target.value)}
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {task ? 'Save changes' : 'Create task'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function DeadlineText({ task }: { task: TaskResponse }) {
  if (!task.deadline) {
    return <span className="text-muted-foreground">No deadline</span>
  }
  const late = isOverdue(task)
  return (
    <span className={cn('tabular-nums', late ? 'text-destructive' : 'text-muted-foreground')}>
      {formatDateTime(task.deadline)} ({formatRelative(task.deadline)})
    </span>
  )
}

function TaskCheck({
  task,
  busy,
  onToggle,
}: {
  task: TaskResponse
  busy: boolean
  onToggle: (task: TaskResponse) => void
}) {
  const done = task.status === 'COMPLETED'
  return (
    <input
      type="checkbox"
      className="size-4 shrink-0 accent-[var(--color-primary)]"
      checked={done}
      disabled={busy}
      onChange={() => onToggle(task)}
      aria-label={`${done ? 'Reopen' : 'Complete'} ${task.title}`}
    />
  )
}

export default function TasksPage() {
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

  const view = searchParams.get('view') === 'board' ? 'board' : 'list'
  const q = searchParams.get('q') ?? ''
  const statusFilter = readMulti(searchParams.get('status'), STATUS_OPTIONS)
  const priorityFilter = readMulti(searchParams.get('priority'), PRIORITY_OPTIONS)
  const category = searchParams.get('category') ?? ''
  const goalId = searchParams.get('goalId') ?? ''
  const tag = searchParams.get('tag') ?? ''
  const deadlineFrom = searchParams.get('deadlineFrom') ?? ''
  const deadlineTo = searchParams.get('deadlineTo') ?? ''
  const overdue = searchParams.get('overdue') === 'true'
  const sort = searchParams.get('sort') ?? 'deadline'
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0)
  const size = Math.max(1, Number(searchParams.get('size') ?? '25') || 25)
  const zoneId = searchParams.get('zoneId') ?? ''
  const reorderMode = searchParams.get('reorder') === '1'

  const [searchDraft, setSearchDraft] = useState(q)
  const [quickTitle, setQuickTitle] = useState('')
  const [selected, setSelected] = useState<Set<string>>(() => new Set())
  const [order, setOrder] = useState<string[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<TaskResponse | null>(null)
  const [deleting, setDeleting] = useState<TaskResponse | null>(null)
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false)
  const [bulkValueAction, setBulkValueAction] = useState<BulkValueAction | null>(null)
  const [bulkPriority, setBulkPriority] = useState<Priority>('MEDIUM')
  const [bulkGoalId, setBulkGoalId] = useState(NO_VALUE)
  const [bulkProjectId, setBulkProjectId] = useState('')
  const [bulkCategory, setBulkCategory] = useState('')
  const [formError, setFormError] = useState<string>()
  const [formFieldErrors, setFormFieldErrors] = useState<Record<string, string>>({})

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

  const taskQuery = useMemo<TaskQuery>(
    () => ({
      q: q || undefined,
      status: statusFilter.length ? statusFilter : undefined,
      priority: priorityFilter.length ? priorityFilter : undefined,
      category: category || undefined,
      goalId: goalId || undefined,
      tag: tag || undefined,
      deadlineFrom: deadlineFrom || undefined,
      deadlineTo: deadlineTo || undefined,
      overdue: overdue || undefined,
      sort,
      page,
      size,
      zoneId: zoneId || undefined,
    }),
    [q, statusFilter, priorityFilter, category, goalId, tag, deadlineFrom, deadlineTo, overdue, sort, page, size, zoneId],
  )

  const listQuery = useQuery({
    queryKey: ['tasks', 'list', taskQuery],
    queryFn: () => taskApi.list(taskQuery),
    enabled: view === 'list',
  })

  const boardQuery = useQuery({
    queryKey: ['tasks', 'board'],
    queryFn: () => taskApi.board(),
    enabled: view === 'board',
  })

  const goalsQuery = useQuery({
    queryKey: ['goals', 'active'],
    queryFn: () => goalApi.active(),
  })

  const goalOptions = useMemo(
    () => (goalsQuery.data ?? []).map((goal) => ({ id: goal.id, title: goal.title })),
    [goalsQuery.data],
  )

  const invalidateTasks = useCallback(
    async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['tasks'] }),
        queryClient.invalidateQueries({ queryKey: ['goals'] }),
        queryClient.invalidateQueries({ queryKey: ['focus'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
    [queryClient],
  )

  const createMutation = useMutation({
    mutationFn: (request: TaskRequest) => taskApi.create(request),
    onSuccess: async (task) => {
      setCreateOpen(false)
      setFormError(undefined)
      setFormFieldErrors({})
      await invalidateTasks()
      toast.success('Task created', { description: task.title })
    },
    onError: (error) => {
      const normalised = toNormalisedError(error)
      setFormError(normalised.message)
      setFormFieldErrors(normalised.fieldErrors)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: TaskRequest }) =>
      taskApi.update(id, request),
    onSuccess: async () => {
      setEditing(null)
      setFormError(undefined)
      setFormFieldErrors({})
      await invalidateTasks()
      toast.success('Task updated')
    },
    onError: (error) => {
      const normalised = toNormalisedError(error)
      setFormError(normalised.message)
      setFormFieldErrors(normalised.fieldErrors)
    },
  })

  const quickCreateMutation = useMutation({
    mutationFn: (request: QuickCreateRequest) => taskApi.quickCreate(request),
    onSuccess: async (task) => {
      setQuickTitle('')
      await invalidateTasks()
      toast.success('Task added', { description: task.title })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const completeMutation = useMutation({
    mutationFn: (task: TaskResponse) =>
      taskApi.complete(task.id, {
        actualMinutes: task.estimatedMinutes > 0 ? task.estimatedMinutes : undefined,
      }),
    onSuccess: async (task) => {
      await invalidateTasks()
      toast.success('Task completed', { description: task.title })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const reopenMutation = useMutation({
    mutationFn: (task: TaskResponse) => taskApi.updateStatus(task.id, { status: 'TODO' }),
    onSuccess: async (task) => {
      await invalidateTasks()
      toast.success('Task reopened', { description: task.title })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeMutation = useMutation({
    mutationFn: (id: string) => taskApi.remove(id),
    onSuccess: async () => {
      setDeleting(null)
      await invalidateTasks()
      toast.success('Task deleted')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const bulkMutation = useMutation({
    mutationFn: (request: BulkTaskRequest) => taskApi.bulk(request),
    onSuccess: async (result) => {
      setSelected(new Set())
      setBulkValueAction(null)
      setBulkDeleteOpen(false)
      await invalidateTasks()
      if (result.failedIds.length > 0) {
        toast.warning(`${result.affected} updated, ${result.failedIds.length} failed`, {
          description: result.message,
        })
        return
      }
      toast.success(`${result.affected} task${result.affected === 1 ? '' : 's'} updated`, {
        description: result.message,
      })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const reorderMutation = useMutation({
    mutationFn: (next: { id: string; position: number }[]) => taskApi.reorder(next),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tasks'] })
      toast.success('Order saved')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const selectedIds = useMemo(() => Array.from(selected), [selected])

  const rows = useMemo(() => {
    const content = listQuery.data?.content ?? []
    return reorderMode && order.length ? orderTasks(content, order) : content
  }, [listQuery.data, reorderMode, order])

  const toggleSelected = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const toggleAll = (ids: string[]) =>
    setSelected((current) => {
      const allSelected = ids.every((id) => current.has(id))
      const next = new Set(current)
      for (const id of ids) {
        if (allSelected) next.delete(id)
        else next.add(id)
      }
      return next
    })

  const toggleFilterValue = (key: 'status' | 'priority', value: TaskStatus | Priority) => {
    const allowed: readonly string[] = key === 'status' ? STATUS_OPTIONS : PRIORITY_OPTIONS
    const current = (searchParams.get(key) ?? '')
      .split(',')
      .filter((item) => allowed.includes(item))
    const next = current.includes(value)
      ? current.filter((item) => item !== value)
      : [...current, value]
    patchParams({ [key]: next.join(',') || null, page: '0' })
  }

  const runBulk = (request: BulkTaskRequest) => bulkMutation.mutate(request)

  const handleRowToggle = (task: TaskResponse) => {
    if (task.status === 'COMPLETED') reopenMutation.mutate(task)
    else completeMutation.mutate(task)
  }

  const activeFilterCount =
    statusFilter.length +
    priorityFilter.length +
    (category ? 1 : 0) +
    (goalId ? 1 : 0) +
    (tag ? 1 : 0) +
    (deadlineFrom ? 1 : 0) +
    (deadlineTo ? 1 : 0) +
    (overdue ? 1 : 0) +
    (zoneId ? 1 : 0)

  const clearFilters = () =>
    patchParams({
      q: null,
      status: null,
      priority: null,
      category: null,
      goalId: null,
      tag: null,
      deadlineFrom: null,
      deadlineTo: null,
      overdue: null,
      zoneId: null,
      page: '0',
    })

  const quickAdd = (event: FormEvent) => {
    event.preventDefault()
    const title = quickTitle.trim()
    if (!title) return
    quickCreateMutation.mutate({ title })
  }

  const enterReorder = () => {
    setOrder((listQuery.data?.content ?? []).map((task) => task.id))
    patchParams({ reorder: '1' })
  }

  const handleMove = (index: number, delta: number) => {
    const next = moveId(order, index, delta)
    if (next === order) return
    setOrder(next)
    reorderMutation.mutate(next.map((id, position) => ({ id, position })))
  }

  const quickAddPending = quickCreateMutation.isPending
  const toggleBusy = completeMutation.isPending || reopenMutation.isPending

  const loadingPanel =
    view === 'list' && listQuery.isPending ? (
      <div className="space-y-2">
        {Array.from({ length: 6 }).map((_, index) => (
          <Skeleton key={index} className="h-14 w-full" />
        ))}
      </div>
    ) : view === 'board' && boardQuery.isPending ? (
      <div className="grid gap-3 md:grid-cols-4">
        {Array.from({ length: 4 }).map((_, column) => (
          <div key={column} className="space-y-2">
            <Skeleton className="h-8 w-full" />
            {Array.from({ length: 3 }).map((__, card) => (
              <Skeleton key={card} className="h-24 w-full" />
            ))}
          </div>
        ))}
      </div>
    ) : null

  const errorPanel = (() => {
    const failure =
      view === 'list' ? listQuery.error : boardQuery.error
    if (!failure) return null
    return (
      <div
        role="alert"
        className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
      >
        <p>{toNormalisedError(failure).message}</p>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            if (view === 'list') void listQuery.refetch()
            else void boardQuery.refetch()
          }}
        >
          <RotateCcw /> Try again
        </Button>
      </div>
    )
  })()

  const listBody = (() => {
    if (loadingPanel) return loadingPanel
    if (errorPanel) return errorPanel
    if (rows.length === 0) {
      return (
        <EmptyState
          icon={<ListTodo />}
          title={activeFilterCount > 0 ? 'No tasks match these filters' : 'No tasks yet'}
          description={
            activeFilterCount > 0
              ? 'Widen the date window or clear a filter to widen the search.'
              : 'Use quick add to capture something immediately, or open the full form for detail.'
          }
          action={
            activeFilterCount > 0 ? (
              <Button variant="outline" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : (
              <Button onClick={() => setCreateOpen(true)}>
                <Plus /> New task
              </Button>
            )
          }
        />
      )
    }
    return (
      <ul className="divide-y">
        {rows.map((task, index) => (
          <li key={task.id} className="flex items-start gap-3 py-3">
            {reorderMode ? (
              <div className="flex flex-col gap-1 pt-0.5">
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`Move ${task.title} up`}
                  disabled={index === 0 || reorderMutation.isPending}
                  onClick={() => handleMove(index, -1)}
                >
                  <ArrowUp />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`Move ${task.title} down`}
                  disabled={index === rows.length - 1 || reorderMutation.isPending}
                  onClick={() => handleMove(index, 1)}
                >
                  <ArrowDown />
                </Button>
              </div>
            ) : (
              <input
                type="checkbox"
                className="mt-1 size-4 shrink-0 accent-[var(--color-primary)]"
                checked={selected.has(task.id)}
                onChange={() => toggleSelected(task.id)}
                aria-label={`Select ${task.title}`}
              />
            )}

            <TaskCheck task={task} busy={toggleBusy} onToggle={handleRowToggle} />

            <div className="min-w-0 flex-1 space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <Link
                  to={`/app/tasks/${task.id}`}
                  className={cn(
                    'truncate text-sm font-medium hover:text-primary hover:underline',
                    task.status === 'COMPLETED' && 'text-muted-foreground line-through',
                  )}
                >
                  {task.title}
                </Link>
                <StatusBadge value={task.status} label={titleCase(task.status)} />
                <StatusBadge value={task.priority} label={titleCase(task.priority)} />
                {task.category ? <Badge variant="outline">{task.category}</Badge> : null}
              </div>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <CalendarClock className="size-3.5" aria-hidden />
                  <DeadlineText task={task} />
                </span>
                <span className="tabular-nums">
                  {formatMinutes(task.estimatedMinutes)} planned
                  {task.actualMinutes > 0 ? ` · ${formatMinutes(task.actualMinutes)} spent` : ''}
                </span>
                {task.goalId ? (
                  <Link
                    to={`/app/goals?goalId=${encodeURIComponent(task.goalId)}`}
                    className="truncate hover:text-foreground hover:underline"
                  >
                    Goal linked
                  </Link>
                ) : null}
                {(task.tags ?? []).map((taskTag) => (
                  <Badge key={taskTag} variant="secondary">
                    {taskTag}
                  </Badge>
                ))}
              </div>
            </div>

            {!reorderMode ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${task.title}`}>
                    <ClipboardList />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onSelect={() => setEditing(task)}>
                    Edit task
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onSelect={() =>
                      runBulk({ taskIds: [task.id], action: 'SET_PRIORITY', priority: 'HIGH' })
                    }
                  >
                    Mark high priority
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onSelect={() =>
                      runBulk({ taskIds: [task.id], action: 'COMPLETE' })
                    }
                  >
                    Complete
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    className="text-destructive"
                    onSelect={() => setDeleting(task)}
                  >
                    <Trash2 /> Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : null}
          </li>
        ))}
      </ul>
    )
  })()

  const boardBody = (() => {
    if (loadingPanel) return loadingPanel
    if (errorPanel) return errorPanel
    const columns = boardQuery.data ?? []
    if (columns.every((column) => column.tasks.length === 0)) {
      return (
        <EmptyState
          icon={<LayoutGrid />}
          title="Nothing on the board"
          description="Create your first task and it will appear in the lane that matches its status."
          action={
            <Button onClick={() => setCreateOpen(true)}>
              <Plus /> New task
            </Button>
          }
        />
      )
    }
    return (
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {columns.map((column) => (
          <section key={column.status} className="rounded-lg border bg-card p-3">
            <header className="mb-3 flex items-center justify-between gap-2">
              <h3 className="text-sm font-semibold">{column.label || titleCase(column.status)}</h3>
              <StatusBadge value={column.status} label={titleCase(column.status)} />
            </header>
            {column.tasks.length === 0 ? (
              <p className="rounded-md border border-dashed px-3 py-6 text-center text-xs text-muted-foreground">
                Nothing here
              </p>
            ) : (
              <ul className="space-y-2">
                {column.tasks.map((task) => (
                  <li key={task.id} className="rounded-md border bg-background/60 p-3">
                    <div className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5 size-4 shrink-0 accent-[var(--color-primary)]"
                        checked={selected.has(task.id)}
                        onChange={() => toggleSelected(task.id)}
                        aria-label={`Select ${task.title}`}
                      />
                      <TaskCheck task={task} busy={toggleBusy} onToggle={handleRowToggle} />
                      <Link
                        to={`/app/tasks/${task.id}`}
                        className="min-w-0 flex-1 text-sm font-medium hover:text-primary hover:underline"
                      >
                        {task.title}
                      </Link>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                      <StatusBadge value={task.priority} label={titleCase(task.priority)} />
                      {task.category ? <Badge variant="outline">{task.category}</Badge> : null}
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                      <DeadlineText task={task} />
                      <span className="tabular-nums">{formatMinutes(task.estimatedMinutes)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </div>
    )
  })()

  const pageMeta = listQuery.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tasks"
        description="Filter, group and reprioritise everything you have committed to."
        actions={
          <>
            {reorderMode ? (
              <>
                <Spinner label="Saving order" />
                <Button variant="outline" onClick={() => patchParams({ reorder: null })}>
                  Exit reorder
                </Button>
              </>
            ) : (
              <Button variant="outline" onClick={enterReorder} disabled={view !== 'list'}>
                Reorder
              </Button>
            )}
            <Button onClick={() => setCreateOpen(true)}>
              <Plus /> New task
            </Button>
          </>
        }
      />

      <form onSubmit={quickAdd} className="flex items-center gap-2">
        <div className="flex-1">
          <Label htmlFor="quick-task" className="sr-only">
            Quick add task
          </Label>
          <Input
            id="quick-task"
            maxLength={200}
            placeholder="Quick add: type a task and press Enter"
            value={quickTitle}
            onChange={(event) => setQuickTitle(event.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary" loading={quickAddPending} disabled={!quickTitle.trim()}>
          Add
        </Button>
      </form>

      <SectionCard
        title="Filters"
        description={`${activeFilterCount} active. Filters are stored in the address bar so this view is shareable.`}
        action={
          activeFilterCount > 0 ? (
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              Clear all
            </Button>
          ) : null
        }
      >
        <div className="space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-56 flex-1 space-y-1.5">
              <Label htmlFor="filter-q">Search</Label>
              <div className="relative">
                <Search
                  className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                  aria-hidden
                />
                <Input
                  id="filter-q"
                  className="pl-8"
                  placeholder="Title or description"
                  value={searchDraft}
                  onChange={(event) => setSearchDraft(event.target.value)}
                />
              </div>
            </div>

            <div className="w-44 space-y-1.5">
              <Label htmlFor="filter-sort">Sort</Label>
              <Select value={sort} onValueChange={(value) => patchParams({ sort: value, page: '0' })}>
                <SelectTrigger id="filter-sort">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SORT_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="w-32 space-y-1.5">
              <Label htmlFor="filter-zone">Time zone</Label>
              <Input
                id="filter-zone"
                placeholder={Intl.DateTimeFormat().resolvedOptions().timeZone}
                value={zoneId}
                onChange={(event) => patchParams({ zoneId: event.target.value || null, page: '0' })}
              />
            </div>
          </div>

          <fieldset className="space-y-1.5">
            <legend className="text-sm font-medium text-muted-foreground">Status</legend>
            <div className="flex flex-wrap gap-3">
              {STATUS_OPTIONS.map((status) => (
                <label key={status} className="inline-flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-[var(--color-primary)]"
                    checked={statusFilter.includes(status)}
                    onChange={() => toggleFilterValue('status', status)}
                  />
                  {titleCase(status)}
                </label>
              ))}
            </div>
          </fieldset>

          <fieldset className="space-y-1.5">
            <legend className="text-sm font-medium text-muted-foreground">Priority</legend>
            <div className="flex flex-wrap gap-3">
              {PRIORITY_OPTIONS.map((priority) => (
                <label key={priority} className="inline-flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-[var(--color-primary)]"
                    checked={priorityFilter.includes(priority)}
                    onChange={() => toggleFilterValue('priority', priority)}
                  />
                  {titleCase(priority)}
                </label>
              ))}
            </div>
          </fieldset>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <div className="space-y-1.5">
              <Label htmlFor="filter-category">Category</Label>
              <Input
                id="filter-category"
                value={category}
                placeholder="Any"
                onChange={(event) => patchParams({ category: event.target.value || null, page: '0' })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-goal">Goal</Label>
              <Select
                value={goalId || NO_VALUE}
                onValueChange={(value) =>
                  patchParams({ goalId: value === NO_VALUE ? null : value, page: '0' })
                }
              >
                <SelectTrigger id="filter-goal">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_VALUE}>Any goal</SelectItem>
                  {goalOptions.map((goal) => (
                    <SelectItem key={goal.id} value={goal.id}>
                      {goal.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-tag">Tag</Label>
              <Input
                id="filter-tag"
                value={tag}
                placeholder="Any"
                onChange={(event) => patchParams({ tag: event.target.value || null, page: '0' })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-from">Deadline from</Label>
              <Input
                id="filter-from"
                type="date"
                value={deadlineFrom}
                onChange={(event) =>
                  patchParams({ deadlineFrom: event.target.value || null, page: '0' })
                }
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-to">Deadline to</Label>
              <Input
                id="filter-to"
                type="date"
                value={deadlineTo}
                onChange={(event) => patchParams({ deadlineTo: event.target.value || null, page: '0' })}
              />
            </div>
          </div>

          <label className="inline-flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-[var(--color-primary)]"
              checked={overdue}
              onChange={(event) =>
                patchParams({ overdue: event.target.checked ? 'true' : null, page: '0' })
              }
            />
            Overdue only
          </label>
        </div>
      </SectionCard>

      {selected.size > 0 ? (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-primary/40 bg-primary/10 p-3">
          <span className="inline-flex items-center gap-2 text-sm font-medium">
            <CheckSquare className="size-4" aria-hidden />
            {selected.size} selected
          </span>
          <Button
            size="sm"
            variant="secondary"
            disabled={bulkMutation.isPending}
            onClick={() => runBulk({ taskIds: selectedIds, action: 'COMPLETE' })}
          >
            Complete
          </Button>
          <Button
            size="sm"
            variant="secondary"
            disabled={bulkMutation.isPending}
            onClick={() => runBulk({ taskIds: selectedIds, action: 'REOPEN' })}
          >
            Reopen
          </Button>
          <Button
            size="sm"
            variant="secondary"
            disabled={bulkMutation.isPending}
            onClick={() => runBulk({ taskIds: selectedIds, action: 'CANCEL' })}
          >
            Cancel
          </Button>
          <Button size="sm" variant="outline" onClick={() => setBulkValueAction('SET_PRIORITY')}>
            Set priority
          </Button>
          <Button size="sm" variant="outline" onClick={() => setBulkValueAction('MOVE_TO_GOAL')}>
            Move to goal
          </Button>
          <Button size="sm" variant="outline" onClick={() => setBulkValueAction('MOVE_TO_PROJECT')}>
            Move to project
          </Button>
          <Button size="sm" variant="outline" onClick={() => setBulkValueAction('SET_CATEGORY')}>
            Set category
          </Button>
          <Button
            size="sm"
            variant="destructive"
            onClick={() => setBulkDeleteOpen(true)}
            disabled={bulkMutation.isPending}
          >
            Delete
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>
            Clear selection
          </Button>
        </div>
      ) : null}

      <Tabs
        value={view}
        onValueChange={(value) => {
          setSelected(new Set())
          patchParams({ view: value, reorder: null })
        }}
      >
        <TabsList>
          <TabsTrigger value="list">
            <ListTodo className="size-4" aria-hidden /> List
          </TabsTrigger>
          <TabsTrigger value="board">
            <LayoutGrid className="size-4" aria-hidden /> Board
          </TabsTrigger>
        </TabsList>

        <TabsContent value="list">
          <SectionCard
            title="Task list"
            description={
              pageMeta
                ? `${pageMeta.totalElements} task${pageMeta.totalElements === 1 ? '' : 's'} · page ${pageMeta.page + 1} of ${Math.max(1, pageMeta.totalPages)}`
                : undefined
            }
            action={
              <Button
                variant="ghost"
                size="sm"
                onClick={() => toggleAll(rows.map((task) => task.id))}
                disabled={rows.length === 0}
              >
                Select all on page
              </Button>
            }
          >
            {listBody}

            {pageMeta && pageMeta.totalPages > 1 ? (
              <nav
                aria-label="Task pagination"
                className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t pt-4"
              >
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Label htmlFor="page-size" className="text-sm">
                    Per page
                  </Label>
                  <Select
                    value={String(size)}
                    onValueChange={(value) => patchParams({ size: value, page: '0' })}
                  >
                    <SelectTrigger id="page-size" className="h-8 w-20">
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

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pageMeta.first}
                    onClick={() => patchParams({ page: String(pageMeta.page - 1) })}
                  >
                    Previous
                  </Button>
                  <span className="text-sm tabular-nums">
                    {pageMeta.page + 1} / {pageMeta.totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pageMeta.last}
                    onClick={() => patchParams({ page: String(pageMeta.page + 1) })}
                  >
                    Next
                  </Button>
                </div>
              </nav>
            ) : null}
          </SectionCard>
        </TabsContent>

        <TabsContent value="board">
          <SectionCard
            title="Status board"
            description="Every open task grouped by status. Tick a card to complete it."
          >
            {boardBody}
          </SectionCard>
        </TabsContent>
      </Tabs>

      <TaskFormDialog
        open={createOpen}
        onOpenChange={(open) => {
          setCreateOpen(open)
          setFormError(undefined)
          setFormFieldErrors({})
        }}
        task={null}
        goalOptions={goalOptions}
        busy={createMutation.isPending}
        errorMessage={formError}
        fieldErrors={formFieldErrors}
        onSubmit={(request) => createMutation.mutate(request)}
      />

      <TaskFormDialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) {
            setEditing(null)
            setFormError(undefined)
            setFormFieldErrors({})
          }
        }}
        task={editing}
        goalOptions={goalOptions}
        busy={updateMutation.isPending}
        errorMessage={formError}
        fieldErrors={formFieldErrors}
        onSubmit={(request) => {
          if (editing) updateMutation.mutate({ id: editing.id, request })
        }}
      />

      <Dialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete task</DialogTitle>
            <DialogDescription>
              {deleting
                ? `"${deleting.title}" and its dependency links will be removed. This cannot be undone.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Keep task
            </Button>
            <Button
              variant="destructive"
              loading={removeMutation.isPending}
              onClick={() => {
                if (deleting) removeMutation.mutate(deleting.id)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={bulkDeleteOpen} onOpenChange={setBulkDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {selected.size} task{selected.size === 1 ? '' : 's'}</DialogTitle>
            <DialogDescription>
              Selected tasks will be removed along with their dependency links. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBulkDeleteOpen(false)}>
              Keep them
            </Button>
            <Button
              variant="destructive"
              loading={bulkMutation.isPending}
              onClick={() => runBulk({ taskIds: selectedIds, action: 'DELETE' })}
            >
              Delete selected
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={bulkValueAction !== null}
        onOpenChange={(open) => !open && setBulkValueAction(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {bulkValueAction === 'SET_PRIORITY'
                ? 'Set priority'
                : bulkValueAction === 'MOVE_TO_GOAL'
                  ? 'Move to goal'
                  : bulkValueAction === 'MOVE_TO_PROJECT'
                    ? 'Move to project'
                    : 'Set category'}
            </DialogTitle>
            <DialogDescription>
              Applies to {selected.size} selected task{selected.size === 1 ? '' : 's'}.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-1.5">
            {bulkValueAction === 'SET_PRIORITY' ? (
              <>
                <Label htmlFor="bulk-priority">Priority</Label>
                <Select
                  value={bulkPriority}
                  onValueChange={(value) => setBulkPriority(value as Priority)}
                >
                  <SelectTrigger id="bulk-priority">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PRIORITY_OPTIONS.map((priority) => (
                      <SelectItem key={priority} value={priority}>
                        {titleCase(priority)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </>
            ) : null}

            {bulkValueAction === 'MOVE_TO_GOAL' ? (
              <>
                <Label htmlFor="bulk-goal">Goal</Label>
                <Select value={bulkGoalId} onValueChange={setBulkGoalId}>
                  <SelectTrigger id="bulk-goal">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_VALUE}>Remove from goal</SelectItem>
                    {goalOptions.map((goal) => (
                      <SelectItem key={goal.id} value={goal.id}>
                        {goal.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </>
            ) : null}

            {bulkValueAction === 'MOVE_TO_PROJECT' ? (
              <>
                <Label htmlFor="bulk-project">Project id</Label>
                <Input
                  id="bulk-project"
                  value={bulkProjectId}
                  placeholder="Leave blank to detach"
                  onChange={(event) => setBulkProjectId(event.target.value)}
                />
              </>
            ) : null}

            {bulkValueAction === 'SET_CATEGORY' ? (
              <>
                <Label htmlFor="bulk-category">Category</Label>
                <Input
                  id="bulk-category"
                  maxLength={48}
                  value={bulkCategory}
                  placeholder="Leave blank to clear"
                  onChange={(event) => setBulkCategory(event.target.value)}
                />
              </>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setBulkValueAction(null)}>
              Cancel
            </Button>
            <Button
              loading={bulkMutation.isPending}
              onClick={() => {
                if (bulkValueAction === 'SET_PRIORITY') {
                  runBulk({ taskIds: selectedIds, action: 'SET_PRIORITY', priority: bulkPriority })
                } else if (bulkValueAction === 'MOVE_TO_GOAL') {
                  runBulk({
                    taskIds: selectedIds,
                    action: 'MOVE_TO_GOAL',
                    goalId: bulkGoalId === NO_VALUE ? '' : bulkGoalId,
                  })
                } else if (bulkValueAction === 'MOVE_TO_PROJECT') {
                  runBulk({
                    taskIds: selectedIds,
                    action: 'MOVE_TO_PROJECT',
                    projectId: bulkProjectId.trim() || '',
                  })
                } else if (bulkValueAction === 'SET_CATEGORY') {
                  runBulk({
                    taskIds: selectedIds,
                    action: 'SET_CATEGORY',
                    category: bulkCategory.trim(),
                  })
                }
              }}
            >
              Apply
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}