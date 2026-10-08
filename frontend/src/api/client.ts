import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

import type { ApiError, ApiErrorField } from '@/types/api'

const ACCESS_TOKEN_KEY = 'lifeos.accessToken'
const REFRESH_TOKEN_KEY = 'lifeos.refreshToken'

const baseURL = import.meta.env.VITE_API_BASE_URL?.replace(/\/+$/, '') ?? ''

export const tokenStore = {
  get access(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  },
  get refresh(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, access)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: ApiErrorField[]
  readonly requestId: string

  constructor(status: number, payload: Partial<ApiError> | null, fallback: string) {
    super(payload?.message || fallback)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = payload?.code ?? 'UNKNOWN'
    this.fieldErrors = payload?.fieldErrors ?? []
    this.requestId = payload?.requestId ?? ''
  }

  /** Maps `fieldErrors` to a `{ field: message }` record for form display. */
  toFieldMap(): Record<string, string> {
    return this.fieldErrors.reduce<Record<string, string>>((acc, item) => {
      if (item.field && !acc[item.field]) acc[item.field] = item.message
      return acc
    }, {})
  }
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler = () => {}
export function onUnauthorized(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler
}

/**
 * Serialises refresh attempts. Concurrent 401s share a single refresh call instead of firing one
 * request each, and the original requests are replayed once the new access token is available.
 */
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) throw new Error('No refresh token stored')

  const { data } = await axios.post<{ accessToken: string; refreshToken: string }>(
    `${baseURL}/api/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  )
  tokenStore.set(data.accessToken, data.refreshToken)
  return data.accessToken
}

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 30_000,
  headers: { Accept: 'application/json' },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.access
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const isUnauthorized = error.response?.status === 401
    const isRefreshCall = original?.url?.includes('/api/auth/refresh')
    const isAuthEndpoint = original?.url?.includes('/api/auth/login')
    const isRegister = original?.url?.includes('/api/auth/register')

    if (isUnauthorized && original && !original._retried && !isRefreshCall && !isAuthEndpoint && !isRegister) {
      original._retried = true
      try {
        refreshPromise = refreshPromise ?? refreshAccessToken()
        const accessToken = await refreshPromise
        original.headers = {
          ...(original.headers ?? {}),
          Authorization: `Bearer ${accessToken}`,
        }
        return await http.request(original)
      } catch {
        tokenStore.clear()
        unauthorizedHandler()
        return Promise.reject(toApiRequestError(error))
      } finally {
        refreshPromise = null
      }
    }

    if (isUnauthorized && original?.url?.includes('/api/auth/refresh')) {
      tokenStore.clear()
      unauthorizedHandler()
    }

    return Promise.reject(toApiRequestError(error))
  },
)

function toApiRequestError(error: AxiosError<ApiError>): ApiRequestError {
  if (error.response) {
    return new ApiRequestError(
      error.response.status,
      error.response.data,
      error.response.statusText || 'Request failed',
    )
  }
  if (error.code === 'ECONNABORTED') {
    return new ApiRequestError(0, { code: 'TIMEOUT', message: 'The request timed out. Try again.' }, 'Timeout')
  }
  return new ApiRequestError(
    0,
    { code: 'NETWORK_ERROR', message: 'Cannot reach the server. Check your connection.' },
    'Network error',
  )
}

type Query = object

/**
 * Drops empty values and expands arrays into repeated parameters, which is what the Spring MVC
 * controller binding expects for `List<T>` query parameters.
 */
export function toSearchParams(query: Query = {}): URLSearchParams {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      const entries = value.filter((item) => item !== undefined && item !== null && item !== '')
      if (entries.length === 0) continue
      params.set(key, entries.join(','))
      continue
    }
    params.set(key, String(value))
  }
  return params
}

export async function get<T>(url: string, query?: Query, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.get<T>(url, { params: toSearchParams(query), ...config })
  return response.data
}

export async function post<T>(
  url: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await http.post<T>(url, body, config)
  return response.data
}

export async function put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await http.put<T>(url, body, config)
  return response.data
}

export async function patch<T>(
  url: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const response = await http.patch<T>(url, body, config)
  return response.data
}

export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T | void> {
  const response = await http.delete<T>(url, config)
  return response.data
}

/**
 * `validateStatus` that also accepts 204, for endpoints that answer 204 in some flows and 200 with a
 * body in others. Spring returns 204 for a delete with no body and 200 when a body is written.
 */
export const okOrNoContent = {
  validateStatus: (status: number) => status === 200 || status === 204,
}

/** Downloads a raw file response through the authenticated client. */
export async function download(url: string, fileName: string): Promise<void> {
  const response = await http.get(url, { responseType: 'blob' })
  const blobUrl = URL.createObjectURL(response.data as Blob)
  const anchor = document.createElement('a')
  anchor.href = blobUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(blobUrl)
}
