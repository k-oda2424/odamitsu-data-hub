/**
 * 買掛帳画面 (accounts-payable-ledger) の型定義。
 * 設計書: claudedocs/design-accounts-payable-ledger.md §7
 */

export interface SupplierInfo {
  shopNo: number
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
}

export type AnomalyCode =
  | 'UNVERIFIED'
  | 'VERIFY_DIFF'
  | 'NEGATIVE_CLOSING'
  | 'PAYMENT_OVER'
  | 'CONTINUITY_BREAK'
  | 'MONTH_GAP'
  | 'MF_DELTA_MISMATCH'

export type AnomalySeverity = 'CRITICAL' | 'WARN' | 'INFO'

export interface Anomaly {
  code: AnomalyCode
  severity: AnomalySeverity
  message: string
}

export interface TaxRateInfo {
  taxRate: number
  verifiedManually: boolean | null
  verificationResult: 0 | 1 | null
  isPaymentOnly: boolean | null
  mfExportEnabled: boolean | null
  mfTransferDate: string | null
}

export interface LedgerRow {
  transactionMonth: string // yyyy-MM-dd
  openingBalanceTaxIncluded: number
  /** 生値の 仕入 change (tax_included_amount_change 合算)。 */
  changeTaxIncluded: number
  /** closing 算出で実際に使われた change (手動確定時は verified_amount 優先、Phase B' 仕様)。 */
  effectiveChangeTaxIncluded: number
  verifiedAmount: number
  /** V026: 振込明細取込時の自動調整額 (= verified - change)。0 なら調整なし。 */
  autoAdjustedAmount?: number
  paymentSettledTaxIncluded: number
  closingBalanceTaxIncluded: number
  taxRateCount: number
  taxRateBreakdown: TaxRateInfo[]
  hasPaymentOnly: boolean
  hasVerifiedManually: boolean
  anomalies: Anomaly[]
  continuityOk: boolean
}

export interface LedgerSummary {
  totalChangeTaxIncluded: number
  totalVerified: number
  totalPaymentSettled: number
  finalClosing: number
  unverifiedMonthCount: number
  continuityBreakCount: number
  negativeClosingMonthCount: number
  paymentOnlyMonthCount: number
  monthGapCount: number
}

export interface AccountsPayableLedgerResponse {
  supplier: SupplierInfo
  fromMonth: string
  toMonth: string
  rows: LedgerRow[]
  summary: LedgerSummary
}

// ---- MF 比較 ----

export interface MfLedgerRow {
  transactionMonth: string
  mfCreditInMonth: number
  mfDebitInMonth: number
  mfPeriodDelta: number
  /** MF 累積残 = 期首 (2025-05-20) 〜当月の Σ(credit − debit)。 */
  mfCumulativeBalance: number
}

export interface MfSupplierLedgerResponse {
  shopNo: number
  supplierNo: number
  supplierName: string | null
  fromMonth: string
  toMonth: string
  matchedSubAccountNames: string[]
  unmatchedCandidates: string[]
  rows: MfLedgerRow[]
  fetchedAt: string
  totalJournalCount: number
  /** MF /journals fetch 開始日 (fiscal year 境界 fallback で実際に採用された値)。 */
  mfStartDate?: string
  /** MF /journals fetch 終了日 (= toMonth)。 */
  mfEndDate?: string
  /**
   * 前期繰越 (supplier 単位期首残)。m_supplier_opening_balance から取得。
   * MF cumulative の初期値に使用 (journal #1 自体は accumulation から除外)。
   * 未登録 supplier は 0。
   */
  openingBalance?: number
}

// ---- UI 用ラベル ----

export const ANOMALY_SHORT_LABEL: Record<AnomalyCode, string> = {
  UNVERIFIED: 'UNV',
  VERIFY_DIFF: 'VDF',
  NEGATIVE_CLOSING: 'NEG',
  PAYMENT_OVER: 'POV',
  CONTINUITY_BREAK: 'BRK',
  MONTH_GAP: 'GAP',
  MF_DELTA_MISMATCH: 'MFX',
}

export const ANOMALY_BADGE_CLASS: Record<AnomalyCode, string> = {
  UNVERIFIED: 'border-red-500 bg-red-50 text-red-700',
  VERIFY_DIFF: 'border-orange-500 bg-orange-50 text-orange-700',
  NEGATIVE_CLOSING: 'border-amber-500 bg-amber-50 text-amber-700',
  PAYMENT_OVER: 'border-amber-500 bg-amber-50 text-amber-700',
  CONTINUITY_BREAK: 'border-red-600 bg-red-100 text-red-800',
  MONTH_GAP: 'border-orange-500 bg-orange-50 text-orange-700',
  MF_DELTA_MISMATCH: 'border-amber-500 bg-amber-50 text-amber-700',
}

export function highestSeverity(anomalies: Anomaly[]): AnomalySeverity | null {
  if (anomalies.length === 0) return null
  if (anomalies.some((a) => a.severity === 'CRITICAL')) return 'CRITICAL'
  if (anomalies.some((a) => a.severity === 'WARN')) return 'WARN'
  return 'INFO'
}

export function rowBgClass(severity: AnomalySeverity | null): string {
  switch (severity) {
    case 'CRITICAL':
      return 'bg-red-50'
    case 'WARN':
      return 'bg-orange-50'
    case 'INFO':
      return 'bg-amber-50'
    default:
      return ''
  }
}

/** 終了月の初期値: 直近の 20日締め月 (今日が 20日以降なら当月、そうでなければ前月)。 */
export function defaultToMonth(today = new Date()): string {
  const d = new Date(today)
  if (d.getDate() < 20) {
    d.setMonth(d.getMonth() - 1)
  }
  d.setDate(20)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}-20`
}

/** 開始月の初期値: toMonth の 12 ヶ月前。 */
export function defaultFromMonth(toMonthIso: string): string {
  const [y, m] = toMonthIso.split('-').map(Number)
  const d = new Date(y, m - 1 - 12, 20)
  const ry = d.getFullYear()
  const rm = String(d.getMonth() + 1).padStart(2, '0')
  return `${ry}-${rm}-20`
}
