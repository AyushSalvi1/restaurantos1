import {
  BarChart3,
  Brain,
  CalendarDays,
  CheckSquare,
  Coins,
  Compass,
  Focus,
  GraduationCap,
  LayoutDashboard,
  Network,
  NotebookPen,
  Settings,
  Shield,
  Sparkles,
  Target,
  TrendingUp,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'

import { cn } from '@/lib/utils'
import { useAuth } from '@/stores/auth'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  adminOnly?: boolean
}

interface NavSection {
  title: string
  items: NavItem[]
}

const SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [
      { to: '/app', label: 'Dashboard', icon: LayoutDashboard },
      { to: '/app/today', label: 'Today', icon: Compass },
      { to: '/app/search', label: 'Search', icon: Sparkles },
    ],
  },
  {
    title: 'Work',
    items: [
      { to: '/app/tasks', label: 'Tasks', icon: CheckSquare },
      { to: '/app/goals', label: 'Goals', icon: Target },
      { to: '/app/calendar', label: 'Calendar', icon: CalendarDays },
      { to: '/app/focus', label: 'Focus', icon: Focus },
    ],
  },
  {
    title: 'Life',
    items: [
      { to: '/app/habits', label: 'Habits', icon: TrendingUp },
      { to: '/app/learning', label: 'Learning', icon: GraduationCap },
      { to: '/app/finance', label: 'Finance', icon: Coins },
      { to: '/app/journal', label: 'Journal', icon: NotebookPen },
      { to: '/app/health', label: 'Life graph', icon: Network },
    ],
  },
  {
    title: 'Intelligence',
    items: [
      { to: '/app/knowledge', label: 'Knowledge', icon: Brain },
      { to: '/app/assistant', label: 'Assistant', icon: Sparkles },
      { to: '/app/analytics', label: 'Analytics', icon: BarChart3 },
    ],
  },
  {
    title: 'Account',
    items: [
      { to: '/app/settings', label: 'Settings', icon: Settings },
      { to: '/app/admin', label: 'Administration', icon: Shield, adminOnly: true },
    ],
  },
]

export function AppSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'

  return (
    <nav className="flex h-full flex-col gap-5 overflow-y-auto p-4" aria-label="Primary">
      <div className="flex items-center gap-2 px-1">
        <span className="grid size-8 place-items-center rounded-lg bg-primary/15 text-sm font-bold text-primary">
          L
        </span>
        <div>
          <p className="text-sm font-semibold leading-none">LIFEOS</p>
          <p className="text-xs text-muted-foreground">Personal operating system</p>
        </div>
      </div>

      {SECTIONS.map((section) => {
        const items = section.items.filter((item) => !item.adminOnly || isAdmin)
        if (items.length === 0) return null
        return (
          <div key={section.title} className="space-y-1">
            <p className="px-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground/70">
              {section.title}
            </p>
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/app'}
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors',
                    isActive
                      ? 'bg-primary/15 font-medium text-primary'
                      : 'text-muted-foreground hover:bg-secondary/60 hover:text-foreground',
                  )
                }
              >
                <item.icon className="size-4 shrink-0" aria-hidden />
                {item.label}
              </NavLink>
            ))}
          </div>
        )
      })}
    </nav>
  )
}
