import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { notificationApi } from '@/api/endpoints'
import type { NotificationQuery } from '@/types/api'

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (query: NotificationQuery) => ['notifications', 'list', query] as const,
  unread: ['notifications', 'unread'] as const,
}

export function useNotifications(query: NotificationQuery = {}) {
  return useQuery({
    queryKey: notificationKeys.list(query),
    queryFn: () => notificationApi.list(query),
  })
}

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: () => notificationApi.unreadCount(),
    refetchInterval: 60_000,
  })
}

export function useMarkRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificationApi.markRead(id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })
}

export function useMarkAllRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (category?: string) => notificationApi.markAllRead(category),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })
}

export function useDeleteNotification() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificationApi.remove(id),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })
}

export function useClearNotifications() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => notificationApi.clearAll(),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })
}
