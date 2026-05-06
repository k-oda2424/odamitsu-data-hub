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
  /** バインド済 MF tenant id (P1-01)。NULL なら未バインド (旧データ互換、次回 callback で確定)。 */
  mfTenantId: string | null
  /** バインド済 MF tenant 名 (P1-01、UI 表示用)。 */
  mfTenantName: string | null
  /** tenant binding 確定時刻 ISO-8601 (P1-01)。 */
  tenantBoundAt: string | null
  /**
   * refresh_token の真の発行日 ISO-8601 (G1-M4 / P1-04 案 α)。
   * <p>
   * rotation 動作時 = 現 active row の発行日、rotation OFF (旧 token 流用) 時 = 旧 row の値を継承。
   * これにより rotation 設定に依らず 540 日寿命カウントが正確になる。
   */
  refreshTokenIssuedAt: string | null
  /**
   * 540 日 - 経過日数。≤ 0 で失効、≤ 60 で予兆 banner 表示 (P1-04 案 α)。
   * 期限超過時は 0 にクランプ (`reAuthExpired=true` で識別)。未接続時 null。
   */
  daysUntilReauth: number | null
  /**
   * T6 (2026-05-04): 必須だが現 scope 設定に含まれていない scope 一覧。
   * 要素ありの場合、関連 API が 403 で失敗する可能性あり。`MfScopeBanner` で警告表示。
   * Backend `MfScopeConstants.REQUIRED_SCOPES` と同期 (=`MF_REQUIRED_SCOPES` 定数)。
   */
  missingScopes: string[]
  /**
   * T6: 現 scope に含まれているが必須ではない scope 一覧。動作影響なし、管理上の指標。
   */
  extraScopes: string[]
  /** T6: missingScopes.isEmpty() の short-hand。false なら `MfScopeBanner` を表示。 */
  scopeOk: boolean
  /**
   * G1-M4 (2026-05-06): refresh_token が 540 日寿命を超過した = 既に再認可必須の状態。
   * `MfReAuthBanner` で最上位 severity (destructive、期限超過メッセージ) として表示する独立フラグ。
   * `reAuthRequired` は ≤ 0 で立つが、`reAuthExpired` は **負値 (= 既に超過)** の状態のみを示す。
   */
  reAuthExpired: boolean
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
  /** MF 突合対象 row のみ (mfExportEnabled=true OR verifiedManually=true OR isPaymentOnly=true) の closing 合計。 */
  selfClosingForMf: number
  /** mfClosing - selfClosingAll。 */
  diffAll: number
  /** mfClosing - selfClosingForMf (メイン突合指標、期首残込み)。 */
  diffForMf: number
  selfRowCount: number
  selfMfTargetRowCount: number
  /** MF 試算表に「買掛金」account が見つかったか。 */
  mfAccountFound: boolean
  /** MF 期首残 = 自社 backfill 起点の前月末時点の MF 買掛金 closing (Phase B'' 追加)。 */
  mfOpeningBalance: number
  /** mfOpeningBalance を取得した MF 基準日 (yyyy-MM-dd)。 */
  openingReferenceDate: string
  /**
   * 期首残調整後の差分 = mfClosing - mfOpeningBalance - selfClosingForMf。
   * 2025-06-20 以降の累積のみで比較した純粋な乖離。
   * 残差は 2025-06〜2025-12 の verified_amount 欠落 (過去振込明細未取込) が主因。
   */
  diffForMfAdjusted: number
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

// SF-16: scope は配列で定義し、検証 (含有チェック) や追加修正をしやすくする。
// space 区切り文字列の手書きはタイポが入りやすく、追加忘れが原因で API エラーが起きるため。
//
// P1-01 (DD-F-04): mfc/admin/tenant.read は MF tenant API (/v2/tenant) 呼び出しに必須。
// 別会社 MF への誤接続検知のため、scope 追加 → 「クライアント設定」更新保存 → 「再認証」が必要。
//
// T6 (2026-05-04): backend `MfScopeConstants.REQUIRED_SCOPES` と必ず同期させること。
// 必須 scope を増減する場合は両方を同時更新し、関連 Javadoc/コメントも合わせて修正する。
// backend `MfOauthService.getStatus()` がこの一覧と DB scope を比較して `MfTokenStatus.missingScopes`
// を返し、`MfScopeBanner` で UI 警告を出す仕組みになっている。
export const MF_REQUIRED_SCOPES = [
  'mfc/accounting/journal.read',
  'mfc/accounting/accounts.read',
  'mfc/accounting/offices.read',
  'mfc/accounting/taxes.read',
  'mfc/accounting/report.read',
  'mfc/admin/tenant.read',
] as const

export const MF_DEFAULT_SCOPE = MF_REQUIRED_SCOPES.join(' ')

export const MF_DEFAULT_CONFIG: Omit<MfOauthClientRequest, 'clientId' | 'clientSecret'> = {
  redirectUri: defaultMfRedirectUri(),
  scope: MF_DEFAULT_SCOPE,
  authorizeUrl: 'https://api.biz.moneyforward.com/authorize',
  tokenUrl: 'https://api.biz.moneyforward.com/token',
  apiBaseUrl: 'https://api-accounting.moneyforward.com',
}
