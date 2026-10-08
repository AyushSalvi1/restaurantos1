import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Database,
  Eye,
  FileWarning,
  Info,
  RefreshCw,
  Save,
  ShieldCheck,
  UserCog,
  Users,
} from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { toast } from 'sonner'

import { adminApi } from '@/api/endpoints'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { LoadingPanel } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import {
  formatDateTime,
  formatMinutes,
  formatNumber,
  formatRelative,
  titleCase,
} from '@/lib/format'
import type {
  AdminUserResponse,
  PageResponse,
  Role,
  SystemStats,
  UserPrivacyMetadata,
  UserStatus,
} from '@/types/api'

const ADMIN_KEYS = {
  stats: ['admin', 'stats'] as const,
  users: (query: { q: string; roles: string[]; page: number; size: number }) =>
    ['admin', 'users', query] as const,
  privacy: (userId: string) => ['admin', 'users', userId, 'privacy'] as const,
  auditLogs: (page: number, size: number) => ['admin', 'audit-logs', page, size] as const,
  errorLogs: (page: number, size: number, severity: string) =>
    ['admin', 'error-logs', page, size, severity] as const,
  settings: ['admin', 'settings'] as const,
  aiConfiguration: ['admin', 'ai-configuration'] as const,
}

const USER_PAGE_SIZE = 20
const LOG_PAGE_SIZE = 25
const ALL_SEVERITIES = 'ALL'
const SEVERITIES = ['ERROR', 'WARN', 'INFO']

type PendingChange =
  | { kind: 'role'; user: AdminUserResponse; role: Role }
  | { kind: 'status'; user: AdminUserResponse; status: UserStatus }

function Pager({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) {
    return <p className="text-xs text-muted-foreground">{formatNumber(totalElements)} record(s)</p>
  }
  return (
    <div className="flex items-center justify-between gap-3">
      <p className="text-xs tabular-nums text-muted-foreground">
        {formatNumber(totalElements)} record(s) · page {page + 1} of {totalPages}
      </p>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="outline" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Previous
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  )
}

function QueryFailure({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <EmptyState
      icon={<AlertTriangle />}
      title="This request failed"
      description={toNormalisedError(error).message}
      action={
        <Button onClick={onRetry}>
          <RefreshCw />
          Try again
        </Button>
      }
    />
  )
}

function OverviewTab() {
  const stats = useQuery({ queryKey: ADMIN_KEYS.stats, queryFn: () => adminApi.stats() })

  if (stats.isPending) return <LoadingPanel label="Loading system statistics" />
  if (stats.isError) return <QueryFailure error={stats.error} onRetry={() => void stats.refetch()} />

  const data: SystemStats = stats.data
  const cards = [
    { label: 'Users', value: formatNumber(data.totalUsers), hint: `${formatNumber(data.activeUsers)} active` },
    { label: 'Disabled users', value: formatNumber(data.disabledUsers) },
    { label: 'New this week', value: formatNumber(data.usersRegisteredLast7Days) },
    { label: 'Tasks', value: formatNumber(data.totalTasks) },
    { label: 'Goals', value: formatNumber(data.totalGoals) },
    { label: 'Habits', value: formatNumber(data.totalHabits) },
    { label: 'Documents', value: formatNumber(data.totalDocuments) },
    { label: 'Knowledge chunks', value: formatNumber(data.totalChunks) },
    { label: 'Journal entries', value: formatNumber(data.totalJournalEntries) },
    { label: 'Focus time', value: formatMinutes(data.totalFocusMinutes) },
    {
      label: 'Errors (24h)',
      value: formatNumber(data.errorsLast24h),
      tone: data.errorsLast24h > 0 ? 'text-destructive' : undefined,
    },
    {
      label: 'Warnings (24h)',
      value: formatNumber(data.warningsLast24h),
      tone: data.warningsLast24h > 0 ? 'text-warning' : undefined,
    },
    { label: 'AI requests (24h)', value: formatNumber(data.aiRequestsLast24h) },
  ]

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((card) => (
          <StatCard
            key={card.label}
            label={card.label}
            value={card.value}
            hint={card.hint}
            tone={card.tone}
          />
        ))}
      </div>
      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <Info className="size-3.5" aria-hidden />
        Counted at {formatDateTime(data.generatedAt)}
      </p>
      <Button variant="outline" size="sm" onClick={() => void stats.refetch()}>
        <RefreshCw />
        Recount
      </Button>
    </div>
  )
}

function PrivacyDialog({ metadata, onClose }: { metadata: UserPrivacyMetadata; onClose: () => void }) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Data held for {metadata.email}</DialogTitle>
          <DialogDescription>
            Counts and timestamps only. This view never exposes journal text, document text or any other
            content belonging to the user.
          </DialogDescription>
        </DialogHeader>

        <dl className="grid gap-3 sm:grid-cols-3">
          <div>
            <dt className="text-xs text-muted-foreground">Journal entries</dt>
            <dd className="text-lg font-semibold tabular-nums">
              {formatNumber(metadata.journalEntryCount)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Documents</dt>
            <dd className="text-lg font-semibold tabular-nums">
              {formatNumber(metadata.knowledgeDocumentCount)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Knowledge chunks</dt>
            <dd className="text-lg font-semibold tabular-nums">
              {formatNumber(metadata.knowledgeChunkCount)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Last journal entry</dt>
            <dd className="text-sm">{formatDateTime(metadata.lastJournalEntryAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Last document upload</dt>
            <dd className="text-sm">{formatDateTime(metadata.lastDocumentUploadAt)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">User id</dt>
            <dd className="truncate font-mono text-xs">{metadata.userId}</dd>
          </div>
        </dl>

        <div className="rounded-md border border-primary/40 bg-primary/5 p-3">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-primary">Access policy</h3>
          <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{metadata.policy}</p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function UsersTab() {
  const queryClient = useQueryClient()
  const [term, setTerm] = useState('')
  const [query, setQuery] = useState('')
  const [role, setRole] = useState('ALL')
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState<PendingChange | null>(null)
  const [privacyUserId, setPrivacyUserId] = useState<string | null>(null)

  useEffect(() => {
    setPage(0)
  }, [query, role])

  const roles = role === 'ALL' ? [] : [role]
  const users = useQuery({
    queryKey: ADMIN_KEYS.users({ q: query, roles, page, size: USER_PAGE_SIZE }),
    queryFn: () =>
      adminApi.users({
        q: query || undefined,
        roles,
        page,
        size: USER_PAGE_SIZE,
      }),
  })

  const privacy = useQuery({
    queryKey: ADMIN_KEYS.privacy(privacyUserId ?? ''),
    queryFn: () => adminApi.privacy(privacyUserId ?? ''),
    enabled: privacyUserId !== null,
  })

  const updateRole = useMutation({
    mutationFn: (input: { userId: string; role: Role }) => adminApi.updateUserRole(input.userId, { role: input.role }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.stats })
      setPending(null)
      toast.success('Role updated')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const updateStatus = useMutation({
    mutationFn: (input: { userId: string; status: UserStatus }) =>
      adminApi.updateUserStatus(input.userId, { status: input.status }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.stats })
      setPending(null)
      toast.success('Status updated')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const applyPending = () => {
    if (!pending) return
    if (pending.kind === 'role') updateRole.mutate({ userId: pending.user.id, role: pending.role })
    else updateStatus.mutate({ userId: pending.user.id, status: pending.status })
  }

  const page_: PageResponse<AdminUserResponse> | undefined = users.data

  return (
    <div className="space-y-4">
      <form
        className="flex flex-col gap-3 sm:flex-row sm:items-end"
        onSubmit={(event: FormEvent) => {
          event.preventDefault()
          setQuery(term.trim())
        }}
      >
        <div className="flex-1 space-y-1.5">
          <Label htmlFor="admin-user-search">Search users</Label>
          <Input
            id="admin-user-search"
            value={term}
            placeholder="Email or name"
            onChange={(event) => setTerm(event.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="admin-user-role">Role</Label>
          <Select value={role} onValueChange={setRole}>
            <SelectTrigger id="admin-user-role" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All roles</SelectItem>
              <SelectItem value="USER">User</SelectItem>
              <SelectItem value="ADMIN">Admin</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="outline">
          Search
        </Button>
      </form>

      {users.isPending ? <LoadingPanel label="Loading users" /> : null}
      {users.isError ? <QueryFailure error={users.error} onRetry={() => void users.refetch()} /> : null}

      {page_ && page_.content.length === 0 ? (
        <EmptyState
          icon={<Users />}
          title="No users matched"
          description="No account matches the current search and role filter."
          action={
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setTerm('')
                setQuery('')
                setRole('ALL')
              }}
            >
              Reset filters
            </Button>
          }
        />
      ) : null}

      {page_ && page_.content.length > 0 ? (
        <div className="space-y-3">
          <ul className="space-y-2">
            {page_.content.map((user) => (
              <li
                key={user.id}
                className="flex flex-col gap-3 rounded-lg border bg-background/40 p-3 lg:flex-row lg:items-start lg:justify-between"
              >
                <div className="min-w-0 space-y-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="truncate text-sm font-medium">{user.fullName || 'Unnamed'}</span>
                    <StatusBadge value={user.role} />
                    <StatusBadge value={user.status} />
                    {!user.emailVerified ? <Badge variant="warning">Unverified email</Badge> : null}
                    {!user.onboardingCompleted ? <Badge variant="outline">Setup incomplete</Badge> : null}
                  </div>
                  <p className="truncate text-xs text-muted-foreground">{user.email}</p>
                  <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    <span>{formatNumber(user.taskCount)} tasks</span>
                    <span>{formatNumber(user.goalCount)} goals</span>
                    <span>{formatNumber(user.activeSessions)} active session(s)</span>
                    <span>joined {formatRelative(user.createdAt)}</span>
                    <span>
                      last login{' '}
                      {user.lastLoginAt ? formatRelative(user.lastLoginAt) : 'never'}
                    </span>
                  </p>
                </div>

                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setPrivacyUserId(user.id)}
                    loading={privacy.isFetching && privacyUserId === user.id}
                  >
                    <Eye />
                    Privacy
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      setPending({
                        kind: 'role',
                        user,
                        role: user.role === 'ADMIN' ? 'USER' : 'ADMIN',
                      })
                    }
                  >
                    <UserCog />
                    {user.role === 'ADMIN' ? 'Demote to user' : 'Promote to admin'}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      setPending({
                        kind: 'status',
                        user,
                        status: user.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED',
                      })
                    }
                  >
                    {user.status === 'DISABLED' ? 'Enable account' : 'Disable account'}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <Pager
            page={page_.page}
            totalPages={page_.totalPages}
            totalElements={page_.totalElements}
            onChange={setPage}
          />
        </div>
      ) : null}

      {privacyUserId !== null && privacy.data ? (
        <PrivacyDialog metadata={privacy.data} onClose={() => setPrivacyUserId(null)} />
      ) : null}
      {privacyUserId !== null && privacy.isError ? (
        <Dialog open onOpenChange={(open) => !open && setPrivacyUserId(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Privacy metadata unavailable</DialogTitle>
              <DialogDescription>{toNormalisedError(privacy.error).message}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setPrivacyUserId(null)}>
                Close
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      ) : null}

      <Dialog open={pending !== null} onOpenChange={(open) => !open && setPending(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pending?.kind === 'role'
                ? `Change ${pending.user.email} to ${pending.role}?`
                : pending
                  ? `Set ${pending.user.email} to ${pending.status}?`
                  : ''}
            </DialogTitle>
            <DialogDescription>
              {pending?.kind === 'role' ? (
                pending.role === 'ADMIN' ? (
                  <>
                    This grants full administrative access to every account, including the audit log, the user
                    list and the system settings. Confirm that {pending.user.email} should receive it.
                  </>
                ) : (
                  <>
                    This removes administrative access from {pending.user.email}. They will keep their own data
                    but lose access to the admin console.
                  </>
                )
              ) : pending ? (
                pending.status === 'DISABLED' ? (
                  <>
                    Disabling {pending.user.email} signs them out everywhere and blocks sign-in until the account
                    is enabled again.
                  </>
                ) : (
                  <>This re-enables sign-in for {pending.user.email}.</>
                )
              ) : null}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPending(null)}>
              Cancel
            </Button>
            <Button
              variant={pending?.kind === 'role' ? 'destructive' : 'default'}
              loading={updateRole.isPending || updateStatus.isPending}
              onClick={applyPending}
            >
              Confirm
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function AuditLogTab() {
  const [page, setPage] = useState(0)
  const logs = useQuery({
    queryKey: ADMIN_KEYS.auditLogs(page, LOG_PAGE_SIZE),
    queryFn: () => adminApi.auditLogs({ page, size: LOG_PAGE_SIZE }),
  })

  return (
    <div className="space-y-4">
      {logs.isPending ? <LoadingPanel label="Loading audit log" /> : null}
      {logs.isError ? <QueryFailure error={logs.error} onRetry={() => void logs.refetch()} /> : null}

      {logs.data && logs.data.content.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck />}
          title="No audit entries"
          description="Nothing has been recorded against the audit log for this page."
        />
      ) : null}

      {logs.data && logs.data.content.length > 0 ? (
        <div className="space-y-3">
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full min-w-[54rem] text-left text-sm">
              <thead className="border-b bg-secondary/40 text-xs uppercase tracking-wide text-muted-foreground">
                <tr>
                  <th scope="col" className="px-3 py-2 font-medium">
                    When
                  </th>
                  <th scope="col" className="px-3 py-2 font-medium">
                    Actor
                  </th>
                  <th scope="col" className="px-3 py-2 font-medium">
                    Action
                  </th>
                  <th scope="col" className="px-3 py-2 font-medium">
                    Entity
                  </th>
                  <th scope="col" className="px-3 py-2 font-medium">
                    Details
                  </th>
                  <th scope="col" className="px-3 py-2 font-medium">
                    IP
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {logs.data.content.map((log) => (
                  <tr key={log.id}>
                    <td className="whitespace-nowrap px-3 py-2 text-xs text-muted-foreground">
                      {formatDateTime(log.createdAt)}
                    </td>
                    <td className="px-3 py-2">
                      <span className="block max-w-48 truncate">{log.actorEmail || '—'}</span>
                      <span className="block max-w-48 truncate font-mono text-xs text-muted-foreground">
                        {log.actorUserId || '—'}
                      </span>
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge value={log.action} label={titleCase(log.action)} />
                    </td>
                    <td className="px-3 py-2">
                      <span className="block text-xs">{log.entityType || '—'}</span>
                      <span className="block max-w-40 truncate font-mono text-xs text-muted-foreground">
                        {log.entityId || '—'}
                      </span>
                    </td>
                    <td className="max-w-72 px-3 py-2">
                      <span className="block whitespace-pre-wrap break-words text-xs text-muted-foreground">
                        {log.details || '—'}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 font-mono text-xs text-muted-foreground">
                      {log.ipAddress || '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={logs.data.page}
            totalPages={logs.data.totalPages}
            totalElements={logs.data.totalElements}
            onChange={setPage}
          />
        </div>
      ) : null}
    </div>
  )
}

function ErrorLogTab() {
  const [page, setPage] = useState(0)
  const [severity, setSeverity] = useState(ALL_SEVERITIES)

  useEffect(() => {
    setPage(0)
  }, [severity])

  const logs = useQuery({
    queryKey: ADMIN_KEYS.errorLogs(page, LOG_PAGE_SIZE, severity),
    queryFn: () =>
      adminApi.errorLogs({
        page,
        size: LOG_PAGE_SIZE,
        severity: severity === ALL_SEVERITIES ? undefined : severity,
      }),
  })

  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="admin-error-severity">Severity</Label>
        <Select value={severity} onValueChange={setSeverity}>
          <SelectTrigger id="admin-error-severity" className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_SEVERITIES}>All severities</SelectItem>
            {SEVERITIES.map((value) => (
              <SelectItem key={value} value={value}>
                {titleCase(value)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {logs.isPending ? <LoadingPanel label="Loading error log" /> : null}
      {logs.isError ? <QueryFailure error={logs.error} onRetry={() => void logs.refetch()} /> : null}

      {logs.data && logs.data.content.length === 0 ? (
        <EmptyState
          icon={<FileWarning />}
          title="No error entries"
          description="Nothing has been logged at this severity for this page."
        />
      ) : null}

      {logs.data && logs.data.content.length > 0 ? (
        <div className="space-y-3">
          <ul className="space-y-2">
            {logs.data.content.map((log) => (
              <li key={log.id} className="rounded-lg border bg-background/40 p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge value={log.severity} label={titleCase(log.severity)} />
                  {log.method ? <Badge variant="outline">{log.method}</Badge> : null}
                  <span className="font-mono text-xs text-muted-foreground">{log.path || '—'}</span>
                  <span className="ml-auto text-xs text-muted-foreground">
                    {formatDateTime(log.createdAt)}
                  </span>
                </div>
                <p className="mt-1.5 whitespace-pre-wrap break-words text-sm">{log.message}</p>
                <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                  <span className="font-mono">{log.exceptionClass || 'no exception recorded'}</span>
                  <span className="font-mono">user {log.userId || '—'}</span>
                </p>
              </li>
            ))}
          </ul>
          <Pager
            page={logs.data.page}
            totalPages={logs.data.totalPages}
            totalElements={logs.data.totalElements}
            onChange={setPage}
          />
        </div>
      ) : null}
    </div>
  )
}

function SettingsTab() {
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ADMIN_KEYS.settings, queryFn: () => adminApi.settings() })

  const [key, setKey] = useState('')
  const [value, setValue] = useState('')
  const [valueType, setValueType] = useState('STRING')
  const [description, setDescription] = useState('')

  const upsert = useMutation({
    mutationFn: () =>
      adminApi.upsertSetting({
        key: key.trim(),
        value,
        valueType,
        description: description.trim() || undefined,
      }),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_KEYS.settings })
      setKey(saved.key)
      setValue(saved.value ?? '')
      setValueType(saved.valueType || 'STRING')
      setDescription(saved.description ?? '')
      toast.success(`Saved ${saved.key}`)
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const startEdit = (settingKey: string, settingValue: string, settingType: string, settingDescription: string) => {
    setKey(settingKey)
    setValue(settingValue)
    setValueType(settingType || 'STRING')
    setDescription(settingDescription)
  }

  const failure = upsert.isError ? toNormalisedError(upsert.error) : null
  const fieldErrors = failure?.fieldErrors ?? {}

  return (
    <div className="space-y-4">
      <form
        className="space-y-4 rounded-lg border p-4"
        onSubmit={(event: FormEvent) => {
          event.preventDefault()
          upsert.mutate()
        }}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="setting-key">Key</Label>
            <Input
              id="setting-key"
              value={key}
              placeholder="ai.temperature"
              onChange={(event) => setKey(event.target.value)}
              aria-invalid={Boolean(fieldErrors.key)}
            />
            <p className="text-xs text-muted-foreground">
              Saving an existing key updates it in place.
            </p>
            <p role="alert" className="text-xs text-destructive">
              {fieldErrors.key}
            </p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="setting-type">Value type</Label>
            <Input
              id="setting-type"
              value={valueType}
              placeholder="STRING"
              onChange={(event) => setValueType(event.target.value)}
              aria-invalid={Boolean(fieldErrors.valueType)}
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="setting-value">Value</Label>
          <Textarea
            id="setting-value"
            rows={3}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            aria-invalid={Boolean(fieldErrors.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="setting-description">Description</Label>
          <Input
            id="setting-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-invalid={Boolean(fieldErrors.description)}
          />
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={upsert.isPending} disabled={key.trim().length === 0}>
            <Save />
            {settings.data?.some((setting) => setting.key === key.trim()) ? 'Update setting' : 'Create setting'}
          </Button>
        </div>
      </form>

      {settings.isPending ? <LoadingPanel label="Loading settings" /> : null}
      {settings.isError ? <QueryFailure error={settings.error} onRetry={() => void settings.refetch()} /> : null}

      {settings.data && settings.data.length === 0 ? (
        <EmptyState
          icon={<Database />}
          title="No settings stored"
          description="Nothing has been written to the settings table yet. Use the form above to create the first one."
        />
      ) : null}

      {settings.data && settings.data.length > 0 ? (
        <ul className="space-y-2">
          {settings.data.map((setting) => (
            <li
              key={setting.key}
              className="flex flex-col gap-2 rounded-lg border bg-background/40 p-3 sm:flex-row sm:items-start sm:justify-between"
            >
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-sm font-medium">{setting.key}</span>
                  <Badge variant="outline">{setting.valueType}</Badge>
                </div>
                <p className="whitespace-pre-wrap break-words font-mono text-xs text-muted-foreground">
                  {setting.value || '(empty)'}
                </p>
                {setting.description ? (
                  <p className="text-xs text-muted-foreground">{setting.description}</p>
                ) : null}
                <p className="text-xs text-muted-foreground">
                  updated {formatRelative(setting.updatedAt)}
                  {setting.updatedBy ? ` by ${setting.updatedBy}` : ''}
                </p>
              </div>
              <Button
                size="sm"
                variant="outline"
                className="shrink-0"
                onClick={() => startEdit(setting.key, setting.value, setting.valueType, setting.description)}
              >
                Edit
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function AiConfigurationTab() {
  const config = useQuery({ queryKey: ADMIN_KEYS.aiConfiguration, queryFn: () => adminApi.aiConfiguration() })

  if (config.isPending) return <LoadingPanel label="Loading AI configuration" />
  if (config.isError) return <QueryFailure error={config.error} onRetry={() => void config.refetch()} />

  const data = config.data
  const remoteConfigured = data.geminiConfigured || data.openAiConfigured || data.ollamaConfigured
  const modelEntries = Object.entries(data.models ?? {})

  return (
    <div className="space-y-4">
      <div
        className={`flex items-start gap-3 rounded-lg border p-4 ${
          remoteConfigured
            ? 'border-success/40 bg-success/5'
            : 'border-warning/40 bg-warning/5'
        }`}
      >
        {remoteConfigured ? (
          <ShieldCheck className="mt-0.5 size-5 shrink-0 text-success" aria-hidden />
        ) : (
          <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning" aria-hidden />
        )}
        <div className="space-y-1">
          <p className={`text-sm font-semibold ${remoteConfigured ? 'text-success' : 'text-warning'}`}>
            {remoteConfigured
              ? `Remote provider configured: ${data.configuredProvider}`
              : 'No remote provider is configured'}
          </p>
          <p className="text-sm text-muted-foreground">
            {remoteConfigured
              ? 'Requests that need generation are sent to the provider named above.'
              : 'LIFEOS is running on the built-in offline provider. No API key is configured, so summaries, insights, plans and answers are produced by the deterministic on-device heuristic. Output is a computation over your own records, not a remote model’s opinion.'}
          </p>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <StatCard label="Configured provider" value={data.configuredProvider || 'none'} />
        <StatCard
          label="Embedding fallback active"
          value={data.embeddingFallbackActive ? 'Yes' : 'No'}
          hint={data.embeddingFallbackActive ? 'Embeddings are produced locally.' : undefined}
        />
        <StatCard label="Vector dimensions" value={formatNumber(data.vectorDimensions)} />
      </div>

      <ul className="divide-y rounded-lg border">
        <li className="flex items-center justify-between gap-3 px-4 py-3">
          <span className="text-sm">Gemini</span>
          <StatusBadge value={data.geminiConfigured ? 'CONFIGURED' : 'NOT_CONFIGURED'} label={data.geminiConfigured ? 'Configured' : 'Not configured'} />
        </li>
        <li className="flex items-center justify-between gap-3 px-4 py-3">
          <span className="text-sm">OpenAI</span>
          <StatusBadge value={data.openAiConfigured ? 'CONFIGURED' : 'NOT_CONFIGURED'} label={data.openAiConfigured ? 'Configured' : 'Not configured'} />
        </li>
        <li className="flex items-center justify-between gap-3 px-4 py-3">
          <span className="text-sm">Ollama (local)</span>
          <StatusBadge value={data.ollamaConfigured ? 'CONFIGURED' : 'NOT_CONFIGURED'} label={data.ollamaConfigured ? 'Configured' : 'Not configured'} />
        </li>
      </ul>

      <div>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Models</h3>
        {modelEntries.length === 0 ? (
          <p className="text-sm text-muted-foreground">The server reported no models.</p>
        ) : (
          <dl className="divide-y rounded-lg border">
            {modelEntries.map(([name, value]) => (
              <div key={name} className="flex items-center justify-between gap-3 px-4 py-2.5">
                <dt className="text-sm capitalize">{titleCase(name)}</dt>
                <dd className="truncate font-mono text-xs text-muted-foreground">{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Notes</h3>
        {data.notes?.length ? (
          <ul className="space-y-2">
            {data.notes.map((note) => (
              <li
                key={note}
                className="flex items-start gap-2 rounded-md border bg-background/40 px-3 py-2 text-sm text-muted-foreground"
              >
                <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                {note}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">The server reported no notes.</p>
        )}
      </div>
    </div>
  )
}

export default function AdminPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Administration"
        description="System counts, account management, logs, settings and the active AI provider."
      />

      <Tabs defaultValue="overview">
        <TabsList className="h-auto w-full flex-wrap justify-start gap-1">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="audit">Audit log</TabsTrigger>
          <TabsTrigger value="errors">Error log</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
          <TabsTrigger value="ai">AI configuration</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <SectionCard title="System statistics" description="Counted server-side across every account.">
            <OverviewTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="users">
          <SectionCard
            title="Users"
            description="Search accounts, change roles and status, and review what data each account holds."
          >
            <UsersTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="audit">
          <SectionCard title="Audit log" description="Every privileged action the server recorded.">
            <AuditLogTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="errors">
          <SectionCard title="Error log" description="Exceptions captured by the server.">
            <ErrorLogTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="settings">
          <SectionCard title="System settings" description="Key/value configuration stored by the server.">
            <SettingsTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="ai">
          <SectionCard title="AI configuration" description="Which provider LIFEOS is actually using.">
            <AiConfigurationTab />
          </SectionCard>
        </TabsContent>
      </Tabs>
    </div>
  )
}