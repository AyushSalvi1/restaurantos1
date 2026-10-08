import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Check, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { settingsApi } from '@/api/endpoints'
import { toNormalisedError } from '@/hooks/use-api-error'
import { Button } from '@/components/ui/button'
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
import { useAuth } from '@/stores/auth'
import type { EmploymentType, ProductivityStyle } from '@/types/api'

const STEPS = ['About you', 'How you work', 'What matters', 'Review'] as const

const EMPLOYMENT_TYPES: { value: EmploymentType; label: string }[] = [
  { value: 'EMPLOYED', label: 'Employed' },
  { value: 'STUDENT', label: 'Student' },
  { value: 'BOTH', label: 'Employed and studying' },
  { value: 'SELF_EMPLOYED', label: 'Self-employed' },
  { value: 'OTHER', label: 'Something else' },
]

const PRODUCTIVITY_STYLES: { value: ProductivityStyle; label: string; hint: string }[] = [
  { value: 'DEEP_WORK', label: 'Deep work', hint: 'Long uninterrupted blocks' },
  { value: 'BALANCED', label: 'Balanced', hint: 'Mix of deep work and short tasks' },
  { value: 'POMODORO', label: 'Pomodoro', hint: '25 minutes with short breaks' },
  { value: 'ADHOC', label: 'Ad hoc', hint: 'Work when it suits me' },
]

const BALANCE_DIMENSIONS = [
  { key: 'productivity', label: 'Productivity' },
  { key: 'learning', label: 'Learning' },
  { key: 'goals', label: 'Goals' },
  { key: 'habits', label: 'Habits' },
  { key: 'finance', label: 'Finance' },
  { key: 'planning', label: 'Planning' },
]

/** Comma and newline separated free text becomes a trimmed, de-duplicated list. */
function parseList(value: string): string[] {
  return Array.from(
    new Set(
      value
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  )
}

export function OnboardingPage() {
  const { user, isAuthenticated, refreshUser } = useAuth()
  const navigate = useNavigate()

  const [step, setStep] = useState(0)
  const [name, setName] = useState('')
  const [occupation, setOccupation] = useState('')
  const [employmentType, setEmploymentType] = useState<EmploymentType>('EMPLOYED')
  const [timezone, setTimezone] = useState('')
  const [workingHoursStart, setWorkingHoursStart] = useState('09:00')
  const [workingHoursEnd, setWorkingHoursEnd] = useState('17:00')
  const [productivityStyle, setProductivityStyle] = useState<ProductivityStyle>('BALANCED')
  const [focusMinutes, setFocusMinutes] = useState(45)
  const [breakMinutes, setBreakMinutes] = useState(10)
  const [weeklyHours, setWeeklyHours] = useState(35)
  const [areas, setAreas] = useState('')
  const [skills, setSkills] = useState('')
  const [primaryGoals, setPrimaryGoals] = useState('')
  const [financialGoals, setFinancialGoals] = useState('')
  const [learningGoals, setLearningGoals] = useState('')
  const [weights, setWeights] = useState<Record<string, number>>(() =>
    Object.fromEntries(BALANCE_DIMENSIONS.map((dimension) => [dimension.key, 50])),
  )

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const onboarding = useQuery({
    queryKey: ['onboarding'],
    queryFn: () => settingsApi.onboarding(),
    enabled: isAuthenticated,
  })

  useEffect(() => {
    if (!user) return
    setName((current) => current || user.fullName)
    setOccupation((current) => current || user.occupation)
    if (user.employmentType) setEmploymentType(user.employmentType)
    setTimezone((current) => current || user.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone)
  }, [user])

  useEffect(() => {
    const state = onboarding.data
    if (!state) return
    const preferences = state.preferences
    setWorkingHoursStart(preferences.workingHoursStart?.slice(0, 5) || '09:00')
    setWorkingHoursEnd(preferences.workingHoursEnd?.slice(0, 5) || '17:00')
    setProductivityStyle(preferences.preferredProductivityStyle || 'BALANCED')
    setFocusMinutes(preferences.preferredFocusMinutes || 45)
    setBreakMinutes(preferences.breakMinutes ?? 10)
    setWeeklyHours(preferences.weeklyProductivityHours ?? 35)
    setAreas((current) => current || preferences.areasOfInterest.join(', '))
    setSkills((current) => current || preferences.currentSkills.join(', '))
    setPrimaryGoals((current) => current || preferences.primaryGoalAreas.join(', '))
    setFinancialGoals((current) => current || preferences.financialGoals.join(', '))
    setLearningGoals((current) => current || preferences.learningGoals.join(', '))
    setWeights((current) => {
      const incoming = preferences.lifeBalanceWeights
      if (!incoming || Object.keys(incoming).length === 0) return current
      return {
        ...current,
        ...BALANCE_DIMENSIONS.reduce<Record<string, number>>((acc, dimension) => {
          const value = incoming[dimension.key]
          if (typeof value === 'number') acc[dimension.key] = value
          return acc
        }, {}),
      }
    })
  }, [onboarding.data])

  const submit = async (skipped: boolean) => {
    setError(undefined)
    setBusy(true)
    try {
      if (skipped) {
        await settingsApi.skipOnboarding()
      } else {
        await settingsApi.submitOnboarding({
          name: name.trim() || undefined,
          occupation: occupation.trim() || undefined,
          employmentType,
          timezone: timezone.trim() || undefined,
          workingHoursStart,
          workingHoursEnd,
          preferredProductivityStyle: productivityStyle,
          preferredFocusMinutes: focusMinutes,
          breakMinutes,
          weeklyProductivityHours: weeklyHours,
          primaryGoals: parseList(primaryGoals),
          areasOfInterest: parseList(areas),
          currentSkills: parseList(skills),
          financialGoals: financialGoals.trim() || undefined,
          learningGoals: learningGoals.trim() || undefined,
          lifeBalanceWeights: weights,
          skipped: false,
        })
      }
      await refreshUser()
      toast.success(skipped ? 'Setup skipped' : 'Setup saved')
      navigate('/app', { replace: true })
    } catch (caught) {
      setError(toNormalisedError(caught).message)
    } finally {
      setBusy(false)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">
        <Loader2 className="size-5 animate-spin" aria-hidden />
      </div>
    )
  }

  const isLast = step === STEPS.length - 1

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-2xl flex-col justify-center gap-6 px-5 py-10">
      <header className="space-y-2">
        <p className="text-sm text-muted-foreground">Step {step + 1} of {STEPS.length}</p>
        <h1 className="text-2xl font-semibold tracking-tight">Set up LIFEOS</h1>
        <p className="text-sm text-muted-foreground">
          These answers tune day planning and the life balance score. Every field is optional and you can
          change them later in Settings.
        </p>
        <Progress value={((step + 1) / STEPS.length) * 100} className="mt-2" />
        <ol className="flex flex-wrap gap-2 pt-1 text-xs">
          {STEPS.map((label, index) => (
            <li
              key={label}
              className={
                index === step
                  ? 'font-medium text-primary'
                  : index < step
                    ? 'text-muted-foreground'
                    : 'text-muted-foreground/60'
              }
            >
              {index < step ? '✓ ' : ''}
              {label}
            </li>
          ))}
        </ol>
      </header>

      {error ? (
        <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      <div className="space-y-5 rounded-xl border bg-card p-5">
        {step === 0 ? (
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">What should we call you?</Label>
              <Input id="name" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="occupation">What do you do?</Label>
              <Input
                id="occupation"
                maxLength={120}
                placeholder="e.g. Software engineer"
                value={occupation}
                onChange={(e) => setOccupation(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Which best describes you?</Label>
              <Select value={employmentType} onValueChange={(value) => setEmploymentType(value as EmploymentType)}>
                <SelectTrigger>
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
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="timezone">Timezone</Label>
              <Input
                id="timezone"
                maxLength={64}
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                placeholder="Europe/London"
              />
              <p className="text-xs text-muted-foreground">
                Deadlines and reminders are shown in this zone.
              </p>
            </div>
          </div>
        ) : null}

        {step === 1 ? (
          <div className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="start">Working day starts</Label>
                <Input
                  id="start"
                  type="time"
                  value={workingHoursStart}
                  onChange={(e) => setWorkingHoursStart(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="end">Working day ends</Label>
                <Input
                  id="end"
                  type="time"
                  value={workingHoursEnd}
                  onChange={(e) => setWorkingHoursEnd(e.target.value)}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Preferred working style</Label>
              <div className="grid gap-2 sm:grid-cols-2">
                {PRODUCTIVITY_STYLES.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setProductivityStyle(option.value)}
                    className={`rounded-lg border p-3 text-left text-sm transition-colors ${
                      productivityStyle === option.value
                        ? 'border-primary bg-primary/10'
                        : 'hover:bg-secondary/60'
                    }`}
                  >
                    <span className="flex items-center gap-2 font-medium">
                      {productivityStyle === option.value ? <Check className="size-3.5" /> : null}
                      {option.label}
                    </span>
                    <span className="mt-0.5 block text-xs text-muted-foreground">{option.hint}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="focus">Focus block (min)</Label>
                <Input
                  id="focus"
                  type="number"
                  min={10}
                  max={180}
                  value={focusMinutes}
                  onChange={(e) => setFocusMinutes(Number(e.target.value))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="break">Break (min)</Label>
                <Input
                  id="break"
                  type="number"
                  min={0}
                  max={60}
                  value={breakMinutes}
                  onChange={(e) => setBreakMinutes(Number(e.target.value))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="weekly">Hours per week</Label>
                <Input
                  id="weekly"
                  type="number"
                  min={1}
                  max={80}
                  step={0.5}
                  value={weeklyHours}
                  onChange={(e) => setWeeklyHours(Number(e.target.value))}
                />
              </div>
            </div>
          </div>
        ) : null}

        {step === 2 ? (
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="areas">Areas you care about</Label>
              <Input
                id="areas"
                placeholder="Health, career, relationships"
                value={areas}
                onChange={(e) => setAreas(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">Separate with commas.</p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="skills">Current skills</Label>
              <Textarea
                id="skills"
                rows={2}
                placeholder="TypeScript, public speaking"
                value={skills}
                onChange={(e) => setSkills(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="goals">Main goal areas</Label>
              <Input
                id="goals"
                placeholder="Career, fitness, side project"
                value={primaryGoals}
                onChange={(e) => setPrimaryGoals(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="financial">Financial goals</Label>
              <Input
                id="financial"
                placeholder="Build a three-month emergency fund"
                value={financialGoals}
                onChange={(e) => setFinancialGoals(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="learning">Learning goals</Label>
              <Input
                id="learning"
                placeholder="Finish the system design course"
                value={learningGoals}
                onChange={(e) => setLearningGoals(e.target.value)}
              />
            </div>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="space-y-5">
            <div className="space-y-1.5">
              <Label htmlFor="weights">Weight each life dimension (0–100)</Label>
              <p className="text-xs text-muted-foreground">
                Dimensions you never use are excluded from your score instead of counting against you.
              </p>
            </div>
            {BALANCE_DIMENSIONS.map((dimension) => (
              <div key={dimension.key} className="space-y-1.5">
                <div className="flex items-center justify-between text-sm">
                  <Label htmlFor={`weight-${dimension.key}`}>{dimension.label}</Label>
                  <span className="tabular-nums text-muted-foreground">
                    {weights[dimension.key]}
                  </span>
                </div>
                <input
                  id={`weight-${dimension.key}`}
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={weights[dimension.key]}
                  onChange={(e) =>
                    setWeights((current) => ({ ...current, [dimension.key]: Number(e.target.value) }))
                  }
                  className="w-full accent-[var(--color-primary)]"
                />
              </div>
            ))}
          </div>
        ) : null}
      </div>

      <footer className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="ghost" onClick={() => void submit(true)} disabled={busy}>
          Skip for now
        </Button>
        <div className="flex items-center gap-2">
          {step > 0 ? (
            <Button variant="outline" onClick={() => setStep((current) => current - 1)} disabled={busy}>
              Back
            </Button>
          ) : null}
          {isLast ? (
            <Button onClick={() => void submit(false)} loading={busy}>
              Finish setup <ArrowRight />
            </Button>
          ) : (
            <Button onClick={() => setStep((current) => current + 1)}>Continue</Button>
          )}
        </div>
      </footer>
    </div>
  )
}
