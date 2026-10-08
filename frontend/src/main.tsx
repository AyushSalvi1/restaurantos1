import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'sonner'

import { AppRouter } from '@/router'
import '@/index.css'
import { queryClient } from '@/providers/query-provider'
import { NotificationsSocketProvider } from '@/providers/notifications-socket'
import { AuthProvider } from '@/stores/auth'

const container = document.getElementById('root')
if (!container) throw new Error('Root container #root is missing from index.html')

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <NotificationsSocketProvider>
            <AppRouter />
            <Toaster position="bottom-right" richColors closeButton />
          </NotificationsSocketProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
