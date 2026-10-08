import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Info, Link2, Network, Plus, Route, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { graphApi } from '@/api/endpoints'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/lib/badges'
import { titleCase } from '@/lib/format'
import { cn } from '@/lib/utils'
import { toNormalisedError } from '@/hooks/use-api-error'
import type {
  GraphEdge,
  GraphEdgeRequest,
  GraphNeighbourhoodResponse,
  GraphNode,
  GraphNodeRequest,
  GraphNodeType,
  GraphRelation,
} from '@/types/api'

const NODE_TYPES: GraphNodeType[] = [
  'GOAL',
  'TASK',
  'HABIT',
  'SKILL',
  'PROJECT',
  'LEARNING_GOAL',
  'LEARNING_TOPIC',
  'CALENDAR_EVENT',
  'FOCUS_SESSION',
  'JOURNAL_ENTRY',
  'KNOWLEDGE_DOCUMENT',
]

const RELATIONS: { value: GraphRelation; label: string }[] = [
  { value: 'CONTRIBUTES_TO', label: 'Contributes to' },
  { value: 'DEPENDS_ON', label: 'Depends on' },
  { value: 'RELATED_TO', label: 'Related to' },
  { value: 'SUPPORTS', label: 'Supports' },
  { value: 'DERIVED_FROM', label: 'Derived from' },
]

const TYPE_COLOURS: Record<string, string> = {
  GOAL: '#38bdf8',
  TASK: '#a78bfa',
  HABIT: '#fbbf24',
  SKILL: '#34d399',
  PROJECT: '#f472b6',
  LEARNING_GOAL: '#c084fc',
  LEARNING_TOPIC: '#818cf8',
  CALENDAR_EVENT: '#fb923c',
  FOCUS_SESSION: '#22d3ee',
  JOURNAL_ENTRY: '#f87171',
  KNOWLEDGE_DOCUMENT: '#94a3b8',
}

const FALLBACK_COLOUR = '#64748b'

const VIEW_WIDTH = 1000
const VIEW_HEIGHT = 620
const NODE_MIN_RADIUS = 10
const NODE_MAX_RADIUS = 26

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

function isNodeType(value: string): value is GraphNodeType {
  return (NODE_TYPES as string[]).includes(value)
}

/** Only forward the relation filter when the server actually used a relation the UI knows. */
function asRelation(value: string): GraphRelation | undefined {
  const known = RELATIONS.map((option) => option.value) as string[]
  return known.includes(value) ? (value as GraphRelation) : undefined
}

function colourFor(node: GraphNode): string {
  if (node.color && /^#[0-9a-f]{3,8}$/i.test(node.color)) return node.color
  return TYPE_COLOURS[node.type] ?? FALLBACK_COLOUR
}

interface Placed {
  node: GraphNode
  x: number
  y: number
  r: number
  colour: string
}

/**
 * Concentric rings by node type. Deterministic and cheap, so the canvas stays put while the user
 * hovers, selects and filters instead of reshuffling on every render.
 */
function layoutNodes(nodes: GraphNode[]): Placed[] {
  const grouped = new Map<string, GraphNode[]>()
  for (const node of nodes) {
    const bucket = grouped.get(node.type)
    if (bucket) bucket.push(node)
    else grouped.set(node.type, [node])
  }

  const types = [...grouped.keys()].sort()
  const centreX = VIEW_WIDTH / 2
  const centreY = VIEW_HEIGHT / 2
  const outerRadius = Math.floor(Math.min(VIEW_WIDTH, VIEW_HEIGHT) / 2) - 70
  const placed: Placed[] = []

  types.forEach((type, typeIndex) => {
    const group = grouped.get(type) ?? []
    const radius = types.length === 1 ? 0 : (outerRadius * (typeIndex + 1)) / types.length
    group.forEach((node, index) => {
      const angle = (Math.PI * 2 * index) / group.length - Math.PI / 2
      placed.push({
        node,
        x: centreX + radius * Math.cos(angle),
        y: centreY + radius * Math.sin(angle),
        r:
          NODE_MIN_RADIUS +
          (NODE_MAX_RADIUS - NODE_MIN_RADIUS) * Math.min(1, Math.max(0, (node.weight ?? 1) / 10)),
        colour: colourFor(node),
      })
    })
  })

  return placed
}

function coerceDetail(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value === '' ? '—' : value
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '—'
  if (typeof value === 'boolean') return value ? 'yes' : 'no'
  if (Array.isArray(value)) {
    if (value.length === 0) return '—'
    return value.map((item) => coerceDetail(item)).join(', ')
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function NodeTypeFilter({
  active,
  counts,
  onToggle,
}: {
  active: GraphNodeType[]
  counts: Map<string, number>
  onToggle: (type: GraphNodeType) => void
}) {
  return (
    <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter node types">
      {NODE_TYPES.map((type) => {
        const on = active.includes(type)
        return (
          <button
            key={type}
            type="button"
            onClick={() => onToggle(type)}
            aria-pressed={on}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs transition-colors',
              on ? 'border-primary bg-primary/15 text-primary' : 'text-muted-foreground hover:bg-secondary/60',
            )}
          >
            <span
              className="size-2 rounded-full"
              style={{ backgroundColor: TYPE_COLOURS[type] ?? FALLBACK_COLOUR }}
              aria-hidden
            />
            {titleCase(type)}
            <span className="tabular-nums opacity-70">{counts.get(type) ?? 0}</span>
          </button>
        )
      })}
    </div>
  )
}

function NodePicker({
  id,
  label,
  type,
  nodes,
  value,
  onChange,
}: {
  id: string
  label: string
  type: GraphNodeType
  nodes: GraphNode[]
  value: string
  onChange: (nodeId: string) => void
}) {
  const [query, setQuery] = useState('')
  const candidates = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return nodes
      .filter((node) => node.type === type)
      .filter((node) =>
        needle === '' ? true : `${node.label} ${node.subtitle} ${node.id}`.toLowerCase().includes(needle),
      )
      .slice(0, 40)
  }, [nodes, type, query])

  return (
    <div className="space-y-1.5">
      <Label htmlFor={`${id}-search`}>{label}</Label>
      <Input
        id={`${id}-search`}
        value={query}
        placeholder={`Search ${titleCase(type).toLowerCase()} nodes`}
        onChange={(event) => setQuery(event.target.value)}
      />
      <div className="max-h-40 overflow-y-auto rounded-md border">
        {candidates.length === 0 ? (
          <p className="p-3 text-xs text-muted-foreground">
            No {titleCase(type).toLowerCase()} nodes in the current view. Clear the type chips, or
            create the node first — its id comes back from the API.
          </p>
        ) : (
          <ul className="divide-y">
            {candidates.map((node) => (
              <li key={`${node.type}-${node.id}`}>
                <button
                  type="button"
                  onClick={() => onChange(node.id)}
                  aria-pressed={value === node.id}
                  className={cn(
                    'flex w-full items-center gap-2 px-2.5 py-1.5 text-left text-xs hover:bg-secondary/60',
                    value === node.id && 'bg-primary/15 text-primary',
                  )}
                >
                  <span
                    className="size-2 shrink-0 rounded-full"
                    style={{ backgroundColor: colourFor(node) }}
                    aria-hidden
                  />
                  <span className="min-w-0 flex-1 truncate">{node.label || node.id}</span>
                  {node.subtitle ? (
                    <span className="max-w-32 truncate text-muted-foreground">{node.subtitle}</span>
                  ) : null}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="text-xs text-muted-foreground">
        Selected id: <span className="font-mono text-foreground">{value || 'none'}</span>
      </p>
    </div>
  )
}

interface EdgeFormValues {
  sourceType: GraphNodeType
  sourceId: string
  targetType: GraphNodeType
  targetId: string
  relation: GraphRelation
  weight: string
}

function EdgeDialog({
  open,
  nodes,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  nodes: GraphNode[]
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: EdgeFormValues) => void
}) {
  const empty: EdgeFormValues = {
    sourceType: 'GOAL',
    sourceId: '',
    targetType: 'TASK',
    targetId: '',
    relation: 'CONTRIBUTES_TO',
    weight: '1',
  }
  const { register, control, handleSubmit, watch, reset, setValue } = useForm<EdgeFormValues>({
    defaultValues: empty,
  })
  const sourceType = watch('sourceType')
  const targetType = watch('targetType')
  const sourceId = watch('sourceId')
  const targetId = watch('targetId')

  const close = (next: boolean) => {
    if (!next) reset(empty)
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Link two nodes</DialogTitle>
          <DialogDescription>
            Both ends must already exist. Any node you create here gets its id from the API, so add
            the node first and then pick it from the list below.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Source type</Label>
              <Controller
                control={control}
                name="sourceType"
                render={({ field }) => (
                  <Select
                    value={field.value}
                    onValueChange={(value) => {
                      field.onChange(value as GraphNodeType)
                      setValue('sourceId', '')
                    }}
                  >
                    <SelectTrigger aria-label="Source node type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {NODE_TYPES.map((type) => (
                        <SelectItem key={type} value={type}>
                          {titleCase(type)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Target type</Label>
              <Controller
                control={control}
                name="targetType"
                render={({ field }) => (
                  <Select
                    value={field.value}
                    onValueChange={(value) => {
                      field.onChange(value as GraphNodeType)
                      setValue('targetId', '')
                    }}
                  >
                    <SelectTrigger aria-label="Target node type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {NODE_TYPES.map((type) => (
                        <SelectItem key={type} value={type}>
                          {titleCase(type)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <NodePicker
              id="edge-source"
              label="Source node"
              type={sourceType}
              nodes={nodes}
              value={sourceId}
              onChange={(nodeId) => setValue('sourceId', nodeId)}
            />
            <NodePicker
              id="edge-target"
              label="Target node"
              type={targetType}
              nodes={nodes}
              value={targetId}
              onChange={(nodeId) => setValue('targetId', nodeId)}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Relation</Label>
              <Controller
                control={control}
                name="relation"
                render={({ field }) => (
                  <Select
                    value={field.value}
                    onValueChange={(value) => field.onChange(value as GraphRelation)}
                  >
                    <SelectTrigger aria-label="Relation">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {RELATIONS.map((option) => (
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
              <Label htmlFor="edge-weight">Weight</Label>
              <Input id="edge-weight" type="number" min={1} {...register('weight')} />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy} disabled={!sourceId || !targetId}>
              Create link
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function NodeDialog({
  open,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: { type: GraphNodeType; label: string; subtitle: string; weight: string }) => void
}) {
  const empty = { type: 'HABIT' as GraphNodeType, label: '', subtitle: '', weight: '3' }
  const { register, control, handleSubmit, reset, formState } = useForm<typeof empty>({
    defaultValues: empty,
  })

  const close = (next: boolean) => {
    if (!next) reset(empty)
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Add a graph node</DialogTitle>
          <DialogDescription>
            The API assigns the id and returns the created node, so it becomes linkable as soon as
            the graph reloads.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-1.5">
            <Label>Type</Label>
            <Controller
              control={control}
              name="type"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as GraphNodeType)}
                >
                  <SelectTrigger aria-label="Node type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {NODE_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {titleCase(type)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="node-label">Label</Label>
            <Input
              id="node-label"
              required
              maxLength={200}
              aria-invalid={Boolean(formState.errors.label)}
              {...register('label', { required: 'A label is required' })}
            />
            {formState.errors.label ? (
              <p className="text-xs text-destructive">{formState.errors.label.message}</p>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="node-subtitle">Subtitle</Label>
              <Input id="node-subtitle" maxLength={200} {...register('subtitle')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="node-weight">Weight</Label>
              <Input id="node-weight" type="number" min={1} max={10} {...register('weight')} />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Create node
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface ConfirmState {
  title: string
  description: string
  confirmLabel: string
  run: () => void
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

function describeEdge(edge: GraphEdge, nodes: GraphNode[]): string {
  const source = nodes.find((node) => node.id === edge.source)
  const target = nodes.find((node) => node.id === edge.target)
  return `${source?.label ?? edge.source} —${titleCase(edge.relation)}→ ${target?.label ?? edge.target}`
}

export default function LifeGraphPage() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['graph'] })

  const [types, setTypes] = useState<GraphNodeType[]>([])
  const [hoveredId, setHoveredId] = useState<string>()
  const [selectedId, setSelectedId] = useState<string>()
  const [placed, setPlaced] = useState<Placed[]>([])
  const [edgeOpen, setEdgeOpen] = useState(false)
  const [nodeOpen, setNodeOpen] = useState(false)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)
  const [pathSegments, setPathSegments] = useState<string[]>()

  const graph = useQuery({
    queryKey: ['graph', 'graph', types],
    queryFn: () => graphApi.graph({ types: types.length > 0 ? types : undefined }),
  })

  const stats = useQuery({ queryKey: ['graph', 'stats'], queryFn: () => graphApi.stats() })

  const nodes = useMemo(() => graph.data?.nodes ?? [], [graph.data])
  const edges = useMemo(() => graph.data?.edges ?? [], [graph.data])

  useEffect(() => {
    setPlaced(layoutNodes(nodes))
  }, [nodes])

  const placedById = useMemo(() => new Map(placed.map((item) => [item.node.id, item])), [placed])

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedId),
    [nodes, selectedId],
  )

  const neighbourhood = useQuery<GraphNeighbourhoodResponse>({
    queryKey: ['graph', 'neighbourhood', selectedId],
    queryFn: () => {
      const node = nodes.find((item) => item.id === selectedId)
      if (!node || !isNodeType(node.type)) {
        throw new Error('The API only resolves neighbourhoods for node types it recognises.')
      }
      return graphApi.neighbourhood(node.type, node.id)
    },
    enabled: selectedId !== undefined && selectedNode !== undefined && isNodeType(selectedNode.type),
  })

  const adjacentIds = useMemo(() => {
    const focus = hoveredId ?? selectedId
    if (!focus) return null
    const ids = new Set<string>([focus])
    for (const edge of edges) {
      if (edge.source === focus) ids.add(edge.target)
      if (edge.target === focus) ids.add(edge.source)
    }
    return ids
  }, [hoveredId, selectedId, edges])

  const selectedEdges = useMemo(
    () =>
      selectedId === undefined
        ? []
        : edges.filter((edge) => edge.source === selectedId || edge.target === selectedId),
    [edges, selectedId],
  )

  const typeCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const node of nodes) counts.set(node.type, (counts.get(node.type) ?? 0) + 1)
    return counts
  }, [nodes])

  const addEdge = useMutation({
    mutationFn: (values: EdgeFormValues) => {
      const body: GraphEdgeRequest = {
        sourceType: values.sourceType,
        sourceId: values.sourceId,
        targetType: values.targetType,
        targetId: values.targetId,
        relation: values.relation,
        weight: values.weight === '' ? undefined : Number(values.weight),
      }
      return graphApi.addEdge(body)
    },
    onSuccess: () => {
      toast.success('Link created')
      setEdgeOpen(false)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeEdge = useMutation({
    mutationFn: (vars: {
      sourceType: GraphNodeType
      sourceId: string
      targetType: GraphNodeType
      targetId: string
      relation?: GraphRelation
    }) => graphApi.removeEdge(vars),
    onSuccess: () => {
      toast.success('Link removed')
      setConfirm(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const addNode = useMutation({
    mutationFn: (values: { type: GraphNodeType; label: string; subtitle: string; weight: string }) => {
      const body: GraphNodeRequest = {
        type: values.type,
        label: values.label.trim(),
        subtitle: values.subtitle.trim() || undefined,
        weight: values.weight === '' ? undefined : Number(values.weight),
      }
      return graphApi.addNode(body)
    },
    onSuccess: (created) => {
      toast.success(`Node created: ${created.label || created.id}`)
      setNodeOpen(false)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeNode = useMutation({
    mutationFn: (vars: { type: GraphNodeType; nodeId: string }) =>
      graphApi.removeNode(vars.type, vars.nodeId),
    onSuccess: () => {
      toast.success('Node removed')
      setConfirm(null)
      setSelectedId(undefined)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const findPaths = useMutation({
    mutationFn: (vars: { type: GraphNodeType; nodeId: string }) =>
      graphApi.paths(vars.type, vars.nodeId),
    onSuccess: (segments) => {
      setPathSegments(segments)
      if (segments.length === 0) toast.info('No path to a goal from that node.')
    },
    onError: (error) => {
      setPathSegments(undefined)
      toast.error(toNormalisedError(error).message)
    },
  })

  const graphEdgeCount = graph.data?.edgeCount ?? edges.length
  const statEdgeCount = stats.data?.count
  const edgeGap =
    statEdgeCount !== undefined && graphEdgeCount !== statEdgeCount
      ? Math.abs(statEdgeCount - graphEdgeCount)
      : 0

  const anyRemovalPending = removeEdge.isPending || removeNode.isPending

  const selectNode = (nodeId: string | undefined) => {
    setSelectedId(nodeId)
    setPathSegments(undefined)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Life graph"
        description="How goals, tasks, habits, skills and everything else connect across your data."
        actions={
          <>
            <Button variant="outline" onClick={() => setNodeOpen(true)}>
              <Plus /> New node
            </Button>
            <Button onClick={() => setEdgeOpen(true)}>
              <Link2 /> Link nodes
            </Button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Nodes in view" value={graph.data?.nodeCount ?? nodes.length} />
        <StatCard label="Edges in view" value={graphEdgeCount} />
        <StatCard label="Edges in account" value={statEdgeCount ?? '—'} hint="From /api/graph/stats" />
        <StatCard
          label="Selected"
          value={selectedNode ? titleCase(selectedNode.type) : 'None'}
          hint={selectedNode?.label}
        />
      </div>

      {edgeGap > 0 ? (
        <p className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            The graph shows {graphEdgeCount} edges while /api/graph/stats reports {statEdgeCount}.{' '}
            {edgeGap} {edgeGap === 1 ? 'edge is' : 'edges are'} hidden by the active type filter,
            because an edge is only drawn when both of its endpoints are in the returned node set.
          </span>
        </p>
      ) : null}

      <SectionCard
        title="Graph"
        description="Hover a node to isolate its edges, click to inspect it."
        action={
          types.length > 0 ? (
            <Button size="sm" variant="ghost" onClick={() => setTypes([])}>
              Clear filter
            </Button>
          ) : null
        }
        contentClassName="space-y-4"
      >
        <NodeTypeFilter
          active={types}
          counts={typeCounts}
          onToggle={(type) =>
            setTypes((current) =>
              current.includes(type) ? current.filter((item) => item !== type) : [...current, type],
            )
          }
        />

        {graph.isError ? (
          <ErrorPanel message={graph.error.message} onRetry={() => void graph.refetch()} />
        ) : graph.isPending ? (
          <Skeleton className="h-[620px] w-full" />
        ) : placed.length === 0 ? (
          <EmptyState
            icon={<Network />}
            title={types.length > 0 ? 'No nodes for this filter' : 'The graph is empty'}
            description={
              types.length > 0
                ? 'Remove a type chip to bring the rest of the graph back.'
                : 'Create a node below, or add goals, tasks and habits elsewhere and they appear here automatically.'
            }
            action={
              types.length > 0 ? (
                <Button variant="outline" onClick={() => setTypes([])}>
                  Clear filter
                </Button>
              ) : (
                <Button onClick={() => setNodeOpen(true)}>
                  <Plus /> Add a node
                </Button>
              )
            }
          />
        ) : (
          <div className="h-[620px] w-full overflow-hidden rounded-lg border bg-secondary/20">
            <svg
              viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
              className="h-full w-full"
              role="img"
              aria-label={`Life graph with ${placed.length} nodes and ${edges.length} edges`}
            >
              <defs>
                <marker
                  id="graph-arrow"
                  viewBox="0 0 10 10"
                  refX="9"
                  refY="5"
                  markerWidth="6"
                  markerHeight="6"
                  orient="auto-start-reverse"
                >
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--color-muted-foreground)" />
                </marker>
              </defs>

              {edges.map((edge) => {
                const from = placedById.get(edge.source)
                const to = placedById.get(edge.target)
                if (!from || !to) return null
                const dimmed = adjacentIds !== null && !(adjacentIds.has(edge.source) && adjacentIds.has(edge.target))
                const dx = to.x - from.x
                const dy = to.y - from.y
                const length = Math.hypot(dx, dy) || 1
                const ux = dx / length
                const uy = dy / length
                return (
                  <g key={edge.id}>
                    <line
                      x1={from.x + ux * (from.r + 2)}
                      y1={from.y + uy * (from.r + 2)}
                      x2={to.x - ux * (to.r + 8)}
                      y2={to.y - uy * (to.r + 8)}
                      stroke="var(--color-muted-foreground)"
                      strokeWidth={Math.max(0.6, Math.min(3, (edge.weight ?? 1) / 2))}
                      markerEnd="url(#graph-arrow)"
                      opacity={dimmed ? 0.08 : 0.55}
                    />
                    {dimmed ? null : (
                      <text
                        x={(from.x + to.x) / 2}
                        y={(from.y + to.y) / 2 - 4}
                        textAnchor="middle"
                        fontSize="9"
                        fill="var(--color-muted-foreground)"
                      >
                        {titleCase(edge.relation)}
                      </text>
                    )}
                  </g>
                )
              })}

              {placed.map((item) => {
                const dimmed = adjacentIds !== null && !adjacentIds.has(item.node.id)
                const selected = item.node.id === selectedId
                return (
                  <g
                    key={`${item.node.type}-${item.node.id}`}
                    opacity={dimmed ? 0.25 : 1}
                    onMouseEnter={() => setHoveredId(item.node.id)}
                    onMouseLeave={() => setHoveredId(undefined)}
                    onClick={() =>
                      selectNode(selectedId === item.node.id ? undefined : item.node.id)
                    }
                    className="cursor-pointer"
                  >
                    <circle
                      cx={item.x}
                      cy={item.y}
                      r={item.r}
                      fill={item.colour}
                      fillOpacity={selected ? 0.95 : 0.7}
                      stroke={selected ? 'var(--color-foreground)' : 'var(--color-border)'}
                      strokeWidth={selected ? 2.5 : 1}
                    />
                    <text
                      x={item.x}
                      y={item.y - item.r - 5}
                      textAnchor="middle"
                      fontSize="11"
                      fontWeight={selected ? 600 : 400}
                      fill="var(--color-foreground)"
                      pointerEvents="none"
                    >
                      {item.node.label || item.node.id}
                    </text>
                    {item.node.subtitle ? (
                      <text
                        x={item.x}
                        y={item.y + item.r + 12}
                        textAnchor="middle"
                        fontSize="9"
                        fill="var(--color-muted-foreground)"
                        pointerEvents="none"
                      >
                        {item.node.subtitle}
                      </text>
                    ) : null}
                  </g>
                )
              })}
            </svg>
          </div>
        )}
      </SectionCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard
          title="Node detail"
          description={selectedNode ? selectedNode.label || selectedNode.id : 'Select a node in the graph.'}
        >
          {!selectedNode ? (
            <EmptyState
              icon={<Network />}
              title="Nothing selected"
              description="Click a node to see its fields, neighbours and the edges that touch it."
            />
          ) : (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge value={selectedNode.status} label={titleCase(selectedNode.status)} />
                <span
                  className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"
                >
                  <span
                    className="size-2 rounded-full"
                    style={{ backgroundColor: colourFor(selectedNode) }}
                    aria-hidden
                  />
                  {titleCase(selectedNode.type)}
                </span>
                <span className="text-xs text-muted-foreground">weight {selectedNode.weight}</span>
              </div>

              <dl className="space-y-1.5 text-sm">
                {Object.entries(selectedNode.detail).map(([key, value]) => (
                  <div key={key} className="flex items-start justify-between gap-3 border-b last:border-0">
                    <dt className="text-muted-foreground">{titleCase(key)}</dt>
                    <dd className="max-w-[60%] break-words text-right">{coerceDetail(value)}</dd>
                  </div>
                ))}
                {Object.keys(selectedNode.detail).length === 0 ? (
                  <p className="text-muted-foreground">This node carries no detail fields.</p>
                ) : null}
              </dl>

              <div className="flex flex-wrap gap-2 border-t pt-3">
                <Button
                  size="sm"
                  variant="outline"
                  loading={findPaths.isPending}
                  disabled={!isNodeType(selectedNode.type)}
                  onClick={() => {
                    setPathSegments(undefined)
                    findPaths.mutate({ type: selectedNode.type as GraphNodeType, nodeId: selectedNode.id })
                  }}
                >
                  <Route /> Paths to goals
                </Button>
                {selectedEdges.map((edge) => (
                  <Button
                    key={edge.id}
                    size="sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    onClick={() => {
                      const source = nodes.find((node) => node.id === edge.source)
                      const target = nodes.find((node) => node.id === edge.target)
                      const sourceType = source?.type ?? ''
                      const targetType = target?.type ?? ''
                      if (!source || !target || !isNodeType(sourceType) || !isNodeType(targetType)) {
                        toast.error('Both endpoints need a recognised type before the link can be removed.')
                        return
                      }
                      setConfirm({
                        title: 'Remove this link?',
                        description: `${describeEdge(edge, nodes)}. The nodes themselves are kept.`,
                        confirmLabel: 'Remove link',
                        run: () =>
                          removeEdge.mutate({
                            sourceType,
                            sourceId: source.id,
                            targetType,
                            targetId: target.id,
                            relation: asRelation(edge.relation),
                          }),
                      })
                    }}
                  >
                    <Trash2 /> Unlink
                  </Button>
                ))}
                <Button
                  size="sm"
                  variant="ghost"
                  className="text-destructive hover:text-destructive"
                  disabled={!isNodeType(selectedNode.type)}
                  onClick={() =>
                    setConfirm({
                      title: `Remove ${selectedNode.label || selectedNode.id}?`,
                      description:
                        'The node and every edge that touches it are removed from the graph.',
                      confirmLabel: 'Remove node',
                      run: () =>
                        removeNode.mutate({
                          type: selectedNode.type as GraphNodeType,
                          nodeId: selectedNode.id,
                        }),
                    })
                  }
                >
                  <Trash2 /> Remove node
                </Button>
              </div>

              {pathSegments ? (
                <div className="space-y-1.5 border-t pt-3">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    Path to goal
                  </p>
                  {pathSegments.length === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      No path from this node reaches a goal.
                    </p>
                  ) : (
                    <nav aria-label="Path to goal" className="flex flex-wrap items-center gap-1">
                      {pathSegments.map((segment, index) => {
                        const node = nodes.find((item) => item.id === segment)
                        const label = node?.label ?? segment
                        return (
                          <span key={`${segment}-${index}`} className="inline-flex items-center gap-1">
                            {index > 0 ? (
                              <ChevronRight className="size-3.5 text-muted-foreground" aria-hidden />
                            ) : null}
                            <button
                              type="button"
                              onClick={() => node && selectNode(node.id)}
                              disabled={!node}
                              className={cn(
                                'rounded-md border px-2 py-0.5 text-xs',
                                node
                                  ? 'hover:border-primary hover:text-primary'
                                  : 'cursor-default text-muted-foreground',
                              )}
                            >
                              {label}
                            </button>
                          </span>
                        )
                      })}
                    </nav>
                  )}
                </div>
              ) : null}
            </div>
          )}
        </SectionCard>

        <SectionCard
          title="Neighbourhood"
          description="What the server reports as directly connected."
        >
          {!selectedNode ? (
            <EmptyState
              icon={<Network />}
              title="Select a node first"
              description="The neighbourhood view is scoped to one node at a time."
            />
          ) : neighbourhood.isError ? (
            <ErrorPanel message={neighbourhood.error.message} onRetry={() => void neighbourhood.refetch()} />
          ) : neighbourhood.isPending ? (
            <Skeleton className="h-48 w-full" />
          ) : (
            <div className="space-y-4">
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Neighbours ({neighbourhood.data.neighbours.length})
                </p>
                {neighbourhood.data.neighbours.length === 0 ? (
                  <p className="mt-1 text-sm text-muted-foreground">
                    Nothing is linked to this node yet.
                  </p>
                ) : (
                  <ul className="mt-2 flex flex-wrap gap-1.5">
                    {neighbourhood.data.neighbours.map((node) => (
                      <li key={`${node.type}-${node.id}`}>
                        <button
                          type="button"
                          onClick={() => selectNode(node.id)}
                          className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs hover:border-primary hover:text-primary"
                        >
                          <span
                            className="size-2 rounded-full"
                            style={{ backgroundColor: colourFor(node) }}
                            aria-hidden
                          />
                          {node.label || node.id}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Edges ({neighbourhood.data.edges.length})
                </p>
                {neighbourhood.data.edges.length === 0 ? (
                  <p className="mt-1 text-sm text-muted-foreground">No edges returned.</p>
                ) : (
                  <ul className="mt-2 space-y-1 text-sm">
                    {neighbourhood.data.edges.map((edge) => (
                      <li key={edge.id} className="flex items-center justify-between gap-2 border-b last:border-0 py-1.5">
                        <span className="min-w-0 truncate">{describeEdge(edge, nodes)}</span>
                        <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                          w{edge.weight}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {neighbourhood.data.pathToGoal.length > 0 ? (
                <div className="space-y-1.5 border-t pt-3">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                    pathToGoal
                  </p>
                  <nav aria-label="Path to goal from neighbourhood" className="flex flex-wrap items-center gap-1">
                    {neighbourhood.data.pathToGoal.map((segment, index) => {
                      const node = nodes.find((item) => item.id === segment)
                      return (
                        <span key={`${segment}-${index}`} className="inline-flex items-center gap-1">
                          {index > 0 ? (
                            <ChevronRight className="size-3.5 text-muted-foreground" aria-hidden />
                          ) : null}
                          <button
                            type="button"
                            onClick={() => node && selectNode(node.id)}
                            disabled={!node}
                            className={cn(
                              'rounded-md border px-2 py-0.5 text-xs',
                              node
                                ? 'hover:border-primary hover:text-primary'
                                : 'cursor-default text-muted-foreground',
                            )}
                          >
                            {node?.label ?? segment}
                          </button>
                        </span>
                      )
                    })}
                  </nav>
                </div>
              ) : null}
            </div>
          )}
        </SectionCard>
      </div>

      <SectionCard
        title="Edges in this view"
        description="Every relation currently drawn, with the endpoints it links."
      >
        {graph.isError ? (
          <ErrorPanel message={graph.error.message} onRetry={() => void graph.refetch()} />
        ) : graph.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : edges.length === 0 ? (
          <EmptyState
            icon={<Link2 />}
            title="No edges yet"
            description="Link two nodes to start building the connections between your goals, tasks and habits."
            action={
              <Button onClick={() => setEdgeOpen(true)}>
                <Link2 /> Link nodes
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="border-b text-left text-xs text-muted-foreground">
                  <th scope="col" className="py-2 font-medium">
                    Source
                  </th>
                  <th scope="col" className="py-2 font-medium">
                    Relation
                  </th>
                  <th scope="col" className="py-2 font-medium">
                    Target
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Weight
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {edges.map((edge) => {
                  const source = nodes.find((node) => node.id === edge.source)
                  const target = nodes.find((node) => node.id === edge.target)
                  const removable =
                    source !== undefined && target !== undefined && isNodeType(source.type) && isNodeType(target.type)
                  return (
                    <tr key={edge.id} className="border-b last:border-0">
                      <td className="py-2.5">{source?.label ?? edge.source}</td>
                      <td className="py-2.5 text-muted-foreground">{titleCase(edge.relation)}</td>
                      <td className="py-2.5">{target?.label ?? edge.target}</td>
                      <td className="py-2.5 text-right tabular-nums">{edge.weight}</td>
                      <td className="py-2.5 text-right">
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-destructive hover:text-destructive"
                          disabled={!removable}
                          onClick={() => {
                            if (!source || !target) return
                            const sourceType = source.type
                            const targetType = target.type
                            if (!isNodeType(sourceType) || !isNodeType(targetType)) return
                            setConfirm({
                              title: 'Remove this link?',
                              description: `${describeEdge(edge, nodes)}. The nodes themselves are kept.`,
                              confirmLabel: 'Remove link',
                              run: () =>
                                removeEdge.mutate({
                                  sourceType,
                                  sourceId: source.id,
                                  targetType,
                                  targetId: target.id,
                                  relation: asRelation(edge.relation),
                                }),
                            })
                          }}
                        >
                          <Trash2 /> Unlink
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      <EdgeDialog
        open={edgeOpen}
        nodes={nodes}
        busy={addEdge.isPending}
        onOpenChange={setEdgeOpen}
        onSubmit={(values) => addEdge.mutate(values)}
      />

      <NodeDialog
        open={nodeOpen}
        busy={addNode.isPending}
        onOpenChange={setNodeOpen}
        onSubmit={(values) => addNode.mutate(values)}
      />

      <ConfirmDialog
        state={confirm}
        busy={anyRemovalPending}
        onOpenChange={(open) => {
          if (!open) setConfirm(null)
        }}
        onConfirm={() => confirm?.run()}
      />
    </div>
  )
}