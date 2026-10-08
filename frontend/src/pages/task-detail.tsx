import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowLeft,
  CalendarClock,
  CheckCircle2,
  Link2,
  Plus,
  RefreshCw,
  Repeat,
  Save,
  Trash2,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { taskApi } from '@/api/endpoints'
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
import { LoadingPanel, Spinner } from '@/components/ui/skeleton'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge, toneClasses, toneFor } from '@/lib/badges'
import {
  formatDate,
  formatDateTime,
  formatMinutes,
  formatRelative,
  fromLocalDateTimeInput,
  toLocalDateTimeInput,
  titleCase,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import type {
  Difficulty,
  EnergyRequirement,
  Priority,
  TaskRequest,
  TaskResponse,
  TaskStatus,
} from '@/types/api'

const STATUS_OPTIONS: readonly TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
const PRIORITY_OPTIONS: readonly Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const DIFFICULTY_OPTIONS: readonly Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const ENERGY_OPTIONS: readonly EnergyRequirement[] = ['LOW', 'MEDIUM', 'HIGH']

interface TaskFormState {
  title: string
  description: string
  notes: string
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

function toForm(task: TaskResponse): TaskFormState {
  return {
    title: task.title,
    description: task.description ?? '',
    notes: task.notes ?? '',
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

function toRequest(form: TaskFormState, status: TaskStatus): TaskRequest {
  return {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    notes: form.notes.trim() || undefined,
    status,
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

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="truncate font-medium">{value}</span>
    </div>
  )
}

export default function TaskDetailPage() {
  const { taskId } = useParams<{ taskId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const id = taskId ?? ''

  const [form, setForm] = useState<TaskFormState | null>(null)
  const [saveError, setSaveError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [completeOpen, setCompleteOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [completeMinutes, setCompleteMinutes] = useState('')
  const [completeNotes, setCompleteNotes] = useState('')
  const [newDependencyId, setNewDependencyId] = useState('')

  const taskQuery = useQuery({
    queryKey: ['tasks', 'detail', id],
    queryFn: () => taskApi.get(id),
    enabled: Boolean(id),
  })

  const dependenciesQuery = useQuery({
    queryKey: ['tasks', 'dependencies', id],
    queryFn: () => taskApi.dependencies(id),
    enabled: Boolean(id),
  })

  const candidatesQuery = useQuery({
    queryKey: ['tasks', 'dependency-candidates'],
    queryFn: () => taskApi.list({ size: 200, status: [...STATUS_OPTIONS], sort: 'title' }),
  })

  const task = taskQuery.data

  useEffect(() => {
    if (task) setForm(toForm(task))
  }, [task])

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tasks'] }),
      queryClient.invalidateQueries({ queryKey: ['goals'] }),
      queryClient.invalidateQueries({ queryKey: ['focus'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  }

  const updateMutation = useMutation({
    mutationFn: (request: TaskRequest) => taskApi.update(id, request),
    onSuccess: async () => {
      setSaveError(undefined)
      setFieldErrors({})
      await invalidate()
      toast.success('Task saved')
    },
    onError: (error) => {
      const normalised = toNormalisedError(error)
      setSaveError(normalised.message)
      setFieldErrors(normalised.fieldErrors)
    },
  })

  const statusMutation = useMutation({
    mutationFn: (status: TaskStatus) => taskApi.updateStatus(id, { status }),
    onSuccess: async (updated) => {
      setForm(toForm(updated))
      await invalidate()
      toast.success(`Status changed to ${titleCase(updated.status)}`)
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const completeMutation = useMutation({
    mutationFn: (body: { actualMinutes?: number; notes?: string }) => taskApi.complete(id, body),
    onSuccess: async () => {
      setCompleteOpen(false)
      setCompleteMinutes('')
      setCompleteNotes('')
      await invalidate()
      toast.success('Task completed')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const deleteMutation = useMutation({
    mutationFn: () => taskApi.remove(id),
    onSuccess: async () => {
      await invalidate()
      toast.success('Task deleted')
      navigate('/app/tasks')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const addDependencyMutation = useMutation({
    mutationFn: (dependencyId: string) => taskApi.addDependency(id, { taskId: dependencyId }),
    onSuccess: async () => {
      setNewDependencyId('')
      await invalidate()
      toast.success('Dependency added')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeDependencyMutation = useMutation({
    mutationFn: (dependencyId: string) => taskApi.removeDependency(id, dependencyId),
    onSuccess: async () => {
      await invalidate()
      toast.success('Dependency removed')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const materialiseMutation = useMutation({
    mutationFn: () => taskApi.materialiseRecurrence(id),
    onSuccess: async (response) => {
      const created = typeof response?.created === 'number' ? response.created : 0
      await invalidate()
      if (created === 0) {
        toast.info('Nothing to materialise yet', {
          description: 'The rule has not reached its next occurrence or has already been expanded.',
        })
        return
      }
      toast.success(`Created ${created} occurrence${created === 1 ? '' : 's'}`)
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const candidates = useMemo(() => {
    const content = candidatesQuery.data?.content ?? []
    const existing = new Set(dependenciesQuery.data ?? [])
    return content.filter((candidate) => candidate.id !== id && !existing.has(candidate.id))
  }, [candidatesQuery.data, dependenciesQuery.data, id])

  const titleById = useMemo(() => {
    const map = new Map<string, string>()
    for (const candidate of candidatesQuery.data?.content ?? []) map.set(candidate.id, candidate.title)
    return map
  }, [candidatesQuery.data])

  if (!id) {
    return (
      <EmptyState
        title="No task selected"
        description="Open a task from the list to see its detail."
        action={
          <Link to="/app/tasks">
            <Button variant="outline">Back to tasks</Button>
          </Link>
        }
      />
    )
  }

  if (taskQuery.isPending) {
    return <LoadingPanel label="Loading task" />
  }

  if (taskQuery.error || !task) {
    const normalised = toNormalisedError(taskQuery.error)
    return (
      <div className="space-y-4">
        <PageHeader title="Task not found" />
        <EmptyState
          icon={<AlertTriangle />}
          title="Task not found"
          description={
            normalised.status === 404
              ? 'This task does not exist any more, or it belongs to another account.'
              : normalised.message
          }
          action={
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={() => void taskQuery.refetch()}>
                <RefreshCw /> Try again
              </Button>
              <Link to="/app/tasks">
                <Button>Back to tasks</Button>
              </Link>
            </div>
          }
        />
      </div>
    )
  }

  if (!form) return <LoadingPanel label="Preparing editor" />

  const set = <K extends keyof TaskFormState>(key: K, value: TaskFormState[K]) =>
    setForm((current) => (current ? { ...current, [key]: value } : current))

  const submit = (event: FormEvent) => {
    event.preventDefault()
    updateMutation.mutate(toRequest(form, task.status))
  }

  const dependencyIds = dependenciesQuery.data ?? []
  const hasRecurrence = Boolean(task.recurrenceRule)

  return (
    <div className="space-y-6">
      <PageHeader
        title={task.title}
        description={`Updated ${formatRelative(task.updatedAt)}`}
        actions={
          <>
            <Link to="/app/tasks">
              <Button variant="outline">
                <ArrowLeft /> Back
              </Button>
            </Link>
            <Button
              variant="destructive"
              onClick={() => setDeleteOpen(true)}
              loading={deleteMutation.isPending}
            >
              <Trash2 /> Delete
            </Button>
          </>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge value={task.status} label={titleCase(task.status)} />
        <StatusBadge value={task.priority} label={titleCase(task.priority)} />
        {task.category ? <Badge variant="outline">{task.category}</Badge> : null}
        {task.tags?.map((tag) => (
          <Badge key={tag} variant="secondary">
            {tag}
          </Badge>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <form onSubmit={submit} className="space-y-6 lg:col-span-2" noValidate>
          <SectionCard title="Details" description="Every editable field of this task.">
            <div className="space-y-4">
              {saveError ? (
                <p
                  role="alert"
                  className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                >
                  {saveError}
                </p>
              ) : null}

              <div className="space-y-1.5">
                <Label htmlFor="detail-title">Title</Label>
                <Input
                  id="detail-title"
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
                  <Label htmlFor="detail-priority">Priority</Label>
                  <Select
                    value={form.priority}
                    onValueChange={(value) => set('priority', value as Priority)}
                  >
                    <SelectTrigger id="detail-priority">
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
                  <Label htmlFor="detail-category">Category</Label>
                  <Input
                    id="detail-category"
                    maxLength={48}
                    value={form.category}
                    onChange={(event) => set('category', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-deadline">Deadline</Label>
                  <Input
                    id="detail-deadline"
                    type="datetime-local"
                    value={form.deadline}
                    onChange={(event) => set('deadline', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-tags">Tags</Label>
                  <Input
                    id="detail-tags"
                    placeholder="comma, separated"
                    value={form.tags}
                    onChange={(event) => set('tags', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-estimated">Estimated minutes</Label>
                  <Input
                    id="detail-estimated"
                    type="number"
                    min={1}
                    value={form.estimatedMinutes}
                    onChange={(event) => set('estimatedMinutes', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-actual">Actual minutes</Label>
                  <Input
                    id="detail-actual"
                    type="number"
                    min={0}
                    value={form.actualMinutes}
                    onChange={(event) => set('actualMinutes', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-difficulty">Difficulty</Label>
                  <Select
                    value={form.difficulty}
                    onValueChange={(value) => set('difficulty', value as Difficulty)}
                  >
                    <SelectTrigger id="detail-difficulty">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DIFFICULTY_OPTIONS.map((difficulty) => (
                        <SelectItem key={difficulty} value={difficulty}>
                          {titleCase(difficulty)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-energy">Energy required</Label>
                  <Select
                    value={form.energyRequirement}
                    onValueChange={(value) => set('energyRequirement', value as EnergyRequirement)}
                  >
                    <SelectTrigger id="detail-energy">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {ENERGY_OPTIONS.map((energy) => (
                        <SelectItem key={energy} value={energy}>
                          {titleCase(energy)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-goal">Goal id</Label>
                  <Input
                    id="detail-goal"
                    value={form.goalId}
                    onChange={(event) => set('goalId', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-project">Project id</Label>
                  <Input
                    id="detail-project"
                    value={form.projectId}
                    onChange={(event) => set('projectId', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-milestone">Milestone id</Label>
                  <Input
                    id="detail-milestone"
                    value={form.milestoneId}
                    onChange={(event) => set('milestoneId', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-position">Position</Label>
                  <Input
                    id="detail-position"
                    type="number"
                    min={0}
                    value={form.position}
                    onChange={(event) => set('position', event.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-recurrence">Recurrence rule</Label>
                  <Input
                    id="detail-recurrence"
                    maxLength={120}
                    placeholder="FREQ=WEEKLY;BYDAY=MO"
                    value={form.recurrenceRule}
                    onChange={(event) => set('recurrenceRule', event.target.value)}
                  />
                  <p className="text-xs text-muted-foreground">
                    The server owns this rule. Saving stores the rule only; occurrences appear once you
                    request materialisation.
                  </p>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="detail-recurrence-end">Recurrence ends</Label>
                  <Input
                    id="detail-recurrence-end"
                    type="datetime-local"
                    value={form.recurrenceEndDate}
                    onChange={(event) => set('recurrenceEndDate', event.target.value)}
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="detail-description">Description</Label>
                <Textarea
                  id="detail-description"
                  rows={4}
                  maxLength={20000}
                  value={form.description}
                  onChange={(event) => set('description', event.target.value)}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="detail-notes">Notes</Label>
                <Textarea
                  id="detail-notes"
                  rows={4}
                  maxLength={20000}
                  value={form.notes}
                  onChange={(event) => set('notes', event.target.value)}
                />
              </div>

              <div className="flex items-center gap-2">
                <Button type="submit" loading={updateMutation.isPending}>
                  <Save /> Save changes
                </Button>
                {updateMutation.isPending ? <Spinner label="Saving" /> : null}
              </div>
            </div>
          </SectionCard>

          <SectionCard
            title="Dependencies"
            description="This task waits for the tasks below to finish before it can close."
          >
            {dependenciesQuery.isPending ? (
              <Spinner label="Loading dependencies" />
            ) : dependencyIds.length === 0 ? (
              <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                Nothing blocks this task.
              </p>
            ) : (
              <ul className="divide-y">
                {dependencyIds.map((dependencyId) => (
                  <li key={dependencyId} className="flex items-center justify-between gap-3 py-2">
                    <Link
                      to={`/app/tasks/${dependencyId}`}
                      className="min-w-0 flex-1 truncate text-sm hover:text-primary hover:underline"
                    >
                      {titleById.get(dependencyId) ?? dependencyId}
                    </Link>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Remove dependency ${titleById.get(dependencyId) ?? dependencyId}`}
                      disabled={removeDependencyMutation.isPending}
                      onClick={() => removeDependencyMutation.mutate(dependencyId)}
                    >
                      <X />
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            <div className="mt-4 space-y-1.5 border-t pt-4">
              <Label htmlFor="new-dependency">Add a dependency</Label>
              <Select
                value={newDependencyId}
                onValueChange={setNewDependencyId}
                disabled={candidates.length === 0}
              >
                <SelectTrigger id="new-dependency">
                  <SelectValue placeholder="Choose a task" />
                </SelectTrigger>
                <SelectContent>
                  {candidates.map((candidate) => (
                    <SelectItem key={candidate.id} value={candidate.id}>
                      {candidate.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                variant="secondary"
                disabled={!newDependencyId || addDependencyMutation.isPending}
                onClick={() => addDependencyMutation.mutate(newDependencyId)}
              >
                <Plus /> Add dependency
              </Button>
            </div>

            {task.blocks?.length ? (
              <div className="mt-5 space-y-2 border-t pt-4">
                <p className="text-sm font-medium text-muted-foreground">Blocks</p>
                <ul className="space-y-1.5">
                  {task.blocks.map((blocked) => (
                    <li key={blocked.taskId} className="flex items-center gap-2 text-sm">
                      <Link2 className="size-3.5 text-muted-foreground" aria-hidden />
                      <Link
                        to={`/app/tasks/${blocked.taskId}`}
                        className="min-w-0 flex-1 truncate hover:text-primary hover:underline"
                      >
                        {blocked.title}
                      </Link>
                      <span
                        className={cn(
                          'inline-flex items-center rounded-full px-2 py-0.5 text-xs',
                          toneClasses(toneFor(blocked.status)),
                        )}
                      >
                        {titleCase(blocked.status)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </SectionCard>
        </form>

        <div className="space-y-6">
          <SectionCard title="State" description="Status and completion are handled by the server.">
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="detail-status">Status</Label>
                <Select
                  value={task.status}
                  onValueChange={(value) => statusMutation.mutate(value as TaskStatus)}
                  disabled={statusMutation.isPending}
                >
                  <SelectTrigger id="detail-status">
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

              <Button
                className="w-full"
                variant="secondary"
                onClick={() => setCompleteOpen(true)}
                disabled={task.status === 'COMPLETED'}
              >
                <CheckCircle2 /> Mark complete
              </Button>

              <p className="text-xs text-muted-foreground">
                Completion records the minutes you actually spent and how the work finished.
              </p>
            </div>
          </SectionCard>

          <SectionCard title="Recurrence" description="The server expands the rule; the client only asks.">
            {hasRecurrence ? (
              <div className="space-y-3">
                <p className="font-mono text-xs">{task.recurrenceRule}</p>
                <Button
                  variant="secondary"
                  className="w-full"
                  loading={materialiseMutation.isPending}
                  onClick={() => materialiseMutation.mutate()}
                >
                  <Repeat /> Materialise next occurrence
                </Button>
                <p className="text-xs text-muted-foreground">
                  Nothing is generated in the background. Each press asks the server to expand the rule up
                  to the next occurrence and reports exactly how many tasks it created.
                </p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                This task does not repeat. Add a recurrence rule above and save to schedule it.
              </p>
            )}
          </SectionCard>

          <SectionCard title="Metadata" description="Read-only information owned by the server.">
            <div className="divide-y">
              <MetaRow label="Created" value={formatDateTime(task.createdAt)} />
              <MetaRow label="Updated" value={formatDateTime(task.updatedAt)} />
              <MetaRow
                label="Deadline"
                value={task.deadline ? formatDateTime(task.deadline) : 'None'}
              />
              <MetaRow label="Planned" value={formatMinutes(task.estimatedMinutes)} />
              <MetaRow label="Spent" value={formatMinutes(task.actualMinutes)} />
              <MetaRow
                label="Completed at"
                value={task.completedAt ? formatDateTime(task.completedAt) : 'Not completed'}
              />
              <MetaRow label="Recurrence parent" value={task.recurrenceParentId || 'None'} />
              <MetaRow label="Recurrence ends" value={formatDate(task.recurrenceEndDate)} />
              <MetaRow label="Milestone id" value={task.milestoneId || 'None'} />
              <MetaRow label="Project id" value={task.projectId || 'None'} />
              <MetaRow label="Goal id" value={task.goalId || 'None'} />
              <MetaRow label="Task id" value={task.id} />
            </div>
            <p className="mt-3 inline-flex items-center gap-1.5 text-xs text-muted-foreground">
              <CalendarClock className="size-3.5" aria-hidden />
              Timestamps are shown in your local time zone.
            </p>
          </SectionCard>
        </div>
      </div>

      <Dialog open={completeOpen} onOpenChange={setCompleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Complete task</DialogTitle>
            <DialogDescription>
              Record what actually happened. Both fields are optional.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="complete-minutes">Actual minutes</Label>
              <Input
                id="complete-minutes"
                type="number"
                min={0}
                placeholder={task.estimatedMinutes ? String(task.estimatedMinutes) : '0'}
                value={completeMinutes}
                onChange={(event) => setCompleteMinutes(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="complete-notes">Completion notes</Label>
              <Textarea
                id="complete-notes"
                rows={3}
                maxLength={20000}
                value={completeNotes}
                onChange={(event) => setCompleteNotes(event.target.value)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCompleteOpen(false)}>
              Cancel
            </Button>
            <Button
              loading={completeMutation.isPending}
              onClick={() =>
                completeMutation.mutate({
                  actualMinutes: toNumber(completeMinutes),
                  notes: completeNotes.trim() || undefined,
                })
              }
            >
              Complete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete task</DialogTitle>
            <DialogDescription>
              {`"${task.title}" and its dependency links will be removed permanently.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              Keep task
            </Button>
            <Button
              variant="destructive"
              loading={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate()}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}