const API_BASE = '/api/v1'

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

/**
 * API 呼び出し時に発生するエラー。
 * G3-M12 (2026-05-06): backend の {@code FinanceExceptionHandler} 等が返す
 * {@code ErrorResponse} JSON ({@code { message, code, timestamp, ... }}) を
 * 全エントリポイント (request / uploadForm / downloadBlob) で parse し、
 * - {@code message}  : ユーザー向け業務メッセージ (UI toast 用)
 * - {@code code}     : 業務エラー code (例: MF_TENANT_MISMATCH, PER_SUPPLIER_MISMATCH) — 分岐用
 * - {@code body}     : parse 済 JSON 全体 (詳細フィールド参照用)
 * - {@code bodyText} : raw text body (parse 失敗 / debug 用)
 *
 * 旧実装では request() のみ raw text を message に詰めていたため、JSON が
 * そのまま toast に表示される問題があった。本クラスは uploadForm/downloadBlob と
 * 揃えた JSON parse パターンで業務メッセージを抽出する。
 */
class ApiError extends Error {
  status: number
  code?: string
  body?: unknown
  bodyText?: string

  constructor(
    status: number,
    message: string,
    code?: string,
    body?: unknown,
    bodyText?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.body = body
    this.bodyText = bodyText
  }
}

function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem('token')
}

/**
 * JSON エラー本文 (text) を parse し、{message, code, body} を抽出する共通ヘルパ。
 * - 空文字列ならフォールバック message のみ返す
 * - JSON parse 失敗時は raw text を message として返す
 * - JSON 内に message/code が無ければ fallback / undefined
 */
function parseErrorBody(
  text: string | undefined,
  fallbackMessage: string,
): { message: string; code?: string; body?: unknown } {
  if (!text) return { message: fallbackMessage }
  try {
    const parsed = JSON.parse(text) as { message?: unknown; code?: unknown }
    const message = typeof parsed?.message === 'string' && parsed.message.length > 0
      ? parsed.message
      : fallbackMessage
    const code = typeof parsed?.code === 'string' ? parsed.code : undefined
    return { message, code, body: parsed }
  } catch {
    // not JSON: raw text を message として使う (短い text のみ; HTML 等は fallback に倒す)
    const trimmed = text.trim()
    if (trimmed.length === 0) return { message: fallbackMessage }
    // HTML や巨大本文は fallback。1000 文字超 / `<` で始まる場合は fallback。
    if (trimmed.length > 1000 || trimmed.startsWith('<')) {
      return { message: fallbackMessage }
    }
    return { message: trimmed }
  }
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
    // G3-M12: JSON エラー本文 ({message, code, ...}) を parse して ApiError に格納する。
    // backend の FinanceBusinessException 由来 message が UI で raw JSON 表示されないようにするため。
    const text = await response.text()
    const { message, code, body: parsedBody } = parseErrorBody(text, response.statusText)
    throw new ApiError(response.status, message, code, parsedBody, text || undefined)
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
    // G3-M12: request() と同じ parse パターンに統一。code/body/bodyText も格納する。
    const text = await response.text()
    const { message, code, body: parsedBody } = parseErrorBody(text, response.statusText)
    throw new ApiError(response.status, message, code, parsedBody, text || undefined)
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
    // G3-M12: code/bodyText も格納するよう統一。バイナリ期待のため text() 自体が
    // 失敗する可能性 (binary stream の途中で abort 等) も考慮し try/catch する。
    let bodyText: string | undefined
    let parsed: { message: string; code?: string; body?: unknown } = {
      message: response.statusText,
    }
    try {
      const text = await response.clone().text()
      bodyText = text || undefined
      parsed = parseErrorBody(text, response.statusText)
    } catch {
      // ignore: body 取得失敗時は statusText のみ
    }
    throw new ApiError(response.status, parsed.message, parsed.code, parsed.body, bodyText)
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
