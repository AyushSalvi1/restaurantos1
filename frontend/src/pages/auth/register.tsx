import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { toNormalisedError } from '@/hooks/use-api-error'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { passwordProblem, useAuth } from '@/stores/auth'
import { AuthLayout, FieldError, FormError } from './auth-layout'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const localPasswordError = password && passwordProblem(password) ? passwordProblem(password) : undefined
  const confirmError = confirm && confirm !== password ? 'The two passwords do not match.' : undefined

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(undefined)
    setFieldErrors({})

    const problem = passwordProblem(password)
    if (problem) {
      setFieldErrors({ password: problem })
      return
    }
    if (confirm !== password) {
      setFieldErrors({ confirm: 'The two passwords do not match.' })
      return
    }

    setSubmitting(true)
    try {
      const user = await register({ email: email.trim(), password, fullName: fullName.trim() })
      navigate(user.onboardingCompleted ? '/app' : '/onboarding', { replace: true })
    } catch (caught) {
      const normalised = toNormalisedError(caught)
      setError(normalised.message)
      setFieldErrors(normalised.fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Set up your personal operating system in under a minute."
      footer={
        <p>
          Already registered?{' '}
          <Link to="/login" className="text-primary hover:underline">
            Sign in
          </Link>
        </p>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <FormError message={error} />

        <div className="space-y-1.5">
          <Label htmlFor="fullName">Full name</Label>
          <Input
            id="fullName"
            autoComplete="name"
            required
            maxLength={120}
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            aria-invalid={Boolean(fieldErrors.fullName)}
          />
          <FieldError message={fieldErrors.fullName} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-invalid={Boolean(fieldErrors.email)}
          />
          <FieldError message={fieldErrors.email} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(localPasswordError ?? fieldErrors.password)}
            aria-describedby="password-hint"
          />
          <p id="password-hint" className="text-xs text-muted-foreground">
            At least 10 characters, including a letter and a digit.
          </p>
          <FieldError message={localPasswordError ?? fieldErrors.password} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="confirm">Confirm password</Label>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            required
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
            aria-invalid={Boolean(confirmError ?? fieldErrors.confirm)}
          />
          <FieldError message={confirmError ?? fieldErrors.confirm} />
        </div>

        <Button type="submit" className="w-full" loading={submitting}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}
