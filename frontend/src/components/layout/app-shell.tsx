import { X } from 'lucide-react'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'

import { AppSidebar } from '@/components/layout/sidebar'
import { AppTopbar } from '@/components/layout/topbar'
import { Button } from '@/components/ui/button'

export function AppShell() {
  const [navOpen, setNavOpen] = useState(false)

  return (
    <div className="flex min-h-screen bg-background">
      <aside className="hidden w-60 shrink-0 border-r lg:block">
        <div className="sticky top-0 h-screen">
          <AppSidebar />
        </div>
      </aside>

      {navOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 bg-black/70"
            onClick={() => setNavOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 w-64 border-r bg-card">
            <Button
              variant="ghost"
              size="icon-sm"
              className="absolute right-2 top-2 z-10"
              onClick={() => setNavOpen(false)}
              aria-label="Close navigation"
            >
              <X />
            </Button>
            <AppSidebar onNavigate={() => setNavOpen(false)} />
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <AppTopbar onOpenNav={() => setNavOpen(true)} />
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
