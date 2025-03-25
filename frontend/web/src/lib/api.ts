const TOKEN_KEY = 'ticketbox.token'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...init, headers })

  if (!response.ok) {
    let message = '请求失败，请稍后重试'
    let code: string | undefined
    try {
      const problem = await response.json()
      if (typeof problem?.detail === 'string') {
        message = problem.detail
      }
      if (typeof problem?.code === 'string') {
        code = problem.code
      }
    } catch {
      // Non-JSON error response; keep the generic message.
    }
    if (response.status === 401 && path !== '/api/auth/login') {
      clearToken()
    }
    throw new ApiError(message, response.status, code)
  }

  return response.json() as Promise<T>
}

export interface Profile {
  username: string
  role: 'USER' | 'ADMIN'
}

export interface LoginResult extends Profile {
  token: string
}

export function register(username: string, password: string) {
  return request<{ id: number; username: string; role: string }>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function login(username: string, password: string) {
  return request<LoginResult>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function me() {
  return request<Profile>('/api/me')
}

export interface EventSummary {
  id: number
  title: string
  city: string
  coverUrl: string | null
}

export interface EventDetail extends EventSummary {
  description: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export function listEvents(params: { city?: string; page?: number; size?: number }) {
  const search = new URLSearchParams()
  if (params.city) {
    search.set('city', params.city)
  }
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 12))
  return request<PageResult<EventSummary>>(`/api/events?${search.toString()}`)
}

export function getEvent(id: number) {
  return request<EventDetail>(`/api/events/${id}`)
}
