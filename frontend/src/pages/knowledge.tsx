import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  BookOpen,
  FileText,
  NotebookPen,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  Upload,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'

import { knowledgeApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
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
import { Progress } from '@/components/ui/progress'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton, Spinner } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import { formatBytes, formatDateTime, formatNumber, formatPercent, titleCase } from '@/lib/format'
import type {
  AskRequest,
  AskResponse,
  DocumentResponse,
  SavedItemRequest,
  SavedItemResponse,
  SavedItemType,
  SearchHit,
} from '@/types/api'

const ACCEPTED_EXTENSIONS: readonly string[] = ['txt', 'md', 'markdown', 'pdf', 'docx']
const ACCEPT_ATTRIBUTE = '.txt,.md,.markdown,.pdf,.docx'
const TOP_K_OPTIONS = Array.from({ length: 20 }, (_, index) => index + 1)
const SAVED_ITEM_TYPES: readonly SavedItemType[] = ['NOTE', 'URL', 'BOOKMARK']
const DOCUMENT_PAGE_SIZE = 10
const ITEM_PAGE_SIZE = 10
const ALL = '__all__'
const CITATION_MARKER = /\[(\d{1,2})\]/

function extensionOf(file: File): string {
  const parts = file.name.split('.')
  return parts.length > 1 ? (parts[parts.length - 1] ?? '').toLowerCase() : ''
}

function titleFromFileName(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.slice(0, dot) : name
}

/**
 * Provider stamp shared by search and Q&A. The backend may answer from a deterministic offline
 * heuristic, so the provider and model are always printed verbatim rather than assumed remote.
 */
function GroundingStamp({
  provider,
  model,
  grounded,
}: {
  provider?: string | null
  model?: string | null
  grounded: boolean
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      <Badge variant={grounded ? 'success' : 'warning'}>
        {grounded ? 'Grounded in your documents' : 'Not grounded in your documents'}
      </Badge>
      <span>Provider: {provider || 'none'}</span>
      <span aria-hidden>·</span>
      <span>Model: {model || 'none'}</span>
    </div>
  )
}

function HitCard({ hit, index }: { hit: SearchHit; index: number }) {
  return (
    <li className="space-y-2 rounded-lg border bg-card p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 space-y-0.5">
          <p className="truncate text-sm font-medium">
            {index + 1}. {hit.documentTitle}
          </p>
          <p className="truncate text-xs text-muted-foreground">
            {hit.filename} · chunk {hit.chunkIndex}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{formatPercent(hit.score * 100, 1)} relevance</Badge>
          <Link
            to={`/app/knowledge?doc=${encodeURIComponent(hit.documentId)}`}
            className="text-xs text-primary hover:underline"
          >
            Open document
          </Link>
        </div>
      </div>
      <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-md bg-secondary/50 p-3 font-mono text-xs leading-relaxed text-secondary-foreground">
        {hit.snippet}
      </pre>
    </li>
  )
}

/** Renders the answer, lifting `[n]` markers into numbered chips that map onto the citation list. */
function AnswerBody({ text }: { text: string }) {
  const segments = text.split(/(\[\d{1,2}\])/g)
  return (
    <p className="whitespace-pre-wrap text-sm leading-relaxed">
      {segments.map((segment, index) => {
        const marker = CITATION_MARKER.exec(segment)
        if (!marker || segment !== marker[0]) return <span key={index}>{segment}</span>
        return (
          <span
            key={index}
            className="mx-0.5 inline-flex min-w-5 items-center justify-center rounded bg-primary/20 px-1 align-middle text-xs font-semibold text-primary"
          >
            {marker[1]}
          </span>
        )
      })}
    </p>
  )
}

function GroundedAnswer({ answer }: { answer: AskResponse }) {
  const citations = answer.citations ?? []
  return (
    <div className="space-y-4">
      {answer.grounded ? null : (
        <p className="rounded-lg border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning">
          No passage in your uploaded documents matched this question, so the answer below was not
          taken from them. Upload a document that covers the topic, or rephrase the question.
        </p>
      )}

      <div className="space-y-2">
        <p className="text-sm font-semibold">Answer</p>
        <div className="rounded-lg border bg-card p-4">
          <AnswerBody text={answer.answer ?? ''} />
        </div>
        <GroundingStamp provider={answer.provider} model={answer.model} grounded={answer.grounded} />
      </div>

      <div className="space-y-2">
        <p className="text-sm font-semibold">
          Citations{answer.grounded ? '' : ' (none — nothing was retrieved)'}
        </p>
        {citations.length === 0 ? (
          <p className="rounded-lg border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
            {answer.grounded
              ? 'The answer carried no citations.'
              : 'This answer is not backed by any passage from your documents.'}
          </p>
        ) : (
          <ul className="space-y-2">
            {citations.map((hit, index) => (
              <HitCard key={hit.chunkId} hit={hit} index={index} />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

interface ItemDraft {
  title: string
  url: string
  content: string
  tags: string
  itemType: SavedItemType
}

const EMPTY_ITEM_DRAFT: ItemDraft = {
  title: '',
  url: '',
  content: '',
  tags: '',
  itemType: 'NOTE',
}

function itemToDraft(item: SavedItemResponse): ItemDraft {
  return {
    title: item.title ?? '',
    url: item.url ?? '',
    content: item.content ?? '',
    tags: (item.tags ?? []).join(', '),
    itemType: (SAVED_ITEM_TYPES.find((option) => option === item.itemType) ?? 'NOTE') as SavedItemType,
  }
}

function itemToRequest(draft: ItemDraft): SavedItemRequest {
  const tags = draft.tags
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
  return {
    title: draft.title.trim(),
    url: draft.url.trim() || undefined,
    content: draft.content.trim() || undefined,
    tags,
    itemType: draft.itemType,
  }
}

export default function KnowledgePage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()

  const patchParams = useCallback(
    (patch: Record<string, string | null>) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(patch)) {
            if (value === null || value === '') next.delete(key)
            else next.set(key, value)
          }
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const qParam = searchParams.get('q') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0)
  const itemQ = searchParams.get('iq') ?? ''
  const itemTypeParam = searchParams.get('itemType') ?? ''
  const itemPage = Math.max(0, Number(searchParams.get('ip') ?? '0') || 0)
  const detailId = searchParams.get('doc')

  const [tab, setTab] = useState('documents')
  const [documentSearch, setDocumentSearch] = useState(qParam)
  const [uploadTitle, setUploadTitle] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadError, setUploadError] = useState<string>()
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const [reprocessing, setReprocessing] = useState<DocumentResponse | null>(null)
  const [deletingDocument, setDeletingDocument] = useState<DocumentResponse | null>(null)

  const [searchQuery, setSearchQuery] = useState('')
  const [searchTopK, setSearchTopK] = useState('5')
  const [submittedSearch, setSubmittedSearch] = useState('')

  const [question, setQuestion] = useState('')
  const [askTopK, setAskTopK] = useState('5')

  const [itemSearch, setItemSearch] = useState(itemQ)
  const [itemEditorOpen, setItemEditorOpen] = useState(false)
  const [editingItem, setEditingItem] = useState<SavedItemResponse | null>(null)
  const [itemDraft, setItemDraft] = useState<ItemDraft>(EMPTY_ITEM_DRAFT)
  const [itemError, setItemError] = useState<string>()
  const [itemFieldErrors, setItemFieldErrors] = useState<Record<string, string>>({})
  const [deletingItem, setDeletingItem] = useState<SavedItemResponse | null>(null)

  useEffect(() => {
    setDocumentSearch(qParam)
  }, [qParam])

  useEffect(() => {
    setItemSearch(itemQ)
  }, [itemQ])

  useEffect(() => {
    if (documentSearch === qParam) return undefined
    const timer = window.setTimeout(() => {
      patchParams({ q: documentSearch.trim() || null, page: '0' })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [documentSearch, qParam, patchParams])

  useEffect(() => {
    if (itemSearch === itemQ) return undefined
    const timer = window.setTimeout(() => {
      patchParams({ iq: itemSearch.trim() || null, ip: '0' })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [itemSearch, itemQ, patchParams])

  const documentQuery = useMemo(
    () => ({ q: qParam || undefined, page, size: DOCUMENT_PAGE_SIZE }),
    [qParam, page],
  )

  const documentsQuery = useQuery({
    queryKey: ['knowledge', 'documents', documentQuery],
    queryFn: () => knowledgeApi.documents(documentQuery),
  })

  const documentDetailQuery = useQuery({
    queryKey: ['knowledge', 'document', detailId],
    queryFn: () => knowledgeApi.document(detailId ?? ''),
    enabled: Boolean(detailId),
  })

  const searchResultQuery = useQuery({
    queryKey: ['knowledge', 'search', submittedSearch, Number(searchTopK)],
    queryFn: () => knowledgeApi.search(submittedSearch, Number(searchTopK)),
    enabled: submittedSearch.trim().length > 0,
  })

  const itemsQuery = useQuery({
    queryKey: ['knowledge', 'items', itemTypeParam, itemQ, itemPage],
    queryFn: () =>
      knowledgeApi.items({
        type: itemTypeParam || undefined,
        q: itemQ || undefined,
        page: itemPage,
        size: ITEM_PAGE_SIZE,
      }),
  })

  const invalidateDocuments = useCallback(
    () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['knowledge', 'documents'] }),
        queryClient.invalidateQueries({ queryKey: ['knowledge', 'search'] }),
      ]),
    [queryClient],
  )

  const uploadMutation = useMutation({
    mutationFn: ({ file, title }: { file: File; title?: string }) =>
      knowledgeApi.upload(file, title, (percent) => setUploadProgress(percent)),
    onSuccess: async (document) => {
      setUploadFile(null)
      setUploadTitle('')
      setUploadError(undefined)
      setUploadProgress(null)
      await invalidateDocuments()
      toast.success('Document uploaded', {
        description: `${document.title} is ${titleCase(document.status).toLowerCase()}.`,
      })
    },
    onError: (error) => {
      setUploadProgress(null)
      setUploadError(toNormalisedError(error).message)
    },
  })

  const reprocessMutation = useMutation({
    mutationFn: (id: string) => knowledgeApi.reprocess(id),
    onSuccess: async (document) => {
      setReprocessing(null)
      await invalidateDocuments()
      if (detailId) {
        await queryClient.invalidateQueries({ queryKey: ['knowledge', 'document', document.id] })
      }
      toast.success('Reprocessing started', { description: document.title })
    },
    onError: (error) => {
      setReprocessing(null)
      toast.error(toNormalisedError(error).message)
    },
  })

  const deleteDocumentMutation = useMutation({
    mutationFn: (id: string) => knowledgeApi.remove(id),
    onSuccess: async () => {
      const title = deletingDocument?.title ?? 'Document'
      setDeletingDocument(null)
      patchParams({ doc: null })
      await invalidateDocuments()
      toast.success('Document deleted', { description: title })
    },
    onError: (error) => {
      setDeletingDocument(null)
      toast.error(toNormalisedError(error).message)
    },
  })

  const askMutation = useMutation({
    mutationFn: (body: AskRequest) => knowledgeApi.ask(body),
    onSuccess: (answer) => {
      if (!answer.grounded) {
        toast.warning('Answer not grounded in your documents', {
          description: 'Nothing relevant was found in the uploaded files.',
        })
      }
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const createItemMutation = useMutation({
    mutationFn: (body: SavedItemRequest) => knowledgeApi.createItem(body),
    onSuccess: async (item) => {
      closeItemEditor()
      await queryClient.invalidateQueries({ queryKey: ['knowledge', 'items'] })
      toast.success('Saved item created', { description: item.title })
    },
    onError: applyItemError,
  })

  const updateItemMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: SavedItemRequest }) =>
      knowledgeApi.updateItem(id, body),
    onSuccess: async (item) => {
      closeItemEditor()
      await queryClient.invalidateQueries({ queryKey: ['knowledge', 'items'] })
      toast.success('Saved item updated', { description: item.title })
    },
    onError: applyItemError,
  })

  const deleteItemMutation = useMutation({
    mutationFn: (id: string) => knowledgeApi.removeItem(id),
    onSuccess: async () => {
      const title = deletingItem?.title ?? 'Saved item'
      setDeletingItem(null)
      await queryClient.invalidateQueries({ queryKey: ['knowledge', 'items'] })
      toast.success('Saved item deleted', { description: title })
    },
    onError: (error) => {
      setDeletingItem(null)
      toast.error(toNormalisedError(error).message)
    },
  })

  function applyItemError(error: unknown) {
    const normalised = toNormalisedError(error)
    setItemError(normalised.message)
    setItemFieldErrors(normalised.fieldErrors)
  }

  function closeItemEditor() {
    setItemEditorOpen(false)
    setEditingItem(null)
    setItemDraft(EMPTY_ITEM_DRAFT)
    setItemError(undefined)
    setItemFieldErrors({})
  }

  function openCreateItem() {
    setEditingItem(null)
    setItemDraft(EMPTY_ITEM_DRAFT)
    setItemError(undefined)
    setItemFieldErrors({})
    setItemEditorOpen(true)
  }

  function openEditItem(item: SavedItemResponse) {
    setEditingItem(item)
    setItemDraft(itemToDraft(item))
    setItemError(undefined)
    setItemFieldErrors({})
    setItemEditorOpen(true)
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files?.[0] ?? null
    setUploadError(undefined)
    if (!picked) {
      setUploadFile(null)
      return
    }
    const extension = extensionOf(picked)
    if (!ACCEPTED_EXTENSIONS.includes(extension)) {
      setUploadFile(null)
      event.target.value = ''
      setUploadError(
        `"${picked.name}" is not a supported file. Upload one of: ${ACCEPT_ATTRIBUTE}.`,
      )
      return
    }
    setUploadFile(picked)
    if (!uploadTitle.trim()) setUploadTitle(titleFromFileName(picked.name))
  }

  function submitUpload(event: FormEvent) {
    event.preventDefault()
    if (!uploadFile) {
      setUploadError('Choose a file to upload first.')
      return
    }
    uploadMutation.mutate({
      file: uploadFile,
      title: uploadTitle.trim() || titleFromFileName(uploadFile.name),
    })
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    setSubmittedSearch(searchQuery.trim())
  }

  function submitAsk(event: FormEvent) {
    event.preventDefault()
    const trimmed = question.trim()
    if (!trimmed) return
    askMutation.mutate({ question: trimmed, topK: Number(askTopK) })
  }

  function submitItem(event: FormEvent) {
    event.preventDefault()
    const body = itemToRequest(itemDraft)
    if (editingItem) updateItemMutation.mutate({ id: editingItem.id, body })
    else createItemMutation.mutate(body)
  }

  const library = documentsQuery.data
  const documents = library?.documents ?? []
  const totalPages = library ? Math.max(1, Math.ceil(library.totalDocuments / DOCUMENT_PAGE_SIZE)) : 1
  const itemPageData = itemsQuery.data
  const items = itemPageData?.content ?? []
  const askAnswer = askMutation.data
  const searchResult = searchResultQuery.data

  return (
    <div className="space-y-6">
      <PageHeader
        title="Knowledge"
        description="Your own documents, semantic search over them, and answers grounded in what you uploaded."
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Documents" value={formatNumber(library?.totalDocuments ?? 0)} hint="Uploaded files" />
        <StatCard label="Chunks" value={formatNumber(library?.totalChunks ?? 0)} hint="Searchable passages" />
        <StatCard
          label="Stored size"
          value={formatBytes(library?.totalBytes ?? 0)}
          hint="Original file bytes"
        />
      </div>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList>
          <TabsTrigger value="documents">
            <FileText className="size-4" aria-hidden /> Documents
          </TabsTrigger>
          <TabsTrigger value="search">
            <Search className="size-4" aria-hidden /> Search
          </TabsTrigger>
          <TabsTrigger value="ask">
            <BookOpen className="size-4" aria-hidden /> Ask
          </TabsTrigger>
          <TabsTrigger value="items">
            <NotebookPen className="size-4" aria-hidden /> Saved items
          </TabsTrigger>
        </TabsList>

        {/* ------------------------------------------------------------ upload */}
        <TabsContent value="documents" className="space-y-6">
          <div id="knowledge-upload" className="scroll-mt-4">
            <SectionCard
              title="Upload a document"
              description={`Accepted: ${ACCEPT_ATTRIBUTE}. Files are chunked and indexed for semantic search.`}
            >
            <form onSubmit={submitUpload} className="space-y-4" noValidate>
              {uploadError ? (
                <p
                  role="alert"
                  className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                >
                  {uploadError}
                </p>
              ) : null}

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="knowledge-file">File</Label>
                  <Input
                    id="knowledge-file"
                    type="file"
                    accept={ACCEPT_ATTRIBUTE}
                    onChange={handleFileChange}
                    aria-invalid={Boolean(uploadError)}
                  />
                  {uploadFile ? (
                    <p className="text-xs text-muted-foreground">
                      {uploadFile.name} · {formatBytes(uploadFile.size)}
                    </p>
                  ) : null}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="knowledge-title">Title (optional)</Label>
                  <Input
                    id="knowledge-title"
                    maxLength={200}
                    placeholder="Defaults to the file name"
                    value={uploadTitle}
                    onChange={(event) => setUploadTitle(event.target.value)}
                  />
                </div>
              </div>

              {uploadProgress !== null ? (
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>Uploading {uploadFile?.name}</span>
                    <span className="tabular-nums">{uploadProgress}%</span>
                  </div>
                  <Progress value={uploadProgress} aria-label="Upload progress" />
                </div>
              ) : null}

              <Button type="submit" loading={uploadMutation.isPending} disabled={!uploadFile}>
                <Upload /> Upload
              </Button>
            </form>
            </SectionCard>
          </div>

          <SectionCard
            title="Document library"
            description="Every document you have uploaded, with its indexing status."
          >
            <div className="mb-4 flex flex-wrap items-end gap-3">
              <div className="min-w-56 flex-1 space-y-1.5">
                <Label htmlFor="knowledge-search">Search documents</Label>
                <div className="relative">
                  <Search
                    className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                    aria-hidden
                  />
                  <Input
                    id="knowledge-search"
                    className="pl-8"
                    placeholder="Title or file name"
                    value={documentSearch}
                    onChange={(event) => setDocumentSearch(event.target.value)}
                  />
                </div>
              </div>
              {qParam ? (
                <Button variant="ghost" onClick={() => patchParams({ q: null, page: '0' })}>
                  Clear search
                </Button>
              ) : null}
            </div>

            {documentsQuery.isPending ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, index) => (
                  <Skeleton key={index} className="h-28 w-full" />
                ))}
              </div>
            ) : documentsQuery.error ? (
              <div
                role="alert"
                className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
              >
                <p>{toNormalisedError(documentsQuery.error).message}</p>
                <Button variant="outline" size="sm" onClick={() => void documentsQuery.refetch()}>
                  <RefreshCw /> Try again
                </Button>
              </div>
            ) : documents.length === 0 ? (
              <EmptyState
                icon={<FileText />}
                title={qParam ? 'No documents match this search' : 'No documents yet'}
                description={
                  qParam
                    ? 'Try a different word, or clear the search to see every document.'
                    : 'Upload a .txt, .md, .pdf or .docx file above. Once it is indexed you can search it and ask questions grounded in it.'
                }
                action={
                  qParam ? (
                    <Button variant="outline" onClick={() => patchParams({ q: null, page: '0' })}>
                      Clear search
                    </Button>
                  ) : (
<Button
                    onClick={() =>
                      document
                        .getElementById('knowledge-upload')
                        ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                    }
                  >
                    <Upload /> Upload your first document
                  </Button>
                  )
                }
              />
            ) : (
              <>
                <ul className="space-y-3">
                  {documents.map((document) => (
                    <li key={document.id}>
                      <Card>
                        <div className="space-y-3 p-4">
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="min-w-0 space-y-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <h3 className="truncate text-sm font-semibold">{document.title}</h3>
                                <StatusBadge
                                  value={document.status}
                                  label={titleCase(document.status)}
                                />
                                {document.extension ? (
                                  <Badge variant="outline">.{document.extension}</Badge>
                                ) : null}
                              </div>
                              <p className="truncate text-xs text-muted-foreground">
                                {document.filename}
                              </p>
                            </div>

                            <div className="flex flex-wrap items-center gap-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => patchParams({ doc: document.id })}
                              >
                                Details
                              </Button>
                              <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => setReprocessing(document)}
                              >
                                <RotateCcw /> Reprocess
                              </Button>
                              <Button
                                variant="destructive"
                                size="sm"
                                onClick={() => setDeletingDocument(document)}
                              >
                                <Trash2 /> Delete
                              </Button>
                            </div>
                          </div>

                          {document.failureReason ? (
                            <p className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                              {document.failureReason}
                            </p>
                          ) : null}

                          <dl className="grid grid-cols-2 gap-3 text-xs sm:grid-cols-4">
                            <div>
                              <dt className="text-muted-foreground">Size</dt>
                              <dd className="font-medium tabular-nums">
                                {formatBytes(document.sizeBytes)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-muted-foreground">Words</dt>
                              <dd className="font-medium tabular-nums">
                                {formatNumber(document.wordCount)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-muted-foreground">Chunks</dt>
                              <dd className="font-medium tabular-nums">
                                {formatNumber(document.chunkCount)}
                              </dd>
                            </div>
                            <div>
                              <dt className="text-muted-foreground">Uploaded</dt>
                              <dd className="font-medium">{formatDateTime(document.createdAt)}</dd>
                            </div>
                          </dl>
                        </div>
                      </Card>
                    </li>
                  ))}
                </ul>

                <nav
                  aria-label="Document pagination"
                  className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
                >
                  <p className="text-sm text-muted-foreground">
                    Page {page + 1} of {totalPages} · {formatNumber(library?.totalDocuments ?? 0)}{' '}
                    documents
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={page === 0}
                      onClick={() => patchParams({ page: String(Math.max(0, page - 1)) })}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={page + 1 >= totalPages}
                      onClick={() => patchParams({ page: String(page + 1) })}
                    >
                      Next
                    </Button>
                  </div>
                </nav>
              </>
            )}
          </SectionCard>
        </TabsContent>

        {/* ---------------------------------------------------- semantic search */}
        <TabsContent value="search" className="space-y-4">
          <SectionCard
            title="Semantic search"
            description="Ranks passages from your documents by meaning, not just keywords."
          >
            <form onSubmit={submitSearch} className="flex flex-wrap items-end gap-3" noValidate>
              <div className="min-w-56 flex-1 space-y-1.5">
                <Label htmlFor="knowledge-query">Query</Label>
                <Input
                  id="knowledge-query"
                  maxLength={500}
                  placeholder="What are the retry limits in the spec?"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                />
              </div>
              <div className="w-32 space-y-1.5">
                <Label htmlFor="knowledge-topk">Passages</Label>
                <Select value={searchTopK} onValueChange={setSearchTopK}>
                  <SelectTrigger id="knowledge-topk">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TOP_K_OPTIONS.map((option) => (
                      <SelectItem key={option} value={String(option)}>
                        {option}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button type="submit" loading={searchResultQuery.isFetching}>
                <Search /> Search
              </Button>
            </form>
          </SectionCard>

          {!submittedSearch ? (
            <EmptyState
              icon={<Search />}
              title="Run a search"
              description="Search returns the passages closest to your query, each with a relevance score and the document it came from."
            />
          ) : searchResultQuery.isPending ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-32 w-full" />
              ))}
            </div>
          ) : searchResultQuery.error ? (
            <div
              role="alert"
              className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
            >
              <p>{toNormalisedError(searchResultQuery.error).message}</p>
              <Button variant="outline" size="sm" onClick={() => void searchResultQuery.refetch()}>
                <RefreshCw /> Try again
              </Button>
            </div>
          ) : (searchResult?.hits.length ?? 0) === 0 ? (
            <EmptyState
              icon={<Search />}
              title="No matching passages"
              description={
                searchResult?.message ||
                'Nothing in your uploaded documents resembles this query.'
              }
              action={
                <Button variant="outline" onClick={() => setTab('documents')}>
                  <Upload /> Upload another document
                </Button>
              }
            />
          ) : (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-muted-foreground">
                  {formatNumber(searchResult?.totalHits ?? 0)} passage
                  {searchResult?.totalHits === 1 ? '' : 's'} matched
                </p>
                <GroundingStamp grounded={searchResult?.grounded ?? false} />
              </div>
              {searchResult?.message ? (
                <p className="rounded-md border border-warning/40 bg-warning/10 px-3 py-2 text-sm text-warning">
                  {searchResult.message}
                </p>
              ) : null}
              <ul className="space-y-3">
                {(searchResult?.hits ?? []).map((hit, index) => (
                  <HitCard key={hit.chunkId} hit={hit} index={index} />
                ))}
              </ul>
            </div>
          )}
        </TabsContent>

        {/* ------------------------------------------------------- grounded Q&A */}
        <TabsContent value="ask" className="space-y-4">
          <SectionCard
            title="Ask your documents"
            description="Answers come only from passages retrieved from your uploads. When nothing matches, LIFEOS says so instead of guessing."
          >
            <form onSubmit={submitAsk} className="space-y-4" noValidate>
              <div className="space-y-1.5">
                <Label htmlFor="knowledge-question">Question</Label>
                <Textarea
                  id="knowledge-question"
                  rows={3}
                  maxLength={2000}
                  placeholder="What does the onboarding guide say about working hours?"
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                />
              </div>
              <div className="flex flex-wrap items-end gap-3">
                <div className="w-32 space-y-1.5">
                  <Label htmlFor="knowledge-ask-topk">Passages to read</Label>
                  <Select value={askTopK} onValueChange={setAskTopK}>
                    <SelectTrigger id="knowledge-ask-topk">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {TOP_K_OPTIONS.map((option) => (
                        <SelectItem key={option} value={String(option)}>
                          {option}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <Button type="submit" loading={askMutation.isPending} disabled={!question.trim()}>
                  <BookOpen /> Ask
                </Button>
              </div>
            </form>
          </SectionCard>

          {askMutation.isPending ? (
            <div className="flex items-center gap-2 rounded-lg border p-4 text-sm text-muted-foreground">
              <Spinner label="Answering" /> Reading your documents…
            </div>
          ) : askMutation.error ? (
            <div
              role="alert"
              className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
            >
              <p>{toNormalisedError(askMutation.error).message}</p>
              <Button variant="outline" size="sm" onClick={() => askMutation.reset()}>
                <RefreshCw /> Dismiss and retry
              </Button>
            </div>
          ) : askAnswer ? (
            <GroundedAnswer answer={askAnswer} />
          ) : (
            <EmptyState
              icon={<BookOpen />}
              title="No question asked yet"
              description="Ask something your documents cover. Each answer lists the exact passages it used."
            />
          )}
        </TabsContent>

        {/* ------------------------------------------------------- saved items */}
        <TabsContent value="items" className="space-y-4">
          <SectionCard
            title="Saved items"
            description="Notes, links and bookmarks you kept alongside your documents."
            action={
              <Button size="sm" onClick={openCreateItem}>
                <Plus /> New item
              </Button>
            }
          >
            <div className="flex flex-wrap items-end gap-3">
              <div className="min-w-56 flex-1 space-y-1.5">
                <Label htmlFor="item-search">Search saved items</Label>
                <div className="relative">
                  <Search
                    className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                    aria-hidden
                  />
                  <Input
                    id="item-search"
                    className="pl-8"
                    placeholder="Title, content or tag"
                    value={itemSearch}
                    onChange={(event) => setItemSearch(event.target.value)}
                  />
                </div>
              </div>
              <div className="w-44 space-y-1.5">
                <Label htmlFor="item-type">Type</Label>
                <Select
                  value={itemTypeParam || ALL}
                  onValueChange={(value) =>
                    patchParams({ itemType: value === ALL ? null : value, ip: '0' })
                  }
                >
                  <SelectTrigger id="item-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={ALL}>Any type</SelectItem>
                    {SAVED_ITEM_TYPES.map((option) => (
                      <SelectItem key={option} value={option}>
                        {titleCase(option)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {itemQ || itemTypeParam ? (
                <Button
                  variant="ghost"
                  onClick={() => patchParams({ iq: null, itemType: null, ip: '0' })}
                >
                  Clear filters
                </Button>
              ) : null}
            </div>
          </SectionCard>

          {itemsQuery.isPending ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-24 w-full" />
              ))}
            </div>
          ) : itemsQuery.error ? (
            <div
              role="alert"
              className="flex flex-col items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4 text-sm text-destructive"
            >
              <p>{toNormalisedError(itemsQuery.error).message}</p>
              <Button variant="outline" size="sm" onClick={() => void itemsQuery.refetch()}>
                <RefreshCw /> Try again
              </Button>
            </div>
          ) : items.length === 0 ? (
            <EmptyState
              icon={<NotebookPen />}
              title={itemQ || itemTypeParam ? 'No saved items match' : 'Nothing saved yet'}
              description={
                itemQ || itemTypeParam
                  ? 'Clear the filters to see everything you have saved.'
                  : 'Keep a note, a link or a bookmark here so it sits next to the documents it relates to.'
              }
              action={
                itemQ || itemTypeParam ? (
                  <Button
                    variant="outline"
                    onClick={() => patchParams({ iq: null, itemType: null, ip: '0' })}
                  >
                    Clear filters
                  </Button>
                ) : (
                  <Button onClick={openCreateItem}>
                    <Plus /> Save your first item
                  </Button>
                )
              }
            />
          ) : (
            <>
              <ul className="space-y-3">
                {items.map((item) => (
                  <li key={item.id}>
                    <Card>
                      <div className="space-y-2 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div className="min-w-0 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="truncate text-sm font-semibold">{item.title}</h3>
                              <StatusBadge
                                value={item.itemType}
                                label={titleCase(item.itemType)}
                              />
                            </div>
                            <p className="text-xs text-muted-foreground">
                              Saved {formatDateTime(item.createdAt)}
                            </p>
                          </div>
                          <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={() => openEditItem(item)}>
                              <Pencil /> Edit
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => setDeletingItem(item)}
                            >
                              <Trash2 /> Delete
                            </Button>
                          </div>
                        </div>

                        {item.content ? (
                          <p className="line-clamp-3 whitespace-pre-wrap text-sm text-muted-foreground">
                            {item.content}
                          </p>
                        ) : null}

                        {item.url ? (
                          <a
                            href={item.url}
                            target="_blank"
                            rel="noreferrer noopener"
                            className="inline-block max-w-full truncate text-xs text-primary hover:underline"
                          >
                            {item.url}
                          </a>
                        ) : null}

                        {(item.tags ?? []).length > 0 ? (
                          <div className="flex flex-wrap gap-1.5">
                            {(item.tags ?? []).map((tag) => (
                              <Badge key={tag} variant="secondary">
                                {tag}
                              </Badge>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    </Card>
                  </li>
                ))}
              </ul>

              {itemPageData && itemPageData.totalPages > 1 ? (
                <nav
                  aria-label="Saved item pagination"
                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
                >
                  <p className="text-sm text-muted-foreground">
                    Page {itemPageData.page + 1} of {itemPageData.totalPages} ·{' '}
                    {formatNumber(itemPageData.totalElements)} items
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={itemPageData.first}
                      onClick={() => patchParams({ ip: String(itemPageData.page - 1) })}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={itemPageData.last}
                      onClick={() => patchParams({ ip: String(itemPageData.page + 1) })}
                    >
                      Next
                    </Button>
                  </div>
                </nav>
              ) : null}
            </>
          )}
        </TabsContent>
      </Tabs>

      {/* ------------------------------------------------------ document detail */}
      <Dialog
        open={Boolean(detailId)}
        onOpenChange={(open) => {
          if (!open) patchParams({ doc: null })
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Document detail</DialogTitle>
            <DialogDescription>
              Indexing state and size as recorded by the ingestion pipeline.
            </DialogDescription>
          </DialogHeader>

          {documentDetailQuery.isPending ? (
            <Spinner label="Loading document" />
          ) : documentDetailQuery.error ? (
            <p role="alert" className="text-sm text-destructive">
              {toNormalisedError(documentDetailQuery.error).message}
            </p>
          ) : documentDetailQuery.data ? (
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Title</dt>
                <dd className="text-right font-medium">{documentDetailQuery.data.title}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">File</dt>
                <dd className="text-right font-medium">{documentDetailQuery.data.filename}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Content type</dt>
                <dd className="text-right font-medium">
                  {documentDetailQuery.data.contentType || '—'}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Status</dt>
                <dd className="text-right">
                  <StatusBadge
                    value={documentDetailQuery.data.status}
                    label={titleCase(documentDetailQuery.data.status)}
                  />
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Size</dt>
                <dd className="text-right font-medium tabular-nums">
                  {formatBytes(documentDetailQuery.data.sizeBytes)}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Words</dt>
                <dd className="text-right font-medium tabular-nums">
                  {formatNumber(documentDetailQuery.data.wordCount)}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Chunks</dt>
                <dd className="text-right font-medium tabular-nums">
                  {formatNumber(documentDetailQuery.data.chunkCount)}
                </dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Uploaded</dt>
                <dd className="text-right font-medium">
                  {formatDateTime(documentDetailQuery.data.createdAt)}
                </dd>
              </div>
              {documentDetailQuery.data.failureReason ? (
                <div className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
                  {documentDetailQuery.data.failureReason}
                </div>
              ) : null}
            </dl>
          ) : null}
        </DialogContent>
      </Dialog>

      {/* ------------------------------------------------------ saved item form */}
      <Dialog
        open={itemEditorOpen}
        onOpenChange={(open) => {
          if (!open) closeItemEditor()
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingItem ? 'Edit saved item' : 'New saved item'}</DialogTitle>
            <DialogDescription>
              Keep a note, a link or a bookmark next to the documents it relates to.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={submitItem} className="space-y-4" noValidate>
            {itemError ? (
              <p
                role="alert"
                className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
              >
                {itemError}
              </p>
            ) : null}

            <div className="space-y-1.5">
              <Label htmlFor="item-title">Title</Label>
              <Input
                id="item-title"
                required
                maxLength={255}
                value={itemDraft.title}
                onChange={(event) =>
                  setItemDraft((current) => ({ ...current, title: event.target.value }))
                }
                aria-invalid={Boolean(itemFieldErrors.title)}
              />
              {itemFieldErrors.title ? (
                <p className="text-xs text-destructive">{itemFieldErrors.title}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="item-type-field">Type</Label>
              <Select
                value={itemDraft.itemType}
                onValueChange={(value) =>
                  setItemDraft((current) => ({ ...current, itemType: value as SavedItemType }))
                }
              >
                <SelectTrigger id="item-type-field">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SAVED_ITEM_TYPES.map((option) => (
                    <SelectItem key={option} value={option}>
                      {titleCase(option)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="item-url">Link</Label>
              <Input
                id="item-url"
                type="url"
                maxLength={700}
                placeholder="https://example.com/notes"
                value={itemDraft.url}
                onChange={(event) =>
                  setItemDraft((current) => ({ ...current, url: event.target.value }))
                }
                aria-invalid={Boolean(itemFieldErrors.url)}
              />
              {itemFieldErrors.url ? (
                <p className="text-xs text-destructive">{itemFieldErrors.url}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="item-content">Content</Label>
              <Textarea
                id="item-content"
                rows={5}
                maxLength={100000}
                value={itemDraft.content}
                onChange={(event) =>
                  setItemDraft((current) => ({ ...current, content: event.target.value }))
                }
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="item-tags">Tags</Label>
              <Input
                id="item-tags"
                maxLength={500}
                placeholder="research, spec"
                value={itemDraft.tags}
                onChange={(event) =>
                  setItemDraft((current) => ({ ...current, tags: event.target.value }))
                }
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeItemEditor}>
                Cancel
              </Button>
              <Button
                type="submit"
                loading={createItemMutation.isPending || updateItemMutation.isPending}
                disabled={!itemDraft.title.trim()}
              >
                {editingItem ? 'Save changes' : 'Create item'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={deletingItem !== null} onOpenChange={(open) => !open && setDeletingItem(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete saved item</DialogTitle>
            <DialogDescription>
              {deletingItem
                ? `"${deletingItem.title}" will be removed from your saved items. This cannot be undone.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingItem(null)}>
              Keep item
            </Button>
            <Button
              variant="destructive"
              loading={deleteItemMutation.isPending}
              onClick={() => {
                if (deletingItem) deleteItemMutation.mutate(deletingItem.id)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={reprocessing !== null}
        onOpenChange={(open) => !open && setReprocessing(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reprocess document</DialogTitle>
            <DialogDescription>
              {reprocessing
                ? `"${reprocessing.title}" will be re-read, re-chunked and re-embedded. Existing chunks are replaced, so search results for it will change.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReprocessing(null)}>
              Cancel
            </Button>
            <Button
              loading={reprocessMutation.isPending}
              onClick={() => {
                if (reprocessing) reprocessMutation.mutate(reprocessing.id)
              }}
            >
              Reprocess
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deletingDocument !== null}
        onOpenChange={(open) => !open && setDeletingDocument(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete document</DialogTitle>
            <DialogDescription>
              {deletingDocument
                ? `"${deletingDocument.title}" will be deleted. The stored file and every chunk indexed from it are removed, so it will disappear from search results and grounded answers. This cannot be undone.`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeletingDocument(null)}>
              Keep document
            </Button>
            <Button
              variant="destructive"
              loading={deleteDocumentMutation.isPending}
              onClick={() => {
                if (deletingDocument) deleteDocumentMutation.mutate(deletingDocument.id)
              }}
            >
              Delete permanently
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}