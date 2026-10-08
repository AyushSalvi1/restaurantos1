import { useQueryClient } from '@tanstack/react-query'

import { ApiRequestError } from '@/api/client'

/**
 * Normalises thrown values into a message plus a per-field map, so every form renders validation
 * problems from `ApiRequestError.fieldErrors` without re-deriving the parsing rules.
 */
export interface NormalisedError {
  message: string
  status: number
  fieldErrors: Record<string, string>
}

export function toNormalisedError(error: unknown): NormalisedError {
  if (error instanceof ApiRequestError) {
    return {
      message: error.message,
      status: error.status,
      fieldErrors: error.toFieldMap(),
    }
  }
  if (error instanceof Error) {
    return { message: error.message, status: 0, fieldErrors: {} }
  }
  return { message: 'Something went wrong. Try again.', status: 0, fieldErrors: {} }
}

export function useQueryInvalidation() {
  const client = useQueryClient()
  return {
    invalidate: (...keys: readonly unknown[][]) =>
      Promise.all(keys.map((key) => client.invalidateQueries({ queryKey: key }))),
    reset: () => client.clear(),
  }
}
