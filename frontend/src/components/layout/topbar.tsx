import { Bell, LogOut, Menu, Search, User as UserIcon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { Avatar, AvatarFallback, AvatarImage, initialsOf } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { useUnreadCount } from '@/hooks/use-notifications'
import { useNotificationsSocket } from '@/providers/notifications-socket'
import { useAuth } from '@/stores/auth'

export function AppTopbar({ onOpenNav }: { onOpenNav: () => void }) {
  const { user, logout } = useAuth()
  const { data: unreadCount } = useUnreadCount()
  const unread = unreadCount?.unread ?? 0
  const { connected } = useNotificationsSocket()
  const navigate = useNavigate()
  const [term, setTerm] = useState('')

  const submitSearch = (event: FormEvent) => {
    event.preventDefault()
    const query = term.trim()
    if (!query) return
    navigate(`/app/search?q=${encodeURIComponent(query)}`)
    setTerm('')
  }

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b bg-background/90 px-4 backdrop-blur">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onOpenNav} aria-label="Open navigation">
        <Menu />
      </Button>

      <form onSubmit={submitSearch} role="search" className="relative flex-1 max-w-md">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          placeholder="Search tasks, goals, knowledge…"
          aria-label="Search everything"
          className="pl-8"
        />
      </form>

      <span
        className="ml-auto hidden items-center gap-1.5 text-xs text-muted-foreground sm:flex"
        title={connected ? 'Live notifications connected' : 'Live notifications offline'}
      >
        <span
          className={`size-1.5 rounded-full ${connected ? 'bg-success' : 'bg-muted-foreground/40'}`}
          aria-hidden
        />
        {connected ? 'Live' : 'Offline'}
      </span>

      <Button asChild variant="ghost" size="icon" className="relative" aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
        <Link to="/app/notifications">
          <Bell />
          {unread > 0 ? (
            <span className="absolute -right-0.5 -top-0.5 grid min-w-4 place-items-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground">
              {unread > 99 ? '99+' : unread}
            </span>
          ) : null}
        </Link>
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            type="button"
            className="flex items-center gap-2 rounded-full p-0.5 transition-colors hover:bg-secondary"
            aria-label="Account menu"
          >
            <Avatar className="size-8">
              {user?.avatarUrl ? <AvatarImage src={user.avatarUrl} alt="" /> : null}
              <AvatarFallback>{initialsOf(user?.fullName)}</AvatarFallback>
            </Avatar>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56">
          <DropdownMenuLabel className="flex flex-col gap-0.5 normal-case">
            <span className="text-sm font-medium text-foreground">{user?.fullName}</span>
            <span className="text-xs font-normal text-muted-foreground">{user?.email}</span>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => navigate('/app/settings')}>
            <UserIcon /> Profile and preferences
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={() => {
              void logout().then(() => navigate('/login', { replace: true }))
            }}
          >
            <LogOut /> Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}
