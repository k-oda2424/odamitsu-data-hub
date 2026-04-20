export interface MfOauthClientResponse {
  id: number | null
  clientId: string | null
  clientSecretConfigured: boolean
  redirectUri: string | null
  scope: string | null
  authorizeUrl: string | null
  tokenUrl: string | null
  apiBaseUrl: string | null
}

export interface MfOauthClientRequest {
  clientId: string
  /** 新規登録時は必須、更新時は空で既存値を維持。 */
  clientSecret?: string
  redirectUri: string
  scope: string
  authorizeUrl: string
  tokenUrl: string
  apiBaseUrl: string
}

export interface MfTokenStatus {
  configured: boolean
  connected: boolean
  expiresAt: string | null
  scope: string | null
  lastRefreshedAt: string | null
  reAuthRequired: boolean
}

export type JournalKind = 'PURCHASE' | 'SALES' | 'PAYMENT'

export const JOURNAL_KIND_LABELS: Record<JournalKind, string> = {
  PURCHASE: '仕入仕訳',
  SALES: '売上仕訳',
  PAYMENT: '買掛支払',
}

// Phase 1 (仕訳突合) で必要な scope:
// - mfc/accounting/journal.read: 仕訳取得
// - mfc/accounting/accounts.read: 勘定科目マスタ（将来的な mf_account_master CSV 取込自動化用）
// - mfc/accounting/offices.read: 事業者情報
// スコープは space 区切り。
//
// (F-6) redirectUri のデフォルトは利用環境によって異なる:
//  - dev: `${window.location.origin}/finance/mf-integration/callback`
//  - prod: 同上 (同一オリジン)
// ハードコードはやめて、実行時にブラウザから組み立てる。SSR では window が無いため
// フォールバック文字列を返し、クライアント側 useEffect 等で上書きする想定。
export const MF_CALLBACK_PATH = '/finance/mf-integration/callback'

export function defaultMfRedirectUri(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin + MF_CALLBACK_PATH
  }
  // SSR / build 時のフォールバック (実際には runtime で上書きされる想定)
  return MF_CALLBACK_PATH
}

export const MF_DEFAULT_CONFIG: Omit<MfOauthClientRequest, 'clientId' | 'clientSecret'> = {
  redirectUri: defaultMfRedirectUri(),
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read',
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}
