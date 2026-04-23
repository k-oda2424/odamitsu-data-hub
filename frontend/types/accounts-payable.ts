export interface AccountsPayable {
  shopNo: number
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  transactionMonth: string // yyyy-MM-dd
  taxRate: number
  taxIncludedAmount: number | null
  taxExcludedAmount: number | null
  taxIncludedAmountChange: number | null
  taxExcludedAmountChange: number | null
  paymentDifference: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean | null
  verifiedManually: boolean
  verificationNote: string | null
  /** 検証時の請求額（振込明細 or 手入力の税込）。 */
  verifiedAmount: number | null
  /** V026: verified_amount に対応する税抜確定額 (振込明細 Excel 由来)。 */
  verifiedAmountTaxExcluded?: number | null
  /** V026: 振込明細取込時の自動調整額 (= verified_amount − tax_included_amount_change)。0 なら調整なし。 */
  autoAdjustedAmount?: number | null
  /**
   * 検証経路の区分（backend 計算フィールド）。
   * - BULK:   振込明細Excelでの一括検証
   * - MANUAL: 詳細ダイアログでの手入力検証
   * - null:   SMILE自動検証など（verifiedManually=false）
   */
  verificationSource: 'BULK' | 'MANUAL' | null
  /** 支払予定日 (MF CSV 出力時の取引日)。振込明細取込時にset。 */
  mfTransferDate: string | null
}

/**
 * 累積残関連フィールド。API に `include=balance` を指定した時のみ set される。
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §4.6
 *         claudedocs/design-phase-b-prime-payment-settled.md §5 (Phase B')
 */
export interface AccountsPayableBalance {
  /** 前月末時点の累積残 (税込・符号あり)。 */
  openingBalanceTaxIncluded: number
  /** 前月末時点の累積残 (税抜・符号あり)。 */
  openingBalanceTaxExcluded: number
  /** 当月完了した支払額 (税込、supplier 単位を change 比で按分)。Phase B'。 */
  paymentSettledTaxIncluded: number
  /** 当月完了した支払額 (税抜)。Phase B'。 */
  paymentSettledTaxExcluded: number
  /** 当月末時点の累積残 (税込・符号あり)。closing = opening + change - payment_settled。 */
  closingBalanceTaxIncluded: number
  /** 当月末時点の累積残 (税抜・符号あり)。 */
  closingBalanceTaxExcluded: number
  /** payment-only 行 (当月仕入無し、前月支払ありの supplier の支払計上行) か。 */
  isPaymentOnly: boolean
}

/** include=balance 指定時の Response 型 (balance フィールドが必須)。 */
export type AccountsPayableWithBalance = AccountsPayable & AccountsPayableBalance

/** balance が含まれているか判定する type guard。 */
export function hasBalance(row: AccountsPayable): row is AccountsPayableWithBalance {
  return (row as Partial<AccountsPayableBalance>).openingBalanceTaxIncluded !== undefined
}

export interface AccountsPayableSummary {
  transactionMonth: string
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

/** 累積残の符号フィルタ（検証フィルタとは独立軸）。 */
export type BalanceFilter = 'all' | 'negative' | 'positive'

export const BALANCE_FILTER_LABELS: Record<BalanceFilter, string> = {
  all: 'すべて',
  negative: '累積負残のみ',
  positive: '正残のみ',
}

export interface VerifyRequest {
  verifiedAmount: number
  note?: string | null
}

export function defaultTransactionMonth(today = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (d.getDate() < 20) {
    d.setMonth(d.getMonth() - 1)
  }
  d.setDate(20)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}-20`
}

export function toMonthInput(dateStr: string): string {
  return dateStr.slice(0, 7)
}

export function fromMonthInput(monthStr: string): string {
  return `${monthStr}-20`
}
