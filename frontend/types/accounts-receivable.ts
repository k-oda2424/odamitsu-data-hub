/**
 * 売掛金一覧画面の型定義。買掛側 accounts-payable.ts と対称。
 */

export interface AccountsReceivable {
  shopNo: number
  partnerNo: number
  partnerCode: string | null
  partnerName: string | null
  transactionMonth: string // yyyy-MM-dd
  taxRate: number
  isOtakeGarbageBag: boolean
  cutoffDate: number | null
  orderNo: number | null
  taxIncludedAmount: number | null
  taxExcludedAmount: number | null
  taxIncludedAmountChange: number | null
  taxExcludedAmountChange: number | null
  invoiceAmount: number | null
  verificationDifference: number | null
  invoiceNo: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean | null
  verifiedManually: boolean
  verificationNote: string | null
}

export interface AccountsReceivableSummary {
  fromDate: string | null
  toDate: string | null
  totalCount: number
  unverifiedCount: number
  unmatchedCount: number
  matchedCount: number
  unmatchedDifferenceSum: number
}

export type VerificationFilter = 'all' | 'unverified' | 'unmatched' | 'matched'

export const VERIFICATION_FILTER_LABELS: Record<VerificationFilter, string> = {
  all: 'すべて',
  unverified: '未検証',
  unmatched: '不一致',
  matched: '一致',
}

export type CutoffType = 'all' | '15' | '20' | 'month_end'

export const CUTOFF_TYPE_LABELS: Record<CutoffType, string> = {
  all: 'すべて',
  '15': '15日締め',
  '20': '20日締め',
  month_end: '月末締め',
}

export interface VerifyRequest {
  taxIncludedAmount: number
  taxExcludedAmount: number
  note?: string | null
  mfExportEnabled?: boolean
}

export interface BulkVerifyResponse {
  matchedCount: number
  mismatchCount: number
  notFoundCount: number
  skippedManualCount: number
  josamaOverwriteCount: number
  quarterlySpecialCount: number
  /** 請求書の締め日に合わせて自動再集計した得意先件数。 */
  reconciledPartners: number
  /** 再集計で削除された旧 AR 行の件数。 */
  reconciledDeletedRows: number
  /** 再集計で新規挿入された AR 行の件数。 */
  reconciledInsertedRows: number
  /** 手動確定済のため再集計をスキップした得意先件数。 */
  reconciledSkippedManualPartners: number
  /** 再集計された得意先の一覧 ("partner_code (YYYY-MM: 月末)" 形式)。 */
  reconciledDetails?: string[]
}

export interface AggregateResponse {
  status: string
  targetDate: string
  cutoffType: string
}

/**
 * デフォルト期間: 直近の 20日締め期間を返す。
 * - 当月20日までなら前月21日〜当月20日
 * - 当月21日以降なら当月21日〜翌月20日（ただし未来なので通常は当月20日基準）
 */
export function defaultDateRange(today = new Date()): { fromDate: string; toDate: string } {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  let toY = d.getFullYear()
  let toM = d.getMonth()
  if (d.getDate() < 20) {
    toM -= 1
    if (toM < 0) {
      toM = 11
      toY -= 1
    }
  }
  const to = new Date(toY, toM, 20)
  const from = new Date(toY, toM - 1, 21)
  const fmt = (x: Date) =>
    `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return { fromDate: fmt(from), toDate: fmt(to) }
}

export function toYyyyMmDd(isoDate: string): string {
  return isoDate.replaceAll('-', '')
}

/** PK を URL 経路に変換（手動確定/MF切替エンドポイント用） */
export function pkToPath(ar: AccountsReceivable): string {
  return `${ar.shopNo}/${ar.partnerNo}/${ar.transactionMonth}/${ar.taxRate}/${ar.isOtakeGarbageBag}`
}
