import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useQueryClient } from '@tanstack/react-query'
import { createContext, use, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'

import { tokenStore } from '@/api/client'
import { notificationKeys } from '@/hooks/use-notifications'
import type { NotificationResponse } from '@/types/api'

interface NotificationsSocketValue {
  connected: boolean
}

const NotificationsSocketContext = createContext<NotificationsSocketValue>({ connected: false })

/**
 * Resolves the SockJS endpoint from an explicit env override, otherwise the current origin, so the
 * same bundle works behind a reverse proxy and against a dev server on another port.
 */
function resolveBrokerUrl(): string {
  const configured = import.meta.env.VITE_WS_URL?.trim()
  if (configured) return configured
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
  return `${protocol}//${window.location.host}/ws`
}

export function NotificationsSocketProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const token = tokenStore.access
    if (!token) return

    const client = new Client({
      webSocketFactory: () => new SockJS(resolveBrokerUrl()),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => setConnected(true),
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    })

    client.onConnect = () => {
      setConnected(true)
      // The server publishes each user's notifications on a user-scoped queue.
      client.subscribe('/user/queue/notifications', (message: IMessage) => {
        try {
          const payload = JSON.parse(message.body) as NotificationResponse
          void queryClient.invalidateQueries({ queryKey: notificationKeys.unread })
          void queryClient.invalidateQueries({ queryKey: notificationKeys.list({}) })
          toast(payload.title, { description: payload.body })
        } catch {
          // Ignore malformed frames rather than tearing down a healthy subscription.
        }
      })
    }

    client.activate()
    clientRef.current = client

    return () => {
      clientRef.current = null
      void client.deactivate()
      setConnected(false)
    }
  }, [queryClient])

  const value = useMemo(() => ({ connected }), [connected])
  return <NotificationsSocketContext value={value}>{children}</NotificationsSocketContext>
}

export function useNotificationsSocket() {
  return use(NotificationsSocketContext)
}
