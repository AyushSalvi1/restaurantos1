import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpenText, Pencil, Plus, Sparkles, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { toast } from 'sonner'

import { journalApi } from '@/api/endpoints'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/lib/badges'
import { addDaysIso, formatDate, formatNumber, titleCase, todayIso } from '@/lib/format'
import { toNormalisedError } from '@/hooks/use-api-error'
import type { JournalMood, JournalRequest, JournalResponse } from '@/types/api'

const MOODS: { value: JournalMood; label: string }[] = [
  { value: 'GREAT', label: 'Great' },
  { value: 'GOOD', label: 'Good' },
  { value: 'NEUTRAL', label: 'Neutral' },
  { value: 'LOW', label: 'Low' },
  { value: 'DIFFICULT', label: 'Difficult' },
]

const PAGE_SIZE = 10

const TOOLTIP_STYLE = {
  backgroundColor: 'var(--color-card)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  color: 'var(--color-card-foreground)',
  fontSize: 12,
}

function countWords(value: string): number {
  const trimmed = value.trim()
  return trimmed === '' ? 0 : trimmed.split(/\s+/).length
}

function parseTags(value: string): string[] {
  return Array.from(
    new Set(
      value
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean),
    ),
  )
}

function excerpt(content: string, length = 160): string {
  const clean = content.replace(/\s+/g, ' ').trim()
  return clean.length > length ? `${clean.slice(0, length)}…` : clean
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
            Delete entry
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface EditorFormValues {
  title: string
  content: string
  entryDate: string
  mood: JournalMood
  moodScore: string
  tags: string
}

function EntryDialog({
  open,
  entry,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  entry: JournalResponse | null
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: EditorFormValues) => void
}) {
  const defaults = (): EditorFormValues => ({
    title: entry?.title ?? '',
    content: entry?.content ?? '',
    entryDate: entry?.entryDate ?? todayIso(),
    mood: entry?.mood ?? 'NEUTRAL',
    moodScore: entry?.moodScore ? String(entry.moodScore) : '3',
    tags: (entry?.tags ?? []).join(', '),
  })

  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<EditorFormValues>({ defaultValues: defaults() })

  const content = watch('content')
  const words = countWords(content ?? '')

  const close = (next: boolean) => {
    if (!next) reset(defaults())
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{entry ? 'Edit entry' : 'New entry'}</DialogTitle>
          <DialogDescription>
            Saving stores the text only. Analysis is a separate step you trigger after writing.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="entry-title">Title</Label>
              <Input
                id="entry-title"
                maxLength={200}
                {...register('title')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="entry-date">Entry date</Label>
              <Input
                id="entry-date"
                type="date"
                required
                {...register('entryDate', { required: 'An entry date is required' })}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="entry-content">Content</Label>
              <span className="text-xs tabular-nums text-muted-foreground">
                {words} {words === 1 ? 'word' : 'words'}
              </span>
            </div>
            <Textarea
              id="entry-content"
              rows={10}
              required
              aria-invalid={Boolean(errors.content)}
              {...register('content', { required: 'Write something before saving' })}
            />
            {errors.content ? (
              <p className="text-xs text-destructive">{errors.content.message}</p>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label>Mood</Label>
              <Controller
                control={control}
                name="mood"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={(value) => field.onChange(value as JournalMood)}>
                    <SelectTrigger aria-label="Mood">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {MOODS.map((option) => (
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
              <Label htmlFor="entry-mood-score">Mood score (1–5)</Label>
              <Input
                id="entry-mood-score"
                type="number"
                min={1}
                max={5}
                {...register('moodScore')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="entry-tags">Tags</Label>
              <Input id="entry-tags" placeholder="work, sleep" {...register('tags')} />
              <p className="text-xs text-muted-foreground">Comma separated.</p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {entry ? 'Save entry' : 'Create entry'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function JournalPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['journal'] })

  const from = searchParams.get('from') ?? ''
  const to = searchParams.get('to') ?? ''
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<{ open: boolean; entry: JournalResponse | null }>({
    open: false,
    entry: null,
  })
  const [detailId, setDetailId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const entries = useQuery({
    queryKey: ['journal', 'list', from, to, page],
    queryFn: () =>
      journalApi.list({
        from: from || undefined,
        to: to || undefined,
        page,
        size: PAGE_SIZE,
      }),
  })

  const statistics = useQuery({
    queryKey: ['journal', 'statistics'],
    queryFn: () => journalApi.statistics(),
  })

  const saveEntry = useMutation({
    mutationFn: (values: EditorFormValues) => {
      const body: JournalRequest = {
        title: values.title.trim() || undefined,
        content: values.content,
        entryDate: values.entryDate,
        mood: values.mood,
        moodScore: values.moodScore === '' ? undefined : Number(values.moodScore),
        tags: parseTags(values.tags),
      }
      return editor.entry ? journalApi.update(editor.entry.id, body) : journalApi.create(body)
    },
    onSuccess: (entry) => {
      toast.success(editor.entry ? 'Entry updated' : 'Entry created')
      setEditor({ open: false, entry: null })
      void invalidate()
      if (!editor.entry) setDetailId(entry.id)
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeEntry = useMutation({
    mutationFn: (id: string) => journalApi.remove(id),
    onSuccess: () => {
      toast.success('Entry deleted')
      setConfirm(null)
      setDetailId(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const setRange = (nextFrom: string, nextTo: string) => {
    const params = new URLSearchParams(searchParams)
    if (nextFrom) params.set('from', nextFrom)
    else params.delete('from')
    if (nextTo) params.set('to', nextTo)
    else params.delete('to')
    setSearchParams(params, { replace: true })
    setPage(0)
  }

  const stats = statistics.data
  const pageData = entries.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Journal"
        description="Daily reflections with mood tracking and server-generated analysis."
        actions={
          <Button
            onClick={() => {
              setEditor({ open: true, entry: null })
            }}
          >
            <Plus /> New entry
          </Button>
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
          <StatCard label="Total entries" value={formatNumber(stats.totalEntries)} />
          <StatCard label="Entries this month" value={formatNumber(stats.entriesThisMonth)} />
          <StatCard
            label="Days written (30d)"
            value={formatNumber(stats.daysWithEntriesLast30)}
            hint="days with at least one entry"
          />
          <StatCard label="Words (30d)" value={formatNumber(stats.wordsLast30)} />
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <SectionCard
          className="lg:col-span-1"
          title="Mood distribution"
          description="Entries per recorded mood."
        >
          {statistics.isError ? (
            <ErrorPanel message={statistics.error.message} onRetry={() => void statistics.refetch()} />
          ) : statistics.isPending ? (
            <Skeleton className="h-56 w-full" />
          ) : (stats?.moodDistribution.length ?? 0) === 0 ? (
            <EmptyState
              icon={<BookOpenText />}
              title="No moods recorded"
              description="Set a mood on an entry and the distribution builds up."
            />
          ) : (
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={stats?.moodDistribution ?? []}
                  layout="vertical"
                  margin={{ top: 0, right: 8, bottom: 0, left: 8 }}
                >
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" horizontal={false} />
                  <XAxis
                    type="number"
                    allowDecimals={false}
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                  />
                  <YAxis
                    type="category"
                    dataKey="mood"
                    width={72}
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                    tickFormatter={(value: string) => titleCase(value)}
                  />
                  <Tooltip contentStyle={TOOLTIP_STYLE} cursor={{ fill: 'var(--color-secondary)' }} />
                  <Bar dataKey="count" name="Entries" fill="var(--color-primary)" radius={[0, 3, 3, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>

        <SectionCard
          className="lg:col-span-1"
          title="Highlights"
          description="What the server picked out of your writing."
        >
          {statistics.isPending ? (
            <Skeleton className="h-56 w-full" />
          ) : (
            <div className="space-y-4 text-sm">
              <div className="space-y-1.5">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Most frequent mood
                </p>
                {stats?.mostFrequentMood ? (
                  <StatusBadge value={stats.mostFrequentMood} label={stats.mostFrequentMood.toLowerCase()} />
                ) : (
                  <p className="text-muted-foreground">Not enough data yet.</p>
                )}
              </div>
              <div className="space-y-1.5">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Top tags
                </p>
                {(stats?.topTags.length ?? 0) === 0 ? (
                  <p className="text-muted-foreground">No tags used yet.</p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {(stats?.topTags ?? []).map((tag) => (
                      <Badge key={tag.tag} variant="outline">
                        {tag.tag}
                        <span className="text-muted-foreground">{tag.count}</span>
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
              <div className="space-y-1.5 border-t pt-3">
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Server reflection
                </p>
                <p className="flex gap-2">
                  <Sparkles className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                  <span>{stats?.reflection}</span>
                </p>
              </div>
            </div>
          )}
        </SectionCard>

        <SectionCard
          className="lg:col-span-1"
          title="Date range"
          description="The filter lives in the URL, so a filtered view can be shared or bookmarked."
        >
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="journal-from">From</Label>
              <Input
                id="journal-from"
                type="date"
                value={from}
                onChange={(event) => setRange(event.target.value, to)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="journal-to">To</Label>
              <Input
                id="journal-to"
                type="date"
                value={to}
                onChange={(event) => setRange(from, event.target.value)}
              />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={() => setRange(addDaysIso(todayIso(), -29), todayIso())}
              >
                Last 30 days
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setRange('', '')}>
                Clear
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              {from || to
                ? `Filtering ${from ? formatDate(from) : 'the beginning'} → ${to ? formatDate(to) : 'today'}.`
                : 'Showing every entry.'}
            </p>
          </div>
        </SectionCard>
      </div>

      <SectionCard title="Entries" description="Newest first.">
        {entries.isError ? (
          <ErrorPanel message={entries.error.message} onRetry={() => void entries.refetch()} />
        ) : entries.isPending ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }, (_, index) => (
              <Skeleton key={index} className="h-16 w-full" />
            ))}
          </div>
        ) : (pageData?.content.length ?? 0) === 0 ? (
          <EmptyState
            icon={<BookOpenText />}
            title={from || to ? 'No entries in this range' : 'No entries yet'}
            description={
              from || to
                ? 'Clear the date filter or widen it to see older writing.'
                : 'Write your first reflection and set a mood to start the history.'
            }
            action={
              from || to ? (
                <Button variant="outline" onClick={() => setRange('', '')}>
                  Clear filter
                </Button>
              ) : (
                <Button
                  onClick={() => {
                    setEditor({ open: true, entry: null })
                  }}
                >
                  <Plus /> Write an entry
                </Button>
              )
            }
          />
        ) : (
          <>
            <ul className="divide-y">
              {(pageData?.content ?? []).map((entry) => (
                <li key={entry.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                  <button
                    type="button"
                    onClick={() => setDetailId(entry.id)}
                    className="min-w-0 flex-1 text-left"
                  >
                    <p className="truncate text-sm font-medium hover:text-primary">
                      {entry.title || 'Untitled entry'}
                    </p>
                    <p className="mt-0.5 line-clamp-2 text-sm text-muted-foreground">
                      {excerpt(entry.content)}
                    </p>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                      <span className="text-xs tabular-nums text-muted-foreground">
                        {formatDate(entry.entryDate)}
                      </span>
                      {entry.mood ? (
                        <StatusBadge value={entry.mood} label={entry.mood.toLowerCase()} />
                      ) : null}
                      {entry.tags.slice(0, 4).map((tag) => (
                        <Badge key={tag} variant="outline">
                          {tag}
                        </Badge>
                      ))}
                      {entry.tags.length > 4 ? (
                        <span className="text-xs text-muted-foreground">
                          +{entry.tags.length - 4}
                        </span>
                      ) : null}
                    </div>
                  </button>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="text-xs tabular-nums text-muted-foreground">
                      {formatNumber(entry.wordCount)} words
                    </span>
                    {entry.aiAnalyzed ? (
                      <StatusBadge value="INFO" label="analysed" />
                    ) : (
                      <StatusBadge value="PENDING" label="not analysed" />
                    )}
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      onClick={() => setEditor({ open: true, entry })}
                      aria-label={`Edit ${entry.title || 'entry'}`}
                    >
                      <Pencil />
                    </Button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      className="text-destructive hover:text-destructive"
                      onClick={() =>
                        setConfirm({
                          id: entry.id,
                          title: `Delete “${entry.title || 'Untitled entry'}”?`,
                          description:
                            'The entry and its stored analysis are permanently removed.',
                        })
                      }
                      aria-label={`Delete ${entry.title || 'entry'}`}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>

            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Page {pageData ? pageData.page + 1 : 1} of {Math.max(pageData?.totalPages ?? 1, 1)} ·{' '}
                {formatNumber(pageData?.totalElements ?? 0)} entries
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={pageData?.first ?? true}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Previous
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={pageData?.last ?? true}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </SectionCard>

      <EntryDialog
        key={editor.entry?.id ?? 'new'}
        open={editor.open}
        entry={editor.entry}
        busy={saveEntry.isPending}
        onOpenChange={(open) => setEditor({ open, entry: open ? editor.entry : null })}
        onSubmit={(values) => saveEntry.mutate(values)}
      />

      <DetailDialogBody
        entryId={detailId}
        onOpenChange={(open) => {
          if (!open) setDetailId(null)
        }}
        onEdit={(entry) => {
          setDetailId(null)
          setEditor({ open: true, entry })
        }}
        onDelete={(entry) =>
          setConfirm({
            id: entry.id,
            title: `Delete “${entry.title || 'Untitled entry'}”?`,
            description: 'The entry and its stored analysis are permanently removed.',
          })
        }
        onAnalysed={invalidate}
      />

      <ConfirmDialog
        state={confirm}
        busy={removeEntry.isPending}
        onOpenChange={(open) => {
          if (!open) setConfirm(null)
        }}
        onConfirm={() => {
          if (confirm) removeEntry.mutate(confirm.id)
        }}
      />
    </div>
  )
}

function DetailDialogBody({
  entryId,
  onOpenChange,
  onEdit,
  onDelete,
  onAnalysed,
}: {
  entryId: string | null
  onOpenChange: (open: boolean) => void
  onEdit: (entry: JournalResponse) => void
  onDelete: (entry: JournalResponse) => void
  onAnalysed: () => void
}) {
  const detail = useQuery({
    queryKey: ['journal', 'entry', entryId],
    queryFn: () => journalApi.get(entryId ?? ''),
    enabled: entryId !== null,
  })

  const analyse = useMutation({
    mutationFn: (id: string) => journalApi.analyse(id),
    onSuccess: () => {
      toast.success('Analysis generated')
      onAnalysed()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const entry = detail.data

  return (
    <Dialog open={entryId !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>{entry?.title || 'Untitled entry'}</DialogTitle>
          <DialogDescription>
            {entry ? `Written for ${formatDate(entry.entryDate)}` : 'Loading entry…'}
          </DialogDescription>
        </DialogHeader>

        {detail.isError ? (
          <ErrorPanel message={detail.error.message} onRetry={() => void detail.refetch()} />
        ) : detail.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : entry ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              {entry.mood ? (
                <StatusBadge value={entry.mood} label={entry.mood.toLowerCase()} />
              ) : null}
              <span className="text-xs text-muted-foreground">
                mood score {entry.moodScore || '—'} · {formatNumber(entry.wordCount)} words · saved{' '}
                {formatDate(entry.createdAt)}
              </span>
              {entry.tags.map((tag) => (
                <Badge key={tag} variant="outline">
                  {tag}
                </Badge>
              ))}
            </div>

            <div className="max-h-72 overflow-y-auto whitespace-pre-wrap rounded-lg border bg-secondary/30 p-3 text-sm leading-relaxed">
              {entry.content}
            </div>

            <div className="space-y-2 rounded-lg border p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  <Sparkles className="size-3.5" aria-hidden />
                  AI analysis
                </p>
                <Button
                  size="sm"
                  variant="outline"
                  loading={analyse.isPending}
                  onClick={() => analyse.mutate(entry.id)}
                >
                  {entry.aiAnalyzed ? 'Re-run analysis' : 'Analyse this entry'}
                </Button>
              </div>
              {entry.aiSummary ? (
                <p className="whitespace-pre-wrap text-sm">{entry.aiSummary}</p>
              ) : (
                <p className="text-sm text-muted-foreground">
                  No analysis stored yet. Running it produces a server-generated summary from your
                  configured provider, which may be the built-in offline heuristic.
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                {entry.aiAnalyzed
                  ? 'Generated on the server. Provider and model are reported by the assistant settings.'
                  : 'Not analysed yet.'}
              </p>
            </div>

            <DialogFooter className="sm:justify-between">
              <Button variant="ghost" onClick={() => onOpenChange(false)}>
                Close
              </Button>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => onEdit(entry)}>
                  <Pencil /> Edit
                </Button>
                <Button variant="destructive" onClick={() => onDelete(entry)}>
                  <Trash2 /> Delete
                </Button>
              </div>
            </DialogFooter>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}