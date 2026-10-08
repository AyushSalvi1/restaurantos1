import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Info, RefreshCw, Search as SearchIcon, SearchX } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { dashboardApi } from '@/api/endpoints'
import { PageHeader, SectionCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/ui/empty-state'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { LoadingPanel } from '@/components/ui/skeleton'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import { formatDate, formatPercent, titleCase } from '@/lib/format'
import type { SearchResponse, SearchResultItem } from '@/types/api'

const ALL_TYPES = '__all__'

/** Server-supplied links only navigate when they already point inside the app shell. */
function appRoute(path: string | null | undefined): string | null {
  return path && path.startsWith('/app') ? path : null
}

function ResultItem({ item }: { item: SearchResultItem }) {
  const target = appRoute(item.link)
  return (
    <li className="rounded-lg border bg-background/40 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="outline">{titleCase(item.type)}</Badge>
        {target ? (
          <Link to={target} className="truncate text-sm font-medium underline-offset-4 hover:underline">
            {item.title}
          </Link>
        ) : (
          <span className="truncate text-sm font-medium">{item.title}</span>
        )}
        {item.status ? <StatusBadge value={item.status} /> : null}
      </div>
      {item.snippet ? <p className="mt-1 text-sm text-muted-foreground">{item.snippet}</p> : null}
      <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span className="tabular-nums">Match {formatPercent(item.score * 100)}</span>
        {item.date ? <span>{formatDate(item.date)}</span> : null}
      </p>
    </li>
  )
}

function Results({ data }: { data: SearchResponse }) {
  const groups = data.groups ?? []
  if (groups.length === 0) {
    return (
      <EmptyState
        icon={<SearchX />}
        title={`No results for “${data.query}”`}
        description="Nothing matched across tasks, goals, habits, learning goals, resources, journal titles and tags, your knowledge base or saved items."
        action={
          <Button asChild size="sm" variant="outline">
            <Link to="/app/tasks">Browse tasks instead</Link>
          </Button>
        }
      />
    )
  }
  return (
    <div className="space-y-4">
      {groups.map((group) => (
        <SectionCard
          key={group.type}
          title={group.label}
          description={`${group.total} match${group.total === 1 ? '' : 'es'}`}
        >
          <ul className="space-y-2">
            {group.items.map((item) => (
              <ResultItem key={`${group.type}-${item.id}`} item={item} />
            ))}
          </ul>
        </SectionCard>
      ))}
    </div>
  )
}

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()
  const activeType = searchParams.get('type') ?? ''
  const [term, setTerm] = useState(query)

  useEffect(() => {
    setTerm(query)
  }, [query])

  const results = useQuery({
    queryKey: ['search', query, activeType],
    queryFn: () => dashboardApi.search(query, activeType || undefined),
    enabled: query.length > 0,
    placeholderData: keepPreviousData,
  })

  const writeParams = (next: URLSearchParams) => setSearchParams(next, { replace: true })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const next = term.trim()
    if (!next) return
    const params = new URLSearchParams(searchParams)
    params.set('q', next)
    writeParams(params)
  }

  const changeType = (value: string) => {
    const params = new URLSearchParams(searchParams)
    if (value && value !== ALL_TYPES) params.set('type', value)
    else params.delete('type')
    writeParams(params)
  }

  const clear = () => {
    setTerm('')
    writeParams(new URLSearchParams())
  }

  const data = results.data
  const availableTypes = data?.availableTypes ?? []
  const hasQuery = query.length > 0

  return (
    <div className="space-y-6">
      <PageHeader
        title="Search"
        description="One search across tasks, goals, habits, learning, journal titles, saved items and your knowledge base."
      />

      <form onSubmit={submit} role="search" className="space-y-2">
        <Label htmlFor="search-query">Search everything</Label>
        <div className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1">
            <SearchIcon
              className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden
            />
            <Input
              id="search-query"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              placeholder="What are you looking for?"
              className="pl-8"
              autoComplete="off"
            />
          </div>
          <Button type="submit" disabled={term.trim().length === 0}>
            Search
          </Button>
          {hasQuery ? (
            <Button type="button" variant="outline" onClick={clear}>
              Clear
            </Button>
          ) : null}
        </div>
      </form>

      <p className="flex items-start gap-2 rounded-lg border border-border bg-secondary/40 px-3 py-2 text-xs text-muted-foreground">
        <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        <span>
          Journal entries are matched on their title and tags only. The body of an entry is never searched, so
          this search cannot surface a private entry you did not name. Knowledge-base results come from the
          meaning of your uploaded documents rather than exact wording.
        </span>
      </p>

      {!hasQuery ? (
        <EmptyState
          icon={<SearchIcon />}
          title="Start typing to search"
          description="Enter at least one character. Results are grouped by where they came from and ranked by where the match landed."
          action={
            <Button asChild size="sm" variant="outline">
              <Link to="/app/tasks">Browse tasks</Link>
            </Button>
          }
        />
      ) : null}

      {hasQuery && results.isPending ? <LoadingPanel label={`Searching for ${query}`} /> : null}

      {hasQuery && results.isError ? (
        <EmptyState
          icon={<AlertTriangle />}
          title="The search failed"
          description={toNormalisedError(results.error).message}
          action={
            <Button onClick={() => void results.refetch()}>
              <RefreshCw />
              Try again
            </Button>
          }
        />
      ) : null}

      {hasQuery && data ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
            <span>
              <span className="font-medium tabular-nums text-foreground">{data.totalResults}</span> result
              {data.totalResults === 1 ? '' : 's'} for <span className="text-foreground">“{data.query}”</span>
            </span>
            <span className="tabular-nums">in {data.tookMillis} ms</span>
            {results.isPlaceholderData ? <span className="text-xs">Updating…</span> : null}
          </div>

          {availableTypes.length > 0 ? (
            <div className="flex flex-wrap items-center gap-2">
              <Label id="search-type-label" htmlFor="search-type">
                Filter by type
              </Label>
              <Select value={activeType || ALL_TYPES} onValueChange={changeType}>
                <SelectTrigger id="search-type" className="w-48" aria-labelledby="search-type-label">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_TYPES}>All types</SelectItem>
                  {availableTypes.map((type) => (
                    <SelectItem key={type} value={type}>
                      {titleCase(type)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}

          <Results data={data} />
        </div>
      ) : null}

      <p className="border-t pt-4 text-xs text-muted-foreground">
        Matching happens on the server over your own records; no remote model was consulted.
      </p>
    </div>
  )
}
