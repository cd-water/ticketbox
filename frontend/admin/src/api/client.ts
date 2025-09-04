const TOKEN_KEY = 'ticketbox.admin.token'

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
  if (init.body && !(init.body instanceof FormData)) {
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

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface AdminEvent {
  id: number
  title: string
  description: string
  city: string
  coverObjectKey: string | null
  coverUrl: string | null
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
}

export interface EventInput {
  title: string
  description: string
  city: string
  coverObjectKey?: string | null
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

export function listEvents(page: number, size: number) {
  return request<PageResult<AdminEvent>>(`/api/admin/events?page=${page}&size=${size}`)
}

export function createEvent(input: EventInput) {
  return request<AdminEvent>('/api/admin/events', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateEvent(id: number, input: EventInput) {
  return request<AdminEvent>(`/api/admin/events/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function publishEvent(id: number) {
  return request<AdminEvent>(`/api/admin/events/${id}/publish`, { method: 'POST' })
}

export function unpublishEvent(id: number) {
  return request<AdminEvent>(`/api/admin/events/${id}/unpublish`, { method: 'POST' })
}

export function uploadFile(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<{ key: string }>('/api/admin/files', { method: 'POST', body: form })
}

export function getEvent(id: number) {
  return request<AdminEvent>(`/api/admin/events/${id}`)
}

export interface PriceTier {
  id: number
  name: string
  price: number
  stock: number
}

export type SeatingMode = 'SEATED' | 'GENERAL_ADMISSION'

export interface Performance {
  id: number
  startsAt: string
  venueName: string
  venueAddress: string
  saleStartAt: string
  seatingMode: SeatingMode
  waitingRoomEnabled: boolean
  admissionRate: number
  tokenTtlSeconds: number
  priceTiers: PriceTier[]
}

export interface WaitingRoomInput {
  enabled: boolean
  admissionRate: number
  tokenTtlSeconds: number
}

export interface SectionInput {
  name: string
  priceTierId: number
  rowCount: number
  seatsPerRow: number
}

export interface SectionSummary {
  id: number
  name: string
  priceTier: PriceTier
  rowCount: number
  seatsPerRow: number
  seatCount: number
}

export interface PerformanceInput {
  eventId: number
  startsAt: string
  venueName: string
  venueAddress: string
  saleStartAt: string
}

export interface PriceTierInput {
  performanceId: number
  name: string
  price: number
  stock: number
}

export interface PerformanceUpdateInput {
  startsAt: string
  venueName: string
  venueAddress: string
  saleStartAt: string
}

export interface PriceTierUpdateInput {
  name: string
  price: number
  stock: number
}

export function listPerformances(eventId: number) {
  return request<Performance[]>(`/api/admin/performances?eventId=${eventId}`)
}

export function createPerformance(input: PerformanceInput) {
  return request<Performance>('/api/admin/performances', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePerformance(id: number, input: PerformanceUpdateInput) {
  return request<Performance>(`/api/admin/performances/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function createPriceTier(input: PriceTierInput) {
  return request<PriceTier>('/api/admin/price-tiers', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updatePriceTier(id: number, input: PriceTierUpdateInput) {
  return request<PriceTier>(`/api/admin/price-tiers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function listSections(performanceId: number) {
  return request<SectionSummary[]>(`/api/admin/performances/${performanceId}/sections`)
}

export function generateSection(performanceId: number, input: SectionInput) {
  return request<SectionSummary>(`/api/admin/performances/${performanceId}/sections`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateWaitingRoom(performanceId: number, input: WaitingRoomInput) {
  return request<Performance>(`/api/admin/performances/${performanceId}/waiting-room`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export interface AdminOrderItem {
  seatId: number | null
  rowNo: number | null
  seatNo: number | null
  sectionName: string | null
  tierName: string | null
  quantity: number
  unitPrice: number
}

export interface AdminTicket {
  id: number
  seatId: number | null
  rowNo: number | null
  seatNo: number | null
  sectionName: string | null
  tierName: string | null
  attendeeName: string
  attendeeIdNo: string
  status: string
  qrPayload: string
}

export interface AdminOrder {
  id: number
  orderNo: string
  status: string
  totalAmount: number
  expiresAt: string
  createdAt: string
  eventId: number | null
  eventTitle: string | null
  startsAt: string | null
  venueName: string | null
  items: AdminOrderItem[]
  tickets: AdminTicket[]
}

export interface AdminOrderEntry {
  username: string
  order: AdminOrder
}

export interface AdminOrderSummary {
  username: string
  id: number
  orderNo: string
  status: string
  totalAmount: number
  createdAt: string
  eventId: number | null
  eventTitle: string | null
  startsAt: string | null
  venueName: string | null
}

export function listAdminOrders(params: {
  status?: string
  performanceId?: number
  page?: number
  size?: number
}) {
  const search = new URLSearchParams()
  if (params.status) {
    search.set('status', params.status)
  }
  if (params.performanceId) {
    search.set('performanceId', String(params.performanceId))
  }
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 20))
  return request<PageResult<AdminOrderSummary>>(`/api/admin/orders?${search.toString()}`)
}

export function getAdminOrder(id: number) {
  return request<AdminOrderEntry>(`/api/admin/orders/${id}`)
}

export interface CheckInResult {
  ticketId: number
  attendeeName: string
  attendeeIdNo: string
  tierName: string | null
  sectionName: string | null
  rowNo: number | null
  seatNo: number | null
  status: string
  checkedInAt: string
  eventTitle: string | null
  startsAt: string | null
  venueName: string | null
}

export function checkIn(payload: string) {
  return request<CheckInResult>('/api/admin/tickets/check-in', {
    method: 'POST',
    body: JSON.stringify({ payload }),
  })
}
