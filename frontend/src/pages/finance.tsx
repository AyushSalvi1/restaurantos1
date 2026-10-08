import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowDownRight,
  ArrowUpRight,
  Equal,
  PiggyBank,
  Plus,
  Receipt,
  Target,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { toast } from 'sonner'

import { financeApi } from '@/api/endpoints'
import { PageHeader, SectionCard, StatCard } from '@/components/page-parts'
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
import { Input, Textarea } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/lib/badges'
import {
  currentMonthIso,
  formatCurrency,
  formatDate,
  formatNumber,
  formatPercent,
  todayIso,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import { toNormalisedError } from '@/hooks/use-api-error'
import type {
  BudgetPeriod,
  BudgetResponse,
  BudgetRequest,
  SavingsGoalRequest,
  SavingsGoalResponse,
  SpendingPattern,
  TransactionRequest,
  TransactionResponse,
  TransactionType,
} from '@/types/api'

const PERIODS: { value: BudgetPeriod; label: string }[] = [
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'YEARLY', label: 'Yearly' },
]

const TRANSACTION_TYPES: { value: TransactionType; label: string }[] = [
  { value: 'INCOME', label: 'Income' },
  { value: 'EXPENSE', label: 'Expense' },
]

const TREND_WINDOWS = [3, 6, 12] as const

const ALL = 'ALL'
const PAGE_SIZE = 20

const PIE_COLOURS = [
  'var(--color-primary)',
  'var(--color-success)',
  'var(--color-warning)',
  'var(--color-destructive)',
  'var(--color-secondary)',
  'var(--color-muted-foreground)',
]

const TOOLTIP_STYLE = {
  backgroundColor: 'var(--color-card)',
  border: '1px solid var(--color-border)',
  borderRadius: 8,
  color: 'var(--color-card-foreground)',
  fontSize: 12,
}

/** Spending direction vocabulary varies server-side, so unknown values fall back to plain text. */
const DIRECTION_TONES: Record<string, { glyph: 'up' | 'down' | 'flat'; className: string }> = {
  UP: { glyph: 'up', className: 'text-destructive' },
  INCREASE: { glyph: 'up', className: 'text-destructive' },
  INCREASED: { glyph: 'up', className: 'text-destructive' },
  HIGHER: { glyph: 'up', className: 'text-destructive' },
  DOWN: { glyph: 'down', className: 'text-success' },
  DECREASE: { glyph: 'down', className: 'text-success' },
  DECREASED: { glyph: 'down', className: 'text-success' },
  LOWER: { glyph: 'down', className: 'text-success' },
  FLAT: { glyph: 'flat', className: 'text-muted-foreground' },
  SAME: { glyph: 'flat', className: 'text-muted-foreground' },
  STABLE: { glyph: 'flat', className: 'text-muted-foreground' },
  UNCHANGED: { glyph: 'flat', className: 'text-muted-foreground' },
}

function ErrorPanel({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="space-y-3 rounded-lg border border-destructive/40 bg-destructive/10 p-4">
      <p className="text-sm text-destructive">{message}</p>
      <Button size="sm" variant="outline" onClick={onRetry}>
        Try again
      </Button>
    </div>
  )
}

function monthRange(month: string): { from: string; to: string } {
  const [year, mon] = month.split('-')
  const lastDay = new Date(Number(year), Number(mon), 0).getDate()
  return { from: `${year}-${mon}-01`, to: `${year}-${mon}-${String(lastDay).padStart(2, '0')}` }
}

function TrendDirection({ pattern }: { pattern: SpendingPattern }) {
  const tone = DIRECTION_TONES[pattern.direction?.toUpperCase() ?? '']
  if (!tone) {
    return <span className="text-muted-foreground">{pattern.direction || '—'}</span>
  }
  return (
    <span className={cn('inline-flex items-center gap-1 font-medium', tone.className)}>
      {tone.glyph === 'up' ? (
        <ArrowUpRight className="size-4" aria-hidden />
      ) : tone.glyph === 'down' ? (
        <ArrowDownRight className="size-4" aria-hidden />
      ) : (
        <Equal className="size-4" aria-hidden />
      )}
      <span className="sr-only">{pattern.direction}</span>
    </span>
  )
}

interface ConfirmState {
  title: string
  description: string
  confirmLabel: string
  run: () => void
}

function ConfirmDialog({
  state,
  busy,
  onOpenChange,
  onConfirm,
}: {
  state: ConfirmState | null
  busy: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
}) {
  return (
    <Dialog open={state !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{state?.title ?? ''}</DialogTitle>
          <DialogDescription>{state?.description ?? ''}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            Cancel
          </Button>
          <Button variant="destructive" loading={busy} onClick={onConfirm}>
            {state?.confirmLabel ?? 'Delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

interface TransactionFormValues {
  transactionType: TransactionType
  amount: string
  category: string
  description: string
  occurredOn: string
  recurring: boolean
  recurrenceRule: string
  account: string
  currency: string
}

function TransactionDialog({
  open,
  transaction,
  categories,
  currency,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  transaction: TransactionResponse | null
  categories: string[]
  currency: string
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (values: TransactionFormValues) => void
}) {
  const defaults = (): TransactionFormValues => ({
    transactionType: transaction?.transactionType ?? 'EXPENSE',
    amount: transaction ? String(transaction.amount) : '',
    category: transaction?.category ?? '',
    description: transaction?.description ?? '',
    occurredOn: transaction?.occurredOn ?? todayIso(),
    recurring: transaction?.recurring ?? false,
    recurrenceRule: transaction?.recurrenceRule ?? '',
    account: transaction?.account ?? '',
    currency: transaction?.currency || currency,
  })

  const {
    register,
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<TransactionFormValues>({ defaultValues: defaults() })

  const recurring = watch('recurring')

  const close = (next: boolean) => {
    if (!next) reset(defaults())
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{transaction ? 'Edit transaction' : 'New transaction'}</DialogTitle>
          <DialogDescription>
            Amounts are positive; the type decides the sign. Categories can be anything you already use.
          </DialogDescription>
        </DialogHeader>

        <form className="space-y-4" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Type</Label>
              <Controller
                control={control}
                name="transactionType"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={(value) => field.onChange(value as TransactionType)}>
                    <SelectTrigger id="transaction-type" aria-label="Transaction type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {TRANSACTION_TYPES.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="transaction-amount">Amount</Label>
              <Input
                id="transaction-amount"
                type="number"
                step="0.01"
                min={0}
                required
                aria-invalid={Boolean(errors.amount)}
                {...register('amount', { required: 'An amount is required' })}
              />
              {errors.amount ? (
                <p className="text-xs text-destructive">{errors.amount.message}</p>
              ) : null}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="transaction-category">Category</Label>
              <Input
                id="transaction-category"
                list="finance-category-options"
                maxLength={80}
                required
                {...register('category', { required: 'A category is required' })}
              />
              <datalist id="finance-category-options">
                {categories.map((category) => (
                  <option key={category} value={category} />
                ))}
              </datalist>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="transaction-date">Occurred on</Label>
              <Input
                id="transaction-date"
                type="date"
                required
                {...register('occurredOn', { required: 'A date is required' })}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="transaction-description">Description</Label>
            <Textarea
              id="transaction-description"
              rows={2}
              maxLength={500}
              {...register('description')}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="transaction-account">Account</Label>
              <Input id="transaction-account" maxLength={80} {...register('account')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="transaction-currency">Currency</Label>
              <Input id="transaction-currency" maxLength={3} {...register('currency')} />
            </div>
            <label className="flex items-end gap-2 pb-2 text-sm">
              <input
                type="checkbox"
                className="size-4 accent-[var(--color-primary)]"
                {...register('recurring')}
              />
              Recurring
            </label>
          </div>

          {recurring ? (
            <div className="space-y-1.5">
              <Label htmlFor="transaction-rule">Recurrence rule</Label>
              <Input
                id="transaction-rule"
                placeholder="FREQ=MONTHLY;BYDAY=1"
                {...register('recurrenceRule')}
              />
              <p className="text-xs text-muted-foreground">
                iCalendar rule, e.g. <code>FREQ=WEEKLY;BYDAY=MO,WE</code>.
              </p>
            </div>
          ) : null}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {transaction ? 'Save transaction' : 'Add transaction'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface BudgetDraft {
  category: string
  period: BudgetPeriod
  amount: string
  startDate: string
  endDate: string
}

function BudgetDialog({
  open,
  categories,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  categories: string[]
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (draft: BudgetDraft) => void
}) {
  const { from: monthStart, to: monthEnd } = monthRange(currentMonthIso())
  const [draft, setDraft] = useState<BudgetDraft>({
    category: '',
    period: 'MONTHLY',
    amount: '',
    startDate: monthStart,
    endDate: monthEnd,
  })

  const close = (next: boolean) => {
    if (!next) {
      setDraft({ category: '', period: 'MONTHLY', amount: '', startDate: monthStart, endDate: monthEnd })
    }
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>New budget</DialogTitle>
          <DialogDescription>
            Leave the category empty for an overall budget that every expense counts towards.
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            onSubmit(draft)
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="budget-category">Category</Label>
              <Input
                id="budget-category"
                list="budget-category-options"
                maxLength={80}
                value={draft.category}
                onChange={(event) => setDraft({ ...draft, category: event.target.value })}
                placeholder="Overall"
              />
              <datalist id="budget-category-options">
                {categories.map((category) => (
                  <option key={category} value={category} />
                ))}
              </datalist>
            </div>
            <div className="space-y-1.5">
              <Label>Period</Label>
              <Select
                value={draft.period}
                onValueChange={(value) => setDraft({ ...draft, period: value as BudgetPeriod })}
              >
                <SelectTrigger aria-label="Budget period">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PERIODS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="budget-amount">Amount</Label>
            <Input
              id="budget-amount"
              type="number"
              step="0.01"
              min={0}
              required
              value={draft.amount}
              onChange={(event) => setDraft({ ...draft, amount: event.target.value })}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="budget-start">Start date</Label>
              <Input
                id="budget-start"
                type="date"
                required
                value={draft.startDate}
                onChange={(event) => setDraft({ ...draft, startDate: event.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="budget-end">End date</Label>
              <Input
                id="budget-end"
                type="date"
                required
                value={draft.endDate}
                onChange={(event) => setDraft({ ...draft, endDate: event.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              Create budget
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface SavingsDraft {
  name: string
  targetAmount: string
  savedAmount: string
  targetDate: string
  notes: string
}

function SavingsDialog({
  open,
  goal,
  currency,
  busy,
  onOpenChange,
  onSubmit,
}: {
  open: boolean
  goal: SavingsGoalResponse | null
  currency: string
  busy: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (draft: SavingsDraft) => void
}) {
  const defaults = (): SavingsDraft => ({
    name: goal?.name ?? '',
    targetAmount: goal ? String(goal.targetAmount) : '',
    savedAmount: goal ? String(goal.savedAmount) : '0',
    targetDate: goal?.targetDate ?? '',
    notes: goal?.notes ?? '',
  })
  const [draft, setDraft] = useState<SavingsDraft>(defaults)

  const close = (next: boolean) => {
    if (!next) setDraft(defaults())
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{goal ? `Edit ${goal.name}` : 'New savings goal'}</DialogTitle>
          <DialogDescription>
            Amounts are recorded in {currency}. Progress is recomputed by the server.
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            onSubmit(draft)
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="savings-name">Name</Label>
            <Input
              id="savings-name"
              required
              maxLength={120}
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="savings-target">Target amount</Label>
              <Input
                id="savings-target"
                type="number"
                step="0.01"
                min={0}
                required
                value={draft.targetAmount}
                onChange={(event) => setDraft({ ...draft, targetAmount: event.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="savings-saved">Saved so far</Label>
              <Input
                id="savings-saved"
                type="number"
                step="0.01"
                min={0}
                value={draft.savedAmount}
                onChange={(event) => setDraft({ ...draft, savedAmount: event.target.value })}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="savings-date">Target date</Label>
            <Input
              id="savings-date"
              type="date"
              value={draft.targetDate}
              onChange={(event) => setDraft({ ...draft, targetDate: event.target.value })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="savings-notes">Notes</Label>
            <Textarea
              id="savings-notes"
              rows={2}
              maxLength={500}
              value={draft.notes}
              onChange={(event) => setDraft({ ...draft, notes: event.target.value })}
            />
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={busy}>
              {goal ? 'Save goal' : 'Create goal'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function BudgetRow({
  budget,
  currency,
  onDelete,
}: {
  budget: BudgetResponse
  currency: string
  onDelete: () => void
}) {
  return (
    <li className="space-y-2 rounded-lg border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium">{budget.category || 'Overall budget'}</p>
          <p className="text-xs text-muted-foreground">
            {formatDate(budget.startDate)} → {formatDate(budget.endDate)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge value={budget.period} label={budget.period.toLowerCase()} />
          <Button
            size="sm"
            variant="ghost"
            className="text-destructive hover:text-destructive"
            onClick={onDelete}
          >
            Delete
          </Button>
        </div>
      </div>
      <Progress
        value={budget.utilisationPercent}
        indicatorClassName={budget.exceeded ? 'bg-destructive' : 'bg-teal-400'}
        aria-label={`${budget.category || 'Overall'} budget utilisation`}
      />
      <p className="text-xs text-muted-foreground">
        <span className={budget.exceeded ? 'font-medium text-destructive' : 'text-foreground'}>
          {formatCurrency(budget.spent, currency)}
        </span>{' '}
        of {formatCurrency(budget.amount, currency)} · {formatPercent(budget.utilisationPercent)}
        {budget.exceeded ? ' · over budget' : ''}
      </p>
    </li>
  )
}

export default function FinancePage() {
  const queryClient = useQueryClient()
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['finance'] })

  const [month, setMonth] = useState(currentMonthIso())
  const [trendMonths, setTrendMonths] = useState<number>(6)
  const [typeFilter, setTypeFilter] = useState(ALL)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [page, setPage] = useState(0)
  const [transactionDialog, setTransactionDialog] = useState<{
    open: boolean
    transaction: TransactionResponse | null
  }>({ open: false, transaction: null })
  const [budgetOpen, setBudgetOpen] = useState(false)
  const [savingsDialog, setSavingsDialog] = useState<{
    open: boolean
    goal: SavingsGoalResponse | null
  }>({ open: false, goal: null })
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const range = monthRange(month)

  const overview = useQuery({ queryKey: ['finance', 'overview'], queryFn: () => financeApi.overview() })
  const summary = useQuery({
    queryKey: ['finance', 'summary', month],
    queryFn: () => financeApi.summary(month),
  })
  const trend = useQuery({
    queryKey: ['finance', 'trend', trendMonths],
    queryFn: () => financeApi.trend(trendMonths),
  })
  const budgets = useQuery({ queryKey: ['finance', 'budgets'], queryFn: () => financeApi.budgets() })
  const savingsGoals = useQuery({
    queryKey: ['finance', 'savings-goals'],
    queryFn: () => financeApi.savingsGoals(),
  })
  const categories = useQuery({
    queryKey: ['finance', 'categories'],
    queryFn: () => financeApi.categories(),
  })
  const spendingPatterns = useQuery({
    queryKey: ['finance', 'spending-patterns'],
    queryFn: () => financeApi.spendingPatterns(),
  })
  const transactions = useQuery({
    queryKey: ['finance', 'transactions', typeFilter, categoryFilter, range.from, range.to, page],
    queryFn: () =>
      financeApi.transactions({
        type: typeFilter === ALL ? undefined : (typeFilter as TransactionType),
        category: categoryFilter || undefined,
        from: range.from,
        to: range.to,
        page,
        size: PAGE_SIZE,
      }),
  })

  const saveTransaction = useMutation({
    mutationFn: (values: TransactionFormValues) => {
      const body: TransactionRequest = {
        transactionType: values.transactionType,
        amount: Number(values.amount),
        category: values.category.trim(),
        description: values.description.trim() || undefined,
        occurredOn: values.occurredOn,
        recurring: values.recurring,
        recurrenceRule: values.recurring ? values.recurrenceRule.trim() || undefined : undefined,
        account: values.account.trim() || undefined,
        currency: values.currency.trim() || undefined,
      }
      return transactionDialog.transaction
        ? financeApi.updateTransaction(transactionDialog.transaction.id, body)
        : financeApi.createTransaction(body)
    },
    onSuccess: () => {
      toast.success(transactionDialog.transaction ? 'Transaction updated' : 'Transaction added')
      setTransactionDialog({ open: false, transaction: null })
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const createBudget = useMutation({
    mutationFn: (draft: BudgetDraft) => {
      const body: BudgetRequest = {
        category: draft.category.trim() || undefined,
        period: draft.period,
        amount: Number(draft.amount),
        startDate: draft.startDate,
        endDate: draft.endDate,
      }
      return financeApi.createBudget(body)
    },
    onSuccess: () => {
      toast.success('Budget created')
      setBudgetOpen(false)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const saveSavingsGoal = useMutation({
    mutationFn: (draft: SavingsDraft) => {
      const body: SavingsGoalRequest = {
        name: draft.name.trim(),
        targetAmount: Number(draft.targetAmount),
        savedAmount: Number(draft.savedAmount) || 0,
        targetDate: draft.targetDate || undefined,
        notes: draft.notes.trim() || undefined,
      }
      return savingsDialog.goal
        ? financeApi.updateSavingsGoal(savingsDialog.goal.id, body)
        : financeApi.createSavingsGoal(body)
    },
    onSuccess: () => {
      toast.success(savingsDialog.goal ? 'Savings goal updated' : 'Savings goal created')
      setSavingsDialog({ open: false, goal: null })
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeTransaction = useMutation({
    mutationFn: (id: string) => financeApi.removeTransaction(id),
    onSuccess: () => {
      toast.success('Transaction deleted')
      setConfirm(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeBudget = useMutation({
    mutationFn: (id: string) => financeApi.removeBudget(id),
    onSuccess: () => {
      toast.success('Budget deleted')
      setConfirm(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const removeSavingsGoal = useMutation({
    mutationFn: (id: string) => financeApi.removeSavingsGoal(id),
    onSuccess: () => {
      toast.success('Savings goal deleted')
      setConfirm(null)
      void invalidate()
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const currency = overview.data?.currency ?? 'USD'
  const summaryData = summary.data
  const cards = summaryData
    ? {
        income: summaryData.income,
        expenses: summaryData.expenses,
        savings: summaryData.savings,
        remainingBudget: summaryData.remainingBudget,
      }
    : {
        income: overview.data?.incomeThisMonth ?? 0,
        expenses: overview.data?.expenseThisMonth ?? 0,
        savings: overview.data?.savingsThisMonth ?? 0,
        remainingBudget: overview.data?.remainingBudget ?? 0,
      }

  const expenseByCategory = overview.data?.expenseByCategory ?? []
  const categoryOptions = categories.data ?? []
  const transactionsPage = transactions.data
  const anyBusy =
    removeTransaction.isPending || removeBudget.isPending || removeSavingsGoal.isPending

  return (
    <div className="space-y-6">
      <PageHeader
        title="Finance"
        description="Income, spending, budgets and savings goals in one place."
        actions={
          <>
            <div className="space-y-1">
              <Label htmlFor="finance-month" className="text-xs">
                Month
              </Label>
              <Input
                id="finance-month"
                type="month"
                className="w-40"
                value={month}
                onChange={(event) => {
                  setMonth(event.target.value || currentMonthIso())
                  setPage(0)
                }}
              />
            </div>
            <Button onClick={() => setTransactionDialog({ open: true, transaction: null })}>
              <Plus /> New transaction
            </Button>
          </>
        }
      />

      {overview.isError ? (
        <ErrorPanel message={overview.error.message} onRetry={() => void overview.refetch()} />
      ) : overview.isPending && summary.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-24 w-full" />
          ))}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Income" value={formatCurrency(cards.income, currency)} tone="text-success" />
          <StatCard
            label="Expenses"
            value={formatCurrency(cards.expenses, currency)}
            tone="text-destructive"
          />
          <StatCard label="Savings" value={formatCurrency(cards.savings, currency)} />
          <StatCard
            label="Remaining budget"
            value={formatCurrency(cards.remainingBudget, currency)}
            hint={cards.remainingBudget < 0 ? 'Over the planned budget' : 'Still available'}
            tone={cards.remainingBudget < 0 ? 'text-destructive' : undefined}
          />
        </div>
      )}

      {summary.isError ? (
        <ErrorPanel message={summary.error.message} onRetry={() => void summary.refetch()} />
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard title="Expense by category" description="Share of spending in the current period.">
          {overview.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : overview.isError ? (
            <ErrorPanel message={overview.error.message} onRetry={() => void overview.refetch()} />
          ) : expenseByCategory.length === 0 ? (
            <EmptyState
              icon={<Receipt />}
              title="No expenses recorded"
              description="Once you add an expense transaction the category split appears here."
              action={
                <Button onClick={() => setTransactionDialog({ open: true, transaction: null })}>
                  <Plus /> Add an expense
                </Button>
              }
            />
          ) : (
            <div className="space-y-4">
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={expenseByCategory}
                      dataKey="total"
                      nameKey="category"
                      innerRadius={45}
                      outerRadius={85}
                      paddingAngle={2}
                      stroke="var(--color-border)"
                    >
                      {expenseByCategory.map((entry, index) => (
                        <Cell key={entry.category} fill={PIE_COLOURS[index % PIE_COLOURS.length]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={TOOLTIP_STYLE} />
                    <Legend wrapperStyle={{ fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-muted-foreground">
                    <th scope="col" className="py-2 font-medium">
                      Category
                    </th>
                    <th scope="col" className="py-2 text-right font-medium">
                      Total
                    </th>
                    <th scope="col" className="py-2 text-right font-medium">
                      Share
                    </th>
                    <th scope="col" className="py-2 text-right font-medium">
                      Entries
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {expenseByCategory.map((entry) => (
                    <tr key={entry.category} className="border-b last:border-0">
                      <td className="py-2">{entry.category}</td>
                      <td className="py-2 text-right tabular-nums">
                        {formatCurrency(entry.total, currency)}
                      </td>
                      <td className="py-2 text-right tabular-nums">{formatPercent(entry.sharePercent, 1)}</td>
                      <td className="py-2 text-right tabular-nums text-muted-foreground">
                        {formatNumber(entry.transactionCount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>

        <SectionCard
          title="Monthly trend"
          description={`Last ${trendMonths} months reported by the server.`}
          action={
            <div className="flex gap-1" role="group" aria-label="Trend window">
              {TREND_WINDOWS.map((window) => (
                <Button
                  key={window}
                  size="sm"
                  variant={trendMonths === window ? 'default' : 'outline'}
                  onClick={() => setTrendMonths(window)}
                  aria-pressed={trendMonths === window}
                >
                  {window}m
                </Button>
              ))}
            </div>
          }
        >
          {trend.isError ? (
            <ErrorPanel message={trend.error.message} onRetry={() => void trend.refetch()} />
          ) : trend.isPending ? (
            <Skeleton className="h-64 w-full" />
          ) : (trend.data ?? []).length === 0 ? (
            <EmptyState
              icon={<TrendingUp />}
              title="No trend data"
              description="Monthly figures appear once transactions span more than one month."
            />
          ) : (
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={trend.data ?? []} margin={{ top: 8, right: 8, bottom: 0, left: 4 }}>
                  <CartesianGrid stroke="var(--color-border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis
                    dataKey="month"
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                    tickFormatter={(value: string) => String(value).slice(0, 7)}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: 'var(--color-muted-foreground)' }}
                    tickFormatter={(value: number) => formatCurrency(value, currency)}
                    width={80}
                  />
                  <Tooltip contentStyle={TOOLTIP_STYLE} cursor={{ fill: 'var(--color-secondary)' }} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="income" name="Income" fill="var(--color-success)" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="expenses" name="Expenses" fill="var(--color-destructive)" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="savings" name="Savings" fill="var(--color-primary)" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </SectionCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard
          title="Budgets"
          description="Progress against the amount you planned."
          action={
            <Button size="sm" variant="outline" onClick={() => setBudgetOpen(true)}>
              <Plus /> New budget
            </Button>
          }
        >
          {budgets.isError ? (
            <ErrorPanel message={budgets.error.message} onRetry={() => void budgets.refetch()} />
          ) : budgets.isPending ? (
            <Skeleton className="h-40 w-full" />
          ) : (budgets.data ?? []).length === 0 ? (
            <EmptyState
              icon={<Target />}
              title="No budgets"
              description="Create a budget to cap spending per category or for the month as a whole."
              action={
                <Button size="sm" onClick={() => setBudgetOpen(true)}>
                  <Plus /> Create a budget
                </Button>
              }
            />
          ) : (
            <ul className="space-y-3">
              {(budgets.data ?? []).map((budget) => (
                <BudgetRow
                  key={budget.id}
                  budget={budget}
                  currency={currency}
                  onDelete={() =>
                    setConfirm({
                      title: `Delete the ${budget.category || 'overall'} budget?`,
                      description: 'The cap is removed; recorded transactions are untouched.',
                      confirmLabel: 'Delete budget',
                      run: () => removeBudget.mutate(budget.id),
                    })
                  }
                />
              ))}
            </ul>
          )}
        </SectionCard>

        <SectionCard
          title="Savings goals"
          description="How close each goal is to its target."
          action={
            <Button size="sm" variant="outline" onClick={() => setSavingsDialog({ open: true, goal: null })}>
              <Plus /> New goal
            </Button>
          }
        >
          {savingsGoals.isError ? (
            <ErrorPanel message={savingsGoals.error.message} onRetry={() => void savingsGoals.refetch()} />
          ) : savingsGoals.isPending ? (
            <Skeleton className="h-40 w-full" />
          ) : (savingsGoals.data ?? []).length === 0 ? (
            <EmptyState
              icon={<PiggyBank />}
              title="No savings goals"
              description="Set a target and track how much has been put away so far."
              action={
                <Button size="sm" onClick={() => setSavingsDialog({ open: true, goal: null })}>
                  <Plus /> Create a goal
                </Button>
              }
            />
          ) : (
            <ul className="space-y-3">
              {(savingsGoals.data ?? []).map((goal) => (
                <li key={goal.id} className="space-y-2 rounded-lg border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{goal.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatCurrency(goal.savedAmount, currency)} of{' '}
                        {formatCurrency(goal.targetAmount, currency)}
                        {goal.targetDate ? ` · by ${formatDate(goal.targetDate)}` : ''}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs tabular-nums text-muted-foreground">
                        {formatPercent(goal.progressPercent)}
                      </span>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setSavingsDialog({ open: true, goal })}
                      >
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-destructive hover:text-destructive"
                        onClick={() =>
                          setConfirm({
                            title: `Delete ${goal.name}?`,
                            description: 'The saved amount history for this goal is removed.',
                            confirmLabel: 'Delete goal',
                            run: () => removeSavingsGoal.mutate(goal.id),
                          })
                        }
                      >
                        Delete
                      </Button>
                    </div>
                  </div>
                  <Progress
                    value={goal.progressPercent}
                    indicatorClassName="bg-success"
                    aria-label={`${goal.name} progress`}
                  />
                  {goal.notes ? (
                    <p className="text-xs text-muted-foreground">{goal.notes}</p>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      </div>

      <SectionCard
        title="Transactions"
        description={`${formatDate(range.from)} to ${formatDate(range.to)}`}
      >
        <div className="mb-4 flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="filter-type" className="text-xs">
              Type
            </Label>
            <div className="w-32">
              <Select
                value={typeFilter}
                onValueChange={(value) => {
                  setTypeFilter(value)
                  setPage(0)
                }}
              >
                <SelectTrigger id="filter-type" aria-label="Filter by type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All</SelectItem>
                  {TRANSACTION_TYPES.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-category" className="text-xs">
              Category
            </Label>
            <Input
              id="filter-category"
              list="filter-category-options"
              className="w-52"
              placeholder="Any category"
              value={categoryFilter}
              onChange={(event) => {
                setCategoryFilter(event.target.value)
                setPage(0)
              }}
            />
            <datalist id="filter-category-options">
              {categoryOptions.map((category) => (
                <option key={category} value={category} />
              ))}
            </datalist>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              setTransactionDialog({ open: true, transaction: null })
            }}
          >
            <Plus /> Add
          </Button>
        </div>

        {transactions.isError ? (
          <ErrorPanel message={transactions.error.message} onRetry={() => void transactions.refetch()} />
        ) : transactions.isPending ? (
          <Skeleton className="h-56 w-full" />
        ) : (transactionsPage?.content.length ?? 0) === 0 ? (
          <EmptyState
            icon={<Wallet />}
            title="No transactions in this range"
            description="Widen the month, clear the filters, or record a new transaction."
            action={
              <Button onClick={() => setTransactionDialog({ open: true, transaction: null })}>
                <Plus /> Add transaction
              </Button>
            }
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-muted-foreground">
                    <th scope="col" className="py-2 font-medium">
                      Date
                    </th>
                    <th scope="col" className="py-2 font-medium">
                      Category
                    </th>
                    <th scope="col" className="py-2 font-medium">
                      Description
                    </th>
                    <th scope="col" className="py-2 font-medium">
                      Account
                    </th>
                    <th scope="col" className="py-2 text-right font-medium">
                      Amount
                    </th>
                    <th scope="col" className="py-2 text-right font-medium">
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {(transactionsPage?.content ?? []).map((transaction) => {
                    const income = transaction.transactionType === 'INCOME'
                    return (
                      <tr key={transaction.id} className="border-b last:border-0">
                        <td className="py-2.5 tabular-nums">{formatDate(transaction.occurredOn)}</td>
                        <td className="py-2.5">
                          <span className="inline-flex items-center gap-2">
                            <StatusBadge
                              value={transaction.transactionType}
                              label={income ? 'in' : 'out'}
                            />
                            {transaction.category}
                          </span>
                        </td>
                        <td className="py-2.5 text-muted-foreground">
                          {transaction.description || '—'}
                        </td>
                        <td className="py-2.5 text-muted-foreground">
                          {transaction.account || '—'}
                        </td>
                        <td
                          className={cn(
                            'py-2.5 text-right font-medium tabular-nums',
                            income ? 'text-success' : 'text-destructive',
                          )}
                        >
                          {income ? '+' : '−'}
                          {formatCurrency(transaction.amount, transaction.currency || currency)}
                        </td>
                        <td className="py-2.5 text-right">
                          <div className="flex justify-end gap-1">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => setTransactionDialog({ open: true, transaction })}
                            >
                              Edit
                            </Button>
                            <Button
                              size="sm"
                              variant="ghost"
                              className="text-destructive hover:text-destructive"
                              onClick={() =>
                                setConfirm({
                                  title: 'Delete this transaction?',
                                  description: `${transaction.category} · ${formatCurrency(transaction.amount, transaction.currency || currency)} on ${formatDate(transaction.occurredOn)}.`,
                                  confirmLabel: 'Delete transaction',
                                  run: () => removeTransaction.mutate(transaction.id),
                                })
                              }
                            >
                              Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
              <span>
                Page {transactionsPage ? transactionsPage.page + 1 : 1} of{' '}
                {Math.max(transactionsPage?.totalPages ?? 1, 1)} ·{' '}
                {formatNumber(transactionsPage?.totalElements ?? 0)} transactions
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  disabled={transactionsPage?.first ?? true}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Previous
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={transactionsPage?.last ?? true}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          </>
        )}
      </SectionCard>

      <SectionCard
        title="Spending patterns"
        description="This period compared with the one before it."
      >
        {spendingPatterns.isError ? (
          <ErrorPanel message={spendingPatterns.error.message} onRetry={() => void spendingPatterns.refetch()} />
        ) : spendingPatterns.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : (spendingPatterns.data ?? []).length === 0 ? (
          <EmptyState
            icon={<ArrowDownRight />}
            title="No comparison available"
            description="Patterns need at least two periods of recorded expenses."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] text-sm">
              <thead>
                <tr className="border-b text-left text-xs text-muted-foreground">
                  <th scope="col" className="py-2 font-medium">
                    Category
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    This period
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Previous
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Change
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Direction
                  </th>
                </tr>
              </thead>
              <tbody>
                {(spendingPatterns.data ?? []).map((pattern) => (
                  <tr key={pattern.category} className="border-b last:border-0">
                    <td className="py-2.5">{pattern.category}</td>
                    <td className="py-2.5 text-right tabular-nums">
                      {formatCurrency(pattern.currentPeriod, currency)}
                    </td>
                    <td className="py-2.5 text-right tabular-nums text-muted-foreground">
                      {formatCurrency(pattern.previousPeriod, currency)}
                    </td>
                    <td className="py-2.5 text-right tabular-nums">
                      {pattern.changePercent > 0 ? '+' : ''}
                      {formatPercent(pattern.changePercent, 1)}
                    </td>
                    <td className="py-2.5 text-right">
                      <TrendDirection pattern={pattern} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>

      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <Equal className="size-3.5" aria-hidden />
        Income and expense rows use the transaction currency; totals fall back to your profile currency.
      </p>

      <TransactionDialog
        key={transactionDialog.transaction?.id ?? 'new'}
        open={transactionDialog.open}
        transaction={transactionDialog.transaction}
        categories={categoryOptions}
        currency={currency}
        busy={saveTransaction.isPending}
        onOpenChange={(open) =>
          setTransactionDialog({ open, transaction: open ? transactionDialog.transaction : null })
        }
        onSubmit={(values) => saveTransaction.mutate(values)}
      />

      <BudgetDialog
        open={budgetOpen}
        categories={categoryOptions}
        busy={createBudget.isPending}
        onOpenChange={setBudgetOpen}
        onSubmit={(draft) => createBudget.mutate(draft)}
      />

      <SavingsDialog
        key={savingsDialog.goal?.id ?? 'new'}
        open={savingsDialog.open}
        goal={savingsDialog.goal}
        currency={currency}
        busy={saveSavingsGoal.isPending}
        onOpenChange={(open) => setSavingsDialog({ open, goal: open ? savingsDialog.goal : null })}
        onSubmit={(draft) => saveSavingsGoal.mutate(draft)}
      />

      <ConfirmDialog
        state={confirm}
        busy={anyBusy}
        onOpenChange={(open) => {
          if (!open) setConfirm(null)
        }}
        onConfirm={() => confirm?.run()}
      />
    </div>
  )
}