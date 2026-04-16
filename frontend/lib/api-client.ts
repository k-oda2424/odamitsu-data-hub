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

  // 204 No Content、または Content-Length=0 / ボディ空 のときは undefined を返す。
  // Spring の ResponseEntity.ok().build() は HTTP 200 + 空ボディを返すため、
  // 200 かつ空を明示的にハンドルしないと response.json() が parse エラーを throw する。
  //
  // 注意: 非 void 型ジェネリクス (例: `api.get<User>('/foo')`) を呼ぶ側は、
  // 空ボディを受ける可能性がある API では `api.get<User | undefined>(...)` を明示するか、
  // DELETE 等の空応答を期待する API 用の `api.delete` を使うこと。
  // 型では undefined を保証できないため、ランタイムで undefined 返却される。
  if (response.status === 204) {
    return undefined as unknown as T
  }
  const contentLength = response.headers.get('Content-Length')
  if (contentLength === '0') {
    return undefined as unknown as T
  }
  const text = await response.text()
  if (text.length === 0) {
    return undefined as unknown as T
  }
  return JSON.parse(text) as T
}

async function uploadForm<T>(endpoint: string, formData: FormData): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  // Content-Type は fetch が multipart boundary を自動設定するため指定しない
  const response = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers,
    body: formData,
  })

  if (response.status === 401) {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({ message: response.statusText }))
    throw new ApiError(response.status, errorBody.message || response.statusText)
  }

  return response.json()
}

/**
 * バイナリレスポンスを取得する（PDF, Excel 等）。
 * Content-Disposition からファイル名を抽出して { blob, filename } を返す。
 */
async function downloadBlob(endpoint: string, method: 'GET' | 'POST' = 'GET'): Promise<{ blob: Blob; filename: string | null; headers: Headers }> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}${endpoint}`, { method, headers })

  if (response.status === 401) {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    // JSON エラー本文があれば message を取り出す
    let message = response.statusText
    try {
      const body = await response.clone().json()
      if (body && typeof body.message === 'string') message = body.message
    } catch {
      // ignore
    }
    throw new ApiError(response.status, message)
  }

  const blob = await response.blob()

  // Content-Disposition から filename を抽出（filename*= 優先, fallback で filename=）
  const disposition = response.headers.get('Content-Disposition')
  let filename: string | null = null
  if (disposition) {
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/)
    if (utf8Match) {
      filename = decodeURIComponent(utf8Match[1])
    } else {
      const plainMatch = disposition.match(/filename="?([^";]+)"?/)
      if (plainMatch) {
        filename = plainMatch[1]
      }
    }
  }

  return { blob, filename, headers: response.headers }
}

export const api = {
  get: <T>(endpoint: string) => request<T>(endpoint),
  post: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'POST', body }),
  put: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'PUT', body }),
  patch: <T>(endpoint: string, body?: unknown) => request<T>(endpoint, { method: 'PATCH', body }),
  delete: (endpoint: string) => request<void>(endpoint, { method: 'DELETE' }),
  deleteWithResponse: <T>(endpoint: string) => request<T>(endpoint, { method: 'DELETE' }),
  postForm: <T>(endpoint: string, formData: FormData) => uploadForm<T>(endpoint, formData),
  download: (endpoint: string) => downloadBlob(endpoint),
  downloadPost: (endpoint: string) => downloadBlob(endpoint, 'POST'),
}

export { ApiError }
