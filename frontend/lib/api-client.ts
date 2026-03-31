const API_BASE = '/api/v1'

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem('token')
}

async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const token = getToken()
  const { body, headers: customHeaders, ...rest } = options

  const headers: Record<string, string> = {}

  if (customHeaders) {
    if (customHeaders instanceof Headers) {
      customHeaders.forEach((value, key) => {
        headers[key] = value
      })
    } else if (typeof customHeaders === 'object') {
      Object.assign(headers, customHeaders)
    }
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...rest,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401) {
    if (!endpoint.startsWith('/auth/login')) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    const errorBody = await response.text()
    throw new ApiError(response.status, errorBody || response.statusText)
  }

  if (response.status === 204) {
    return undefined as unknown as T
  }

  return response.json()
}

export const api = {
  get: <T>(endpoint: string) => request<T>(endpoint),
  post: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'POST', body }),
  put: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'PUT', body }),
  delete: (endpoint: string) => request<void>(endpoint, { method: 'DELETE' }),
}

export { ApiError }
