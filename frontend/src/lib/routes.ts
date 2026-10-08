/**
 * The backend returns navigation hints as API-level paths (`/habits`, `/tasks?filter=overdue`) because
 * it has no knowledge of the SPA's route names. This table is the single place that translates them.
 *
 * Anything not listed resolves to `null`, and the UI renders plain text instead of a link. A dead link
 * is worse than no link, so an unmapped path never becomes clickable.
 */
const RESOURCE_ROUTES: Record<string, string> = {
  tasks: '/app/tasks',
  goals: '/app/goals',
  habits: '/app/habits',
  calendar: '/app/calendar',
  focus: '/app/focus',
  learning: '/app/learning',
  finance: '/app/finance',
  journal: '/app/journal',
  knowledge: '/app/knowledge',
  notifications: '/app/notifications',
  settings: '/app/settings',
  admin: '/app/admin',
  graph: '/app/health',
  dashboard: '/app',
  today: '/app/today',
  analytics: '/app/analytics',
  'analytics/balance': '/app/analytics',
  'ai/plan-day': '/app/assistant',
  ai: '/app/assistant',
  assistant: '/app/assistant',
  search: '/app/search',
}

function splitPath(path: string): { route: string; query: URLSearchParams } {
  const [rawPath, rawQuery = ''] = path.split('?')
  return { route: rawPath.replace(/^\/+|\/+$/g, ''), query: new URLSearchParams(rawQuery) }
}

/** Translates a server navigation hint into a client route, or `null` when none matches. */
export function resolveAppPath(path?: string | null): string | null {
  if (!path) return null
  const trimmed = path.trim()
  if (!trimmed) return null

  // A path that is already client-absolute is taken as-is, which keeps the helper usable if the
  // backend ever learns the route names.
  if (trimmed.startsWith('/app/') || trimmed === '/app') return trimmed

  const { route, query } = splitPath(trimmed)
  const target = RESOURCE_ROUTES[route]
  if (!target) return null

  const params = new URLSearchParams(query)
  // The server uses `filter=overdue`; the task list reads a boolean `overdue` parameter.
  if (params.get('filter') === 'overdue') {
    params.delete('filter')
    params.set('overdue', 'true')
  }
  const suffix = params.toString()
  return suffix ? `${target}?${suffix}` : target
}

/** Convenience wrapper for `Link` targets; returns `undefined` so `to` can be omitted entirely. */
export function appPathOrUndefined(path?: string | null): string | undefined {
  return resolveAppPath(path) ?? undefined
}
