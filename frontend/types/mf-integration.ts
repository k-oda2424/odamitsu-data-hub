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

export type MfEnumKind = 'FINANCIAL_STATEMENT' | 'CATEGORY'

export const MF_ENUM_KIND_LABELS: Record<MfEnumKind, string> = {
  FINANCIAL_STATEMENT: '財務諸表区分',
  CATEGORY: '大分類',
}

export interface MfEnumTranslation {
  id: number | null
  enumKind: MfEnumKind
  englishCode: string
  japaneseName: string
}

export interface MfAccountSyncSample {
  accountName: string
  subAccountName: string | null
  category: string | null
  taxClassification: string | null
}

export interface MfAccountSyncFieldDiff {
  accountName: string
  subAccountName: string | null
  changes: string
}

export interface MfAccountSyncResult {
  applied: boolean
  insertCount: number
  updateCount: number
  deleteCount: number
  insertSamples: MfAccountSyncSample[]
  updateSamples: MfAccountSyncFieldDiff[]
  deleteSamples: MfAccountSyncSample[]
  unknownEnums: string[]
}

export interface MfLocalRowSummary {
  source: string
  shopNo: number | null
  partyNo: number | null
  partyCode: string | null
  partyName: string | null
  taxRate: number | null
  amount: number
  note: string | null
  unmatched: boolean
}

export interface MfBranchSummary {
  journalId: string
  journalNumber: number | null
  transactionDate: string | null
  debitAccount: string | null
  debitSubAccount: string | null
  creditAccount: string | null
  creditSubAccount: string | null
  tradePartnerName: string | null
  taxName: string | null
  amount: number | null
  enteredBy: string | null
  unmatched: boolean
}

export interface MfReconcileRow {
  kind: JournalKind
  kindLabel: string
  localCount: number
  localAmount: number
  mfCount: number
  mfAmount: number
  countDiff: number
  amountDiff: number
  matched: boolean
  localItems: MfLocalRowSummary[]
  mfItems: MfBranchSummary[]
}

export interface MfUnclassifiedBreakdown {
  debitAccount: string | null
  creditAccount: string | null
  count: number
  totalAmount: number
}

export interface MfReconcileReport {
  transactionMonth: string
  fetchedAt: string
  rows: MfReconcileRow[]
  mfUnknownBranchCount: number
  unclassified: MfUnclassifiedBreakdown[]
}

// ---- Phase B: 残高突合 (design-supplier-partner-ledger-balance.md §5) ----

export interface MfPayableBalance {
  /** MF 試算表 買掛金 closing_balance。 */
  mfClosing: number
  /** 自社 summary 全 row の closing 合計 (mf_export_enabled 問わず)。 */
  selfClosingAll: number
  /** MF 突合対象 row のみ (mfExportEnabled=true OR verifiedManually=true) の closing 合計。 */
  selfClosingForMf: number
  /** mfClosing - selfClosingAll。 */
  diffAll: number
  /** mfClosing - selfClosingForMf (メイン突合指標)。 */
  diffForMf: number
  selfRowCount: number
  selfMfTargetRowCount: number
  /** MF 試算表に「買掛金」account が見つかったか。 */
  mfAccountFound: boolean
}

export interface MfBalanceReconcileReport {
  period: string // yyyy-MM-dd
  fetchedAt: string
  mfEndDate: string
  payable: MfPayableBalance
}

// Phase 1 (仕訳突合) で必要な scope:
// - mfc/accounting/journal.read: 仕訳取得
// - mfc/accounting/accounts.read: 勘定科目マスタ（将来的な mf_account_master CSV 取込自動化用）
// - mfc/accounting/offices.read: 事業者情報
// - mfc/accounting/taxes.read: 税区分マスタ
// Phase 2 (試算表/残高突合, design-supplier-partner-ledger-balance.md §3) で追加:
// - mfc/accounting/report.read: 試算表 (trial_balance_bs) 取得
// スコープは space 区切り。scope 追加時は画面で「クライアント設定」更新保存 → 「再認証」が必要。
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
  scope: 'mfc/accounting/journal.read mfc/accounting/accounts.read mfc/accounting/offices.read mfc/accounting/taxes.read mfc/accounting/report.read',
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}
