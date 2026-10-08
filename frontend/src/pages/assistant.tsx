import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Bot,
  CalendarClock,
  Lock,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  Send,
  Sparkles,
  Trash2,
  Unlock,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { aiApi } from '@/api/endpoints'
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
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import { resolveAppPath } from '@/lib/routes'
import {
  formatDateTime,
  formatMinutes,
  formatPercent,
  formatRelative,
  formatTime,
  fromLocalDateTimeInput,
  titleCase,
  toLocalDateTimeInput,
  todayIso,
} from '@/lib/format'
import type {
  AiMessageResponse,
  ChatRequest,
  ChatResponse,
  ConversationSummary,
  PlanBlock,
  PlanDayRequest,
  PlanDayResponse,
  PlanUpdateBlockRequest,
} from '@/types/api'

const CONVERSATION_PAGE_SIZE = 20
const HISTORY_LIMITS = ['5', '10', '20', '40'] as const

interface BlockEdit {
  startAt: string
  endAt: string
  title: string
  taskId: string
  locked: boolean
}

interface PlanDraft {
  date: string
  availableMinutes: string
  focusBlockMinutes: string
  breakMinutes: string
  energyLevel: string
  includeHabits: boolean
  includeBreaks: boolean
  useAi: boolean
  excludeTaskIds: string
}

function initialPlanDraft(): PlanDraft {
  return {
    date: todayIso(),
    availableMinutes: '480',
    focusBlockMinutes: '50',
    breakMinutes: '10',
    energyLevel: '3',
    includeHabits: true,
    includeBreaks: false,
    useAi: false,
    excludeTaskIds: '',
  }
}

function blockEditOf(block: PlanBlock): BlockEdit {
  return {
    startAt: toLocalDateTimeInput(block.startAt),
    endAt: toLocalDateTimeInput(block.endAt),
    title: block.title ?? '',
    taskId: block.taskId ?? '',
    locked: Boolean(block.locked),
  }
}

function instantOf(value: string, fallback: string): string {
  return fromLocalDateTimeInput(value) ?? fallback
}

const BLOCK_TONE: Record<string, string> = {
  TASK: 'border-sky-400/40 bg-sky-400/10',
  FOCUS: 'border-primary/40 bg-primary/10',
  EVENT: 'border-amber-400/40 bg-amber-400/10',
  HABIT: 'border-emerald-400/40 bg-emerald-400/10',
  BREAK: 'border-border bg-secondary/50',
}

function blockTone(kind: string): string {
  return BLOCK_TONE[kind?.toUpperCase() ?? ''] ?? 'border-border bg-card'
}

function ProviderPanel() {
  const providersQuery = useQuery({
    queryKey: ['ai', 'providers'],
    queryFn: () => aiApi.providers(),
  })

  if (providersQuery.isPending) {
    return <Skeleton className="h-24 w-full" />
  }

  if (providersQuery.error) {
    return (
      <div
        role="alert"
        className="flex flex-col items-start gap-3 rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
      >
        <p>{toNormalisedError(providersQuery.error).message}</p>
        <Button variant="outline" size="sm" onClick={() => void providersQuery.refetch()}>
          <RefreshCw /> Try again
        </Button>
      </div>
    )
  }

  const status = providersQuery.data
  if (!status) return null
  const offline = status.degraded || !status.remoteProviderConfigured

  return (
    <SectionCard
      title="Provider status"
      description="Which engine produced the answers below. LIFEOS falls back to a built-in offline provider when no API key is configured."
    >
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge variant={offline ? 'warning' : 'success'}>
            {status.activeProvider || 'unknown'} active
          </Badge>
          <span className="text-muted-foreground">
            Configured provider: {status.configuredProvider || 'none'}
          </span>
        </div>

        {offline ? (
          <p className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>
              LIFEOS is running on the built-in offline provider. Replies and plans are produced by
              deterministic rules over your own data rather than by a remote model, so the wording is
              plain and the reasoning is fixed. Configuring a provider key enables richer output.
            </span>
          </p>
        ) : null}

        <ul className="grid gap-2 sm:grid-cols-3">
          {(status.providers ?? []).map((provider) => (
            <li
              key={provider.name}
              className="rounded-md border p-3 text-xs"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{provider.name}</span>
                <StatusBadge
                  value={provider.configured ? 'READY' : 'FAILED'}
                  label={provider.configured ? 'Ready' : 'No key'}
                />
              </div>
              <p className="mt-1 text-muted-foreground">{provider.detail || '—'}</p>
            </li>
          ))}
        </ul>

        {status.note ? (
          <p className="text-xs text-muted-foreground">{status.note}</p>
        ) : null}
      </div>
    </SectionCard>
  )
}

function ChatPanel() {
  const queryClient = useQueryClient()
  const bottomRef = useRef<HTMLDivElement | null>(null)

  const [conversationId, setConversationId] = useState<string | null>(null)
  const [composer, setComposer] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<string[]>([])
  const [historyLimit, setHistoryLimit] = useState<string>('20')
  const [conversationPage, setConversationPage] = useState(0)
  const [liveReply, setLiveReply] = useState<ChatResponse | null>(null)
  const [renameTarget, setRenameTarget] = useState<ConversationSummary | null>(null)
  const [renameTitle, setRenameTitle] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<ConversationSummary | null>(null)

  const scopesQuery = useQuery({ queryKey: ['ai', 'scopes'], queryFn: () => aiApi.scopes() })

  const conversationsQuery = useQuery({
    queryKey: ['ai', 'conversations', conversationPage, CONVERSATION_PAGE_SIZE],
    queryFn: () => aiApi.conversations({ page: conversationPage, size: CONVERSATION_PAGE_SIZE }),
  })

  const conversationQuery = useQuery({
    queryKey: ['ai', 'conversation', conversationId],
    queryFn: () => aiApi.conversation(conversationId ?? ''),
    enabled: Boolean(conversationId),
  })

  const chatMutation = useMutation({
    mutationFn: (body: ChatRequest) => aiApi.chat(body),
    onSuccess: async (response) => {
      setLiveReply(response)
      setComposer('')
      setConversationId(response.conversationId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['ai', 'conversation', response.conversationId] }),
        queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] }),
      ])
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const renameMutation = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      aiApi.renameConversation(id, { title }),
    onSuccess: async (detail) => {
      setRenameTarget(null)
      setRenameTitle('')
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] }),
        queryClient.invalidateQueries({ queryKey: ['ai', 'conversation', detail.summary.id] }),
      ])
      toast.success('Conversation renamed', { description: detail.summary.title })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => aiApi.removeConversation(id),
    onSuccess: async () => {
      const removedId = deleteTarget?.id
      setDeleteTarget(null)
      if (removedId && removedId === conversationId) {
        setConversationId(null)
        setLiveReply(null)
      }
      await queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] })
      if (removedId) {
        queryClient.removeQueries({ queryKey: ['ai', 'conversation', removedId] })
      }
      toast.success('Conversation deleted')
    },
    onError: (error) => {
      setDeleteTarget(null)
      toast.error(toNormalisedError(error).message)
    },
  })

  const messages: AiMessageResponse[] = conversationQuery.data?.messages ?? []
  const summary = conversationQuery.data?.summary

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length, chatMutation.isPending])

  function sendMessage(event: FormEvent) {
    event.preventDefault()
    const text = composer.trim()
    if (!text) return
    chatMutation.mutate({
      message: text,
      conversationId: conversationId ?? undefined,
      contextScopes: selectedScopes.length > 0 ? selectedScopes : undefined,
      historyLimit: Number(historyLimit),
    })
  }

  function toggleScope(scope: string) {
    setSelectedScopes((current) =>
      current.includes(scope) ? current.filter((item) => item !== scope) : [...current, scope],
    )
  }

  function startNewChat() {
    setConversationId(null)
    setLiveReply(null)
    setComposer('')
  }

  function openRename(conversation: ConversationSummary) {
    setRenameTarget(conversation)
    setRenameTitle(conversation.title)
  }

  const conversations = conversationsQuery.data?.content ?? []
  const conversationPageData = conversationsQuery.data
  const scopes = scopesQuery.data ?? []

  return (
    <div className="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <SectionCard
        title="History"
        description="Your past conversations with the assistant."
        action={
          <Button size="sm" variant="secondary" onClick={startNewChat}>
            <Plus /> New chat
          </Button>
        }
        contentClassName="p-2"
      >
        {conversationsQuery.isPending ? (
          <div className="space-y-2 p-2">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-14 w-full" />
            ))}
          </div>
        ) : conversationsQuery.error ? (
          <div role="alert" className="space-y-2 p-2 text-sm text-destructive">
            <p>{toNormalisedError(conversationsQuery.error).message}</p>
            <Button variant="outline" size="sm" onClick={() => void conversationsQuery.refetch()}>
              <RefreshCw /> Try again
            </Button>
          </div>
        ) : conversations.length === 0 ? (
          <p className="px-2 py-6 text-center text-sm text-muted-foreground">
            No conversations yet. Send a message to start one.
          </p>
        ) : (
          <ul className="space-y-1">
            {conversations.map((conversation) => {
              const active = conversation.id === conversationId
              return (
                <li key={conversation.id}>
                  <div
                    className={
                      active
                        ? 'rounded-md border border-primary/40 bg-primary/10 p-2'
                        : 'rounded-md border p-2 hover:bg-secondary/50'
                    }
                  >
                    <button
                      type="button"
                      className="w-full text-left"
                      onClick={() => {
                        setConversationId(conversation.id)
                        setLiveReply(null)
                      }}
                    >
                      <p className="truncate text-sm font-medium">{conversation.title}</p>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {conversation.messageCount} message
                        {conversation.messageCount === 1 ? '' : 's'} ·{' '}
                        {formatRelative(conversation.lastMessageAt)}
                      </p>
                    </button>
                    <div className="mt-1.5 flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Rename ${conversation.title}`}
                        onClick={() => openRename(conversation)}
                      >
                        <Pencil />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Delete ${conversation.title}`}
                        onClick={() => setDeleteTarget(conversation)}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        {conversationPageData && conversationPageData.totalPages > 1 ? (
          <div className="mt-2 flex items-center justify-between gap-2 px-2">
            <Button
              variant="outline"
              size="sm"
              disabled={conversationPageData.first}
              onClick={() => setConversationPage(Math.max(0, conversationPageData.page - 1))}
            >
              Previous
            </Button>
            <span className="text-xs text-muted-foreground">
              {conversationPageData.page + 1}/{conversationPageData.totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={conversationPageData.last}
              onClick={() => setConversationPage(conversationPageData.page + 1)}
            >
              Next
            </Button>
          </div>
        ) : null}
      </SectionCard>

      <div className="space-y-4">
        <Card>
          <div className="flex min-h-96 flex-col">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b p-4">
              <div className="space-y-0.5">
                <p className="text-sm font-semibold">
                  {summary?.title ?? (conversationId ? 'Conversation' : 'New conversation')}
                </p>
                <p className="text-xs text-muted-foreground">
                  {summary
                    ? `${summary.messageCount} messages · started ${formatDateTime(summary.createdAt)}`
                    : 'Your first message starts a new conversation.'}
                </p>
              </div>
              {conversationId ? (
                <Button variant="ghost" size="sm" onClick={startNewChat}>
                  <Plus /> New chat
                </Button>
              ) : null}
            </div>

            <div className="max-h-[32rem] flex-1 space-y-3 overflow-y-auto p-4">
              {!conversationId && messages.length === 0 ? (
                <EmptyState
                  icon={<MessageSquare />}
                  title="Ask about your life data"
                  description="The assistant answers from your tasks, goals, habits, finance, learning and uploaded documents. Pick the scopes below to narrow what it reads."
                />
              ) : conversationQuery.isPending ? (
                <div className="space-y-3">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton key={index} className="h-16 w-full" />
                  ))}
                </div>
              ) : conversationQuery.error ? (
                <div
                  role="alert"
                  className="space-y-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
                >
                  <p>{toNormalisedError(conversationQuery.error).message}</p>
                  <Button variant="outline" size="sm" onClick={() => void conversationQuery.refetch()}>
                    <RefreshCw /> Try again
                  </Button>
                </div>
              ) : messages.length === 0 ? (
                <p className="py-10 text-center text-sm text-muted-foreground">
                  This conversation has no messages.
                </p>
              ) : (
                <ul className="space-y-3">
                  {messages.map((message) => {
                    const fromUser = message.role === 'USER'
                    const isLive = liveReply?.messageId === message.id
                    const citations = message.citations ?? []
                    return (
                      <li
                        key={message.id}
                        className={
                          fromUser
                            ? 'ml-10 rounded-lg border bg-secondary/50 p-3'
                            : 'mr-10 rounded-lg border bg-card p-3'
                        }
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                            {fromUser ? 'You' : titleCase(message.role)}
                          </span>
                          <span className="text-xs text-muted-foreground">
                            {formatDateTime(message.createdAt)}
                          </span>
                        </div>
                        <p className="mt-1.5 whitespace-pre-wrap text-sm">{message.content}</p>

                        {fromUser ? null : (
                          <div className="mt-2 space-y-2">
                            <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                              {isLive && liveReply ? (
                                <Badge variant={liveReply.grounded ? 'success' : 'warning'}>
                                  {liveReply.grounded ? 'Grounded' : 'Not grounded'}
                                </Badge>
                              ) : null}
                              <span>Provider: {message.provider || 'none'}</span>
                              <span aria-hidden>·</span>
                              <span>Model: {message.model || 'none'}</span>
                            </div>

                            {isLive && liveReply && (liveReply.contextUsed ?? []).length > 0 ? (
                              <div className="flex flex-wrap items-center gap-1.5">
                                <span className="text-xs text-muted-foreground">Context read:</span>
                                {(liveReply.contextUsed ?? []).map((scope) => (
                                  <Badge key={scope} variant="secondary">
                                    {scope}
                                  </Badge>
                                ))}
                              </div>
                            ) : null}

                            {citations.length > 0 ? (
                              <ul className="space-y-2">
                                {citations.map((hit, index) => (
                                  <li key={hit.chunkId} className="rounded-md border p-2">
                                    <div className="flex flex-wrap items-center justify-between gap-2">
                                      <span className="truncate text-xs font-medium">
                                        {index + 1}. {hit.documentTitle}
                                      </span>
                                      <Badge variant="outline">
                                        {formatPercent(hit.score * 100, 1)}
                                      </Badge>
                                    </div>
                                    <p className="mt-1 line-clamp-2 font-mono text-xs text-muted-foreground">
                                      {hit.snippet}
                                    </p>
                                  </li>
                                ))}
                              </ul>
                            ) : null}
                          </div>
                        )}
                      </li>
                    )
                  })}
                </ul>
              )}

              {chatMutation.isPending ? (
                <p className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Spinner label="Waiting for the assistant" /> Thinking…
                </p>
              ) : null}
              <div ref={bottomRef} />
            </div>

            <Separator />

            <form onSubmit={sendMessage} className="space-y-3 p-4" noValidate>
              <div className="space-y-1.5">
                <Label>Context scopes</Label>
                {scopesQuery.isPending ? (
                  <Skeleton className="h-8 w-full" />
                ) : scopesQuery.error ? (
                  <p className="text-xs text-destructive">
                    {toNormalisedError(scopesQuery.error).message}
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {scopes.map((scope) => {
                      const active = selectedScopes.includes(scope)
                      return (
                        <button
                          key={scope}
                          type="button"
                          aria-pressed={active}
                          onClick={() => toggleScope(scope)}
                          className={
                            active
                              ? 'rounded-full border border-primary/40 bg-primary/15 px-2.5 py-1 text-xs font-medium text-primary'
                              : 'rounded-full border px-2.5 py-1 text-xs text-muted-foreground hover:bg-secondary/60'
                          }
                        >
                          {scope}
                        </button>
                      )
                    })}
                  </div>
                )}
                <p className="text-xs text-muted-foreground">
                  {selectedScopes.length === 0
                    ? 'No scopes selected: the assistant chooses what to read.'
                    : `Reading ${selectedScopes.length} scope${selectedScopes.length === 1 ? '' : 's'}.`}
                </p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="assistant-composer">Message</Label>
                <Textarea
                  id="assistant-composer"
                  rows={3}
                  maxLength={8000}
                  placeholder="Which tasks are at risk this week and why?"
                  value={composer}
                  onChange={(event) => setComposer(event.target.value)}
                />
              </div>

              <div className="flex flex-wrap items-end gap-3">
                <div className="w-32 space-y-1.5">
                  <Label htmlFor="assistant-history">History turns</Label>
                  <Select value={historyLimit} onValueChange={setHistoryLimit}>
                    <SelectTrigger id="assistant-history">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {HISTORY_LIMITS.map((option) => (
                        <SelectItem key={option} value={option}>
                          {option}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <Button type="submit" loading={chatMutation.isPending} disabled={!composer.trim()}>
                  <Send /> Send
                </Button>
              </div>

              <p className="text-xs text-muted-foreground">
                Grounding state is returned with each reply. Older turns only keep the provider and
                model that produced them.
              </p>
            </form>
          </div>
        </Card>
      </div>

      <Dialog open={renameTarget !== null} onOpenChange={(open) => !open && setRenameTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rename conversation</DialogTitle>
            <DialogDescription>
              Give this conversation a title you will recognise in the history list.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="rename-conversation">Title</Label>
            <Input
              id="rename-conversation"
              maxLength={200}
              value={renameTitle}
              onChange={(event) => setRenameTitle(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenameTarget(null)}>
              Cancel
            </Button>
            <Button
              loading={renameMutation.isPending}
              disabled={!renameTitle.trim()}
              onClick={() => {
                const title = renameTitle.trim()
                if (renameTarget && title) {
                  renameMutation.mutate({ id: renameTarget.id, title })
                }
              }}
            >
              Save title
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete conversation</DialogTitle>
            <DialogDescription>
              {deleteTarget
                ? `"${deleteTarget.title}" and all ${deleteTarget.messageCount} of its messages will be removed. This cannot be undone.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Keep conversation
            </Button>
            <Button
              variant="destructive"
              loading={deleteMutation.isPending}
              onClick={() => {
                if (deleteTarget) deleteMutation.mutate(deleteTarget.id)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PlannerPanel() {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<PlanDraft>(initialPlanDraft)
  const [plan, setPlan] = useState<PlanDayResponse | null>(null)
  const [edits, setEdits] = useState<Record<string, BlockEdit>>({})

  const planMutation = useMutation({
    mutationFn: (body: PlanDayRequest) => aiApi.planDay(body),
    onSuccess: (response) => {
      setPlan(response)
      setEdits({})
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const applyMutation = useMutation({
    mutationFn: (blocks: PlanUpdateBlockRequest[]) => aiApi.applyPlan({ blocks }),
    onSuccess: (response) => {
      setPlan(response)
      setEdits({})
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      toast.success('Plan updated', {
        description: `${response.blocks.length} blocks sent back to the scheduler.`,
      })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const editFor = useMemo(
    () => (block: PlanBlock): BlockEdit => edits[block.id] ?? blockEditOf(block),
    [edits],
  )

  const orderedBlocks = useMemo(() => {
    if (!plan) return []
    const time = (value: string) => {
      const parsed = new Date(value).getTime()
      return Number.isNaN(parsed) ? 0 : parsed
    }
    return [...plan.blocks].sort(
      (a, b) =>
        time(fromLocalDateTimeInput(editFor(a).startAt) ?? a.startAt) -
        time(fromLocalDateTimeInput(editFor(b).startAt) ?? b.startAt),
    )
  }, [plan, editFor])

  const pendingCount = Object.keys(edits).length

  function setEdit(blockId: string, patch: Partial<BlockEdit>) {
    setEdits((current) => ({
      ...current,
      [blockId]: { ...(current[blockId] ?? blockEditOfById(blockId)), ...patch },
    }))
  }

  function blockEditOfById(blockId: string): BlockEdit {
    const block = plan?.blocks.find((item) => item.id === blockId)
    return block ? blockEditOf(block) : { startAt: '', endAt: '', title: '', taskId: '', locked: false }
  }

  function payloadFor(overrides: Record<string, Partial<BlockEdit>> = {}): PlanUpdateBlockRequest[] {
    if (!plan) return []
    return plan.blocks.map((block) => {
      const edit = { ...editFor(block), ...overrides[block.id] }
      return {
        blockId: block.id,
        startAt: instantOf(edit.startAt, block.startAt),
        endAt: instantOf(edit.endAt, block.endAt),
        title: edit.title.trim() || block.title,
        taskId: edit.taskId.trim(),
        locked: edit.locked,
      }
    })
  }

  function submitPlan(event: FormEvent) {
    event.preventDefault()
    const excludeTaskIds = draft.excludeTaskIds
      .split(/[\s,]+/)
      .map((value) => value.trim())
      .filter(Boolean)
    planMutation.mutate({
      date: draft.date || undefined,
      availableMinutes: Number(draft.availableMinutes),
      focusBlockMinutes: Number(draft.focusBlockMinutes),
      breakMinutes: Number(draft.breakMinutes),
      energyLevel: Number(draft.energyLevel),
      includeHabits: draft.includeHabits,
      includeBreaks: draft.includeBreaks,
      useAi: draft.useAi,
      excludeTaskIds: excludeTaskIds.length > 0 ? excludeTaskIds : undefined,
    })
  }

  function applyLock(block: PlanBlock, locked: boolean) {
    applyMutation.mutate(payloadFor({ [block.id]: { locked } }))
  }

  function applyAll() {
    const blocks = payloadFor()
    if (blocks.length === 0) return
    applyMutation.mutate(blocks)
  }

  return (
    <div className="space-y-4">
      <SectionCard
        title="Plan a day"
        description="The server builds the schedule from your real deadlines, dependencies, fixed events and available hours. The model, when enabled, only narrates the schedule that was already computed — it never creates or moves work."
      >
        <form onSubmit={submitPlan} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-1.5">
              <Label htmlFor="plan-date">Date</Label>
              <Input
                id="plan-date"
                type="date"
                value={draft.date}
                onChange={(event) => setDraft((current) => ({ ...current, date: event.target.value }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="plan-available">Available minutes</Label>
              <Input
                id="plan-available"
                type="number"
                min={30}
                max={1440}
                step={15}
                value={draft.availableMinutes}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, availableMinutes: event.target.value }))
                }
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="plan-focus">Focus block minutes</Label>
              <Input
                id="plan-focus"
                type="number"
                min={10}
                max={240}
                step={5}
                value={draft.focusBlockMinutes}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, focusBlockMinutes: event.target.value }))
                }
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="plan-break">Break minutes</Label>
              <Input
                id="plan-break"
                type="number"
                min={0}
                max={120}
                step={5}
                value={draft.breakMinutes}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, breakMinutes: event.target.value }))
                }
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="plan-energy">Energy level (1-16)</Label>
              <Input
                id="plan-energy"
                type="number"
                min={1}
                max={16}
                value={draft.energyLevel}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, energyLevel: event.target.value }))
                }
              />
            </div>
            <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
              <Label htmlFor="plan-exclude">Exclude task ids</Label>
              <Input
                id="plan-exclude"
                maxLength={2000}
                placeholder="Comma-separated task ids to leave out of the day"
                value={draft.excludeTaskIds}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, excludeTaskIds: event.target.value }))
                }
              />
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-3">
            <label className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
              Include habits
              <Switch
                checked={draft.includeHabits}
                onCheckedChange={(checked) =>
                  setDraft((current) => ({ ...current, includeHabits: checked }))
                }
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
              Include breaks
              <Switch
                checked={draft.includeBreaks}
                onCheckedChange={(checked) =>
                  setDraft((current) => ({ ...current, includeBreaks: checked }))
                }
              />
            </label>
            <label className="flex items-center justify-between gap-3 rounded-md border p-3 text-sm">
              Narrate with AI
              <Switch
                checked={draft.useAi}
                onCheckedChange={(checked) =>
                  setDraft((current) => ({ ...current, useAi: checked }))
                }
              />
            </label>
          </div>

          <Button type="submit" loading={planMutation.isPending}>
            <CalendarClock /> Build the plan
          </Button>
        </form>
      </SectionCard>

      {planMutation.isPending ? (
        <Card>
          <div className="flex items-center gap-2 p-4 text-sm text-muted-foreground">
            <Spinner label="Building the plan" /> Placing events, habits and tasks…
          </div>
        </Card>
      ) : plan ? (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Date" value={formatDateTime(`${plan.date}T00:00:00`)} hint="Planned day" />
            <StatCard
              label="Scheduled"
              value={formatMinutes(plan.scheduledMinutes)}
              hint={`of ${formatMinutes(plan.availableMinutes)} available`}
            />
            <StatCard label="Breaks" value={formatMinutes(plan.breakMinutes)} hint="Rest in the day" />
            <StatCard
              label="Utilisation"
              value={formatPercent(plan.utilisationPercent, 1)}
              hint={`${plan.blocks.length} blocks`}
            />
          </div>

          <SectionCard
            title="Schedule"
            description="Generated by the server. Lock a block to pin it, or edit times, title and task, then apply — the whole schedule is sent back so nothing is dropped."
            action={
              <Button
                size="sm"
                onClick={applyAll}
                loading={applyMutation.isPending}
                disabled={pendingCount === 0}
              >
                <RefreshCw /> Apply changes{pendingCount > 0 ? ` (${pendingCount})` : ''}
              </Button>
            }
          >
            <div className="space-y-4">
              {plan.warnings.length > 0 ? (
                <ul className="space-y-2">
                  {plan.warnings.map((warning) => (
                    <li
                      key={warning}
                      className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning"
                    >
                      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
                      {warning}
                    </li>
                  ))}
                </ul>
              ) : null}

              {plan.unscheduledTaskIds.length > 0 ? (
                <div className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2">
                  <p className="text-sm font-medium text-destructive">
                    {plan.unscheduledTaskIds.length} task
                    {plan.unscheduledTaskIds.length === 1 ? '' : 's'} did not fit this day
                  </p>
                  <ul className="mt-1 space-y-0.5">
                    {plan.unscheduledTaskIds.map((taskId) => (
                      <li key={taskId} className="truncate font-mono text-xs text-destructive">
                        {taskId}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}

              {orderedBlocks.length === 0 ? (
                <EmptyState
                  icon={<CalendarClock />}
                  title="Nothing to schedule"
                  description="No open tasks, habits or events fell inside this day. Add work, widen the available minutes, or pick another date."
                />
              ) : (
                <ol className="space-y-3">
                  {orderedBlocks.map((block) => {
                    const edit = editFor(block)
                    const dirty = edits[block.id] !== undefined
                    return (
                      <li
                        key={block.id}
                        className={`space-y-3 rounded-lg border-l-4 p-3 ${blockTone(block.kind)} ${
                          dirty ? 'ring-1 ring-primary/60' : ''
                        }`}
                      >
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-semibold tabular-nums">
                                {formatTime(fromLocalDateTimeInput(edit.startAt) ?? block.startAt)}–
                                {formatTime(fromLocalDateTimeInput(edit.endAt) ?? block.endAt)}
                              </span>
                              <Badge variant="outline">{titleCase(block.kind)}</Badge>
                              <Badge variant="secondary">
                                {formatMinutes(block.durationMinutes)}
                              </Badge>
                              {block.locked ? <StatusBadge value="LOCKED" label="Locked" /> : null}
                            </div>
                            <p className="text-sm">{edit.title.trim() || block.title}</p>
                            {block.detail ? (
                              <p className="text-xs text-muted-foreground">{block.detail}</p>
                            ) : null}
                            <p className="text-xs text-muted-foreground">
                              {block.priority ? `Priority ${titleCase(block.priority)} · ` : ''}
                              {block.energyFit ? `Energy ${titleCase(block.energyFit)} · ` : ''}
                              {block.movable ? 'Movable' : 'Fixed'}
                              {block.taskId ? ` · task ${block.taskId}` : ''}
                              {block.habitId ? ` · habit ${block.habitId}` : ''}
                              {block.goalId ? ` · goal ${block.goalId}` : ''}
                            </p>
                          </div>

                          <div className="flex items-center gap-2">
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={applyMutation.isPending}
                              onClick={() => {
                                setEdit(block.id, { locked: !edit.locked })
                                applyLock(block, !edit.locked)
                              }}
                            >
                              {edit.locked ? <Unlock /> : <Lock />}
                              {edit.locked ? 'Unlock' : 'Lock'}
                            </Button>
                          </div>
                        </div>

                        <details className="rounded-md border p-3">
                          <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
                            Edit times, title and task
                          </summary>
                          <div className="mt-3 grid gap-3 sm:grid-cols-2">
                            <div className="space-y-1.5">
                              <Label htmlFor={`block-start-${block.id}`}>Start</Label>
                              <Input
                                id={`block-start-${block.id}`}
                                type="datetime-local"
                                value={edit.startAt}
                                onChange={(event) =>
                                  setEdit(block.id, { startAt: event.target.value })
                                }
                              />
                            </div>
                            <div className="space-y-1.5">
                              <Label htmlFor={`block-end-${block.id}`}>End</Label>
                              <Input
                                id={`block-end-${block.id}`}
                                type="datetime-local"
                                value={edit.endAt}
                                onChange={(event) =>
                                  setEdit(block.id, { endAt: event.target.value })
                                }
                              />
                            </div>
                            <div className="space-y-1.5">
                              <Label htmlFor={`block-title-${block.id}`}>Title</Label>
                              <Input
                                id={`block-title-${block.id}`}
                                maxLength={300}
                                value={edit.title}
                                onChange={(event) =>
                                  setEdit(block.id, { title: event.target.value })
                                }
                              />
                            </div>
                            <div className="space-y-1.5">
                              <Label htmlFor={`block-task-${block.id}`}>Task id</Label>
                              <Input
                                id={`block-task-${block.id}`}
                                maxLength={100}
                                value={edit.taskId}
                                onChange={(event) =>
                                  setEdit(block.id, { taskId: event.target.value })
                                }
                              />
                            </div>
                          </div>
                          <div className="mt-3 flex flex-wrap gap-2">
                            <Button
                              size="sm"
                              variant="secondary"
                              loading={applyMutation.isPending}
                              onClick={() => applyMutation.mutate(payloadFor())}
                            >
                              Apply this schedule
                            </Button>
                            {dirty ? (
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() =>
                                  setEdits((current) => {
                                    const next = { ...current }
                                    delete next[block.id]
                                    return next
                                  })
                                }
                              >
                                Discard this block
                              </Button>
                            ) : null}
                          </div>
                        </details>
                      </li>
                    )
                  })}
                </ol>
              )}

              <div className="space-y-2 rounded-md border p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  Generated by
                </p>
                <p className="text-sm">{plan.generatedBy}</p>
                <p className="text-sm text-muted-foreground">{plan.rationale}</p>
              </div>
            </div>
          </SectionCard>
        </div>
      ) : (
        <EmptyState
          icon={<CalendarClock />}
          title="No plan built yet"
          description="Set the hours you actually have and LIFEOS will lay out your events, habits and tasks around real deadlines and dependencies."
          action={
            <Button onClick={() => planMutation.mutate({ date: draft.date || undefined })}>
              <CalendarClock /> Build today's plan
            </Button>
          }
        />
      )}
    </div>
  )
}

function RecommendationsPanel() {
  const recommendationsQuery = useQuery({
    queryKey: ['ai', 'recommendations'],
    queryFn: () => aiApi.recommendations(),
  })

  if (recommendationsQuery.isPending) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Skeleton key={index} className="h-32 w-full" />
        ))}
      </div>
    )
  }

  if (recommendationsQuery.error) {
    return (
      <div
        role="alert"
        className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
      >
        <p>{toNormalisedError(recommendationsQuery.error).message}</p>
        <Button variant="outline" size="sm" onClick={() => void recommendationsQuery.refetch()}>
          <RefreshCw /> Try again
        </Button>
      </div>
    )
  }

  const data = recommendationsQuery.data
  const recommendations = data?.recommendations ?? []

  return (
    <SectionCard
      title="Recommendations"
      description={
        data
          ? `Generated ${formatDateTime(data.generatedAt)} by ${data.generatedBy}.`
          : 'What your own records suggest doing next.'
      }
    >
      {recommendations.length === 0 ? (
        <EmptyState
          icon={<Sparkles />}
          title="Nothing to recommend yet"
          description="Recommendations appear once there are tasks, habits, budgets or goals for the rules to react to. Keep recording and check back."
          action={
            <Button variant="outline" onClick={() => void recommendationsQuery.refetch()}>
              <RefreshCw /> Check again
            </Button>
          }
        />
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2">
          {recommendations.map((recommendation) => {
            const internalTarget = resolveAppPath(recommendation.actionPath)
            return (
              <li key={recommendation.id}>
                <Card>
                  <div className="flex h-full flex-col gap-3 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusBadge
                            value={recommendation.type}
                            label={titleCase(recommendation.type)}
                          />
                        </div>
                        <h3 className="text-sm font-semibold">{recommendation.title}</h3>
                      </div>
                      <Badge variant="outline">
                        {formatPercent(recommendation.score)} score
                      </Badge>
                    </div>

                    <p className="text-sm text-muted-foreground">{recommendation.rationale}</p>

                    {recommendation.supportingData.length > 0 ? (
                      <ul className="space-y-1 text-xs text-muted-foreground">
                        {recommendation.supportingData.map((line) => (
                          <li key={line} className="flex gap-2">
                            <span aria-hidden>•</span>
                            <span>{line}</span>
                          </li>
                        ))}
                      </ul>
                    ) : null}

                    <div className="mt-auto flex flex-wrap items-center gap-2 pt-1">
                      {internalTarget ? (
                        <Button size="sm" variant="secondary" asChild>
                          <Link to={internalTarget}>{recommendation.actionLabel}</Link>
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-foreground">
                          {recommendation.actionLabel}
                          <span className="block">
                            This suggestion points at <span className="font-mono">{recommendation.actionPath}</span>,
                            which has no page in LIFEOS, so no link is offered.
                          </span>
                        </span>
                      )}
                    </div>
                  </div>
                </Card>
              </li>
            )
          })}
        </ul>
      )}
    </SectionCard>
  )
}

export default function AssistantPage() {
  const [tab, setTab] = useState('chat')

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assistant"
        description="Chat with your own data, build a realistic day, and see what your records suggest next."
      />

      <ProviderPanel />

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          <TabsTrigger value="chat">
            <MessageSquare className="size-4" aria-hidden /> Chat
          </TabsTrigger>
          <TabsTrigger value="planner">
            <CalendarClock className="size-4" aria-hidden /> Day planner
          </TabsTrigger>
          <TabsTrigger value="recommendations">
            <Sparkles className="size-4" aria-hidden /> Recommendations
          </TabsTrigger>
        </TabsList>

        <TabsContent value="chat">
          <ChatPanel />
        </TabsContent>
        <TabsContent value="planner">
          <PlannerPanel />
        </TabsContent>
        <TabsContent value="recommendations">
          <RecommendationsPanel />
        </TabsContent>
      </Tabs>

      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <Bot className="size-3.5" aria-hidden />
        Every response records which provider and model produced it. Offline answers come from rules
        over your records, not from a language model.
      </p>
    </div>
  )
}