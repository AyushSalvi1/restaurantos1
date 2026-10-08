import { useState } from 'react'
import { AlertTriangle, Bell, BellOff, CheckCheck, Inbox, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

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
import { LoadingPanel } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  useClearNotifications,
  useDeleteNotification,
  useMarkAllRead,
  useMarkRead,
  useNotifications,
} from '@/hooks/use-notifications'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import { formatDateTime, formatNumber, formatRelative, titleCase } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { NotificationCategory, NotificationResponse } from '@/types/api'

const ALL = 'ALL'
const PAGE_SIZE = 20

const CATEGORIES: NotificationCategory[] = [
  'TASK',
  'GOAL',
  'HABIT',
  'CALENDAR',
  'FINANCE',
  'LEARNING',
  'AI_INSIGHT',
  'SYSTEM',
]

/** Server-supplied links only navigate when they already point inside the app shell. */
function appRoute(path: string | null | undefined): string | null {
  return path && path.startsWith('/app') ? path : null
}

type PendingDelete = { kind: 'one'; id: string } | { kind: 'all' }

function NotificationRow({
  notification,
  onMarkRead,
  onDelete,
  busy,
}: {
  notification: NotificationResponse
  onMarkRead: (id: string) => void
  onDelete: (id: string) => void
  busy: boolean
}) {
  const target = appRoute(notification.link)
  return (
    <li
      className={cn(
        'flex flex-col gap-2 rounded-lg border bg-background/40 p-3 sm:flex-row sm:items-start sm:justify-between',
        !notification.read && 'border-primary/50 bg-primary/5',
      )}
    >
      <div className="min-w-0 space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          {!notification.read ? (
            <span className="size-2 shrink-0 rounded-full bg-primary" aria-hidden />
          ) : null}
          <Badge variant="outline">{titleCase(notification.category)}</Badge>
          <StatusBadge value={notification.priority} />
          {target ? (
            <Link
              to={target}
              className="truncate text-sm font-medium underline-offset-4 hover:underline"
            >
              {notification.title}
            </Link>
          ) : (
            <span className="truncate text-sm font-medium">{notification.title}</span>
          )}
        </div>
        {notification.body ? (
          <p className="text-sm text-muted-foreground">{notification.body}</p>
        ) : null}
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          <time dateTime={notification.createdAt} title={formatDateTime(notification.createdAt)}>
            {formatRelative(notification.createdAt)}
          </time>
          {notification.read && notification.readAt ? (
            <span>Read {formatRelative(notification.readAt)}</span>
          ) : null}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        {notification.read ? null : (
          <Button size="sm" variant="outline" disabled={busy} onClick={() => onMarkRead(notification.id)}>
            <CheckCheck />
            Mark read
          </Button>
        )}
        <Button
          size="icon-sm"
          variant="ghost"
          disabled={busy}
          aria-label={`Delete notification: ${notification.title}`}
          onClick={() => onDelete(notification.id)}
        >
          <Trash2 />
        </Button>
      </div>
    </li>
  )
}

export default function NotificationsPage() {
  const [category, setCategory] = useState<NotificationCategory | typeof ALL>(ALL)
  const [page, setPage] = useState(0)
  const [pendingDelete, setPendingDelete] = useState<PendingDelete | null>(null)

  const query = category === ALL ? { page, size: PAGE_SIZE } : { category, page, size: PAGE_SIZE }
  const notifications = useNotifications(query)

  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()
  const removeOne = useDeleteNotification()
  const clearAll = useClearNotifications()

  const data = notifications.data
  const items = data?.notifications ?? []
  const totalPages = data?.totalPages ?? 0
  const busy = markRead.isPending || markAllRead.isPending || removeOne.isPending || clearAll.isPending

  const markOne = (id: string) => {
    markRead.mutate(id, {
      onSuccess: () => toast.success('Marked as read'),
      onError: (error) => toast.error(toNormalisedError(error).message),
    })
  }

  const markCurrentCategory = () => {
    markAllRead.mutate(category === ALL ? undefined : category, {
      onSuccess: (result) =>
        toast.success(`${formatNumber(result.marked)} notification(s) marked as read`),
      onError: (error) => toast.error(toNormalisedError(error).message),
    })
  }

  const confirmDelete = () => {
    if (!pendingDelete) return
    const options = {
      onSuccess: () =>
        toast.success(pendingDelete.kind === 'all' ? 'All notifications removed' : 'Notification deleted'),
      onError: (error: unknown) => toast.error(toNormalisedError(error).message),
    }
    if (pendingDelete.kind === 'all') clearAll.mutate(undefined, options)
    else removeOne.mutate(pendingDelete.id, options)
    setPendingDelete(null)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Notifications"
        description="Everything LIFEOS raised for you, newest first."
        actions={
          <>
            <Button
              variant="outline"
              disabled={busy || (data?.unreadCount ?? 0) === 0}
              onClick={markCurrentCategory}
              loading={markAllRead.isPending}
            >
              <CheckCheck />
              {category === ALL ? 'Mark all read' : `Mark ${titleCase(category)} read`}
            </Button>
            <Button
              variant="destructive"
              disabled={busy || items.length === 0}
              onClick={() => setPendingDelete({ kind: 'all' })}
            >
              <Trash2 />
              Clear all
            </Button>
          </>
        }
      />

      <Tabs
        value={category}
        onValueChange={(value) => {
          setCategory(value as NotificationCategory | typeof ALL)
          setPage(0)
        }}
      >
        <TabsList className="h-auto w-full flex-wrap justify-start gap-1 overflow-x-auto">
          <TabsTrigger value={ALL}>All</TabsTrigger>
          {CATEGORIES.map((value) => (
            <TabsTrigger key={value} value={value}>
              {titleCase(value)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <Bell className="size-3.5" aria-hidden />
          <span className="font-medium tabular-nums text-foreground">{data?.unreadCount ?? 0}</span> unread
        </span>
        <span className="tabular-nums">
          {formatNumber(data?.totalElements ?? 0)} notification
          {(data?.totalElements ?? 0) === 1 ? '' : 's'} in total
        </span>
      </div>

      {notifications.isPending ? <LoadingPanel label="Loading notifications" /> : null}

      {notifications.isError ? (
        <EmptyState
          icon={<AlertTriangle />}
          title="Notifications could not be loaded"
          description={toNormalisedError(notifications.error).message}
          action={
            <Button onClick={() => void notifications.refetch()}>
              Try again
            </Button>
          }
        />
      ) : null}

      {!notifications.isPending && !notifications.isError && items.length === 0 ? (
        <EmptyState
          icon={<Inbox />}
          title={category === ALL ? 'No notifications' : `No ${titleCase(category).toLowerCase()} notifications`}
          description={
            category === ALL
              ? 'Nothing has been raised for you yet. Deadlines, habit reminders and budget warnings land here.'
              : 'Nothing in this category right now. Switch to All to see everything raised for you.'
          }
          action={
            <Button asChild size="sm" variant="outline">
              <Link to="/app/today">Go to today</Link>
            </Button>
          }
        />
      ) : null}

      {items.length > 0 ? (
        <SectionCard
          title={category === ALL ? 'All notifications' : `${titleCase(category)} notifications`}
          description={`Page ${(data?.page ?? 0) + 1} of ${Math.max(totalPages, 1)}`}
          contentClassName="p-3 sm:p-4"
        >
          <ul className="space-y-2">
            {items.map((notification) => (
              <NotificationRow
                key={notification.id}
                notification={notification}
                busy={busy}
                onMarkRead={markOne}
                onDelete={(id) => setPendingDelete({ kind: 'one', id })}
              />
            ))}
          </ul>
        </SectionCard>
      ) : null}

      {totalPages > 1 ? (
        <div className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Previous
          </Button>
          <p className="text-sm tabular-nums text-muted-foreground">
            Page {page + 1} of {totalPages}
          </p>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </Button>
        </div>
      ) : null}

      <Dialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pendingDelete?.kind === 'all' ? 'Remove every notification?' : 'Delete this notification?'}
            </DialogTitle>
            <DialogDescription>
              {pendingDelete?.kind === 'all'
                ? 'This removes every notification on your account, including unread ones, and cannot be undone. To stop new ones arriving, turn categories off in Settings instead.'
                : 'This notification is removed from your account and cannot be undone.'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingDelete(null)}>
              Keep it
            </Button>
            <Button variant="destructive" loading={busy} onClick={confirmDelete}>
              {pendingDelete?.kind === 'all' ? (
                <>
                  <BellOff />
                  Remove everything
                </>
              ) : (
                <>
                  <Trash2 />
                  Delete
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}