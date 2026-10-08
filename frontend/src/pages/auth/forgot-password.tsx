import { useMutation } from '@tanstack/react-query'
import { MailCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { authApi } from '@/api/endpoints'
import { toNormalisedError } from '@/hooks/use-api-error'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthLayout, FieldError, FormError } from './auth-layout'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const mutation = useMutation({
    mutationFn: () => authApi.forgotPassword({ email: email.trim() }),
    onSuccess: () => setSent(true),
    onError: (caught) => {
      const normalised = toNormalisedError(caught)
      setError(normalised.message)
      setFieldErrors(normalised.fieldErrors)
    },
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setError(undefined)
    setFieldErrors({})
    mutation.mutate()
  }

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We will email a single-use link if the address is registered."
      footer={
        <p>
          Remembered it?{' '}
          <Link to="/login" className="text-primary hover:underline">
            Back to sign in
          </Link>
        </p>
      }
    >
      {sent ? (
        <div className="space-y-4">
          <div className="flex gap-3 rounded-lg border border-success/40 bg-success/10 p-4 text-sm">
            <MailCheck className="size-5 shrink-0 text-success" aria-hidden />
            <p>
              If an account exists for <span className="font-medium">{email.trim()}</span>, a reset link
              is on its way. The link expires shortly and can only be used once.
            </p>
          </div>
          <p className="text-sm text-muted-foreground">
            We report the same outcome whether or not the address is registered, so this page cannot be
            used to discover which emails exist.
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <FormError message={error} />
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
          <Button type="submit" className="w-full" loading={mutation.isPending}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
