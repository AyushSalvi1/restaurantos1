import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Download,
  FileDown,
  KeyRound,
  Laptop,
  Loader2,
  LogOut,
  Mail,
  Save,
  ShieldAlert,
  Trash2,
} from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { tokenStore } from '@/api/client'
import { accountApi, authApi, settingsApi } from '@/api/endpoints'
import { PageHeader, SectionCard } from '@/components/page-parts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { toNormalisedError } from '@/hooks/use-api-error'
import { StatusBadge } from '@/lib/badges'
import { formatBytes, formatDateTime, formatNumber, formatRelative } from '@/lib/format'
import { passwordProblem, useAuth } from '@/stores/auth'
import type {
  CountResponse,
  EmploymentType,
  ExportResponse,
  NotificationPreferenceRequest,
  NotificationPreferenceResponse,
  ProductivityStyle,
} from '@/types/api'

type NotificationToggleField = {
  [K in keyof NotificationPreferenceResponse]: NotificationPreferenceResponse[K] extends boolean ? K : never
}[keyof NotificationPreferenceResponse]

const EMPLOYMENT_TYPES: { value: EmploymentType; label: string }[] = [
  { value: 'EMPLOYED', label: 'Employed' },
  { value: 'STUDENT', label: 'Student' },
  { value: 'BOTH', label: 'Employed and studying' },
  { value: 'SELF_EMPLOYED', label: 'Self-employed' },
  { value: 'OTHER', label: 'Something else' },
]

const PRODUCTIVITY_STYLES: { value: ProductivityStyle; label: string }[] = [
  { value: 'DEEP_WORK', label: 'Deep work' },
  { value: 'BALANCED', label: 'Balanced' },
  { value: 'POMODORO', label: 'Pomodoro' },
  { value: 'ADHOC', label: 'Ad hoc' },
]

const NOTIFICATION_TOGGLES: { field: NotificationToggleField; label: string; hint: string }[] = [
  { field: 'inAppEnabled', label: 'In-app notifications', hint: 'Show notifications inside LIFEOS' },
  { field: 'emailEnabled', label: 'Email notifications', hint: 'Send a copy to your account email' },
  { field: 'taskEnabled', label: 'Tasks', hint: 'Deadlines, overdue work and completions' },
  { field: 'goalEnabled', label: 'Goals', hint: 'Progress, deadlines and stalled goals' },
  { field: 'habitEnabled', label: 'Habits', hint: 'Reminders and streak warnings' },
  { field: 'calendarEnabled', label: 'Calendar', hint: 'Events starting soon' },
  { field: 'financeEnabled', label: 'Finance', hint: 'Budget thresholds and overspend' },
  { field: 'learningEnabled', label: 'Learning', hint: 'Goal deadlines and study reminders' },
  { field: 'aiInsightEnabled', label: 'AI insights', hint: 'New insights and forecasts' },
  { field: 'reminderEnabled', label: 'Reminders', hint: 'Habit and event reminder nudges' },
]

/** Comma separated free text becomes a trimmed, de-duplicated list. */
function parseList(value: string): string[] {
  return Array.from(
    new Set(
      value
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  )
}

function joinList(value: string[] | null | undefined): string {
  return (value ?? []).join(', ')
}

/** Endpoints that answer 204 resolve to `void`, so the affected count is read defensively. */
function countOf(result: CountResponse | void): number {
  return result && typeof result === 'object' ? result.count : 0
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="text-xs text-destructive">
      {message}
    </p>
  )
}

function TextField({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string
  label: string
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <FieldError message={error} /> : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

function SaveRow({ saving, onSave }: { saving: boolean; onSave: () => void }) {
  return (
    <div className="flex justify-end border-t pt-4">
      <Button type="button" onClick={onSave} loading={saving}>
        <Save />
        Save changes
      </Button>
    </div>
  )
}

function ProfileTab() {
  const queryClient = useQueryClient()
  const { refreshUser } = useAuth()
  const me = useQuery({ queryKey: ['auth', 'me'], queryFn: () => authApi.me() })

  const [fullName, setFullName] = useState('')
  const [avatarUrl, setAvatarUrl] = useState('')
  const [timezone, setTimezone] = useState('')
  const [locale, setLocale] = useState('')
  const [occupation, setOccupation] = useState('')
  const [employmentType, setEmploymentType] = useState<EmploymentType>('EMPLOYED')

  useEffect(() => {
    const user = me.data
    if (!user) return
    setFullName(user.fullName ?? '')
    setAvatarUrl(user.avatarUrl ?? '')
    setTimezone(user.timezone ?? '')
    setLocale(user.locale ?? '')
    setOccupation(user.occupation ?? '')
    setEmploymentType(user.employmentType ?? 'EMPLOYED')
  }, [me.data])

  const save = useMutation({
    mutationFn: () =>
      authApi.updateProfile({
        fullName: fullName.trim(),
        avatarUrl: avatarUrl.trim(),
        timezone: timezone.trim(),
        locale: locale.trim(),
        occupation: occupation.trim(),
        employmentType,
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(['auth', 'me'], updated)
      await refreshUser()
      toast.success('Profile saved')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const failure = save.isError ? toNormalisedError(save.error) : null
  const fieldErrors = failure?.fieldErrors ?? {}

  if (me.isPending) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Loading your profile…
      </p>
    )
  }

  if (me.isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{toNormalisedError(me.error).message}</p>
        <Button variant="outline" size="sm" onClick={() => void me.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  return (
    <form
      className="space-y-4"
      onSubmit={(event: FormEvent) => {
        event.preventDefault()
        save.mutate()
      }}
    >
      <p className="text-xs text-muted-foreground">
        Signed in as <span className="text-foreground">{me.data?.email}</span>
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="profile-full-name" label="Full name" error={fieldErrors.fullName}>
          <Input
            id="profile-full-name"
            value={fullName}
            maxLength={120}
            onChange={(event) => setFullName(event.target.value)}
          />
        </TextField>
        <TextField id="profile-occupation" label="Occupation" error={fieldErrors.occupation}>
          <Input
            id="profile-occupation"
            value={occupation}
            maxLength={120}
            onChange={(event) => setOccupation(event.target.value)}
          />
        </TextField>
        <TextField
          id="profile-avatar"
          label="Avatar URL"
          hint="Leave empty to use your initials."
          error={fieldErrors.avatarUrl}
        >
          <Input
            id="profile-avatar"
            value={avatarUrl}
            placeholder="https://…"
            onChange={(event) => setAvatarUrl(event.target.value)}
          />
        </TextField>
        <TextField
          id="profile-timezone"
          label="Timezone"
          hint="Deadlines and reminders are shown in this zone."
          error={fieldErrors.timezone}
        >
          <Input
            id="profile-timezone"
            value={timezone}
            placeholder="Europe/London"
            onChange={(event) => setTimezone(event.target.value)}
          />
        </TextField>
        <TextField id="profile-locale" label="Locale" hint="For example en-GB." error={fieldErrors.locale}>
          <Input
            id="profile-locale"
            value={locale}
            placeholder="en-GB"
            onChange={(event) => setLocale(event.target.value)}
          />
        </TextField>
        <div className="space-y-1.5">
          <Label htmlFor="profile-employment">Employment</Label>
          <Select value={employmentType} onValueChange={(value) => setEmploymentType(value as EmploymentType)}>
            <SelectTrigger id="profile-employment">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {EMPLOYMENT_TYPES.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldError message={fieldErrors.employmentType} />
        </div>
      </div>
      <SaveRow saving={save.isPending} onSave={() => save.mutate()} />
    </form>
  )
}

function SessionsTab() {
  const queryClient = useQueryClient()
  const refreshToken = tokenStore.refresh ?? undefined
  const sessions = useQuery({
    queryKey: ['auth', 'sessions', refreshToken ?? null],
    queryFn: () => authApi.sessions(refreshToken),
  })
  const [revokeTarget, setRevokeTarget] = useState<string | null>(null)

  const revoke = useMutation({
    mutationFn: (sessionId: string) => authApi.revokeSession(sessionId),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] })
      setRevokeTarget(null)
      toast.success(`${formatNumber(countOf(result))} session(s) revoked`)
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const list = sessions.data?.sessions ?? []
  const target = list.find((session) => session.id === revokeTarget) ?? null

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Every device holding a refresh token for your account. Revoking a session signs that device out
        immediately.
      </p>

      {sessions.isPending ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" aria-hidden /> Loading sessions…
        </p>
      ) : null}

      {sessions.isError ? (
        <div className="space-y-3">
          <p className="text-sm text-destructive">{toNormalisedError(sessions.error).message}</p>
          <Button variant="outline" size="sm" onClick={() => void sessions.refetch()}>
            Try again
          </Button>
        </div>
      ) : null}

      {!sessions.isPending && !sessions.isError && list.length === 0 ? (
        <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
          No active sessions were returned for this account.
        </p>
      ) : null}

      {list.length > 0 ? (
        <ul className="space-y-2">
          {list.map((session) => (
            <li
              key={session.id}
              className="flex flex-col gap-2 rounded-lg border bg-background/40 p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <Laptop className="size-4 text-muted-foreground" aria-hidden />
                  <span className="truncate text-sm">{session.userAgent || 'Unknown device'}</span>
                  {session.current ? <Badge variant="success">This device</Badge> : null}
                </div>
                <p className="text-xs text-muted-foreground">
                  {session.ipAddress || 'unknown IP'} · issued {formatRelative(session.issuedAt)} · expires{' '}
                  {formatDateTime(session.expiresAt)}
                </p>
              </div>
              {session.current ? (
                <p className="shrink-0 text-xs text-muted-foreground">
                  Use sign out to end this session.
                </p>
              ) : (
                <Button
                  size="sm"
                  variant="outline"
                  className="shrink-0"
                  onClick={() => setRevokeTarget(session.id)}
                >
                  <LogOut />
                  Revoke
                </Button>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      <Dialog
        open={target !== null}
        onOpenChange={(open) => {
          if (!open) setRevokeTarget(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke this session?</DialogTitle>
            <DialogDescription>
              {target?.userAgent || 'That device'} will be signed out immediately and will need to sign in
              again. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRevokeTarget(null)}>
              Keep it
            </Button>
            <Button
              variant="destructive"
              loading={revoke.isPending}
              onClick={() => target && revoke.mutate(target.id)}
            >
              Revoke session
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function SecurityTab() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [clientError, setClientError] = useState<string | null>(null)

  const changePassword = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setClientError(null)
      toast.success('Password changed')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const resend = useMutation({
    mutationFn: () => authApi.resendVerification(),
    onSuccess: () => toast.success('Verification email sent. Check your inbox.'),
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const problem = passwordProblem(newPassword)
    if (problem) {
      setClientError(problem)
      return
    }
    if (newPassword !== confirmPassword) {
      setClientError('The two new passwords do not match.')
      return
    }
    setClientError(null)
    changePassword.mutate()
  }

  const failure = changePassword.isError ? toNormalisedError(changePassword.error) : null
  const fieldErrors = failure?.fieldErrors ?? {}

  return (
    <div className="space-y-6">
      <form className="space-y-4" onSubmit={submit}>
        <div className="space-y-1.5">
          <Label htmlFor="security-current">Current password</Label>
          <Input
            id="security-current"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            aria-invalid={Boolean(fieldErrors.currentPassword)}
          />
          <FieldError message={fieldErrors.currentPassword} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="security-new">New password</Label>
            <Input
              id="security-new"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              aria-invalid={Boolean(fieldErrors.newPassword)}
            />
            <p className="text-xs text-muted-foreground">
              At least 10 characters, with a letter and a digit.
            </p>
            <FieldError message={fieldErrors.newPassword} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="security-confirm">Confirm new password</Label>
            <Input
              id="security-confirm"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
            />
          </div>
        </div>
        {clientError ? <FieldError message={clientError} /> : null}
        <div className="flex justify-end border-t pt-4">
          <Button
            type="submit"
            loading={changePassword.isPending}
            disabled={!currentPassword || !newPassword || !confirmPassword}
          >
            <KeyRound />
            Change password
          </Button>
        </div>
      </form>

      <div className="space-y-3 border-t pt-5">
        <div>
          <h3 className="text-sm font-semibold">Email verification</h3>
          <p className="text-sm text-muted-foreground">
            A verified address is required before password resets can be delivered.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          loading={resend.isPending}
          onClick={() => resend.mutate()}
        >
          <Mail />
          Resend verification email
        </Button>
      </div>
    </div>
  )
}

function PreferencesTab() {
  const queryClient = useQueryClient()
  const preferences = useQuery({ queryKey: ['settings', 'preferences'], queryFn: () => settingsApi.preferences() })

  const [workingHoursStart, setWorkingHoursStart] = useState('09:00')
  const [workingHoursEnd, setWorkingHoursEnd] = useState('17:00')
  const [dayStart, setDayStart] = useState('07:00')
  const [dayEnd, setDayEnd] = useState('22:00')
  const [style, setStyle] = useState<ProductivityStyle>('BALANCED')
  const [focusMinutes, setFocusMinutes] = useState(45)
  const [breakMinutes, setBreakMinutes] = useState(10)
  const [weeklyHours, setWeeklyHours] = useState(35)
  const [areasOfInterest, setAreasOfInterest] = useState('')
  const [currentSkills, setCurrentSkills] = useState('')
  const [primaryGoalAreas, setPrimaryGoalAreas] = useState('')
  const [financialGoals, setFinancialGoals] = useState('')
  const [learningGoals, setLearningGoals] = useState('')
  const [weights, setWeights] = useState<Record<string, number>>({})

  useEffect(() => {
    const value = preferences.data
    if (!value) return
    setWorkingHoursStart(value.workingHoursStart?.slice(0, 5) ?? '09:00')
    setWorkingHoursEnd(value.workingHoursEnd?.slice(0, 5) ?? '17:00')
    setDayStart(value.dayStart?.slice(0, 5) ?? '07:00')
    setDayEnd(value.dayEnd?.slice(0, 5) ?? '22:00')
    setStyle(value.preferredProductivityStyle ?? 'BALANCED')
    setFocusMinutes(value.preferredFocusMinutes ?? 45)
    setBreakMinutes(value.breakMinutes ?? 10)
    setWeeklyHours(value.weeklyProductivityHours ?? 35)
    setAreasOfInterest(joinList(value.areasOfInterest))
    setCurrentSkills(joinList(value.currentSkills))
    setPrimaryGoalAreas(joinList(value.primaryGoalAreas))
    setFinancialGoals(joinList(value.financialGoals))
    setLearningGoals(joinList(value.learningGoals))
    setWeights(value.lifeBalanceWeights ?? {})
  }, [preferences.data])

  const save = useMutation({
    mutationFn: () =>
      settingsApi.updatePreferences({
        workingHoursStart,
        workingHoursEnd,
        dayStart,
        dayEnd,
        preferredProductivityStyle: style,
        preferredFocusMinutes: focusMinutes,
        breakMinutes,
        weeklyProductivityHours: weeklyHours,
        areasOfInterest: parseList(areasOfInterest),
        currentSkills: parseList(currentSkills),
        primaryGoalAreas: parseList(primaryGoalAreas),
        financialGoals: parseList(financialGoals),
        learningGoals: parseList(learningGoals),
        lifeBalanceWeights: weights,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['settings', 'preferences'], updated)
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success('Preferences saved')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  if (preferences.isPending) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Loading preferences…
      </p>
    )
  }

  if (preferences.isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{toNormalisedError(preferences.error).message}</p>
        <Button variant="outline" size="sm" onClick={() => void preferences.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  const weightKeys = Object.keys(weights).sort()

  return (
    <form
      className="space-y-5"
      onSubmit={(event: FormEvent) => {
        event.preventDefault()
        save.mutate()
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="pref-work-start" label="Working day starts">
          <Input
            id="pref-work-start"
            type="time"
            value={workingHoursStart}
            onChange={(event) => setWorkingHoursStart(event.target.value)}
          />
        </TextField>
        <TextField id="pref-work-end" label="Working day ends">
          <Input
            id="pref-work-end"
            type="time"
            value={workingHoursEnd}
            onChange={(event) => setWorkingHoursEnd(event.target.value)}
          />
        </TextField>
        <TextField id="pref-day-start" label="Day starts" hint="Used when planning the day.">
          <Input id="pref-day-start" type="time" value={dayStart} onChange={(event) => setDayStart(event.target.value)} />
        </TextField>
        <TextField id="pref-day-end" label="Day ends">
          <Input id="pref-day-end" type="time" value={dayEnd} onChange={(event) => setDayEnd(event.target.value)} />
        </TextField>
        <TextField id="pref-focus" label="Focus block (minutes)">
          <Input
            id="pref-focus"
            type="number"
            min={10}
            max={180}
            value={focusMinutes}
            onChange={(event) => setFocusMinutes(Number(event.target.value))}
          />
        </TextField>
        <TextField id="pref-break" label="Break (minutes)">
          <Input
            id="pref-break"
            type="number"
            min={0}
            max={60}
            value={breakMinutes}
            onChange={(event) => setBreakMinutes(Number(event.target.value))}
          />
        </TextField>
        <TextField id="pref-weekly" label="Productivity hours per week">
          <Input
            id="pref-weekly"
            type="number"
            min={1}
            max={80}
            step={0.5}
            value={weeklyHours}
            onChange={(event) => setWeeklyHours(Number(event.target.value))}
          />
        </TextField>
        <div className="space-y-1.5">
          <Label htmlFor="pref-style">Working style</Label>
          <Select value={style} onValueChange={(value) => setStyle(value as ProductivityStyle)}>
            <SelectTrigger id="pref-style">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PRODUCTIVITY_STYLES.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <fieldset className="space-y-4 rounded-lg border p-4">
        <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          List fields, separated with commas
        </legend>
        <TextField id="pref-areas" label="Areas of interest">
          <Input
            id="pref-areas"
            value={areasOfInterest}
            placeholder="Health, career, relationships"
            onChange={(event) => setAreasOfInterest(event.target.value)}
          />
        </TextField>
        <TextField id="pref-skills" label="Current skills">
          <Input
            id="pref-skills"
            value={currentSkills}
            placeholder="TypeScript, public speaking"
            onChange={(event) => setCurrentSkills(event.target.value)}
          />
        </TextField>
        <TextField id="pref-goal-areas" label="Primary goal areas">
          <Input
            id="pref-goal-areas"
            value={primaryGoalAreas}
            placeholder="Career, fitness, side project"
            onChange={(event) => setPrimaryGoalAreas(event.target.value)}
          />
        </TextField>
        <TextField id="pref-financial" label="Financial goals">
          <Input
            id="pref-financial"
            value={financialGoals}
            placeholder="Build a three-month emergency fund"
            onChange={(event) => setFinancialGoals(event.target.value)}
          />
        </TextField>
        <TextField id="pref-learning" label="Learning goals">
          <Input
            id="pref-learning"
            value={learningGoals}
            placeholder="Finish the system design course"
            onChange={(event) => setLearningGoals(event.target.value)}
          />
        </TextField>
      </fieldset>

      <fieldset className="space-y-3 rounded-lg border p-4">
        <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Life balance weights
        </legend>
        <p className="text-xs text-muted-foreground">
          A dimension you weight at zero is excluded from the balance score instead of counting against you.
        </p>
        {weightKeys.length === 0 ? (
          <p className="text-sm text-muted-foreground">No dimensions are configured yet.</p>
        ) : (
          weightKeys.map((key) => (
            <div key={key} className="space-y-1.5">
              <div className="flex items-center justify-between text-sm">
                <Label htmlFor={`weight-${key}`} className="capitalize">
                  {key}
                </Label>
                <span className="tabular-nums text-muted-foreground">{weights[key]}</span>
              </div>
              <input
                id={`weight-${key}`}
                type="range"
                min={0}
                max={100}
                step={5}
                value={weights[key]}
                onChange={(event) =>
                  setWeights((current) => ({ ...current, [key]: Number(event.target.value) }))
                }
                className="w-full accent-[var(--color-primary)]"
              />
            </div>
          ))
        )}
      </fieldset>

      <SaveRow saving={save.isPending} onSave={() => save.mutate()} />
    </form>
  )
}

function NotificationSettingsTab() {
  const queryClient = useQueryClient()
  const settings = useQuery({
    queryKey: ['settings', 'notification-preferences'],
    queryFn: () => settingsApi.notificationPreferences(),
  })
  const [values, setValues] = useState<NotificationPreferenceResponse | null>(null)

  useEffect(() => {
    if (settings.data) setValues(settings.data)
  }, [settings.data])

  const save = useMutation({
    mutationFn: (body: NotificationPreferenceRequest) => settingsApi.updateNotificationPreferences(body),
    onSuccess: (updated) => {
      queryClient.setQueryData(['settings', 'notification-preferences'], updated)
      setValues(updated)
      toast.success('Notification preferences saved')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  if (settings.isPending || !values) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Loading notification preferences…
      </p>
    )
  }

  if (settings.isError) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-destructive">{toNormalisedError(settings.error).message}</p>
        <Button variant="outline" size="sm" onClick={() => void settings.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  const setToggle = (field: NotificationToggleField, next: boolean) => {
    setValues({ ...values, [field]: next })
  }

  return (
    <form
      className="space-y-5"
      onSubmit={(event: FormEvent) => {
        event.preventDefault()
        save.mutate(values)
      }}
    >
      <ul className="divide-y rounded-lg border">
        {NOTIFICATION_TOGGLES.map(({ field, label, hint }) => {
          const inputId = `notify-${field}`
          return (
            <li key={field} className="flex items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <Label htmlFor={inputId} className="cursor-pointer">
                  {label}
                </Label>
                <p className="text-xs text-muted-foreground">{hint}</p>
              </div>
              <Switch
                id={inputId}
                checked={values[field]}
                onCheckedChange={(next) => setToggle(field, next)}
              />
            </li>
          )
        })}
      </ul>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="notify-quiet-start" label="Quiet hours start" hint="No notifications before this time.">
          <Input
            id="notify-quiet-start"
            type="time"
            value={values.quietHoursStart?.slice(0, 5) ?? ''}
            onChange={(event) => setValues({ ...values, quietHoursStart: event.target.value })}
          />
        </TextField>
        <TextField id="notify-quiet-end" label="Quiet hours end">
          <Input
            id="notify-quiet-end"
            type="time"
            value={values.quietHoursEnd?.slice(0, 5) ?? ''}
            onChange={(event) => setValues({ ...values, quietHoursEnd: event.target.value })}
          />
        </TextField>
      </div>

      <SaveRow saving={save.isPending} onSave={() => save.mutate(values)} />
    </form>
  )
}

function DataExportTab() {
  const sections = useQuery({
    queryKey: ['account', 'export-sections'],
    queryFn: () => accountApi.exportSections(),
  })
  const [result, setResult] = useState<ExportResponse | null>(null)

  const generate = useMutation({
    mutationFn: () => accountApi.export(),
    onSuccess: (response) => {
      setResult(response)
      toast.success(response.message || 'Export generated')
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const download = useMutation({
    mutationFn: (file: { exportId: string; fileName: string }) =>
      accountApi.downloadExport(file.exportId, file.fileName),
    onSuccess: () => toast.success('Download started'),
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Generating an export bundles your records into a single file on the server. Downloading is a separate
        step and only works for an export this account created.
      </p>

      <div className="rounded-lg border p-4">
        <h3 className="text-sm font-semibold">Included sections</h3>
        {sections.isPending ? (
          <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" aria-hidden /> Loading sections…
          </p>
        ) : null}
        {sections.isError ? (
          <div className="mt-2 space-y-2">
            <p className="text-sm text-destructive">{toNormalisedError(sections.error).message}</p>
            <Button variant="outline" size="sm" onClick={() => void sections.refetch()}>
              Try again
            </Button>
          </div>
        ) : null}
        {sections.data ? (
          sections.data.length > 0 ? (
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {sections.data.map((section) => (
                <li key={section}>
                  <Badge variant="outline">{section}</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-sm text-muted-foreground">The server listed no exportable sections.</p>
          )
        ) : null}
      </div>

      <Button variant="outline" loading={generate.isPending} onClick={() => generate.mutate()}>
        <FileDown />
        Generate export
      </Button>

      {result ? (
        <div className="space-y-3 rounded-lg border border-primary/40 bg-primary/5 p-4">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge value={result.status} />
            <span className="text-sm font-medium">{result.fileName}</span>
          </div>
          <dl className="grid gap-2 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-xs text-muted-foreground">Size</dt>
              <dd>{formatBytes(result.sizeBytes)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Generated</dt>
              <dd>{formatDateTime(result.generatedAt)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">Export id</dt>
              <dd className="truncate font-mono text-xs">{result.exportId}</dd>
            </div>
          </dl>
          {result.message ? <p className="text-sm text-muted-foreground">{result.message}</p> : null}
          <Button
            loading={download.isPending}
            onClick={() =>
              download.mutate({ exportId: result.exportId, fileName: result.fileName })
            }
          >
            <Download />
            Download
          </Button>
        </div>
      ) : (
        <p className="rounded-lg border border-dashed px-4 py-5 text-sm text-muted-foreground">
          No export generated in this session yet.
        </p>
      )}
    </div>
  )
}

function DangerZoneTab() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [acknowledged, setAcknowledged] = useState(false)
  const [typedEmail, setTypedEmail] = useState('')
  const [confirmEmail, setConfirmEmail] = useState(false)

  const removeAccount = useMutation({
    mutationFn: () => authApi.deleteAccount({ password, confirmEmail: confirmEmail || undefined }),
    onSuccess: async () => {
      await logout()
      toast.success('Account deleted')
      navigate('/login', { replace: true })
    },
    onError: (error) => toast.error(toNormalisedError(error).message),
  })

  const failure = removeAccount.isError ? toNormalisedError(removeAccount.error) : null
  const fieldErrors = failure?.fieldErrors ?? {}
  const emailMatches =
    typedEmail.trim().length > 0 && typedEmail.trim().toLowerCase() === (user?.email ?? '').toLowerCase()
  const canSubmit = password.length > 0 && acknowledged && (!confirmEmail || emailMatches)

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3 rounded-lg border border-destructive/40 bg-destructive/5 p-4">
        <ShieldAlert className="mt-0.5 size-5 shrink-0 text-destructive" aria-hidden />
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-destructive">Deleting is permanent</h3>
          <p className="text-sm text-muted-foreground">
            Deleting your account removes every task, goal, habit, calendar event, transaction, journal entry,
            uploaded document, focus session and learning record you own. Nothing is kept, and there is no way to
            restore it. Export your data first if you want a copy.
          </p>
        </div>
      </div>

      <Button variant="destructive" onClick={() => setOpen(true)}>
        <Trash2 />
        Delete my account
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <form
            className="space-y-4"
            onSubmit={(event: FormEvent) => {
              event.preventDefault()
              removeAccount.mutate()
            }}
          >
            <DialogHeader>
              <DialogTitle>Delete your LIFEOS account?</DialogTitle>
              <DialogDescription>
                This removes all of your data permanently and signs you out everywhere. There is no undo.
              </DialogDescription>
            </DialogHeader>

            <div className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/5 p-3">
              <input
                id="danger-ack"
                type="checkbox"
                checked={acknowledged}
                onChange={(event) => setAcknowledged(event.target.checked)}
                className="mt-0.5 size-4 accent-[var(--color-destructive)]"
              />
              <Label htmlFor="danger-ack" className="text-foreground">
                I understand that all of my data is removed and cannot be recovered.
              </Label>
            </div>

            <div className="flex items-start gap-2 rounded-md border p-3">
              <input
                id="danger-confirm-email"
                type="checkbox"
                checked={confirmEmail}
                onChange={(event) => setConfirmEmail(event.target.checked)}
                className="mt-0.5 size-4 accent-[var(--color-primary)]"
              />
              <Label htmlFor="danger-confirm-email" className="text-foreground">
                Also require me to type my account email
              </Label>
            </div>

            {confirmEmail ? (
              <TextField
                id="danger-email"
                label="Type your account email to confirm"
                hint={user?.email}
              >
                <Input
                  id="danger-email"
                  type="email"
                  value={typedEmail}
                  placeholder={user?.email ?? 'you@example.com'}
                  onChange={(event) => setTypedEmail(event.target.value)}
                  aria-invalid={typedEmail.trim().length > 0 && !emailMatches}
                />
                <FieldError
                  message={
                    typedEmail.trim().length > 0 && !emailMatches
                      ? 'That does not match your account email.'
                      : undefined
                  }
                />
              </TextField>
            ) : null}

            <TextField id="danger-password" label="Your password" error={fieldErrors.password}>
              <Input
                id="danger-password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </TextField>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant="destructive"
                loading={removeAccount.isPending}
                disabled={!canSubmit}
              >
                <AlertTriangle />
                Delete account permanently
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings"
        description="Each section saves on its own, so a failure in one never discards the others."
      />

      <Tabs defaultValue="profile">
        <TabsList className="h-auto w-full flex-wrap justify-start gap-1">
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="sessions">Sessions</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
          <TabsTrigger value="preferences">Preferences</TabsTrigger>
          <TabsTrigger value="notifications">Notifications</TabsTrigger>
          <TabsTrigger value="export">Data export</TabsTrigger>
          <TabsTrigger value="danger">Danger zone</TabsTrigger>
        </TabsList>

        <TabsContent value="profile">
          <SectionCard title="Profile" description="How you appear across LIFEOS.">
            <ProfileTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="sessions">
          <SectionCard title="Active sessions" description="Devices currently signed in.">
            <SessionsTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="security">
          <SectionCard title="Security" description="Password and email verification.">
            <SecurityTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="preferences">
          <SectionCard
            title="Work preferences"
            description="Used by day planning, focus defaults and the life balance score."
          >
            <PreferencesTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="notifications">
          <SectionCard
            title="Notification preferences"
            description="What LIFEOS is allowed to raise, and when it stays quiet."
          >
            <NotificationSettingsTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="export">
          <SectionCard title="Data export" description="Generate a file, then download it.">
            <DataExportTab />
          </SectionCard>
        </TabsContent>

        <TabsContent value="danger">
          <SectionCard title="Danger zone" description="Irreversible actions.">
            <DangerZoneTab />
          </SectionCard>
        </TabsContent>
      </Tabs>
    </div>
  )
}