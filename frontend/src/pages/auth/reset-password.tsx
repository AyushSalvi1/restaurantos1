import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { authApi } from '@/api/endpoints'
import { toNormalisedError } from '@/hooks/use-api-error'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { passwordProblem } from '@/stores/auth'
import { AuthLayout, FieldError, FormError } from './auth-layout'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: () => authApi.resetPassword({ token, newPassword: password }),
    onSuccess: () => navigate('/login', { replace: true }),
    onError: (caught) => {
      const normalised = toNormalisedError(caught)
      setError(normalised.message)
      setFieldErrors(normalised.fieldErrors)
    },
  })

  const localPasswordError = password ? (passwordProblem(password) ?? undefined) : undefined
  const confirmError = confirm && confirm !== password ? 'The two passwords do not match.' : undefined

  const submit = (event: FormEvent) => {
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
    mutation.mutate()
  }

  if (!token) {
    return (
      <AuthLayout
        title="Reset link missing"
        subtitle="Open the link from your email, or request a new one."
        footer={
          <p>
            <Link to="/forgot-password" className="text-primary hover:underline">
              Request a new link
            </Link>
          </p>
        }
      >
        <p className="text-sm text-muted-foreground">
          This page needs the single-use token from the reset email. Without it the server cannot verify
          who is making the change.
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="Setting a new password invalidates your other sessions."
      footer={
        <p>
          <Link to="/login" className="text-primary hover:underline">
            Back to sign in
          </Link>
        </p>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <FormError message={error} />

        <div className="space-y-1.5">
          <Label htmlFor="password">New password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(localPasswordError ?? fieldErrors.password)}
          />
          <p className="text-xs text-muted-foreground">
            At least 10 characters, including a letter and a digit.
          </p>
          <FieldError message={localPasswordError ?? fieldErrors.password} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="confirm">Confirm new password</Label>
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

        <Button type="submit" className="w-full" loading={mutation.isPending}>
          Update password
        </Button>
      </form>
    </AuthLayout>
  )
}
